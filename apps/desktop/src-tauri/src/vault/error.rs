use serde::Serialize;

/// Errors a vault command returns to the UI. Serialized as `{"code": …, "message": …}`
/// (plus `issues` for `invalidFields`) so screens switch on `code` and never show raw text.
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
    /// A user save that breaks the field rules (`qasa_vault::validate`). Nothing was
    /// written; `issues` says which fields to fix.
    #[error("some fields need fixing")]
    InvalidFields(Vec<qasa_vault::validate::FieldIssue>),
    #[error("storage error: {0}")]
    Storage(String),
    #[error("vault data could not be decrypted")]
    Corrupt,
    /// The fingerprint / Hello seal no longer opens the vault; use the main password.
    #[error("biometric unlock needs the main password again")]
    BiometricInvalidated,
    /// The user dismissed the biometric prompt.
    #[error("cancelled")]
    Cancelled,
    #[error("biometric unlock is not available")]
    BiometricUnavailable,
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
            CommandError::InvalidFields(_) => "invalidFields",
            CommandError::Storage(_) => "storage",
            CommandError::Corrupt => "corrupt",
            CommandError::BiometricInvalidated => "biometricInvalidated",
            CommandError::Cancelled => "cancelled",
            CommandError::BiometricUnavailable => "biometricUnavailable",
        }
    }
}

impl Serialize for CommandError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let issues = match self {
            CommandError::InvalidFields(issues) => Some(issues),
            _ => None,
        };
        let mut s = serializer.serialize_struct("CommandError", 2 + usize::from(issues.is_some()))?;
        s.serialize_field("code", self.code())?;
        s.serialize_field("message", &self.to_string())?;
        if let Some(issues) = issues {
            s.serialize_field("issues", issues)?;
        }
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
