use sqlx::PgPool;

#[derive(Debug, Clone, sqlx::FromRow, serde::Serialize)]
pub struct BreachMatch {
    pub suffix: String,
    pub source: String,
}

pub async fn lookup_by_prefix(pool: &PgPool, prefix: &str) -> anyhow::Result<Vec<BreachMatch>> {
    let rows = sqlx::query_as::<_, BreachMatch>(
        r#"
        SELECT suffix, source
        FROM breach_hashes
        WHERE prefix = $1
        ORDER BY suffix
        LIMIT 10000
        "#,
    )
    .bind(prefix)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn ingest_batch(
    pool: &PgPool,
    entries: &[(String, String, String)],
) -> anyhow::Result<u64> {
    if entries.is_empty() {
        return Ok(0);
    }

    let mut tx = pool.begin().await?;
    let mut inserted = 0u64;

    for chunk in entries.chunks(500) {
        for (prefix, suffix, source) in chunk {
            let result = sqlx::query(
                r#"
                INSERT INTO breach_hashes (prefix, suffix, source)
                VALUES ($1, $2, $3)
                ON CONFLICT (prefix, suffix) DO NOTHING
                "#,
            )
            .bind(prefix)
            .bind(suffix)
            .bind(source)
            .execute(&mut *tx)
            .await?;
            inserted += result.rows_affected();
        }
    }

    tx.commit().await?;
    Ok(inserted)
}

pub async fn count_by_prefix(pool: &PgPool, prefix: &str) -> anyhow::Result<i64> {
    let count: i64 = sqlx::query_scalar(
        r#"
        SELECT COUNT(*)::bigint FROM breach_hashes WHERE prefix = $1
        "#,
    )
    .bind(prefix)
    .fetch_one(pool)
    .await?;
    Ok(count)
}

pub async fn total_count(pool: &PgPool) -> anyhow::Result<i64> {
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*)::bigint FROM breach_hashes")
        .fetch_one(pool)
        .await?;
    Ok(count)
}
