// Cards and profiles (docs/autofill-plan.md §3.2): a click into a field opens the menu,
// a card waits for the desktop's confirmation, a profile fills the whole form, and a
// locked vault unlocks from the menu and then lists the items without a second click.
import { LOGINS } from '../fake-desktop'
import { clickMenuRow, expect, openMenuByClick, test } from './harness'

test('a card fills after the desktop confirms it', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/checkout.html`)
  const number = page.locator('#ccnumber')
  await openMenuByClick(page, number)
  await clickMenuRow(page, number, 0)
  await expect(number).toHaveValue('4242424242424242')
  await expect(page.locator('#ccname')).toHaveValue('Ali Barznji')
  await expect(page.locator('#ccmonth')).toHaveValue('03')
  await expect(page.locator('#ccyear')).toHaveValue('2029')
  await expect(page.locator('#cvc')).toHaveValue('123')
  // A field pushed off the page gets nothing.
  await expect(page.locator('#hidden-cc')).toHaveValue('')
  expect(env.fake.confirms).toEqual(['card-1'])
  // The card fill touched no profile field.
  await expect(page.locator('#city')).toHaveValue('')
})

test('a dismissed confirmation fills nothing', async ({ env }) => {
  env.fake.confirmAnswer = 'deny'
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/checkout.html`)
  const number = page.locator('#ccnumber')
  await openMenuByClick(page, number)
  await clickMenuRow(page, number, 0)
  await expect.poll(() => env.fake.confirms.length).toBe(1)
  await page.waitForTimeout(2000)
  await expect(number).toHaveValue('')
  await expect(page.locator('#cvc')).toHaveValue('')
})

test('a profile fills the whole address form, below the fold too', async ({ env }) => {
  const page = await env.context.newPage()
  await page.setViewportSize({ width: 1000, height: 300 })
  await page.goto(`${env.servers.main}/checkout.html`)
  const name = page.locator('#fullname')
  await openMenuByClick(page, name)
  await clickMenuRow(page, name, 0)
  await expect(name).toHaveValue('Ali Barznji')
  await expect(page.locator('#email')).toHaveValue('ali@example.com')
  await expect(page.locator('#phone')).toHaveValue('+9647501234567')
  await expect(page.locator('#street')).toHaveValue('100 Salim St')
  await expect(page.locator('#line2')).toHaveValue('Building 4')
  await expect(page.locator('#city')).toHaveValue('Sulaymaniyah')
  await expect(page.locator('#zip')).toHaveValue('46001')
  await expect(page.locator('#country')).toHaveValue('IQ')
  // No confirmation for a profile, and no card value.
  expect(env.fake.confirms).toEqual([])
  await expect(page.locator('#ccnumber')).toHaveValue('')
})

test('clicking into a login field opens the menu', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = page.locator('#username')
  await openMenuByClick(page, user)
  await clickMenuRow(page, user, 0)
  await expect(user).toHaveValue(LOGINS[0].username)
  await expect(page.locator('#password')).toHaveValue(LOGINS[0].password)
})

test('a script clicking a field opens nothing', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await expect.poll(() => env.fake.ops.includes('status')).toBe(true)
  await page.evaluate(() => {
    const f = document.getElementById('username') as HTMLInputElement
    f.focus()
    f.click()
  })
  await page.waitForTimeout(800)
  const user = page.locator('#username')
  // Where a row would be: nothing there to pick.
  await clickMenuRow(page, user, 0)
  await page.waitForTimeout(500)
  await expect(user).toHaveValue('')
  expect(env.fake.urlOps).not.toContain('fill 127.0.0.1')
})

test('locked: Unlock in the menu, then the logins show without a second click', async ({ env }) => {
  env.fake.state = 'locked'
  env.fake.unlockDelayMs = 1500
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = page.locator('#username')
  await openMenuByClick(page, user)
  // The menu shows "Qasa is locked." and the Unlock button where the first row would be.
  const b = (await user.boundingBox())!
  const { MENU_GAP, MENU_PADDING, MENU_HEADER } = await import('../../src/content/layout')
  // The action button sits under the one-line message (about 40 px).
  await page.mouse.click(b.x + 60, b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + 52)
  await expect.poll(() => env.fake.unlocks).toBe(1)
  await expect.poll(() => env.fake.state, { timeout: 5000 }).toBe('unlocked')
  // The menu reloads itself (status is cached up to 3 s) and lists the logins.
  await page.waitForTimeout(4500)
  await clickMenuRow(page, user, 0)
  await expect(user).toHaveValue(LOGINS[0].username)
})
