// The in-page menu from the keyboard: ArrowDown in the field opens it with the first row
// highlighted, the arrows move (and wrap), Enter picks without submitting the form, and
// Escape closes it with focus kept in the field. Only real key presses count.
import { LOGINS } from '../fake-desktop'
import { expect, iconHosts, readMenu, test } from './harness'

async function focusLogin(page: import('@playwright/test').Page) {
  await expect.poll(() => iconHosts(page)).toBe(1)
  const user = page.locator('#username')
  // A script's focus opens nothing; the key presses below are real.
  await user.focus()
  return user
}

const selected = async (page: import('@playwright/test').Page) => (await readMenu(page)).options.findIndex((o) => o.selected)

test('ArrowDown opens the menu, the arrows move and wrap, Enter fills the highlighted login', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = await focusLogin(page)
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => selected(page)).toBe(0)
  const menu = await readMenu(page)
  expect(menu.options.map((o) => o.name)).toEqual([expect.stringContaining(LOGINS[0].title), expect.stringContaining(LOGINS[1].title)])
  await page.keyboard.press('ArrowDown')
  expect(await selected(page)).toBe(1)
  await page.keyboard.press('ArrowDown')
  expect(await selected(page)).toBe(0)
  await page.keyboard.press('ArrowUp')
  expect(await selected(page)).toBe(1)
  await page.waitForTimeout(600) // armed
  await page.keyboard.press('Enter')
  await expect(user).toHaveValue(LOGINS[1].username)
  await expect(page.locator('#password')).toHaveValue(LOGINS[1].password)
  // The Enter picked; it did not submit the form.
  await page.waitForTimeout(300)
  await expect(page.locator('#result')).toHaveText('')
  await expect(user).toBeFocused()
  await expect.poll(async () => (await readMenu(page)).open).toBe(false)
})

test('Escape closes the menu and keeps focus in the field', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = await focusLogin(page)
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => selected(page)).toBe(0)
  await page.keyboard.press('Escape')
  await expect.poll(async () => (await readMenu(page)).open).toBe(false)
  await expect(user).toBeFocused()
  // Enter now belongs to the page again: it submits, and nothing is filled.
  await page.keyboard.press('Enter')
  await expect(page.locator('#result')).not.toHaveText('')
  await expect(page.locator('#password')).toHaveValue('')
})

test('Tab closes the menu', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await focusLogin(page)
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => selected(page)).toBe(0)
  await page.keyboard.press('Tab')
  await expect.poll(async () => (await readMenu(page)).open).toBe(false)
  await expect(page.locator('#password')).toBeFocused()
})

test('keys the page dispatches do nothing', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = await focusLogin(page)
  const press = (key: string) =>
    page.evaluate((k) => {
      document.getElementById('username')!.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true, composed: true }))
    }, key)
  // A made-up ArrowDown opens nothing.
  await press('ArrowDown')
  await page.waitForTimeout(800)
  expect((await readMenu(page)).open).toBe(false)
  // With a real menu open and armed: made-up keys don't move, pick or close it.
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => selected(page)).toBe(0)
  await page.waitForTimeout(600)
  await press('ArrowDown')
  await press('Enter')
  await press('Escape')
  await page.waitForTimeout(500)
  const menu = await readMenu(page)
  expect(menu.open).toBe(true)
  expect(menu.options.findIndex((o) => o.selected)).toBe(0)
  await expect(user).toHaveValue('')
  expect(env.fake.urlOps.filter((o) => o.startsWith('fill'))).toEqual([])
})

test('Enter in the first 500 ms picks nothing', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  const user = await focusLogin(page)
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => selected(page), { intervals: [20] }).toBe(0)
  await page.keyboard.press('Enter')
  await page.waitForTimeout(800)
  await expect(user).toHaveValue('')
  await expect(page.locator('#result')).toHaveText('')
  // Once armed, the same Enter fills.
  await page.keyboard.press('Enter')
  await expect(user).toHaveValue(LOGINS[0].username)
})
