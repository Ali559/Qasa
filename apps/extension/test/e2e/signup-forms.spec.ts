// Forms A–K of docs/autofill-test-pages/signup.html (docs/autofill-hardening.md test matrix):
// "Use a strong password" fills every new-password field with one value, and the page's
// own script (which, for H, only believes `input` events) sees that value in both. K is a
// login: its logins come first and the strong password last (R7).
import type { Locator, Page } from '@playwright/test'

import { MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from '../../src/content/layout'
import { LOGINS } from '../fake-desktop'
import { box, expect, iconHosts, readMenu, test } from './harness'

/** The new-password field the menu opens on, and every field that must get the password. */
const FORMS: Record<string, { field: string; fresh: string[]; maxLength?: number }> = {
  a: { field: 'password', fresh: ['password', 'password_confirmation'] },
  b: { field: 'password', fresh: ['password', 'password_repeat'] },
  c: { field: 'pw1', fresh: ['pw1', 'pw2'] },
  d: { field: 'password', fresh: ['password'] },
  e: { field: 'new', fresh: ['new', 'confirm'] },
  f: { field: 'password', fresh: ['password', 'confirm_password'], maxLength: 12 },
  g: { field: 'f2', fresh: ['f2', 'f3'] },
  h: { field: 'password', fresh: ['password', 'confirmPassword'] },
  i: { field: 'password', fresh: ['password'] },
  j: { field: 'password', fresh: ['password'] },
}

/** Clicks into `field` (away from the icon) to open its menu, and waits for it to arm. */
async function openMenu(page: Page, field: Locator): Promise<boolean> {
  await expect.poll(() => iconHosts(page)).toBeGreaterThan(0)
  const rtl = await field.evaluate((el) => getComputedStyle(el).direction === 'rtl')
  const b = await box(field)
  await page.mouse.click(rtl ? b.x + b.width - 40 : b.x + 40, b.y + b.height / 2)
  await page.waitForTimeout(900) // generate round trip + the 500 ms arming
  return rtl
}

/** Clicks row `index` of the open menu under `field`; RTL menus align to the field's end. */
async function clickRow(page: Page, field: Locator, index: number, rtl: boolean): Promise<void> {
  const b = await box(field)
  const x = rtl ? b.x + b.width - MENU_WIDTH / 2 : b.x + MENU_WIDTH / 2
  const y = b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + index * ROW_HEIGHT + ROW_HEIGHT / 2
  await page.mouse.click(x, y)
}

/** Opens the menu by clicking into the field (away from the icon), then picks row 0
 *  ("Use a strong password") once the menu is armed; RTL menus align to the field's end. */
async function pickStrongPassword(page: Page, field: Locator): Promise<void> {
  const rtl = await openMenu(page, field)
  await clickRow(page, field, 0, rtl)
}

for (const [id, form] of Object.entries(FORMS)) {
  test(`form ${id.toUpperCase()}: the strong password fills every new field, and the page sees it`, async ({ env }) => {
    env.fake.generates.length = 0
    const page = await env.context.newPage()
    await page.goto(`${env.servers.main}/autofill-test-pages/signup.html?only=${id}`)
    const input = (name: string) => page.locator(`#${id} [name="${name}"]`)
    await pickStrongPassword(page, input(form.field))
    await expect.poll(() => input(form.fresh[0]).inputValue()).not.toBe('')
    const generated = await input(form.fresh[0]).inputValue()
    for (const name of form.fresh) await expect(input(name)).toHaveValue(generated)
    // The page's own report: every new field filled, all the same, seen through events.
    await expect(page.locator(`#${id} .out`)).toHaveText(/^OK: new password fields match/)
    await expect(page.locator(`#${id} .out`)).not.toContainText('never saw')
    await expect(page).toHaveTitle(`${id} OK`)
    if (form.maxLength) {
      expect(generated.length).toBeLessThanOrEqual(form.maxLength)
      expect(env.fake.generates.at(-1)).toEqual({ maxLength: form.maxLength })
    } else {
      expect(env.fake.generates.at(-1)).toEqual({})
    }
    // A change form's current password is never touched.
    if (id === 'e') await expect(input('current')).toHaveValue('')
  })
}

for (const id of ['i', 'j']) {
  test(`form ${id.toUpperCase()}: a new field offers the strong password only, never logins`, async ({ env }) => {
    const page = await env.context.newPage()
    await page.goto(`${env.servers.main}/autofill-test-pages/signup.html?only=${id}`)
    await openMenu(page, page.locator(`#${id} [name="password"]`))
    await expect.poll(async () => (await readMenu(page)).options.length).toBe(1)
    expect((await readMenu(page)).options[0].name).toContain('Use a strong password')
  })
}

test('form K: a login offers its logins, then the strong password last (R7)', async ({ env }) => {
  env.fake.generates.length = 0
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/autofill-test-pages/signup.html?only=k`)
  const email = page.locator('#k [name="email"]')
  const password = page.locator('#k [name="password"]')
  const rtl = await openMenu(page, password)
  const names = async () => (await readMenu(page)).options.map((o) => o.name)
  await expect.poll(names).toEqual([expect.stringContaining(LOGINS[0].title), expect.stringContaining(LOGINS[1].title), expect.stringContaining('Use a strong password')])
  // The strong password goes into this one field; the email is left alone.
  await clickRow(page, password, 2, rtl)
  await expect.poll(() => password.inputValue()).not.toBe('')
  await expect(email).toHaveValue('')
  expect(env.fake.generates.at(-1)).toEqual({})
  // A login still fills as one.
  await openMenu(page, password)
  await clickRow(page, password, 0, rtl)
  await expect(email).toHaveValue(LOGINS[0].username)
  await expect(password).toHaveValue(LOGINS[0].password)
})

test('a login password with no saved login offers the strong password first (R7)', async ({ env }) => {
  // The fake desktop matches by exact host: on localhost it has one login, taken out here.
  const index = LOGINS.findIndex((l) => new URL(l.website).hostname === 'localhost')
  const [removed] = LOGINS.splice(index, 1)
  try {
    const page = await env.context.newPage()
    await page.goto(`${env.servers.other}/autofill-test-pages/signup.html?only=k`)
    const password = page.locator('#k [name="password"]')
    const rtl = await openMenu(page, password)
    await expect.poll(async () => (await readMenu(page)).options.map((o) => o.name)).toEqual([expect.stringContaining('Use a strong password')])
    await clickRow(page, password, 0, rtl)
    await expect.poll(() => password.inputValue()).not.toBe('')
    await expect(page.locator('#k [name="email"]')).toHaveValue('')
  } finally {
    LOGINS.splice(index, 0, removed)
  }
})

test('a form re-rendered between the menu opening and the pick is still filled', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/rerender.html`)
  await pickStrongPassword(page, page.locator('#password'))
  await expect(page.locator('#signup')).toHaveAttribute('data-render', '2')
  await expect.poll(() => page.locator('#password').inputValue()).not.toBe('')
  await expect(page.locator('#confirm')).toHaveValue(await page.locator('#password').inputValue())
})

test('ArrowDown in an empty new-password field opens the menu', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/autofill-test-pages/signup.html?only=a`)
  await expect.poll(() => iconHosts(page)).toBeGreaterThan(0)
  const field = page.locator('#a [name="password"]')
  await field.focus()
  await page.keyboard.press('ArrowDown')
  await page.waitForTimeout(900)
  const b = await box(field)
  await page.mouse.click(b.x + MENU_WIDTH / 2, b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + ROW_HEIGHT / 2)
  await expect.poll(() => field.inputValue()).not.toBe('')
  await expect(page.locator('#a [name="password_confirmation"]')).toHaveValue(await field.inputValue())
})

test('a fill that cannot happen keeps the menu open (R5)', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/signup.html`)
  const field = page.locator('#password')
  await expect.poll(() => iconHosts(page)).toBeGreaterThan(0)
  let b = await box(field)
  await page.mouse.click(b.x + 40, b.y + b.height / 2)
  await page.waitForTimeout(900)
  // The page turns the field invisible before the pick: nothing may be filled.
  await field.evaluate((el) => ((el as HTMLElement).style.opacity = '0'))
  b = await box(field)
  const x = b.x + MENU_WIDTH / 2
  const y = b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + ROW_HEIGHT / 2
  await page.mouse.click(x, y)
  await page.waitForTimeout(500)
  await expect(field).toHaveValue('')
  await expect(page.locator('#confirm')).toHaveValue('')
  // Still up (now saying it couldn't fill): our host is what's under the menu.
  const hit = await page.evaluate(({ x, y }) => (document.elementFromPoint(x, y)?.getAttribute('style') ?? '').includes('2147483647'), { x, y })
  expect(hit).toBe(true)
})

test('the menu works over a modal <dialog> (top layer)', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/dialog.html`)
  // The host joins the modal dialog (outside it, everything is inert).
  await expect.poll(() => page.evaluate(() => document.querySelectorAll('#dlg > div[style*="2147483647"]').length)).toBe(1)
  const field = page.locator('#username')
  const b = await box(field)
  await page.mouse.click(b.x + 40, b.y + b.height / 2)
  await page.waitForTimeout(900)
  await page.mouse.click(b.x + MENU_WIDTH / 2, b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + ROW_HEIGHT / 2)
  await expect(page.locator('#password')).not.toHaveValue('')
})

test('a filter on <html> does not block picks', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await page.addStyleTag({ content: 'html { filter: grayscale(0.2); }' })
  await expect.poll(() => iconHosts(page)).toBeGreaterThan(0)
  const field = page.locator('#username')
  const b = await box(field)
  await page.mouse.click(b.x + 40, b.y + b.height / 2)
  await page.waitForTimeout(900)
  await page.mouse.click(b.x + MENU_WIDTH / 2, b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + ROW_HEIGHT / 2)
  await expect(page.locator('#password')).not.toHaveValue('')
})
