//! What crosses the channel. Byte fields are base64 in JSON.

use serde::{Deserialize, Serialize};

mod b64 {
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine;
    use serde::{Deserialize, Deserializer, Serializer};

    pub fn serialize<S: Serializer>(bytes: &[u8], s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&STANDARD.encode(bytes))
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(d: D) -> Result<Vec<u8>, D::Error> {
        let s = String::deserialize(d)?;
        STANDARD.decode(s).map_err(serde::de::Error::custom)
    }
}

/// The vault's key material, minus anything secret: the salt and KDF version let a new
/// device derive the same keys from the same main password; the verifier lets both sides
/// see they hold the same vault.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct VaultMeta {
    #[serde(with = "b64")]
    pub salt: Vec<u8>,
    pub kdf_version: u32,
    #[serde(with = "b64")]
    pub verifier: Vec<u8>,
}

/// Sent by both sides right after the channel opens.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct Intro {
    pub protocol: u32,
    pub device_name: String,
    pub platform: String,
    pub account_id: Option<String>,
    /// `None` on a device that has no vault yet (pairing a new device).
    pub vault: Option<VaultMeta>,
}

/// One item exactly as stored: envelope and payload are vault-key ciphertext, with the
/// payload in the canonical (iOS) JSON shape inside.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct WireItem {
    pub id: String,
    pub kind: String,
    #[serde(with = "b64")]
    pub envelope: Vec<u8>,
    #[serde(with = "b64")]
    pub payload: Vec<u8>,
    pub schema_version: i64,
    pub created_at: i64,
    pub updated_at: i64,
    pub last_used_at: Option<i64>,
    pub collection_id: Option<String>,
    /// A tombstone: the item was deleted at this time (ms). Envelope and payload are empty.
    pub deleted_at: Option<i64>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
pub struct WireCollection {
    pub id: String,
    pub name: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub deleted_at: Option<i64>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
pub struct Snapshot {
    pub items: Vec<WireItem>,
    pub collections: Vec<WireCollection>,
}

/// Session messages after the channel is open.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Message {
    Intro(Intro),
    Snapshot(Snapshot),
    Done { applied: u32 },
    Refuse { reason: String },
}
