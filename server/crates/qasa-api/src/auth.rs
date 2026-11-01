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
        // A valid signature is not enough: the device may have been unlinked since the
        // token was issued, or the token revoked by an admin.
        let live = qasa_db::repos::device::is_license_live(
            &state.pool,
            claims.account_id()?,
            claims.device_id()?,
            &claims.jti,
            claims.iat,
        )
        .await?;
        if !live {
            return Err(ApiError::Core(qasa_core::CoreError::Token(
                "token revoked".into(),
            )));
        }
        Ok(LicenseAuth(claims))
    }
}

/// Any signed-in admin (read access). Besides the signature, the admin must still exist,
/// not be disabled, and not have logged this token out; `claims.role` is replaced with
/// the role currently stored, so a demotion applies on the next request.
pub struct AdminAuth(pub qasa_core::AdminClaims);

impl FromRequestParts<AppState> for AdminAuth {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, Self::Rejection> {
        let token = extract_bearer(&parts.headers)?.to_string();
        let mut claims = state.admin_issuer.verify(&token)?;
        let role = qasa_db::repos::admin::live_role(&state.pool, claims.admin_id()?, &claims.jti)
            .await?
            .ok_or_else(|| {
                ApiError::Core(qasa_core::CoreError::Token("admin session ended".into()))
            })?;
        claims.role = role;
        Ok(AdminAuth(claims))
    }
}

/// An admin allowed to change things. `viewer` admins get 403 on every mutating route.
pub struct AdminWrite(pub qasa_core::AdminClaims);

impl FromRequestParts<AppState> for AdminWrite {
    type Rejection = ApiError;

    async fn from_request_parts(parts: &mut Parts, state: &AppState) -> Result<Self, Self::Rejection> {
        let AdminAuth(claims) = AdminAuth::from_request_parts(parts, state).await?;
        if claims.role != "admin" {
            return Err(ApiError::Core(qasa_core::CoreError::Forbidden));
        }
        Ok(AdminWrite(claims))
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
