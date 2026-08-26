use std::net::Ipv4Addr;

use thiserror::Error;

#[derive(Debug, Error)]
pub enum PoolError {
    #[error("invalid CIDR: {0}")]
    InvalidCidr(String),
    #[error("no available IPs in pool")]
    Exhausted,
}

/// Parse a CIDR string and return network address + prefix length.
pub fn parse_cidr(cidr: &str) -> Result<(Ipv4Addr, u8), PoolError> {
    let parts: Vec<&str> = cidr.split('/').collect();
    if parts.len() != 2 {
        return Err(PoolError::InvalidCidr(cidr.into()));
    }
    let network: Ipv4Addr = parts[0]
        .parse()
        .map_err(|_| PoolError::InvalidCidr(cidr.into()))?;
    let prefix: u8 = parts[1]
        .parse()
        .map_err(|_| PoolError::InvalidCidr(cidr.into()))?;
    Ok((network, prefix))
}

/// Allocate the next free IP from a CIDR, skipping network/broadcast and already-used IPs.
pub fn allocate_ip(cidr: &str, used: &[Ipv4Addr]) -> Result<Ipv4Addr, PoolError> {
    let (network, prefix) = parse_cidr(cidr)?;
    let host_bits = 32 - prefix as u32;
    let total_hosts = 1u32 << host_bits;
    if total_hosts < 4 {
        return Err(PoolError::Exhausted);
    }

    let network_u32 = u32::from(network);
    // Skip network address (.0) and reserve .1 for gateway
    for offset in 2..total_hosts - 1 {
        let candidate = Ipv4Addr::from(network_u32 + offset);
        if !used.iter().any(|ip| *ip == candidate) {
            return Ok(candidate);
        }
    }
    Err(PoolError::Exhausted)
}

/// Pick the VPN node with the lowest utilization under 80% capacity.
pub fn select_node_id<T: NodeCandidate>(nodes: &[T]) -> Option<&T> {
    nodes
        .iter()
        .filter(|n| n.is_eligible())
        .min_by(|a, b| {
            a.utilization()
                .partial_cmp(&b.utilization())
                .unwrap_or(std::cmp::Ordering::Equal)
        })
}

pub trait NodeCandidate {
    fn is_eligible(&self) -> bool;
    fn utilization(&self) -> f64;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allocates_first_free_ip() {
        let ip = allocate_ip("10.64.0.0/24", &[]).unwrap();
        assert_eq!(ip, "10.64.0.2".parse::<Ipv4Addr>().unwrap());
    }

    #[test]
    fn skips_used_ips() {
        let used = vec!["10.64.0.2".parse().unwrap()];
        let ip = allocate_ip("10.64.0.0/24", &used).unwrap();
        assert_eq!(ip, "10.64.0.3".parse::<Ipv4Addr>().unwrap());
    }
}
