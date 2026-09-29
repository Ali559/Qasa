use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use qasa_core::CoreError;
use serde_json::json;

#[derive(Debug)]
pub enum ApiError {
    Core(CoreError),
    Internal(anyhow::Error),
    RateLimited,
    /// VPN / breach (and similar) require a live premium subscription.
    SubscriptionRequired,
}

impl From<CoreError> for ApiError {
    fn from(value: CoreError) -> Self {
        Self::Core(value)
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(value: anyhow::Error) -> Self {
        Self::Internal(value)
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(value: sqlx::Error) -> Self {
        Self::Internal(value.into())
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        match self {
            ApiError::Core(CoreError::InvalidCredentials) => (
                StatusCode::UNAUTHORIZED,
                Json(json!({ "error": "invalid_credentials" })),
            )
                .into_response(),
            ApiError::Core(CoreError::DeviceLimitExceeded) => (
                StatusCode::FORBIDDEN,
                Json(json!({ "error": "device_limit_exceeded" })),
            )
                .into_response(),
            ApiError::Core(CoreError::NotFound) => (
                StatusCode::NOT_FOUND,
                Json(json!({ "error": "not_found" })),
            )
                .into_response(),
            ApiError::Core(CoreError::Forbidden) => (
                StatusCode::FORBIDDEN,
                Json(json!({ "error": "forbidden" })),
            )
                .into_response(),
            ApiError::Core(CoreError::BadRequest(msg)) => (
                StatusCode::BAD_REQUEST,
                Json(json!({ "error": "bad_request", "message": msg })),
            )
                .into_response(),
            ApiError::Core(CoreError::Token(msg)) => (
                StatusCode::UNAUTHORIZED,
                Json(json!({ "error": "invalid_token", "message": msg })),
            )
                .into_response(),
            ApiError::RateLimited => (
                StatusCode::TOO_MANY_REQUESTS,
                Json(json!({
                    "error": "rate_limit_exceeded",
                    "message": "Maximum 60 prefix queries per hour."
                })),
            )
                .into_response(),
            ApiError::SubscriptionRequired => (
                StatusCode::FORBIDDEN,
                Json(json!({
                    "error": "subscription_required",
                    "message": "An active Premium subscription is required for this feature."
                })),
            )
                .into_response(),
            ApiError::Internal(err) => {
                tracing::error!(error = %err, "internal server error");
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    Json(json!({ "error": "internal_error" })),
                )
                    .into_response()
            }
        }
    }
}

pub type ApiResult<T> = Result<T, ApiError>;
