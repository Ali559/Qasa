# Qasa local development helpers
#
#   make up        # start Postgres
#   make api       # run API locally (cargo)
#   make dev       # up + api
#   make down      # stop containers
#   make smoke     # API smoke test
#   make help

SHELL := /bin/bash
ROOT  := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
SERVER := $(ROOT)/server
ANDROID := $(ROOT)/apps/android
DESKTOP := $(ROOT)/apps/desktop
CORE := $(ROOT)/core
COMPOSE := docker compose -f $(SERVER)/docker/docker-compose.yml
API_URL ?= http://127.0.0.1:8080

.PHONY: help up down logs api android env wait-db migrate-hint smoke worker dash clean desktop desktop-build core-test

help:
	@echo "Qasa local commands"
	@echo ""
	@echo "  make up       Start Postgres (Docker)"
	@echo "  make down     Stop Docker services"
	@echo "  make logs     Tail Docker logs"
	@echo "  make env      Ensure server/.env exists (from .env.example)"
	@echo "  make api      Run qasa-api locally on :8080"
	@echo "  make android  Build & install debug APK (needs emulator/device)"
	@echo "  make dev      Start Docker then run the API"
	@echo "  make smoke    Run server/scripts/smoke-api.sh"
	@echo "  make worker   Run qasa-worker (needs QASA_ENV=prod)"
	@echo "  make dash     Run admin dashboard (Vite)"
	@echo "  make desktop  Run the desktop app (Tauri dev)"
	@echo "  make desktop-build  Build the desktop app bundle"
	@echo "  make core-test  Test core/ (vault crypto, golden vectors)"
	@echo "  make clean    docker compose down -v (wipes DB volumes)"
	@echo ""

up:
	$(COMPOSE) up -d
	@echo "Postgres → localhost:$${QASA_POSTGRES_PORT:-5433}"

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

env:
	@if [[ ! -f $(SERVER)/.env ]]; then \
		cp $(SERVER)/.env.example $(SERVER)/.env; \
		echo "Created server/.env from .env.example"; \
	else \
		echo "server/.env already exists"; \
	fi

wait-db:
	@echo "Waiting for Postgres…"
	@for i in $$(seq 1 30); do \
		if $(COMPOSE) exec -T postgres pg_isready -U qasa -d qasa >/dev/null 2>&1; then \
			echo "Postgres is ready."; \
			exit 0; \
		fi; \
		sleep 1; \
	done; \
	echo "Postgres did not become ready in time." >&2; \
	exit 1

api: env
	cd $(SERVER) && cargo run -p qasa-api

android:
	cd $(ANDROID) && \
		JAVA_HOME="$${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}" \
		./gradlew :app:assembleDebug :app:installDebug

dev: up wait-db api

smoke: env
	QASA_API_URL=$(API_URL) $(SERVER)/scripts/smoke-api.sh

worker: env
	cd $(SERVER) && cargo run -p qasa-worker

dash:
	cd $(ROOT)/apps/dashboard && bun run dev

desktop:
	cd $(DESKTOP) && bun install && bun run tauri dev

desktop-build:
	cd $(DESKTOP) && bun install && bun run tauri build

core-test:
	cd $(CORE) && cargo test

clean:
	$(COMPOSE) down -v
	@echo "Containers stopped and volumes removed."
