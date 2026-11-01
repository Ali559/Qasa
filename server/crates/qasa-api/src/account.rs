use axum::extract::State;
use axum::Json;
use qasa_core::CoreError;
use serde::Deserialize;
use serde_json::json;
use uuid::Uuid;

use crate::auth::LicenseAuth;
use crate::error::ApiResult;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct DeleteAccountRequest {
    /// Must repeat the account id the token belongs to. A stray or replayed request
    /// without it cannot delete anything; the app shows its own confirm step first.
    pub confirm_account_id: Uuid,
}

/// Permanently delete the caller's account (see `qasa_db::repos::account_deletion`).
pub async fn delete_mine(
    State(state): State<AppState>,
    LicenseAuth(claims): LicenseAuth,
    Json(body): Json<DeleteAccountRequest>,
) -> ApiResult<Json<serde_json::Value>> {
    let account_id = claims.account_id()?;
    if body.confirm_account_id != account_id {
        return Err(CoreError::BadRequest(
            "confirm_account_id does not match this account".into(),
        )
        .into());
    }

    let summary = qasa_db::repos::account_deletion::delete_account(&state.pool, account_id)
        .await?
        .ok_or(CoreError::NotFound)?;
    tracing::info!(%account_id, ?summary, "account deleted by user");

    Ok(Json(json!({ "deleted": true, "removed": summary })))
}
