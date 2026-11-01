use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow, Serialize)]
pub struct AccountRow {
    pub id: Uuid,
    pub public_id: String,
    pub tier: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, sqlx::FromRow, Serialize)]
pub struct DeviceRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub fingerprint: String,
    pub device_name: String,
    pub platform: String,
    pub location: Option<String>,
    pub license_jti: Option<String>,
    pub last_seen_at: Option<DateTime<Utc>>,
    pub revoked_at: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct AdminUserRow {
    pub id: Uuid,
    pub email: String,
    pub password_hash: String,
    pub role: String,
}

#[derive(Debug, Clone, sqlx::FromRow, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminUserListItem {
    pub id: String,
    pub status: String,
    pub tier: String,
    pub devices: String,
    pub expiry: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expiry_alert: Option<bool>,
}
