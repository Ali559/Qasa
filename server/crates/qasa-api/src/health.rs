use axum::extract::State;
use axum::Json;
use serde_json::json;

use crate::error::ApiResult;
use crate::state::AppState;

pub async fn health(State(state): State<AppState>) -> ApiResult<Json<serde_json::Value>> {
    sqlx::query("SELECT 1")
        .execute(&state.pool)
        .await?;
    Ok(Json(json!({ "status": "ok" })))
}
