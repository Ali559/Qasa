//! End-to-end tests through the real router: HTTP status codes and JSON bodies, against
//! a disposable Postgres. `#[ignore]`d like the `qasa-db` integration tests, so a plain
//! `cargo test` never reports them as passed; run with `./scripts/test-integration.sh`.

use std::net::SocketAddr;

use axum::body::Body;
use axum::extract::ConnectInfo;
use axum::http::{Method, Request, StatusCode};
use axum::Router;
use qasa_core::{hash_password, AppConfig, AppEnvironment, LicenseKeyPair};
use serde_json::{json, Value};
use sqlx::PgPool;
use tower::ServiceExt;
use uuid::Uuid;

use crate::state::AppState;

/// Same guard as `qasa-db/tests/support`: refuse anything not named `*_test`, because
/// these tests write rows.
async fn pool() -> PgPool {
    let url = std::env::var("QASA_TEST_DATABASE_URL")
        .expect("QASA_TEST_DATABASE_URL is not set; run ./scripts/test-integration.sh");
    let pool = PgPool::connect(&url).await.expect("test database unreachable");
    let name: String = sqlx::query_scalar("SELECT current_database()")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert!(
        name.ends_with("_test") || std::env::var("QASA_TEST_ALLOW_ANY_DB").is_ok(),
        "refusing to run against `{name}`: not a *_test database"
    );
    pool
}

struct Api {
    router: Router,
    pool: PgPool,
}

impl Api {
    async fn new() -> Self {
        let pool = pool().await;
        let config = AppConfig {
            env: AppEnvironment::Dev,
            database_url: String::new(),
            bind_addr: "127.0.0.1:0".into(),
            license_private_key_b64: None,
            admin_jwt_secret: "router-test-secret".into(),
            admin_seed_email: None,
            admin_seed_password: None,
            admin_cors_origin: None,
            admin_allow_cidrs: Vec::new(),
            trusted_proxy_cidrs: Vec::new(),
        };
        let state = AppState::new(pool.clone(), config.clone(), LicenseKeyPair::generate());
        Self {
            router: crate::build_router(state, &config),
            pool,
        }
    }

    async fn call(&self, method: Method, path: &str, token: Option<&str>, body: Option<Value>) -> (StatusCode, Value) {
        let mut req = Request::builder().method(method).uri(path);
        if let Some(t) = token {
            req = req.header("authorization", format!("Bearer {t}"));
        }
        let mut req = match body {
            Some(b) => req
                .header("content-type", "application/json")
                .body(Body::from(b.to_string()))
                .unwrap(),
            None => req.body(Body::empty()).unwrap(),
        };
        req.extensions_mut()
            .insert(ConnectInfo(SocketAddr::from(([127, 0, 0, 1], 40000))));
        let res = self.router.clone().oneshot(req).await.unwrap();
        let status = res.status();
        let bytes = axum::body::to_bytes(res.into_body(), 1 << 20).await.unwrap();
        let json = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
        (status, json)
    }

    async fn register(&self, account: Uuid, fingerprint: &str) -> (StatusCode, Value) {
        self.call(
            Method::POST,
            "/v1/devices/register",
            None,
            Some(json!({
                "account_id": account,
                "device_fingerprint": fingerprint,
                "device_name": fingerprint,
                "platform": "ios",
            })),
        )
        .await
    }

    async fn token(&self, account: Uuid, fingerprint: &str) -> String {
        let (status, body) = self.register(account, fingerprint).await;
        assert_eq!(status, StatusCode::CREATED, "{body}");
        body["license_token"].as_str().unwrap().to_string()
    }

    async fn make_premium(&self, account: Uuid, token: &str) {
        let (_, intent) = self
            .call(Method::POST, "/v1/payments/intents", Some(token), Some(json!({ "gateway": "fib", "plan_id": "premium_monthly" })))
            .await;
        let (status, _) = self
            .call(
                Method::POST,
                "/v1/payments/webhook/fib",
                None,
                Some(json!({ "order_id": intent["gateway_order_id"], "status": "success", "amount_iqd": intent["amount_iqd"] })),
            )
            .await;
        assert_eq!(status, StatusCode::OK, "settling {account}");
    }

    async fn admin(&self, role: &str) -> String {
        let email = format!("{role}-{}@router.test", Uuid::now_v7());
        sqlx::query("INSERT INTO admin_users (id, email, password_hash, role) VALUES ($1, $2, $3, $4)")
            .bind(Uuid::now_v7())
            .bind(&email)
            .bind(hash_password("router-pass").unwrap())
            .bind(role)
            .execute(&self.pool)
            .await
            .unwrap();
        let (status, body) = self
            .call(Method::POST, "/v1/admin/auth/login", None, Some(json!({ "email": email, "password": "router-pass" })))
            .await;
        assert_eq!(status, StatusCode::OK, "{body}");
        body["token"].as_str().unwrap().to_string()
    }
}

/// Wait out the one-second `iat` resolution between an unlink and a new token.
async fn next_second() {
    tokio::time::sleep(std::time::Duration::from_millis(1100)).await;
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn unlinked_device_gets_401_and_can_relink() {
    let api = Api::new().await;
    let account = Uuid::now_v7();
    let token = api.token(account, "fp-a").await;

    let (status, _) = api.call(Method::DELETE, "/v1/devices/me", Some(&token), None).await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    let (status, body) = api.call(Method::GET, "/v1/devices", Some(&token), None).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    assert_eq!(body["message"], "token revoked");

    next_second().await;
    let (status, body) = api.register(account, "fp-a").await;
    assert_eq!(status, StatusCode::CREATED, "re-link used to be a 500: {body}");
    let fresh = body["license_token"].as_str().unwrap();
    let (status, _) = api.call(Method::GET, "/v1/devices", Some(fresh), None).await;
    assert_eq!(status, StatusCode::OK);
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn the_price_comes_from_the_plan() {
    let api = Api::new().await;
    let token = api.token(Uuid::now_v7(), "fp-p").await;

    let (status, body) = api
        .call(Method::POST, "/v1/payments/intents", Some(&token), Some(json!({ "gateway": "fib", "plan_id": "premium_yearly", "amount_iqd": 1 })))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");

    let (status, body) = api
        .call(Method::POST, "/v1/payments/intents", Some(&token), Some(json!({ "gateway": "fib", "plan_id": "premium_yearly" })))
        .await;
    assert_eq!(status, StatusCode::OK);
    let plan_price: i32 = sqlx::query_scalar("SELECT amount_iqd FROM plans WHERE id = 'premium_yearly'")
        .fetch_one(&api.pool)
        .await
        .unwrap();
    assert_eq!(body["amount_iqd"], plan_price);

    let (status, _) = api
        .call(Method::POST, "/v1/payments/intents", Some(&token), Some(json!({ "gateway": "fib", "plan_id": "premium_lifetime" })))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
}

/// What the apps show under the auto-renew toggle follows the saved method, not the
/// toggle alone: paying with auto-renew on saves one, removing it stops renewals.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn subscription_reports_whether_it_will_really_renew() {
    let api = Api::new().await;
    let token = api.token(Uuid::now_v7(), "fp-r").await;

    let pay = |auto_renew: bool| {
        let (api, token) = (&api, &token);
        async move {
            let (status, intent) = api
                .call(Method::POST, "/v1/payments/intents", Some(token), Some(json!({ "gateway": "fib", "plan_id": "premium_monthly", "auto_renew": auto_renew })))
                .await;
            assert_eq!(status, StatusCode::OK, "{intent}");
            assert_eq!(intent["kind"], "checkout");
            let (status, body) = api
                .call(Method::POST, "/v1/payments/webhook/fib", None, Some(json!({ "order_id": intent["gateway_order_id"], "status": "success", "amount_iqd": intent["amount_iqd"] })))
                .await;
            assert_eq!(status, StatusCode::OK, "{body}");
        }
    };

    pay(false).await;
    let (_, body) = api.call(Method::GET, "/v1/subscription", Some(&token), None).await;
    assert_eq!(body["subscription"]["renewal"]["will_renew"], false, "{body}");
    assert_eq!(body["subscription"]["renewal"]["mode"], "none", "auto-renew off");

    pay(true).await;
    let (_, body) = api.call(Method::GET, "/v1/subscription", Some(&token), None).await;
    let renewal = &body["subscription"]["renewal"];
    assert_eq!(renewal["will_renew"], true, "{body}");
    assert_eq!(renewal["mode"], "charge");
    assert_eq!(renewal["gateway"], "fib");
    let price: i32 = sqlx::query_scalar("SELECT amount_iqd FROM plans WHERE id = 'premium_monthly'")
        .fetch_one(&api.pool)
        .await
        .unwrap();
    assert_eq!(renewal["amount_iqd"], price);

    let (_, body) = api
        .call(Method::PATCH, "/v1/subscription/auto-renew", Some(&token), Some(json!({ "auto_renew": false })))
        .await;
    assert_eq!(body["subscription"]["renewal"]["will_renew"], false, "toggle off stops it");
    let (_, body) = api
        .call(Method::PATCH, "/v1/subscription/auto-renew", Some(&token), Some(json!({ "auto_renew": true })))
        .await;
    assert_eq!(body["subscription"]["renewal"]["will_renew"], true, "and on again resumes it");

    let (status, body) = api.call(Method::DELETE, "/v1/payment-method", Some(&token), None).await;
    assert_eq!((status, body["removed"].as_bool()), (StatusCode::OK, Some(true)));
    let (_, body) = api.call(Method::GET, "/v1/subscription", Some(&token), None).await;
    assert_eq!(body["subscription"]["auto_renew"], true);
    let renewal = &body["subscription"]["renewal"];
    assert_eq!(renewal["will_renew"], false, "no method, nothing to charge");
    assert_eq!(renewal["mode"], "link", "so it renews by a link instead");
    assert!(renewal["link"].is_null(), "none sent yet");

    // The checkout screen learns the same per wallet.
    let (_, pricing) = api.call(Method::GET, "/v1/pricing", None, None).await;
    assert_eq!(pricing["auto_renew"]["fib"], "charge", "{pricing}");
}

/// A locale-only update used to switch notifications off.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn prefs_patch_only_changes_what_is_sent() {
    let api = Api::new().await;
    let token = api.token(Uuid::now_v7(), "fp-n").await;

    let (status, body) = api
        .call(Method::PATCH, "/v1/devices/prefs", Some(&token), Some(json!({ "locale": "ar", "push_token": "fcm-1", "notifications_enabled": true })))
        .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!((body["locale"].as_str(), body["notifications_enabled"].as_bool()), (Some("ar"), Some(true)));

    let (_, body) = api
        .call(Method::PATCH, "/v1/devices/prefs", Some(&token), Some(json!({ "locale": "ckb" })))
        .await;
    assert_eq!(body["locale"], "ckb");
    assert_eq!(body["notifications_enabled"], true, "omitted field must keep its value");

    let (_, body) = api.call(Method::PATCH, "/v1/devices/prefs", Some(&token), Some(json!({}))).await;
    assert_eq!(body["locale"], "ckb", "an empty patch changes nothing");
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn device_limit_403_lists_the_devices() {
    let api = Api::new().await;
    let account = Uuid::now_v7();
    api.token(account, "fp-1").await;

    let (status, body) = api.register(account, "fp-2").await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(body["error"], "device_limit_exceeded");
    assert_eq!(body["device_limit"], 1);
    assert_eq!(body["devices"].as_array().unwrap().len(), 1);
    assert_eq!(body["devices"][0]["is_primary"], true);
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn link_check_and_deleted_accounts() {
    let api = Api::new().await;
    let (status, _) = api
        .call(Method::POST, "/v1/devices/link-check", None, Some(json!({ "account_id": Uuid::now_v7() })))
        .await;
    assert_eq!(status, StatusCode::NOT_FOUND);

    let account = Uuid::now_v7();
    let token = api.token(account, "fp-d").await;
    let (status, _) = api
        .call(Method::DELETE, "/v1/account", Some(&token), Some(json!({ "confirm_account_id": Uuid::now_v7() })))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST, "a mismatched confirmation deletes nothing");
    let (status, _) = api
        .call(Method::DELETE, "/v1/account", Some(&token), Some(json!({ "confirm_account_id": account })))
        .await;
    assert_eq!(status, StatusCode::OK);
    let (status, body) = api.register(account, "fp-d").await;
    assert_eq!((status, body["error"].as_str()), (StatusCode::GONE, Some("account_deleted")));
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn premium_lapse_and_breach_limit_flow() {
    let api = Api::new().await;
    let account = Uuid::now_v7();
    let token = api.token(account, "fp-l").await;
    api.make_premium(account, &token).await;
    next_second().await;
    let (_, lic) = api.call(Method::GET, "/v1/license", Some(&token), None).await;
    assert_eq!((lic["tier"].as_str(), lic["breach_monitor_limit"].as_i64()), (Some("premium"), Some(10)));

    sqlx::query("UPDATE subscriptions SET expires_at = now() - interval '1 minute' WHERE account_id = $1")
        .bind(account)
        .execute(&api.pool)
        .await
        .unwrap();
    let (status, lic) = api.call(Method::GET, "/v1/license", Some(&token), None).await;
    assert_eq!(status, StatusCode::OK, "a lapsed account keeps its primary device");
    assert_eq!((lic["tier"].as_str(), lic["breach_monitor_limit"].as_i64()), (Some("free"), Some(0)));
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn viewer_admins_cannot_write_and_logout_ends_the_session() {
    let api = Api::new().await;
    let viewer = api.admin("viewer").await;
    let admin = api.admin("admin").await;
    let account = Uuid::now_v7();
    api.token(account, "fp-v").await;
    let block = format!("/v1/admin/accounts/{account}/block");

    let (status, _) = api.call(Method::GET, "/v1/admin/overview", Some(&viewer), None).await;
    assert_eq!(status, StatusCode::OK);
    let (status, _) = api.call(Method::POST, &block, Some(&viewer), Some(json!({ "reason": "x" }))).await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    let (status, _) = api.call(Method::POST, &block, Some(&admin), Some(json!({ "reason": "x" }))).await;
    assert_eq!(status, StatusCode::OK);

    let (status, _) = api.call(Method::POST, "/v1/admin/auth/logout", Some(&admin), None).await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    let (status, body) = api.call(Method::GET, "/v1/admin/overview", Some(&admin), None).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    assert_eq!(body["message"], "admin session ended");
}

/// The limiter is shared through Postgres, so a second router (another API process)
/// sees the same count.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn rate_limits_are_shared_across_processes() {
    let a = Api::new().await;
    let b = Api::new().await;
    let email = format!("nobody-{}@router.test", Uuid::now_v7());
    let attempt = |api: &Api| {
        let email = email.clone();
        let router = api.router.clone();
        async move {
            let mut req = Request::builder()
                .method(Method::POST)
                .uri("/v1/admin/auth/login")
                .header("content-type", "application/json")
                .body(Body::from(json!({ "email": email, "password": "wrong" }).to_string()))
                .unwrap();
            // A different address per attempt, so only the per-email limit is in play.
            let peer: SocketAddr = format!("10.9.{}.{}:1", rand_octet(), rand_octet()).parse().unwrap();
            req.extensions_mut().insert(ConnectInfo(peer));
            router.oneshot(req).await.unwrap().status()
        }
    };
    let mut statuses = Vec::new();
    for i in 0..11 {
        statuses.push(attempt(if i % 2 == 0 { &a } else { &b }).await);
    }
    assert!(statuses[..10].iter().all(|s| *s == StatusCode::UNAUTHORIZED), "{statuses:?}");
    assert_eq!(statuses[10], StatusCode::TOO_MANY_REQUESTS, "{statuses:?}");
}

fn rand_octet() -> u8 {
    use rand::Rng;
    rand::thread_rng().gen_range(1..=254)
}

#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn inbox_endpoints_and_broadcast_audience() {
    let api = Api::new().await;
    let account = Uuid::now_v7();
    let token = api.token(account, "fp-inbox").await;
    let admin = api.admin("admin").await;

    let (status, body) = api
        .call(Method::POST, "/v1/admin/notifications", Some(&admin), Some(json!({ "title_en": "t", "body_en": "b", "audience": "vip" })))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST, "{body}");
    for audience in ["all", "free"] {
        let (status, _) = api
            .call(Method::POST, "/v1/admin/notifications", Some(&admin), Some(json!({ "title_en": audience, "body_en": "b", "audience": audience })))
            .await;
        assert_eq!(status, StatusCode::OK);
    }
    let (status, _) = api
        .call(Method::POST, "/v1/admin/notifications", Some(&admin), Some(json!({ "title_en": "subs", "body_en": "b", "audience": "subscribers" })))
        .await;
    assert_eq!(status, StatusCode::OK);

    let (status, inbox) = api.call(Method::GET, "/v1/notifications?limit=10", Some(&token), None).await;
    assert_eq!(status, StatusCode::OK);
    let titles: Vec<&str> = inbox["notifications"].as_array().unwrap().iter().map(|n| n["title_en"].as_str().unwrap()).collect();
    assert!(titles.contains(&"all") && titles.contains(&"free"), "{titles:?}");
    assert!(!titles.contains(&"subs"), "a free account must not get the subscribers notice");
    let unread = inbox["unread_count"].as_i64().unwrap();
    assert!(unread >= 2);

    let first = inbox["notifications"][0]["id"].as_str().unwrap().to_string();
    let (status, body) = api.call(Method::POST, &format!("/v1/notifications/{first}/read"), Some(&token), None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["unread_count"].as_i64().unwrap(), unread - 1);

    let (status, _) = api.call(Method::DELETE, &format!("/v1/notifications/{first}"), Some(&token), None).await;
    assert_eq!(status, StatusCode::NO_CONTENT);
    let (status, _) = api.call(Method::GET, &format!("/v1/notifications/{first}"), Some(&token), None).await;
    assert_eq!(status, StatusCode::NOT_FOUND);
    let (status, _) = api.call(Method::POST, &format!("/v1/notifications/{}/read", Uuid::now_v7()), Some(&token), None).await;
    assert_eq!(status, StatusCode::NOT_FOUND);

    let (status, body) = api.call(Method::POST, "/v1/notifications/read-all", Some(&token), None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["unread_count"], 0);
    let (_, body) = api.call(Method::GET, "/v1/notifications/unread-count", Some(&token), None).await;
    assert_eq!(body["unread_count"], 0);
}

fn wg_key() -> String {
    use base64::Engine;
    let mut b = [0u8; 32];
    b[..16].copy_from_slice(Uuid::now_v7().as_bytes());
    base64::engine::general_purpose::STANDARD.encode(b)
}

/// Device connects → agent fetches peers → agent reports a handshake → status says the
/// tunnel is up → disconnect → the agent's peer list drops it.
#[tokio::test]
#[ignore = "needs a disposable Postgres; run ./scripts/test-integration.sh"]
async fn vpn_connect_agent_sync_and_status() {
    let api = Api::new().await;
    let account = Uuid::now_v7();
    let token = api.token(account, "fp-vpn").await;
    api.make_premium(account, &token).await;
    next_second().await;
    let (_, lic) = api.call(Method::GET, "/v1/license", Some(&token), None).await;
    let token = lic["license_token"].as_str().unwrap().to_string();

    let node = Uuid::now_v7();
    let agent_token = "router-agent-token";
    sqlx::query(
        "INSERT INTO vpn_nodes (id, name, city, public_ip, wg_public_key, agent_token_hash, ip_pool_cidr, status, last_heartbeat) \
         VALUES ($1, 'R', 'Erbil', '203.0.113.200', 'srvkey', $2, $3::cidr, 'online', now())",
    )
    .bind(node)
    .bind(qasa_vpn::hash_agent_token(agent_token))
    .bind(format!("10.202.{}.0/24", node.as_bytes()[15]))
    .execute(&api.pool)
    .await
    .unwrap();

    let (status, nodes) = api.call(Method::GET, "/v1/vpn/nodes", Some(&token), None).await;
    assert_eq!(status, StatusCode::OK);
    assert!(nodes["nodes"].as_array().unwrap().iter().any(|n| n["id"] == node.to_string()));

    let (status, _) = api
        .call(Method::POST, "/v1/vpn/connect", Some(&token), Some(json!({ "client_public_key": "not-a-key", "node_id": node })))
        .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);

    let key = wg_key();
    let (status, conn) = api
        .call(Method::POST, "/v1/vpn/connect", Some(&token), Some(json!({ "client_public_key": key, "node_id": node })))
        .await;
    assert_eq!(status, StatusCode::OK, "{conn}");
    assert_eq!(conn["reused"], false);
    assert_eq!(conn["node"]["id"], node.to_string());
    let client_ip = conn["config"]["client_ip"].as_str().unwrap().to_string();
    let (_, again) = api
        .call(Method::POST, "/v1/vpn/connect", Some(&token), Some(json!({ "client_public_key": key, "node_id": node })))
        .await;
    assert_eq!(again["reused"], true, "same key on the same node reuses the session");

    let agent = |method: Method, path: String, body: Option<Value>| {
        let router = api.router.clone();
        async move {
            let req = Request::builder().method(method).uri(path).header("x-qasa-agent-token", agent_token);
            let mut req = match body {
                Some(b) => req.header("content-type", "application/json").body(Body::from(b.to_string())),
                None => req.body(Body::empty()),
            }
            .unwrap();
            req.extensions_mut().insert(ConnectInfo(SocketAddr::from(([127, 0, 0, 1], 1))));
            let res = router.oneshot(req).await.unwrap();
            let status = res.status();
            let bytes = axum::body::to_bytes(res.into_body(), 1 << 20).await.unwrap();
            (status, serde_json::from_slice::<Value>(&bytes).unwrap_or(Value::Null))
        }
    };

    let (status, peers) = agent(Method::GET, format!("/v1/internal/vpn/nodes/{node}/peers"), None).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(peers["peers"], json!([{ "public_key": key, "allowed_ip": format!("{client_ip}/32"), "throttle_mbps": null }]));

    let (_, status_body) = api.call(Method::GET, "/v1/vpn/status", Some(&token), None).await;
    assert_eq!(status_body["tunnel_up"], false, "no handshake yet");

    let now = chrono::Utc::now().timestamp();
    let (status, hb) = agent(
        Method::POST,
        format!("/v1/internal/vpn/nodes/{node}/heartbeat"),
        Some(json!({ "cpu_percent": 5, "memory_percent": 5, "peers": [{ "public_key": key, "last_handshake": now, "rx_bytes": 4096, "tx_bytes": 1024 }] })),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(hb["metered_accounts"], 1);
    let (_, status_body) = api.call(Method::GET, "/v1/vpn/status", Some(&token), None).await;
    assert_eq!(status_body["tunnel_up"], true);

    let (status, _) = api.call(Method::POST, "/v1/vpn/disconnect", Some(&token), None).await;
    assert_eq!(status, StatusCode::OK);
    let (_, peers) = agent(Method::GET, format!("/v1/internal/vpn/nodes/{node}/peers"), None).await;
    assert_eq!(peers["peers"], json!([]), "a disconnected peer is no longer desired");

    let mut bad = Request::builder()
        .uri(format!("/v1/internal/vpn/nodes/{node}/peers"))
        .header("x-qasa-agent-token", "wrong")
        .body(Body::empty())
        .unwrap();
    bad.extensions_mut().insert(ConnectInfo(SocketAddr::from(([127, 0, 0, 1], 1))));
    assert_eq!(api.router.clone().oneshot(bad).await.unwrap().status(), StatusCode::FORBIDDEN);
}
