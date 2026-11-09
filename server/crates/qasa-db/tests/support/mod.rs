//! Shared harness for the `qasa-db` integration tests.
//!
//! Two problems this exists to solve:
//!
//! 1. **False-green.** A test that quietly returns when no database is configured is
//!    reported by libtest as `ok`. A run of the whole suite would look green while
//!    nothing had been executed. These tests are therefore `#[ignore]`d by default, and
//!    this harness *panics* rather than skipping, so an explicit request to run them
//!    either really runs them or fails loudly.
//!
//! 2. **Wrong-database damage.** The tests create and delete rows, and
//!    `reconcile_active_peers` rewrites `vpn_nodes.active_peers` for *every* node in the
//!    database. Pointed at a developer's working database or a production instance that
//!    is destructive. The guard below refuses to run unless the target database is
//!    explicitly marked as disposable.

#![allow(dead_code)]

use sqlx::postgres::PgPoolOptions;
use sqlx::{Executor, PgPool};

/// Environment variable naming the disposable database to test against.
pub const DB_URL_VAR: &str = "QASA_TEST_DATABASE_URL";

/// Escape hatch for the rare case where the disposable database is not named
/// `*_test`. Required even when the name looks right, it documents intent.
pub const ALLOW_ANY_DB_VAR: &str = "QASA_TEST_ALLOW_ANY_DB";

/// Suffix that marks a database as disposable test state.
const TEST_DB_SUFFIX: &str = "_test";

/// Connects to the disposable test database, or panics with an actionable message.
///
/// # Panics
/// Panics if the URL is unset, unparseable, unreachable, or points at a database that
/// has not been marked disposable. There is deliberately no "skip" path: a silent skip
/// is exactly the failure mode this harness exists to eliminate.
pub async fn test_pool() -> PgPool {
    let url = std::env::var(DB_URL_VAR).unwrap_or_else(|_| {
        panic!(
            "\n\
             {DB_URL_VAR} is not set.\n\
             \n\
             These tests need a disposable Postgres and will not silently skip.\n\
             Run them with:\n\
             \n    ./scripts/test-integration.sh\n\
             \n\
             or set {DB_URL_VAR} to a database dedicated to testing, e.g.\n\
             \n    {DB_URL_VAR}=postgres://qasa:qasa@127.0.0.1:5432/qasa_test\n"
        )
    });

    let pool = PgPoolOptions::new()
        .max_connections(5)
        .acquire_timeout(std::time::Duration::from_secs(10))
        .connect(&url)
        .await
        .unwrap_or_else(|e| {
            panic!(
                "{DB_URL_VAR} is set to `{url}` but the database is unreachable: {e}\n\
                 Run ./scripts/test-integration.sh, which recreates qasa_test on the local Postgres."
            )
        });

    assert_database_is_disposable(&pool, &url).await;
    assert_migrations_applied(&pool).await;
    pool
}

/// Refuses to run against anything that is not explicitly a test database.
async fn assert_database_is_disposable(pool: &PgPool, url: &str) {
    if std::env::var(ALLOW_ANY_DB_VAR).is_ok() {
        eprintln!("warning: {ALLOW_ANY_DB_VAR} is set, skipping the disposable-database check");
        return;
    }

    let current: String = sqlx::query_scalar("SELECT current_database()")
        .fetch_one(pool)
        .await
        .expect("could not read current_database()");

    if !current.ends_with(TEST_DB_SUFFIX) {
        // Deliberately no `truncate`: the point is to never touch this database at all.
        panic!(
            "\n\
             REFUSING TO RUN: `{current}` does not end with `{TEST_DB_SUFFIX}`.\n\
             \n\
             These tests insert, delete, and run `reconcile_active_peers`, which rewrites\n\
             `vpn_nodes.active_peers` for every node in the database. Running them against a\n\
             development or production database would corrupt it.\n\
             \n\
             Create a disposable database and point {DB_URL_VAR} at it:\n\
             \n    createdb {current}_test\n\
             \n\
             If `{url}` really is a throwaway database, re-run with:\n\
             \n    {ALLOW_ANY_DB_VAR}=1 {DB_URL_VAR}=...\n"
        );
    }
}

/// Fails with a clear message rather than a confusing "relation does not exist" storm.
async fn assert_migrations_applied(pool: &PgPool) {
    let missing = sqlx::query_scalar::<_, String>(
        "SELECT table_name FROM information_schema.tables \
         WHERE table_schema = 'public' \
         AND table_name IN ('vpn_nodes', 'vpn_sessions', 'license_revocations', 'payments')",
    )
    .fetch_all(pool)
    .await
    .expect("could not inspect schema");

    if missing.len() < 4 {
        panic!(
            "\n\
             The test database is missing tables: {}\n\
             Migrations have not been applied. Run `./scripts/test-integration.sh`, which \
             applies them for you.\n",
            missing.join(", ")
        );
    }
}

/// Removes rows created by a test, child rows first, so the database is left as it was
/// found.
///
/// Statements are passed pre-formatted rather than parameterised: this is test cleanup
/// over values the test itself just generated, and a stray unbound `$1` would silently
/// delete nothing while still reporting success.
///
/// Best-effort by design: cleanup failures are reported loudly but never fail a test,
/// since the assertions have already run by this point.
pub async fn cleanup(pool: &PgPool, statements: &[String]) {
    for stmt in statements {
        if let Err(e) = pool.execute(stmt.as_str()).await {
            eprintln!("warning: cleanup failed (`{stmt}`): {e}");
        }
    }
}
