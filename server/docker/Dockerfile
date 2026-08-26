FROM rust:1.85-bookworm AS builder
WORKDIR /app
COPY Cargo.toml Cargo.lock ./
COPY crates ./crates
COPY migrations ./migrations
RUN cargo build --release -p qasa-api -p qasa-worker

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/target/release/qasa-api /usr/local/bin/qasa-api
COPY --from=builder /app/target/release/qasa-worker /usr/local/bin/qasa-worker
ENV BIND_ADDR=0.0.0.0:8080
EXPOSE 8080
CMD ["qasa-api"]
