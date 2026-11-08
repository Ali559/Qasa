import { defineConfig } from '@playwright/test'

// One worker: every test drives the same unpacked extension, fake desktop and fixture
// servers (see test/e2e/harness.ts). Run `bun run build` first (`bun run e2e` does).
export default defineConfig({
  testDir: 'test/e2e',
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 8_000 },
  reporter: [['list']],
  retries: 0,
})
