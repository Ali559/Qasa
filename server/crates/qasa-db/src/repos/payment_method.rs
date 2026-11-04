//! Payment methods saved for automatic renewals.
//!
//! A row holds the gateway's opaque token for the customer's wallet or card, never the
//! details themselves. An account has at most one live method: the newest checkout the
//! customer paid with auto-renew on.

use chrono::{DateTime, Utc};
use qasa_core::new_id;
use sqlx::{PgConnection, PgPool};
use uuid::Uuid;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct PaymentMethodRow {
    pub id: Uuid,
    pub account_id: Uuid,
    pub gateway: String,
    pub token: String,
    pub created_at: DateTime<Utc>,
}

/// Make `token` the account's live method, retiring the one it replaces.
pub async fn save(
    conn: &mut PgConnection,
    account_id: Uuid,
    gateway: &str,
    token: &str,
) -> anyhow::Result<()> {
    sqlx::query(
        "UPDATE payment_methods SET revoked_at = now() WHERE account_id = $1 AND revoked_at IS NULL",
    )
    .bind(account_id)
    .execute(&mut *conn)
    .await?;
    sqlx::query(
        "INSERT INTO payment_methods (id, account_id, gateway, token) VALUES ($1, $2, $3, $4)",
    )
    .bind(new_id())
    .bind(account_id)
    .bind(gateway)
    .bind(token)
    .execute(&mut *conn)
    .await?;
    Ok(())
}

pub async fn live_for_account(
    pool: &PgPool,
    account_id: Uuid,
) -> anyhow::Result<Option<PaymentMethodRow>> {
    let row = sqlx::query_as::<_, PaymentMethodRow>(
        r#"
        SELECT id, account_id, gateway, token, created_at
        FROM payment_methods
        WHERE account_id = $1 AND revoked_at IS NULL
        "#,
    )
    .bind(account_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

/// Forget the account's saved method. Returns `false` if it had none.
pub async fn revoke_for_account(pool: &PgPool, account_id: Uuid) -> anyhow::Result<bool> {
    let n = sqlx::query(
        "UPDATE payment_methods SET revoked_at = now() WHERE account_id = $1 AND revoked_at IS NULL",
    )
    .bind(account_id)
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n > 0)
}

/// Drop one method the gateway said is no longer valid.
pub async fn revoke(pool: &PgPool, method_id: Uuid) -> anyhow::Result<()> {
    sqlx::query("UPDATE payment_methods SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL")
        .bind(method_id)
        .execute(pool)
        .await?;
    Ok(())
}
