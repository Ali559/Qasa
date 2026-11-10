// Launches Chromium with the unpacked extension (dist/chrome), a user-level native
// messaging host manifest pointing at the real nm-host binary, and a fake desktop on a
// temp unix socket (QASA_BRIDGE_SOCKET). Clicks into the closed-shadow-root menu are made
// by coordinates (src/content/layout.ts), with real mouse events.
import { test as base, chromium, expect, type BrowserContext, type Locator, type Page } from '@playwright/test'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

import { ICON_INSET, ICON_SIZE, MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from '../../src/content/layout'
import { NATIVE_HOST } from '../../src/shared/protocol'
import { FakeDesktop } from '../fake-desktop'
import { startFixtureServers, type FixtureServers } from './server'

const ROOT = join(import.meta.dirname, '../..')
export const EXTENSION_DIR = join(ROOT, 'dist/chrome')
export const EXTENSION_ID = 'fiamcbmecfeeofaadngagmdldkgncmmd'
const NM_HOST = join(ROOT, '../desktop/nm-host/target/debug/qasa-nm-host')

export interface Env {
  context: BrowserContext
  fake: FakeDesktop
  servers: FixtureServers
  close(): Promise<void>
}

/** A fresh profile with the extension and the native host installed. */
export async function launch(servers: FixtureServers, opts: { pair: boolean } = { pair: true }): Promise<Env> {
  if (!existsSync(join(EXTENSION_DIR, 'manifest.json'))) throw new Error('Run `bun run build` first')
  if (!existsSync(NM_HOST)) throw new Error(`nm-host not built: ${NM_HOST} (make nm-host)`)
  // Short paths: unix socket paths are limited to ~104 bytes on macOS.
  const dir = mkdtempSync(join(tmpdir(), 'qasa-e2e-'))
  const profile = join(dir, 'profile')
  const socket = join(dir, 'bridge.sock')
  // Chromium on macOS and Linux reads user-level host manifests from the profile.
  mkdirSync(join(profile, 'NativeMessagingHosts'), { recursive: true })
  writeFileSync(
    join(profile, 'NativeMessagingHosts', `${NATIVE_HOST}.json`),
    JSON.stringify({ name: NATIVE_HOST, description: 'Qasa (e2e)', path: NM_HOST, type: 'stdio', allowed_origins: [`chrome-extension://${EXTENSION_ID}/`] }),
  )
  const fake = new FakeDesktop(socket)
  await fake.start()
  const context = await chromium.launchPersistentContext(profile, {
    // Extensions need the full browser; `--headless=new` keeps it off screen.
    headless: false,
    args: ['--headless=new', `--disable-extensions-except=${EXTENSION_DIR}`, `--load-extension=${EXTENSION_DIR}`],
    env: { ...process.env, QASA_BRIDGE_SOCKET: socket },
    viewport: { width: 1000, height: 800 },
  })
  let [worker] = context.serviceWorkers()
  worker ??= await context.waitForEvent('serviceworker')
  expect(worker.url()).toBe(`chrome-extension://${EXTENSION_ID}/background.js`)
  const env: Env = {
    context,
    fake,
    servers,
    close: async () => {
      await context.close()
      await fake.stop()
      rmSync(dir, { recursive: true, force: true })
    },
  }
  if (opts.pair) await pairThroughPopup(env)
  return env
}

/** The popup as a page of its own, acting on `target`'s tab. */
export async function openPopup(env: Env, target?: Page): Promise<Page> {
  const popup = await env.context.newPage()
  let query = ''
  if (target) {
    // Without the `tabs` permission tab URLs are hidden, so find the tab by making it the
    // active one.
    await popup.goto(`chrome-extension://${EXTENSION_ID}/popup.html?tab=-1`)
    await target.bringToFront()
    const tabId = await popup.evaluate(async () => (await chrome.tabs.query({ active: true, currentWindow: true }))[0]?.id)
    query = `?tab=${tabId}`
  }
  await popup.goto(`chrome-extension://${EXTENSION_ID}/popup.html${query}`)
  return popup
}

export async function pairThroughPopup(env: Env): Promise<string> {
  const popup = await openPopup(env)
  await popup.getByRole('button', { name: 'Connect' }).click()
  const code = (await popup.getByTestId('pair-code').textContent())!.replace(/\s/g, '')
  await expect(popup.locator('main')).toHaveAttribute('data-state', 'unlocked')
  await popup.close()
  return code
}

// MARK: in-page menu

export async function box(locator: Locator) {
  const b = await locator.boundingBox()
  if (!b) throw new Error('no bounding box')
  return b
}

/** Clicks the Qasa icon inside `field` (left-to-right page). */
export async function clickIcon(page: Page, field: Locator): Promise<void> {
  const b = await box(field)
  await page.mouse.click(b.x + b.width - ICON_INSET - ICON_SIZE / 2, b.y + b.height / 2)
}

/** Clicks row `index` of the menu opened under `field`. */
export async function clickMenuRow(page: Page, field: Locator, index: number): Promise<void> {
  const b = await box(field)
  const top = b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER
  await page.mouse.click(b.x + MENU_WIDTH / 2, top + index * ROW_HEIGHT + ROW_HEIGHT / 2)
}

/** Opens the menu on `field` and picks row `index` once the menu is armed. */
export async function fillVia(page: Page, field: Locator, index = 0): Promise<void> {
  await expect.poll(async () => (await Promise.all(page.frames().map(iconHosts))).reduce((a, b) => a + b, 0)).toBeGreaterThan(0)
  await clickIcon(page, field)
  await page.waitForTimeout(800) // the menu ignores clicks for its first 500 ms
  await clickMenuRow(page, field, index)
}

/** Text of the open in-page menu, read through the closed shadow root by hit-testing
 *  isn't possible, so callers check effects instead; this waits for the menu to arm. */
export async function openMenuByClick(page: Page, field: Locator): Promise<void> {
  await expect.poll(async () => (await Promise.all(page.frames().map(iconHosts))).reduce((a, b) => a + b, 0)).toBeGreaterThan(0)
  const b = await box(field)
  // Into the field, away from the icon.
  await page.mouse.click(b.x + 12, b.y + b.height / 2)
  await page.waitForTimeout(800)
}

/** Number of Qasa host elements on <html> of `page`'s main frame. */
export function iconHosts(target: Page | import('@playwright/test').Frame): Promise<number> {
  return target.evaluate(() => [...document.documentElement.children].filter((e) => e.tagName === 'DIV' && (e.getAttribute('style') ?? '').includes('2147483647')).length)
}

type Fixtures = { env: Env }
type WorkerFixtures = { shared: Env; servers: FixtureServers }

/** `env`: one paired browser and fake desktop shared by every test in the worker. */
export const test = base.extend<Fixtures, WorkerFixtures>({
  servers: [
    async ({}, use) => {
      const servers = await startFixtureServers()
      await use(servers)
      await servers.close()
    },
    { scope: 'worker' },
  ],
  shared: [
    async ({ servers }, use) => {
      const env = await launch(servers)
      await use(env)
      await env.close()
    },
    { scope: 'worker' },
  ],
  env: async ({ shared }, use) => {
    shared.fake.state = 'unlocked'
    shared.fake.saves.length = 0
    shared.fake.copies.length = 0
    shared.fake.urlOps.length = 0
    shared.fake.confirms.length = 0
    shared.fake.confirmAnswer = 'approve'
    shared.fake.unlockDelayMs = 0
    await use(shared)
    // A test that changed the vault's state leaves that answer in the service worker's
    // 3 s status cache, and a page that saw it rechecks only after 30 s.
    if (shared.fake.state !== 'unlocked') await new Promise((r) => setTimeout(r, 3500))
    // Keep the first (blank) page so the browser always has a window.
    for (const p of shared.context.pages().slice(1)) await p.close()
  },
})

export { expect }
