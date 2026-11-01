//! Reading and changing a WireGuard interface's peers, for the node agent.
//!
//! Pure functions over `wg show <iface> dump` output, so the sync logic is testable
//! without a WireGuard interface.

use std::collections::HashMap;

use crate::config::{DesiredPeer, PeerStat};

/// A peer currently configured on the interface.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WgPeer {
    pub public_key: String,
    pub allowed_ips: String,
    pub stat: PeerStat,
}

/// Parse `wg show <iface> dump`. The first line describes the interface itself; every
/// other line is a peer: public key, preshared key, endpoint, allowed IPs, latest
/// handshake, rx bytes, tx bytes, keepalive — tab separated.
pub fn parse_dump(dump: &str) -> Vec<WgPeer> {
    dump.lines()
        .skip(1)
        .filter_map(|line| {
            let f: Vec<&str> = line.split('\t').collect();
            if f.len() < 8 {
                return None;
            }
            Some(WgPeer {
                public_key: f[0].to_string(),
                allowed_ips: f[3].to_string(),
                stat: PeerStat {
                    public_key: f[0].to_string(),
                    last_handshake: f[4].parse().unwrap_or(0),
                    rx_bytes: f[5].parse().unwrap_or(0),
                    tx_bytes: f[6].parse().unwrap_or(0),
                },
            })
        })
        .collect()
}

/// A change to apply with `wg set`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WgOp {
    /// Add the peer, or correct its allowed IPs.
    Set { public_key: String, allowed_ip: String },
    Remove { public_key: String },
}

/// What to change so the interface has exactly the desired peers. Peers the API no
/// longer lists (disconnected, expired, unlinked, blocked) are removed.
pub fn plan(current: &[WgPeer], desired: &[DesiredPeer]) -> Vec<WgOp> {
    let have: HashMap<&str, &str> = current
        .iter()
        .map(|p| (p.public_key.as_str(), p.allowed_ips.as_str()))
        .collect();
    let want: HashMap<&str, &str> = desired
        .iter()
        .map(|p| (p.public_key.as_str(), p.allowed_ip.as_str()))
        .collect();

    let mut ops: Vec<WgOp> = desired
        .iter()
        .filter(|d| have.get(d.public_key.as_str()) != Some(&d.allowed_ip.as_str()))
        .map(|d| WgOp::Set {
            public_key: d.public_key.clone(),
            allowed_ip: d.allowed_ip.clone(),
        })
        .collect();
    ops.extend(
        current
            .iter()
            .filter(|p| !want.contains_key(p.public_key.as_str()))
            .map(|p| WgOp::Remove {
                public_key: p.public_key.clone(),
            }),
    );
    ops
}

/// `wg set` arguments for one change.
pub fn wg_set_args(interface: &str, op: &WgOp) -> Vec<String> {
    match op {
        WgOp::Set {
            public_key,
            allowed_ip,
        } => vec![
            "set".into(),
            interface.into(),
            "peer".into(),
            public_key.clone(),
            "allowed-ips".into(),
            allowed_ip.clone(),
        ],
        WgOp::Remove { public_key } => vec![
            "set".into(),
            interface.into(),
            "peer".into(),
            public_key.clone(),
            "remove".into(),
        ],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const A: &str = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    const B: &str = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBA=";
    const C: &str = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA=";

    fn dump() -> String {
        [
            "privkey\tpubkey\t51820\toff",
            &format!("{A}\t(none)\t1.2.3.4:5555\t10.64.0.2/32\t1793600000\t1000\t2000\t25"),
            &format!("{B}\t(none)\t(none)\t10.64.0.3/32\t0\t0\t0\toff"),
        ]
        .join("\n")
    }

    fn want(key: &str, ip: &str) -> DesiredPeer {
        DesiredPeer {
            public_key: key.into(),
            allowed_ip: ip.into(),
            throttle_mbps: None,
        }
    }

    #[test]
    fn parses_peers_and_skips_the_interface_line() {
        let peers = parse_dump(&dump());
        assert_eq!(peers.len(), 2);
        assert_eq!(peers[0].allowed_ips, "10.64.0.2/32");
        assert_eq!(
            peers[0].stat,
            PeerStat {
                public_key: A.into(),
                last_handshake: 1_793_600_000,
                rx_bytes: 1000,
                tx_bytes: 2000
            }
        );
        assert_eq!(peers[1].stat.last_handshake, 0, "never handshaken");
        assert!(parse_dump("").is_empty());
        assert!(parse_dump("iface only\ngarbage line").is_empty());
    }

    #[test]
    fn plan_adds_corrects_and_removes() {
        let current = parse_dump(&dump());
        let ops = plan(
            &current,
            &[want(A, "10.64.0.2/32"), want(B, "10.64.0.9/32"), want(C, "10.64.0.4/32")],
        );
        assert_eq!(
            ops,
            vec![
                WgOp::Set { public_key: B.into(), allowed_ip: "10.64.0.9/32".into() },
                WgOp::Set { public_key: C.into(), allowed_ip: "10.64.0.4/32".into() },
            ],
            "A is already right; B's address changed; C is new"
        );

        let ops = plan(&current, &[want(A, "10.64.0.2/32")]);
        assert_eq!(ops, vec![WgOp::Remove { public_key: B.into() }]);

        assert!(plan(&current, &[want(A, "10.64.0.2/32"), want(B, "10.64.0.3/32")]).is_empty());
    }

    #[test]
    fn wg_set_arguments() {
        assert_eq!(
            wg_set_args("wg0", &WgOp::Remove { public_key: A.into() }),
            ["set", "wg0", "peer", A, "remove"]
        );
        assert_eq!(
            wg_set_args("wg0", &WgOp::Set { public_key: A.into(), allowed_ip: "10.64.0.2/32".into() }),
            ["set", "wg0", "peer", A, "allowed-ips", "10.64.0.2/32"]
        );
    }

    #[test]
    fn wg_key_validation() {
        assert!(crate::is_wg_key(A));
        assert!(!crate::is_wg_key("dev-client-key-123"));
        assert!(!crate::is_wg_key(""));
        assert!(!crate::is_wg_key("AAAA"));
    }
}
