//! Deleting an account at the user's request.
//!
//! App Store guideline 5.1.1(v) requires in-app account deletion. Qasa has no email or
//! name for an account, so "personal data" here is what describes the person or their
//! devices: device names, fingerprints, locations and push tokens, monitored email
//! hashes, VPN session logs, usage and notifications. All of that is deleted.
//!
//! What stays, keyed only by the now-meaningless account id: payments and subscriptions,
//! which are financial records. The account row itself remains as a tombstone with
//! `deleted_at` set, so the id can never be registered again.

use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Default, serde::Serialize)]
pub struct DeletionSummary {
    pub devices: u64,
    pub monitored_emails: u64,
    pub vpn_sessions: u64,
    pub notifications: u64,
    pub subscriptions_ended: u64,
    pub payments_closed: u64,
}

/// Returns `None` when the account does not exist or was already deleted.
pub async fn delete_account(
    pool: &PgPool,
    account_id: Uuid,
) -> anyhow::Result<Option<DeletionSummary>> {
    let mut tx = pool.begin().await?;

    let live: Option<Uuid> = sqlx::query_scalar(
        "SELECT id FROM accounts WHERE id = $1 AND deleted_at IS NULL FOR UPDATE",
    )
    .bind(account_id)
    .fetch_optional(&mut *tx)
    .await?;
    if live.is_none() {
        return Ok(None);
    }

    let mut summary = DeletionSummary::default();

    let devices: Vec<Uuid> = sqlx::query_scalar("SELECT id FROM devices WHERE account_id = $1")
        .bind(account_id)
        .fetch_all(&mut *tx)
        .await?;
    crate::repos::device::end_vpn_sessions(&mut tx, &devices).await?;

    summary.vpn_sessions = sqlx::query("DELETE FROM vpn_sessions WHERE account_id = $1")
        .bind(account_id)
        .execute(&mut *tx)
        .await?
        .rows_affected();

    // Targeted notifications (breach alerts, expiry reminders) were written for this
    // account alone; broadcasts are shared and only lose this account's receipt.
    summary.notifications = sqlx::query(
        r#"
        DELETE FROM notifications n
        WHERE n.audience = 'targeted'
          AND EXISTS (
            SELECT 1 FROM notification_receipts r
            WHERE r.notification_id = n.id AND r.account_id = $1
          )
          AND NOT EXISTS (
            SELECT 1 FROM notification_receipts r
            WHERE r.notification_id = n.id AND r.account_id <> $1
          )
        "#,
    )
    .bind(account_id)
    .execute(&mut *tx)
    .await?
    .rows_affected();

    for table in ["notification_receipts", "license_revocations", "vpn_usage"] {
        sqlx::query(&format!("DELETE FROM {table} WHERE account_id = $1"))
            .bind(account_id)
            .execute(&mut *tx)
            .await?;
    }

    summary.monitored_emails =
        sqlx::query("DELETE FROM monitored_email_hashes WHERE account_id = $1")
            .bind(account_id)
            .execute(&mut *tx)
            .await?
            .rows_affected();

    summary.devices = sqlx::query("DELETE FROM devices WHERE account_id = $1")
        .bind(account_id)
        .execute(&mut *tx)
        .await?
        .rows_affected();

    // Paid time is forfeited; the user chose to delete. Ended rather than deleted, so
    // the financial record still adds up.
    summary.subscriptions_ended = sqlx::query(
        r#"
        UPDATE subscriptions
        SET status = 'expired',
            expires_at = LEAST(expires_at, now()),
            auto_renew = false,
            cancelled_at = COALESCE(cancelled_at, now()),
            cancellation_reason = COALESCE(cancellation_reason, 'account_deleted')
        WHERE account_id = $1 AND status IN ('active', 'cancelled')
        "#,
    )
    .bind(account_id)
    .execute(&mut *tx)
    .await?
    .rows_affected();

    // An open checkout cannot complete for a deleted account. A confirmation that still
    // arrives is refused by `settle_payment` (`AccountDeleted`) for staff to refund.
    summary.payments_closed = sqlx::query(
        r#"
        UPDATE payments SET status = 'expired', completed_at = now(), deep_link = NULL
        WHERE account_id = $1 AND status = 'pending'
        "#,
    )
    .bind(account_id)
    .execute(&mut *tx)
    .await?
    .rows_affected();

    sqlx::query(
        r#"
        UPDATE accounts
        SET deleted_at = now(),
            tier = 'free',
            recovery_email_hash = NULL,
            updated_at = now()
        WHERE id = $1
        "#,
    )
    .bind(account_id)
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;
    Ok(Some(summary))
}
