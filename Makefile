# Qasa local development helpers
#
#   make db        # qasa role + database on the local Postgres (no Docker)
#   make api       # run API locally (cargo)
#   make dev       # db + api
#   make smoke     # API smoke test
#   make help

SHELL := /bin/bash
ROOT  := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SERVER := $(ROOT)/server
ANDROID := $(ROOT)/apps/android
DESKTOP := $(ROOT)/apps/desktop
CORE := $(ROOT)/core
API_URL ?= http://127.0.0.1:8080

.PHONY: help db api android env smoke worker dash desktop desktop-build nm-host core-test ios-ffi android-ffi

help:
	@echo "Qasa local commands"
	@echo ""
	@echo "  make db       Create the qasa role and database on the local Postgres"
	@echo "  make env      Ensure server/.env exists (from .env.example)"
	@echo "  make api      Run qasa-api locally on :8080"
	@echo "  make android  Build & install debug APK (needs emulator/device)"
	@echo "  make dev      Set up the database, then run the API"
	@echo "  make smoke    Run server/scripts/smoke-api.sh"
	@echo "  make worker   Run qasa-worker (needs QASA_ENV=prod)"
	@echo "  make dash     Run admin dashboard (Vite)"
	@echo "  make desktop  Run the desktop app (Tauri dev)"
	@echo "  make desktop-build  Build the desktop app bundle"
	@echo "  make nm-host  Build the browser extension's native messaging host"
	@echo "  make extension  Build the browser extension (dist/chrome, dist/firefox)"
	@echo "  make core-test  Test core/ (vault crypto, golden vectors, sync)"
	@echo "  make ios-ffi  Build QasaFFI.xcframework + Swift bindings (Wi-Fi sync)"
	@echo "  make android-ffi  Build libqasa_ffi.so + Kotlin bindings (Wi-Fi sync)"
	@echo ""

# The local Postgres (Homebrew: brew services start postgresql@18), no Docker. Creates the
# `qasa` role and database if missing; the API applies migrations when it starts.
db:
	@pg_isready -q || { echo "Postgres is not running: brew services start postgresql@18" >&2; exit 1; }
	@psql -d postgres -Atqc "SELECT 1 FROM pg_roles WHERE rolname = 'qasa'" | grep -q 1 \
		|| psql -d postgres -qc "CREATE ROLE qasa LOGIN PASSWORD 'qasa'"
	@psql -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname = 'qasa'" | grep -q 1 \
		|| psql -d postgres -qc "CREATE DATABASE qasa OWNER qasa"
	@echo "Postgres: postgres://qasa:qasa@localhost:5432/qasa"

env:
	@if [[ ! -f $(SERVER)/.env ]]; then \
		cp $(SERVER)/.env.example $(SERVER)/.env; \
		echo "Created server/.env from .env.example"; \
	else \
		echo "server/.env already exists"; \
	fi

api: env
	cd $(SERVER) && cargo run -p qasa-api

android:
	cd $(ANDROID) && \
		JAVA_HOME="$${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}" \
		./gradlew :app:assembleDebug :app:installDebug

dev: db api

smoke: env
	QASA_API_URL=$(API_URL) $(SERVER)/scripts/smoke-api.sh

worker: env
	cd $(SERVER) && cargo run -p qasa-worker

dash:
	cd $(ROOT)/apps/dashboard && bun run dev

desktop: nm-host
	cd $(DESKTOP) && bun install && bun run tauri dev

desktop-build:
	cd $(DESKTOP) && bun install && bun run tauri build

nm-host:
	cd $(DESKTOP)/nm-host && CARGO_INCREMENTAL=0 cargo build

core-test:
	cd $(CORE) && CARGO_INCREMENTAL=0 cargo test

ios-ffi:
	$(CORE)/qasa-ffi/scripts/build-ios.sh

android-ffi:
	$(CORE)/qasa-ffi/scripts/build-android.sh

.PHONY: extension

extension:
	cd $(ROOT)/apps/extension && bun install && bun run build
