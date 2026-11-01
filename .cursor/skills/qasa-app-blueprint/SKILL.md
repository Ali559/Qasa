---
name: qasa-app-blueprint
description: >-
  Qasa/Kaso product architecture — screens, local vault, Rust API, admin dashboard,
  payments, breach k-anonymity, VPN. Use when working on Qasa iOS, server, or integrations.
---

# Qasa Product Blueprint

The full verbatim product blueprint is in [blueprint.md](blueprint.md).

## Codebase addendum

- Product branding in code: **Qasa** (monorepo: apps under `apps/`, API in `server/`); blueprint text may say Kaso.
- iOS app: `apps/ios/qasa_ios/`
- Rust API: `server/` — run with `cargo run -p qasa-api`
- Admin dashboard: `apps/dashboard/` — set `VITE_QASA_API_URL`
- Mobile auth: **license JWT** via `Authorization: Bearer` from `POST /v1/devices/register` or `GET /v1/license`
- Admin auth: separate HS256 JWT from `POST /v1/admin/auth/login`
- Dev API: `http://127.0.0.1:8080` (use Mac LAN IP from physical device)
- Smoke test: `server/scripts/smoke-api.sh`
- See `server/README.md` for endpoints
