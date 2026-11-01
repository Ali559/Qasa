//! Fixed-window rate limiting shared by every API process, stored in Postgres.

use std::time::Duration;

use sqlx::PgPool;

/// Count one hit for `key` and say whether it is still within `max` per `window`.
/// Atomic per key: concurrent requests from many processes cannot all slip through.
pub async fn hit(pool: &PgPool, key: &str, max: u32, window: Duration) -> anyhow::Result<bool> {
    let secs = window.as_secs_f64();
    let hits: i32 = sqlx::query_scalar(
        r#"
        INSERT INTO rate_limit_buckets AS b (key, window_start, hits)
        VALUES ($1, now(), 1)
        ON CONFLICT (key) DO UPDATE SET
          window_start = CASE
            WHEN b.window_start <= now() - make_interval(secs => $2) THEN now()
            ELSE b.window_start
          END,
          hits = CASE
            WHEN b.window_start <= now() - make_interval(secs => $2) THEN 1
            ELSE b.hits + 1
          END
        RETURNING hits
        "#,
    )
    .bind(key)
    .bind(secs)
    .fetch_one(pool)
    .await?;
    Ok(hits as i64 <= i64::from(max))
}

/// Forget windows that ended long ago, so the table stays the size of current traffic.
pub async fn purge_stale(pool: &PgPool, older_than: Duration) -> anyhow::Result<u64> {
    let n = sqlx::query(
        "DELETE FROM rate_limit_buckets WHERE window_start < now() - make_interval(secs => $1)",
    )
    .bind(older_than.as_secs_f64())
    .execute(pool)
    .await?
    .rows_affected();
    Ok(n)
}
