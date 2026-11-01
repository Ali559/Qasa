use std::path::PathBuf;

use anyhow::Context;
use clap::{Parser, Subcommand};
use qasa_db::{connect, run_migrations};

#[derive(Parser)]
#[command(name = "qasa-breach", about = "Qasa breach hash index tools")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Ingest pre-computed SHA-256 hashes from a file (one hash per line).
    Ingest {
        /// Path to hash file (64-char hex SHA-256 per line).
        #[arg(short, long)]
        file: PathBuf,

        /// Default breach source label when not specified per line.
        #[arg(short, long, default_value = "local")]
        source: String,

        /// Database URL (defaults to DATABASE_URL env).
        #[arg(long, env = "DATABASE_URL")]
        database_url: Option<String>,
    },

    /// Show index statistics.
    Stats {
        #[arg(long, env = "DATABASE_URL")]
        database_url: Option<String>,
    },
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    match cli.command {
        Commands::Ingest {
            file,
            source,
            database_url,
        } => {
            let url = database_url.unwrap_or_else(|| {
                "postgres://qasa:qasa@localhost:5432/qasa".to_string()
            });
            let pool = connect(&url).await?;
            run_migrations(&pool).await?;

            let content = tokio::fs::read_to_string(&file)
                .await
                .with_context(|| format!("reading {}", file.display()))?;

            let mut entries = Vec::new();
            let mut skipped = 0u64;

            for line in content.lines() {
                match qasa_breach::parse_hash_line(line, &source) {
                    Some(entry) => entries.push(entry),
                    None if line.trim().is_empty() || line.trim_start().starts_with('#') => {}
                    None => skipped += 1,
                }
            }

            println!("parsed {} hashes (skipped {} invalid lines)", entries.len(), skipped);

            let inserted = qasa_db::repos::breach::ingest_batch(&pool, &entries).await?;
            let total = qasa_db::repos::breach::total_count(&pool).await?;

            println!("inserted {inserted} new rows");
            println!("total index size: {total}");
        }
        Commands::Stats { database_url } => {
            let url = database_url.unwrap_or_else(|| {
                "postgres://qasa:qasa@localhost:5432/qasa".to_string()
            });
            let pool = connect(&url).await?;
            run_migrations(&pool).await?;

            let total = qasa_db::repos::breach::total_count(&pool).await?;
            println!("total hashes indexed: {total}");
        }
    }

    Ok(())
}
