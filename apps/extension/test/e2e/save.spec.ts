// Save / update: on submit the content script reports the credentials and the desktop
// (here the fake) receives `save` with the frame's URL from the browser.
import { LOGINS } from '../fake-desktop'
import { expect, fillVia, iconHosts, test } from './harness'

test('submitting a login sends save once (click on the button)', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await page.locator('#username').pressSequentially('new@user.test')
  await page.locator('#password').pressSequentially('typed-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect.poll(() => env.fake.saves).toEqual([{ url: `${env.servers.main}/login.html`, username: 'new@user.test', password: 'typed-password' }])
  await page.waitForTimeout(500)
  expect(env.fake.saves).toHaveLength(1)
})

test('Enter in the password field sends save once', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await page.locator('#username').pressSequentially('enter@user.test')
  await page.locator('#password').pressSequentially('enter-password')
  await page.locator('#password').press('Enter')
  await expect.poll(() => env.fake.saves.length).toBe(1)
  expect(env.fake.saves[0]).toMatchObject({ username: 'enter@user.test', password: 'enter-password' })
  await page.waitForTimeout(500)
  expect(env.fake.saves).toHaveLength(1)
})

test('a change-password form sends the new password for an update', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/change-password.html`)
  await page.locator('#current').pressSequentially('alpha-127-password')
  await page.locator('#new').pressSequentially('next-password-1')
  await page.locator('#confirm').pressSequentially('next-password-1')
  await page.getByRole('button', { name: 'Change password' }).click()
  await expect.poll(() => env.fake.saves).toEqual([{ url: `${env.servers.main}/change-password.html`, username: 'alice@127', password: 'next-password-1' }])
})

test('a signup form sends the new account', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/signup.html`)
  await page.locator('#username').pressSequentially('fresh@user.test')
  await page.locator('#password').pressSequentially('fresh-password')
  await page.locator('#confirm').pressSequentially('fresh-password')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect.poll(() => env.fake.saves).toEqual([{ url: `${env.servers.main}/signup.html`, username: 'fresh@user.test', password: 'fresh-password' }])
})

test('multi-step: the username from step 1 is saved with the password from step 2', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/multistep-1.html`)
  await page.locator('#username').pressSequentially('steps@user.test')
  await page.getByRole('button', { name: 'Next' }).click()
  await page.waitForURL('**/multistep-2.html')
  await page.locator('#password').pressSequentially('step-two-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect.poll(() => env.fake.saves).toEqual([{ url: `${env.servers.main}/multistep-2.html`, username: 'steps@user.test', password: 'step-two-password' }])
})

test('submitting a login Qasa just filled, unchanged, sends nothing', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#password')).toHaveValue(LOGINS[0].password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.locator('#result')).toContainText(LOGINS[0].username)
  await page.waitForTimeout(800)
  expect(env.fake.saves).toEqual([])
})

test('an empty password sends nothing', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await page.locator('#username').pressSequentially('nobody@user.test')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForTimeout(800)
  expect(env.fake.saves).toEqual([])
})

test('a login in a script-written frame is saved with the parent page URL', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/iframe-same.html`)
  const written = page.frameLocator('#written')
  await written.locator('#username').pressSequentially('framed@user.test')
  await written.locator('#password').pressSequentially('framed-password')
  // The parent's copy has taken the frame on (its icons are up).
  await expect.poll(() => iconHosts(page.frames().find((f) => f.url() === 'about:blank')!)).toBe(1)
  await written.locator('#password').press('Enter')
  await expect.poll(() => env.fake.saves).toEqual([{ url: `${env.servers.main}/iframe-same.html`, username: 'framed@user.test', password: 'framed-password' }])
})
