// The unpacked extension, the real nm-host and the desktop's real bridge (the Rust
// `bridge_demo_server`: an in-memory vault that approves pairings and saves by itself and
// prints what it did). Covers pairing, the ≥60 site match, filling and saving end to end.
import { spawn, type ChildProcess } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createInterface } from 'node:readline'

import { chromium, expect, test, type BrowserContext } from '@playwright/test'

import { NATIVE_HOST } from '../../src/shared/protocol'
import { EXTENSION_DIR, EXTENSION_ID, fillVia, openPopup, pairThroughPopup, type Env } from '../e2e/harness'
import { startFixtureServers, type FixtureServers } from '../e2e/server'

const ROOT = join(import.meta.dirname, '../..')
const NM_HOST = join(ROOT, '../desktop/nm-host/target/debug/qasa-nm-host')
const TAURI = join(ROOT, '../desktop/src-tauri')

let dir: string
let desktop: ChildProcess
let lines: string[] = []
let context: BrowserContext
let servers: FixtureServers

/** Waits until the demo server printed a line starting with `prefix`. */
async function printed(prefix: string): Promise<string> {
  await expect.poll(() => lines.find((l) => l.startsWith(prefix)) ?? null, { timeout: 15_000 }).not.toBeNull()
  return lines.find((l) => l.startsWith(prefix))!
}

test.beforeAll(async () => {
  dir = mkdtempSync(join(tmpdir(), 'qasa-real-'))
  const socket = join(dir, 'bridge.sock')
  desktop = spawn('cargo', ['test', '-q', '--lib', 'bridge_demo_server', '--', '--ignored', '--nocapture'], {
    cwd: TAURI,
    env: { ...process.env, QASA_BRIDGE_SOCKET: socket, CARGO_INCREMENTAL: '0' },
    stdio: ['ignore', 'pipe', 'inherit'],
  })
  createInterface({ input: desktop.stdout! }).on('line', (l) => lines.push(l))
  await expect.poll(() => lines.some((l) => l.startsWith('LISTENING')), { timeout: 240_000 }).toBe(true)

  const profile = join(dir, 'profile')
  mkdirSync(join(profile, 'NativeMessagingHosts'), { recursive: true })
  writeFileSync(
    join(profile, 'NativeMessagingHosts', `${NATIVE_HOST}.json`),
    JSON.stringify({ name: NATIVE_HOST, description: 'Qasa (real bridge e2e)', path: NM_HOST, type: 'stdio', allowed_origins: [`chrome-extension://${EXTENSION_ID}/`] }),
  )
  servers = await startFixtureServers()
  context = await chromium.launchPersistentContext(profile, {
    headless: false,
    args: ['--headless=new', `--disable-extensions-except=${EXTENSION_DIR}`, `--load-extension=${EXTENSION_DIR}`],
    env: { ...process.env, QASA_BRIDGE_SOCKET: socket },
    viewport: { width: 1000, height: 800 },
  })
  if (context.serviceWorkers().length === 0) await context.waitForEvent('serviceworker')
})

test.afterAll(async () => {
  await context?.close()
  await servers?.close()
  desktop?.kill()
  rmSync(dir, { recursive: true, force: true })
})

const env = () => ({ context }) as unknown as Env

test('pairs, fills each site with its own login only, and saves', async () => {
  await pairThroughPopup(env())
  expect(await printed('PAIRED')).toBe('PAIRED Chrome')

  // 127.0.0.1 gets its login …
  const page = await context.newPage()
  await page.goto(`${servers.main}/login.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#username')).toHaveValue('alice')
  await expect(page.locator('#password')).toHaveValue('alice-pass-127')

  // … and localhost (another site to the matcher) gets only its own.
  const other = await context.newPage()
  await other.goto(`${servers.other}/login.html`)
  await fillVia(other, other.locator('#password'), 0)
  await expect(other.locator('#username')).toHaveValue('bob')
  await expect(other.locator('#password')).toHaveValue('bob-pass-localhost')

  // The popup lists only the page's site.
  const popup = await openPopup(env(), page)
  await expect(popup.getByText('Fixture 127')).toBeVisible()
  await expect(popup.getByText('Fixture localhost')).toHaveCount(0)
  await popup.close()

  // A new password on a saved login becomes an update once the desktop confirms it.
  await page.bringToFront()
  await page.locator('#password').fill('alice-new-pass')
  await page.locator('#password').press('Enter')
  expect(await printed('SAVED')).toBe('SAVED update 127.0.0.1 alice')

  // Filling again gets the new password: the save really went into the vault.
  const again = await context.newPage()
  await again.goto(`${servers.main}/login.html`)
  await fillVia(again, again.locator('#username'), 0)
  await expect(again.locator('#password')).toHaveValue('alice-new-pass')

  // A login for another site is a new item.
  const signup = await context.newPage()
  await signup.goto(`${servers.other}/signup.html`)
  await signup.locator('input[type=email], input[autocomplete=username], input[name=email]').first().fill('new@localhost')
  for (const pw of await signup.locator('input[type=password]').all()) await pw.fill('fresh-pass-1')
  await signup.locator('button[type=submit]').click()
  await expect.poll(() => lines.filter((l) => l.startsWith('SAVED')).length).toBe(2)
  expect(lines.filter((l) => l.startsWith('SAVED'))[1]).toBe('SAVED save localhost new@localhost')
})
