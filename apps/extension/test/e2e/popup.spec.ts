// The toolbar popup in each bridge state.
import { expect, openPopup, test } from './harness'

test('app not running, then running again', async ({ env }) => {
  await env.fake.stop()
  try {
    const popup = await openPopup(env)
    await expect(popup.getByText("Qasa isn't running")).toBeVisible()
    await expect(popup.getByRole('link', { name: 'Download Qasa' })).toHaveAttribute('href', 'https://qasa-app.com/download')
    await env.fake.start()
    await popup.getByRole('button', { name: 'Try again' }).click()
    await expect(popup.locator('main')).toHaveAttribute('data-state', 'unlocked')
  } finally {
    await env.fake.start().catch(() => undefined)
  }
})

test('locked: Unlock Qasa brings the app forward, then the list shows', async ({ env }) => {
  env.fake.state = 'locked'
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const popup = await openPopup(env, page)
  await expect(popup.getByText('Qasa is locked')).toBeVisible()
  await popup.getByRole('button', { name: 'Unlock Qasa' }).click()
  await expect.poll(() => env.fake.unlocks).toBeGreaterThan(0)
  await expect(popup.getByTestId('login-row')).toHaveCount(2)
})

test('no vault', async ({ env }) => {
  env.fake.state = 'noVault'
  const popup = await openPopup(env)
  await expect(popup.getByText('Set up Qasa first')).toBeVisible()
})

test('unlocked: site logins, search, copy and generator', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const popup = await openPopup(env, page)
  const rows = popup.getByTestId('login-row')
  await expect(rows).toHaveCount(2)
  await expect(rows.nth(0)).toContainText('Alpha 127')
  await expect(rows.nth(0)).toContainText('alice@127')
  await expect(rows.nth(1)).toContainText('Beta 127')

  await rows.nth(0).getByRole('button', { name: 'Copy Password' }).click()
  await expect(popup.getByRole('status')).toHaveText('Copied. Qasa clears it in 30 seconds.')
  await rows.nth(0).getByRole('button', { name: 'Copy 2FA Code' }).click()
  await expect(popup.getByRole('status')).toHaveText('2FA code copied')
  expect(env.fake.copies).toEqual([
    { itemId: 'a1', field: 'password' },
    { itemId: 'a1', field: 'totp' },
  ])
  await expect(rows.nth(1).getByRole('button', { name: 'Copy 2FA Code' })).toHaveCount(0)

  await popup.getByPlaceholder('Search your passwords').fill('local')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('Localhost Account')
  // Not a match for this page: copy only, no Fill.
  await expect(rows.first().getByRole('button', { name: 'Fill' })).toHaveCount(0)

  await popup.getByRole('button', { name: 'Generator' }).click()
  await expect(popup.getByTestId('generated')).toHaveText(/^\S{12,}$/)
})

test('a page without a content script shows the hint', async ({ env }) => {
  const popup = await openPopup(env) // its "active tab" is the popup itself
  await expect(popup.getByText('Open a website to see its logins here.')).toBeVisible()
})

test('language picker switches to Arabic, right to left', async ({ env }) => {
  const popup = await openPopup(env)
  await popup.getByRole('combobox').selectOption('ar')
  await expect(popup.locator('html')).toHaveAttribute('dir', 'rtl')
  await expect(popup.getByRole('button', { name: 'تسجيلات الدخول' })).toBeVisible()
  await popup.getByRole('combobox').selectOption('en')
  await expect(popup.locator('html')).toHaveAttribute('dir', 'ltr')
})
