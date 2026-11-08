//! The vault's view of Touch ID / Windows Hello (`apps/desktop/biometric`), behind a trait
//! so tests run without hardware.

use qasa_biometric::{Error, Kind, Sealed};
use serde::Serialize;

#[derive(Serialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct BiometricStatus {
    /// What this computer offers (`none` on Linux or without enrolled biometrics).
    pub kind: Kind,
    /// The user turned it on.
    pub enabled: bool,
    /// A seal exists. Enabled but not sealed is iOS's "Sealed on your next master password
    /// unlock".
    pub sealed: bool,
}

pub trait Biometrics: Send {
    fn kind(&self) -> Kind;
    fn available(&self) -> bool {
        self.kind() != Kind::None
    }
    fn seal(&self, secret: &[u8; 32]) -> Result<Sealed, Error>;
    fn unseal(&self, sealed: &Sealed, reason: &str) -> Result<[u8; 32], Error>;
}

pub struct PlatformBiometrics;

impl Biometrics for PlatformBiometrics {
    fn kind(&self) -> Kind {
        qasa_biometric::kind()
    }
    fn seal(&self, secret: &[u8; 32]) -> Result<Sealed, Error> {
        qasa_biometric::seal(secret)
    }
    fn unseal(&self, sealed: &Sealed, reason: &str) -> Result<[u8; 32], Error> {
        qasa_biometric::unseal(sealed, reason)
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;

    /// "Seals" by XOR with a fixed pad; enough to test the vault's lifecycle.
    #[derive(Default)]
    pub struct FakeBiometrics;

    impl Biometrics for FakeBiometrics {
        fn kind(&self) -> Kind {
            Kind::TouchId
        }
        fn seal(&self, secret: &[u8; 32]) -> Result<Sealed, Error> {
            Ok(Sealed {
                platform: "fake".into(),
                version: 1,
                key_ref: vec![],
                peer: vec![],
                ciphertext: secret.iter().map(|b| b ^ 0x5a).collect(),
            })
        }
        fn unseal(&self, sealed: &Sealed, _reason: &str) -> Result<[u8; 32], Error> {
            let bytes: Vec<u8> = sealed.ciphertext.iter().map(|b| b ^ 0x5a).collect();
            bytes.try_into().map_err(|_| Error::Invalidated)
        }
    }
}
