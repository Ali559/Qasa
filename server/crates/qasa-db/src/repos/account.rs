use qasa_core::{mask_public_id, new_id};
use sqlx::PgPool;
use uuid::Uuid;

use crate::models::AccountRow;

pub async fn upsert_account(pool: &PgPool, account_id: Uuid) -> anyhow::Result<AccountRow> {
    let public_id = mask_public_id(account_id);
    let row = sqlx::query_as::<_, AccountRow>(
        r#"
        INSERT INTO accounts (id, public_id, tier)
        VALUES ($1, $2, 'free')
        ON CONFLICT (id) DO UPDATE SET updated_at = now()
        RETURNING id, public_id, tier, created_at, updated_at
        "#,
    )
    .bind(account_id)
    .bind(public_id)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn get_account(pool: &PgPool, account_id: Uuid) -> anyhow::Result<Option<AccountRow>> {
    let row = sqlx::query_as::<_, AccountRow>(
        r#"
        SELECT id, public_id, tier, created_at, updated_at
        FROM accounts
        WHERE id = $1
        "#,
    )
    .bind(account_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn set_account_tier(pool: &PgPool, account_id: Uuid, tier: &str) -> anyhow::Result<()> {
    sqlx::query(
        r#"
        UPDATE accounts SET tier = $2, updated_at = now()
        WHERE id = $1
        "#,
    )
    .bind(account_id)
    .bind(tier)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn list_accounts_for_admin(pool: &PgPool) -> anyhow::Result<Vec<AccountRow>> {
    let rows = sqlx::query_as::<_, AccountRow>(
        r#"
        SELECT id, public_id, tier, created_at, updated_at
        FROM accounts
        ORDER BY created_at DESC
        LIMIT 200
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn seed_admin_if_missing(
    pool: &PgPool,
    email: &str,
    password_hash: &str,
) -> anyhow::Result<()> {
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM admin_users WHERE email = $1)")
        .bind(email)
        .fetch_one(pool)
        .await?;

    if exists {
        return Ok(());
    }

    let id = new_id();
    sqlx::query(
        r#"
        INSERT INTO admin_users (id, email, password_hash, role)
        VALUES ($1, $2, $3, 'admin')
        "#,
    )
    .bind(id)
    .bind(email)
    .bind(password_hash)
    .execute(pool)
    .await?;
    Ok(())
}
