use axum::extract::FromRequestParts;
use axum::http::header::AUTHORIZATION;
use axum::http::request::Parts;
use qasa_core::LicenseClaims;

use crate::error::{ApiError, ApiResult};
use crate::state::AppState;

pub struct LicenseAuth(pub LicenseClaims);

impl FromRequestParts<AppState> for LicenseAuth {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, Self::Rejection> {
        let token = extract_bearer(&parts.headers)?.to_string();
        let claims = state.license_issuer.verify(&token)?;
        if qasa_db::repos::device::is_jti_revoked(&state.pool, &claims.jti).await? {
            return Err(ApiError::Core(qasa_core::CoreError::Token(
                "token revoked".into(),
            )));
        }
        Ok(LicenseAuth(claims))
    }
}

pub struct AdminAuth(pub qasa_core::AdminClaims);

impl FromRequestParts<AppState> for AdminAuth {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, Self::Rejection> {
        let token = extract_bearer(&parts.headers)?.to_string();
        let claims = state.admin_issuer.verify(&token)?;
        Ok(AdminAuth(claims))
    }
}

fn extract_bearer(headers: &axum::http::HeaderMap) -> ApiResult<&str> {
    let value = headers
        .get(AUTHORIZATION)
        .ok_or_else(|| ApiError::Core(qasa_core::CoreError::Token("missing bearer token".into())))?
        .to_str()
        .map_err(|e| ApiError::Core(qasa_core::CoreError::Token(e.to_string())))?;
    value
        .strip_prefix("Bearer ")
        .ok_or_else(|| ApiError::Core(qasa_core::CoreError::Token("expected bearer token".into())))
}
