import { defineConfig } from '@playwright/test'

// The extension against the desktop's real bridge code (Rust), not the fake desktop:
// test/real/real-bridge.spec.ts starts `bridge_demo_server` from apps/desktop/src-tauri.
export default defineConfig({
  testDir: 'test/real',
  workers: 1,
  fullyParallel: false,
  timeout: 300_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  retries: 0,
})
