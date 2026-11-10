//! Account secrets in the OS keychain (macOS Keychain, Windows Credential Manager, Linux
//! Secret Service), like iOS `VaultKeychainStore`: the account id, this device's
//! fingerprint, the license token, the cached license verifying key, and the backup
//! restore keys (base64; they never leave Rust). Tests use the in-memory store.

const SERVICE: &str = "com.qasa-app.desktop";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Secret {
    AccountId,
    DeviceFingerprint,
    LicenseToken,
    LicenseVerifyingKey,
    /// This device's current backup restore key (32 bytes), set once the server confirmed
    /// its hash.
    RestoreKey,
    /// `"1"` once a backup file holding `RestoreKey` was written.
    RestoreKeyWritten,
    /// The key from a backup this device restored, until registration has used it.
    PendingRestoreKey,
}

impl Secret {
    pub const ALL: [Secret; 7] = [
        Secret::AccountId,
        Secret::DeviceFingerprint,
        Secret::LicenseToken,
        Secret::LicenseVerifyingKey,
        Secret::RestoreKey,
        Secret::RestoreKeyWritten,
        Secret::PendingRestoreKey,
    ];
    /// The backup restore secrets, cleared by every local reset.
    pub const RESTORE: [Secret; 3] = [Secret::RestoreKey, Secret::RestoreKeyWritten, Secret::PendingRestoreKey];

    fn key(self) -> &'static str {
        match self {
            Secret::AccountId => "qasa.accountId",
            Secret::DeviceFingerprint => "qasa.deviceFingerprint",
            Secret::LicenseToken => "qasa.licenseToken",
            Secret::LicenseVerifyingKey => "qasa.license.verifyingKey.b64",
            Secret::RestoreKey => "qasa.restoreKey.b64",
            Secret::RestoreKeyWritten => "qasa.restoreKeyWritten",
            Secret::PendingRestoreKey => "qasa.pendingRestoreKey.b64",
        }
    }
}

pub trait SecretStore: Send + Sync {
    fn get(&self, secret: Secret) -> Option<String>;
    fn set(&self, secret: Secret, value: Option<&str>);
}

/// The OS keychain. Read and write failures are treated as "absent": the vault still
/// works offline, and account features retry on the next unlock.
pub struct KeyringStore;

impl SecretStore for KeyringStore {
    fn get(&self, secret: Secret) -> Option<String> {
        keyring::Entry::new(SERVICE, secret.key()).ok()?.get_password().ok()
    }

    fn set(&self, secret: Secret, value: Option<&str>) {
        let Ok(entry) = keyring::Entry::new(SERVICE, secret.key()) else { return };
        let result = match value {
            Some(v) => entry.set_password(v),
            None => match entry.delete_credential() {
                Err(keyring::Error::NoEntry) => Ok(()),
                other => other,
            },
        };
        if let Err(e) = result {
            eprintln!("[qasa] keychain write failed for {}: {e}", secret.key());
        }
    }
}

#[cfg(test)]
#[derive(Default)]
pub struct MemoryStore(std::sync::Mutex<std::collections::HashMap<Secret, String>>);

#[cfg(test)]
impl SecretStore for MemoryStore {
    fn get(&self, secret: Secret) -> Option<String> {
        self.0.lock().ok()?.get(&secret).cloned()
    }

    fn set(&self, secret: Secret, value: Option<&str>) {
        if let Ok(mut map) = self.0.lock() {
            match value {
                Some(v) => map.insert(secret, v.to_string()),
                None => map.remove(&secret),
            };
        }
    }
}
