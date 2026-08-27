use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;
use sqlx::PgPool;

#[derive(Debug, Clone, sqlx::FromRow, Serialize, Deserialize)]
pub struct PlanRow {
    pub id: String,
    pub title: String,
    pub interval: String,
    pub amount_iqd: i32,
    pub days: i32,
    pub vpn_quota_gb: i32,
    pub active: bool,
    pub sort_order: i32,
    pub features_json: JsonValue,
    pub updated_at: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
}

pub async fn list_active(pool: &PgPool) -> anyhow::Result<Vec<PlanRow>> {
    let rows = sqlx::query_as::<_, PlanRow>(
        r#"
        SELECT id, title, interval, amount_iqd, days, vpn_quota_gb, active,
               sort_order, features_json, updated_at, created_at
        FROM plans
        WHERE active = true
        ORDER BY sort_order ASC, id ASC
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn list_all(pool: &PgPool) -> anyhow::Result<Vec<PlanRow>> {
    let rows = sqlx::query_as::<_, PlanRow>(
        r#"
        SELECT id, title, interval, amount_iqd, days, vpn_quota_gb, active,
               sort_order, features_json, updated_at, created_at
        FROM plans
        ORDER BY sort_order ASC, id ASC
        "#,
    )
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn get_plan(pool: &PgPool, plan_id: &str) -> anyhow::Result<Option<PlanRow>> {
    let row = sqlx::query_as::<_, PlanRow>(
        r#"
        SELECT id, title, interval, amount_iqd, days, vpn_quota_gb, active,
               sort_order, features_json, updated_at, created_at
        FROM plans
        WHERE id = $1
        "#,
    )
    .bind(plan_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn upsert_plan(
    pool: &PgPool,
    id: &str,
    title: &str,
    interval: &str,
    amount_iqd: i32,
    days: i32,
    vpn_quota_gb: i32,
    active: bool,
    sort_order: i32,
    features_json: JsonValue,
) -> anyhow::Result<PlanRow> {
    let row = sqlx::query_as::<_, PlanRow>(
        r#"
        INSERT INTO plans (id, title, interval, amount_iqd, days, vpn_quota_gb, active, sort_order, features_json)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
        ON CONFLICT (id) DO UPDATE SET
          title = EXCLUDED.title,
          interval = EXCLUDED.interval,
          amount_iqd = EXCLUDED.amount_iqd,
          days = EXCLUDED.days,
          vpn_quota_gb = EXCLUDED.vpn_quota_gb,
          active = EXCLUDED.active,
          sort_order = EXCLUDED.sort_order,
          features_json = EXCLUDED.features_json,
          updated_at = now()
        RETURNING id, title, interval, amount_iqd, days, vpn_quota_gb, active,
                  sort_order, features_json, updated_at, created_at
        "#,
    )
    .bind(id)
    .bind(title)
    .bind(interval)
    .bind(amount_iqd)
    .bind(days)
    .bind(vpn_quota_gb)
    .bind(active)
    .bind(sort_order)
    .bind(features_json)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn plan_amount(pool: &PgPool, plan_id: &str) -> anyhow::Result<i32> {
    if let Some(plan) = get_plan(pool, plan_id).await? {
        return Ok(plan.amount_iqd);
    }
    Ok(crate::repos::subscription::plan_amount_fallback(plan_id))
}

pub async fn plan_days(pool: &PgPool, plan_id: &str) -> anyhow::Result<i64> {
    if let Some(plan) = get_plan(pool, plan_id).await? {
        return Ok(plan.days as i64);
    }
    Ok(crate::repos::subscription::plan_days_fallback(plan_id))
}

pub async fn vpn_quota_gb(pool: &PgPool, plan_id: &str) -> anyhow::Result<i32> {
    if let Some(plan) = get_plan(pool, plan_id).await? {
        return Ok(plan.vpn_quota_gb);
    }
    Ok(100)
}
