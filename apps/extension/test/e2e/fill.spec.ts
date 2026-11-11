// Filling through the in-page menu (real mouse clicks into the closed shadow root) and
// through the popup's Fill button, on each fixture page.
import { LOGINS, totp } from '../fake-desktop'
import { clickIcon, clickMenuRow, expect, fillVia, iconHosts, openPopup, test } from './harness'

const alpha = LOGINS[0]
const localhost = LOGINS[2]
/** The current code, or the previous one if a 30 s window just ended. */
const recentCodes = () => [totp(alpha.totpSecret!), totp(alpha.totpSecret!, Date.now() - 30_000)]

test('simple login', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#username')).toHaveValue(alpha.username)
  await expect(page.locator('#password')).toHaveValue(alpha.password)
  expect(await iconHosts(page)).toBe(1)
  expect(env.fake.urlOps).toEqual(['matches 127.0.0.1', 'fill 127.0.0.1'])
})

test('the menu uses IBM Plex Sans Arabic, loaded from the extension into the page', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await fillVia(page, page.locator('#username'), 0)
  const faces = await page.evaluate(() => [...document.fonts].filter((f) => f.family.replace(/"/g, '').startsWith('qasa-')).map((f) => [f.family.replace(/"/g, '').split('-')[1], f.weight, f.status]))
  expect(faces.sort()).toEqual([['plex', '400', 'loaded'], ['plex', '500', 'loaded'], ['plex', '600', 'loaded'], ['plex', '700', 'loaded']])
})

test('second row fills the second login', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await fillVia(page, page.locator('#password'), 1)
  await expect(page.locator('#username')).toHaveValue(LOGINS[1].username)
  await expect(page.locator('#password')).toHaveValue(LOGINS[1].password)
})

test('controlled (React-like) inputs update their state', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/react.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#state')).toHaveText(JSON.stringify({ email: alpha.username, password: alpha.password }))
})

test('login inside an open shadow root', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/shadow.html`)
  const user = page.locator('login-box #username') // Playwright pierces open roots
  await fillVia(page, user, 0)
  await expect(user).toHaveValue(alpha.username)
  await expect(page.locator('login-box #password')).toHaveValue(alpha.password)
})

test('multi-step: username page, then password page', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/multistep-1.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#username')).toHaveValue(alpha.username)
  await page.getByRole('button', { name: 'Next' }).click()
  await page.waitForURL('**/multistep-2.html')
  await fillVia(page, page.locator('#password'), 0)
  await expect(page.locator('#password')).toHaveValue(alpha.password)
})

test('one-time-code field gets the current 2FA code', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/otp.html`)
  await fillVia(page, page.locator('#code'), 0) // only items with 2FA are listed
  await expect.poll(() => page.locator('#code').inputValue()).toMatch(/^\d{6}$/)
  expect(recentCodes()).toContain(await page.locator('#code').inputValue())
  expect(env.fake.urlOps).toContain('totp 127.0.0.1')
})

test('login with a code field on the same form fills all three', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login-otp.html`)
  await fillVia(page, page.locator('#password'), 0)
  await expect(page.locator('#password')).toHaveValue(alpha.password)
  await expect.poll(() => page.locator('#mfa').inputValue()).toMatch(/^\d{6}$/)
  expect(recentCodes()).toContain(await page.locator('#mfa').inputValue())
})

test('same-origin iframes, including a script-written one, are handled exactly once', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/iframe-same.html`)
  const framed = page.frameLocator('#framed')
  await expect.poll(async () => iconHosts(page.frames().find((f) => f.url().endsWith('/login.html'))!)).toBe(1)
  await fillVia(page, framed.locator('#username'), 0)
  await expect(framed.locator('#password')).toHaveValue(alpha.password)

  const written = page.frameLocator('#written')
  const writtenFrame = page.frames().find((f) => f.url() === 'about:blank')!
  await expect.poll(() => iconHosts(writtenFrame)).toBe(1)
  await clickIcon(page, written.locator('#username'))
  await page.waitForTimeout(800)
  await clickMenuRow(page, written.locator('#username'), 0)
  await expect(written.locator('#username')).toHaveValue(alpha.username)
  await expect(written.locator('#password')).toHaveValue(alpha.password)
  expect(await iconHosts(writtenFrame)).toBe(1)
  expect(await iconHosts(page)).toBe(0) // the top page has no fields of its own
})

test('a cross-origin iframe only gets logins for its own origin', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/iframe-cross.html`)
  const frame = page.frameLocator('#cross')
  await fillVia(page, frame.locator('#username'), 0)
  await expect(frame.locator('#username')).toHaveValue(localhost.username)
  await expect(frame.locator('#password')).toHaveValue(localhost.password)
  // The top page still gets its own.
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#username')).toHaveValue(alpha.username)
  expect(env.fake.urlOps).toEqual(['matches localhost', 'fill localhost', 'matches 127.0.0.1', 'fill 127.0.0.1'])
  // Nothing from the top page leaked into the frame.
  await expect(frame.locator('#password')).toHaveValue(localhost.password)
})

test('the popup Fill button fills the page', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const popup = await openPopup(env, page)
  const row = popup.getByTestId('login-row').filter({ hasText: 'Beta 127' })
  await row.getByRole('button', { name: 'Fill' }).click()
  await expect(page.locator('#username')).toHaveValue(LOGINS[1].username)
  await expect(page.locator('#password')).toHaveValue(LOGINS[1].password)
})

test('no icons when Qasa has no vault', async ({ env }) => {
  env.fake.state = 'noVault'
  const page = await env.context.newPage()
  await page.waitForTimeout(3500) // the service worker shares one status answer for 3 s
  await page.goto(`${env.servers.main}/login.html`)
  await page.waitForTimeout(1500)
  expect(await iconHosts(page)).toBe(0)
})
