use qasa_core::new_id;
use sqlx::PgPool;

use crate::models::AdminUserRow;

pub async fn find_by_email(pool: &PgPool, email: &str) -> anyhow::Result<Option<AdminUserRow>> {
    let row = sqlx::query_as::<_, AdminUserRow>(
        r#"
        SELECT id, email, password_hash, role
        FROM admin_users
        WHERE email = $1
        "#,
    )
    .bind(email)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn create_admin(
    pool: &PgPool,
    email: &str,
    password_hash: &str,
) -> anyhow::Result<AdminUserRow> {
    let id = new_id();
    let row = sqlx::query_as::<_, AdminUserRow>(
        r#"
        INSERT INTO admin_users (id, email, password_hash, role)
        VALUES ($1, $2, $3, 'admin')
        RETURNING id, email, password_hash, role
        "#,
    )
    .bind(id)
    .bind(email)
    .bind(password_hash)
    .fetch_one(pool)
    .await?;
    Ok(row)
}
