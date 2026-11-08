use serde::Serialize;

/// Errors a vault command returns to the UI. Serialized as `{"code": …, "message": …}`
/// so screens switch on `code` and never show raw text.
#[derive(Debug, thiserror::Error)]
pub enum CommandError {
    #[error("the vault is locked")]
    Locked,
    #[error("no vault exists on this computer")]
    NoVault,
    #[error("a vault already exists on this computer")]
    VaultExists,
    #[error("wrong master password")]
    WrongPassword,
    #[error("the master password must be at least 8 characters")]
    WeakPassword,
    #[error("this vault was created by a newer version of Qasa")]
    UnsupportedKdf,
    #[error("item not found")]
    NotFound,
    #[error("invalid input: {0}")]
    Invalid(String),
    #[error("storage error: {0}")]
    Storage(String),
    #[error("vault data could not be decrypted")]
    Corrupt,
}

impl CommandError {
    fn code(&self) -> &'static str {
        match self {
            CommandError::Locked => "locked",
            CommandError::NoVault => "noVault",
            CommandError::VaultExists => "vaultExists",
            CommandError::WrongPassword => "wrongPassword",
            CommandError::WeakPassword => "weakPassword",
            CommandError::UnsupportedKdf => "unsupportedKdf",
            CommandError::NotFound => "notFound",
            CommandError::Invalid(_) => "invalid",
            CommandError::Storage(_) => "storage",
            CommandError::Corrupt => "corrupt",
        }
    }
}

impl Serialize for CommandError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("CommandError", 2)?;
        s.serialize_field("code", self.code())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

impl From<rusqlite::Error> for CommandError {
    fn from(e: rusqlite::Error) -> Self {
        CommandError::Storage(e.to_string())
    }
}

impl From<qasa_vault::VaultError> for CommandError {
    fn from(e: qasa_vault::VaultError) -> Self {
        match e {
            qasa_vault::VaultError::UnsupportedKdfVersion(_) => CommandError::UnsupportedKdf,
            qasa_vault::VaultError::InvalidSalt => CommandError::Corrupt,
            qasa_vault::VaultError::InvalidCiphertext | qasa_vault::VaultError::DecryptionFailed => CommandError::Corrupt,
        }
    }
}

pub type CommandResult<T> = Result<T, CommandError>;
