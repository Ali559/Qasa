//! Device push via Firebase Cloud Messaging (HTTP v1).
//!
//! One send path for iOS and Android — both store an FCM registration token on
//! `devices.push_token`. Apple APNs keys live in the Firebase Console; the
//! server only needs a Firebase service account.
//!
//! Env (optional — without these, inbox rows still write and pushes are logged):
//! - `QASA_FCM_PROJECT_ID` — Firebase project id (optional if present in the JSON)
//! - `QASA_FCM_SERVICE_ACCOUNT_JSON` — path to service account JSON, OR
//! - `QASA_FCM_SERVICE_ACCOUNT_JSON_INLINE` — raw JSON contents

use std::env;
use std::fs;
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use jsonwebtoken::{encode, Algorithm, EncodingKey, Header};
use qasa_db::repos::push_copy::LocalizedNotice;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::PgPool;
use tokio::sync::Mutex;
use uuid::Uuid;

#[derive(Clone, Debug)]
pub struct PushConfig {
    pub fcm: Option<FcmConfig>,
}

#[derive(Clone, Debug)]
pub struct FcmConfig {
    pub project_id: String,
    pub client_email: String,
    pub private_key_pem: String,
}

#[derive(Debug, Deserialize)]
struct ServiceAccountFile {
    project_id: String,
    client_email: String,
    private_key: String,
}

impl PushConfig {
    pub fn from_env() -> Self {
        let fcm = load_fcm_from_env();
        if fcm.is_none() {
            tracing::info!(
                "FCM credentials not set (QASA_FCM_*); device pushes will be logged only"
            );
        }
        Self { fcm }
    }

    pub fn is_configured(&self) -> bool {
        self.fcm.is_some()
    }
}

fn load_fcm_from_env() -> Option<FcmConfig> {
    let raw = env::var("QASA_FCM_SERVICE_ACCOUNT_JSON_INLINE")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .or_else(|| {
            env::var("QASA_FCM_SERVICE_ACCOUNT_JSON")
                .ok()
                .and_then(|p| fs::read_to_string(p).ok())
        })?;

    let sa: ServiceAccountFile = match serde_json::from_str(&raw) {
        Ok(v) => v,
        Err(err) => {
            tracing::error!(error = %err, "invalid FCM service account JSON");
            return None;
        }
    };

    let project_id = env::var("QASA_FCM_PROJECT_ID")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .unwrap_or(sa.project_id);

    if project_id.is_empty() || sa.client_email.is_empty() || sa.private_key.is_empty() {
        tracing::error!("FCM service account missing project_id, client_email, or private_key");
        return None;
    }

    Some(FcmConfig {
        project_id,
        client_email: sa.client_email,
        private_key_pem: sa.private_key,
    })
}

struct CachedToken {
    access_token: String,
    expires_at: Instant,
}

#[derive(Clone)]
pub struct PushService {
    config: Arc<PushConfig>,
    http: reqwest::Client,
    token_cache: Arc<Mutex<Option<CachedToken>>>,
}

impl PushService {
    pub fn new(config: PushConfig) -> Self {
        Self {
            config: Arc::new(config),
            http: reqwest::Client::builder()
                .timeout(Duration::from_secs(15))
                .build()
                .expect("reqwest client"),
            token_cache: Arc::new(Mutex::new(None)),
        }
    }

    pub fn from_env() -> Self {
        Self::new(PushConfig::from_env())
    }

    /// Create inbox row + attempt FCM device push (iOS + Android; never email).
    pub async fn notify_account(
        &self,
        pool: &PgPool,
        account_id: Uuid,
        notice: &LocalizedNotice,
        dedupe_key: &str,
    ) -> anyhow::Result<Option<qasa_db::repos::notification::NotificationRow>> {
        let Some(n) = qasa_db::repos::notification::notify_account(
            pool,
            account_id,
            notice,
            dedupe_key,
        )
        .await?
        else {
            return Ok(None);
        };
        match self
            .deliver_account(pool, account_id, notice, n.id)
            .await
        {
            Ok(sent) if sent > 0 => {
                let _ = sqlx::query(
                    r#"
                    UPDATE notification_receipts
                    SET pushed_at = now()
                    WHERE notification_id = $1 AND account_id = $2
                    "#,
                )
                .bind(n.id)
                .bind(account_id)
                .execute(pool)
                .await;
            }
            Ok(_) => {}
            Err(err) => tracing::warn!(error = %err, "push deliver_account error"),
        }
        Ok(Some(n))
    }

    /// Deliver a localized notice to all opted-in devices for an account.
    pub async fn deliver_account(
        &self,
        pool: &PgPool,
        account_id: Uuid,
        notice: &LocalizedNotice,
        notification_id: Uuid,
    ) -> anyhow::Result<u32> {
        #[derive(sqlx::FromRow)]
        struct Target {
            id: Uuid,
            push_token: Option<String>,
            locale: String,
            notifications_enabled: bool,
            platform: String,
        }

        let targets = sqlx::query_as::<_, Target>(
            r#"
            SELECT id, push_token, locale, notifications_enabled, platform
            FROM devices
            WHERE account_id = $1 AND revoked_at IS NULL
            "#,
        )
        .bind(account_id)
        .fetch_all(pool)
        .await?;

        let mut sent = 0u32;
        for t in targets {
            if !t.notifications_enabled {
                continue;
            }
            let Some(token) = t.push_token.filter(|s| !s.is_empty()) else {
                tracing::debug!(%account_id, "device opted in but has no push_token");
                continue;
            };

            let (title, body) = localize(notice, &t.locale);
            match self
                .send_fcm(&token, &t.platform, title, body, &notice.kind, notification_id)
                .await
            {
                Ok(()) => sent += 1,
                Err(SendError::DeadToken(reason)) => {
                    // FCM says this token will never work again (app uninstalled, token
                    // rotated). Drop it so we stop sending to it on every notification;
                    // the app uploads a fresh one through PATCH /v1/devices/prefs.
                    tracing::info!(%account_id, device_id = %t.id, reason, "clearing dead FCM token");
                    sqlx::query(
                        "UPDATE devices SET push_token = NULL WHERE id = $1 AND push_token = $2",
                    )
                    .bind(t.id)
                    .bind(&token)
                    .execute(pool)
                    .await?;
                }
                Err(SendError::Other(err)) => {
                    tracing::warn!(
                        error = %err,
                        %account_id,
                        platform = %t.platform,
                        "FCM delivery failed"
                    )
                }
            }
        }
        Ok(sent)
    }

    async fn send_fcm(
        &self,
        fcm_token: &str,
        platform: &str,
        title: &str,
        body: &str,
        kind: &str,
        notification_id: Uuid,
    ) -> Result<(), SendError> {
        let Some(fcm) = &self.config.fcm else {
            tracing::info!(
                kind,
                %notification_id,
                platform,
                token_prefix = &fcm_token[..8.min(fcm_token.len())],
                title,
                "push queued (set QASA_FCM_* to deliver via FCM)"
            );
            return Ok(());
        };

        let access_token = self.oauth_access_token(fcm).await.map_err(SendError::Other)?;
        let url = format!(
            "https://fcm.googleapis.com/v1/projects/{}/messages:send",
            fcm.project_id
        );

        // Same endpoint for iOS + Android; optional platform blocks for UX only.
        let mut message = json!({
            "token": fcm_token,
            "notification": {
                "title": title,
                "body": body,
            },
            "data": {
                "kind": kind,
                "notification_id": notification_id.to_string(),
            },
        });

        let plat = platform.to_lowercase();
        if plat.contains("android") {
            message["android"] = json!({
                "priority": "HIGH",
                "notification": {
                    "sound": "default",
                    "channel_id": "qasa_alerts",
                }
            });
        } else {
            // iOS / default — APNs delivered through FCM
            message["apns"] = json!({
                "headers": {
                    "apns-priority": "10",
                    "apns-push-type": "alert",
                },
                "payload": {
                    "aps": {
                        "sound": "default",
                    }
                }
            });
        }

        let res = self
            .http
            .post(&url)
            .bearer_auth(&access_token)
            .json(&json!({ "message": message }))
            .send()
            .await
            .map_err(|e| SendError::Other(e.into()))?;

        let status = res.status();
        if !status.is_success() {
            let text = res.text().await.unwrap_or_default();
            if let Some(reason) = dead_token_reason(status.as_u16(), &text) {
                return Err(SendError::DeadToken(reason));
            }
            return Err(SendError::Other(anyhow::anyhow!("FCM HTTP {status}: {text}")));
        }
        tracing::info!(%notification_id, kind, platform, "FCM push delivered");
        Ok(())
    }

    async fn oauth_access_token(&self, fcm: &FcmConfig) -> anyhow::Result<String> {
        {
            let cache = self.token_cache.lock().await;
            if let Some(cached) = cache.as_ref() {
                if cached.expires_at > Instant::now() + Duration::from_secs(60) {
                    return Ok(cached.access_token.clone());
                }
            }
        }

        let assertion = make_google_jwt(fcm)?;
        #[derive(Deserialize)]
        struct TokenResponse {
            access_token: String,
            expires_in: u64,
        }

        let res = self
            .http
            .post("https://oauth2.googleapis.com/token")
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"),
                ("assertion", assertion.as_str()),
            ])
            .send()
            .await?;

        let status = res.status();
        if !status.is_success() {
            let text = res.text().await.unwrap_or_default();
            anyhow::bail!("Google OAuth token HTTP {status}: {text}");
        }

        let token: TokenResponse = res.json().await?;
        let expires_at = Instant::now() + Duration::from_secs(token.expires_in.saturating_sub(30));
        {
            let mut cache = self.token_cache.lock().await;
            *cache = Some(CachedToken {
                access_token: token.access_token.clone(),
                expires_at,
            });
        }
        Ok(token.access_token)
    }
}

/// Why an FCM send failed.
#[derive(Debug)]
enum SendError {
    /// The registration token is permanently invalid; stop using it.
    DeadToken(&'static str),
    /// Anything else (network, auth, quota, server error): keep the token.
    Other(anyhow::Error),
}

/// Classify an FCM HTTP v1 error response. Only errors that are about *this token* count:
/// `UNREGISTERED` (404) means the app instance is gone, and an `INVALID_ARGUMENT` (400)
/// that names the registration token means it was never valid. A 400 about the payload
/// must not cost a device its token.
fn dead_token_reason(status: u16, body: &str) -> Option<&'static str> {
    if status == 404 || body.contains("UNREGISTERED") {
        return Some("unregistered");
    }
    if status == 400
        && body.contains("INVALID_ARGUMENT")
        && body.to_ascii_lowercase().contains("registration token")
    {
        return Some("invalid_token");
    }
    None
}

#[cfg(test)]
mod tests {
    use super::dead_token_reason;

    #[test]
    fn unregistered_tokens_are_dead() {
        let body = r#"{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}"#;
        assert_eq!(dead_token_reason(404, body), Some("unregistered"));
    }

    #[test]
    fn malformed_tokens_are_dead() {
        let body = r#"{"error":{"code":400,"message":"The registration token is not a valid FCM registration token","status":"INVALID_ARGUMENT"}}"#;
        assert_eq!(dead_token_reason(400, body), Some("invalid_token"));
    }

    /// A bad payload is our bug, not the device's: keep its token.
    #[test]
    fn other_errors_keep_the_token() {
        let payload = r#"{"error":{"code":400,"message":"Invalid value at 'message.android.priority'","status":"INVALID_ARGUMENT"}}"#;
        assert_eq!(dead_token_reason(400, payload), None);
        assert_eq!(dead_token_reason(401, "UNAUTHENTICATED"), None);
        assert_eq!(dead_token_reason(429, "QUOTA_EXCEEDED"), None);
        assert_eq!(dead_token_reason(503, "UNAVAILABLE"), None);
    }
}

fn localize<'a>(notice: &'a LocalizedNotice, locale: &str) -> (&'a str, &'a str) {
    match locale.to_lowercase().as_str() {
        "ckb" | "ku" => (&notice.title_ckb, &notice.body_ckb),
        "ar" => (&notice.title_ar, &notice.body_ar),
        _ => (&notice.title_en, &notice.body_en),
    }
}

#[derive(Debug, Serialize)]
struct GoogleClaims {
    iss: String,
    scope: String,
    aud: String,
    iat: u64,
    exp: u64,
}

fn make_google_jwt(fcm: &FcmConfig) -> anyhow::Result<String> {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let claims = GoogleClaims {
        iss: fcm.client_email.clone(),
        scope: "https://www.googleapis.com/auth/firebase.messaging".into(),
        aud: "https://oauth2.googleapis.com/token".into(),
        iat: now,
        exp: now + 3600,
    };
    let header = Header::new(Algorithm::RS256);
    let key = EncodingKey::from_rsa_pem(fcm.private_key_pem.as_bytes())
        .map_err(|e| anyhow::anyhow!("invalid FCM service account private_key: {e}"))?;
    encode(&header, &claims, &key).map_err(|e| anyhow::anyhow!("FCM OAuth JWT: {e}"))
}
