use thiserror::Error;

#[derive(Debug, Error)]
pub enum CoreError {
    #[error("invalid credentials")]
    InvalidCredentials,
    #[error("token error: {0}")]
    Token(String),
    #[error("device limit exceeded")]
    DeviceLimitExceeded,
    #[error("not found")]
    NotFound,
    #[error("forbidden")]
    Forbidden,
    #[error("bad request: {0}")]
    BadRequest(String),
}
