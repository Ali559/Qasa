use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use thiserror::Error;

#[derive(Debug, Error)]
pub enum RateLimitError {
    #[error("rate limit exceeded: max {max} requests per {window_secs}s")]
    Exceeded { max: u32, window_secs: u64 },
}

/// Simple in-memory sliding-window rate limiter keyed by account ID.
/// Suitable for single-instance dev; swap for Redis in production.
#[derive(Debug)]
pub struct RateLimiter {
    max_requests: u32,
    window: Duration,
    buckets: Mutex<HashMap<String, Vec<Instant>>>,
}

impl RateLimiter {
    pub fn new(max_requests: u32, window: Duration) -> Self {
        Self {
            max_requests,
            window,
            buckets: Mutex::new(HashMap::new()),
        }
    }

    pub fn check(&self, key: &str) -> Result<(), RateLimitError> {
        let now = Instant::now();
        let cutoff = now - self.window;

        let mut buckets = self.buckets.lock().expect("rate limiter lock");
        let entries = buckets.entry(key.to_string()).or_default();
        entries.retain(|t| *t > cutoff);

        if entries.len() as u32 >= self.max_requests {
            return Err(RateLimitError::Exceeded {
                max: self.max_requests,
                window_secs: self.window.as_secs(),
            });
        }

        entries.push(now);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allows_under_limit() {
        let limiter = RateLimiter::new(3, Duration::from_secs(3600));
        for _ in 0..3 {
            limiter.check("acct-1").unwrap();
        }
        assert!(limiter.check("acct-1").is_err());
    }

    #[test]
    fn separate_keys_independent() {
        let limiter = RateLimiter::new(1, Duration::from_secs(3600));
        limiter.check("a").unwrap();
        limiter.check("b").unwrap();
    }
}
