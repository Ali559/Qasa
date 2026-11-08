// The menu must not fill when the click could have been tricked: too fast after the menu
// appeared, a covered or transparent field, a restyled or covered menu, or a click the
// page made up.
import { LOGINS } from '../fake-desktop'
import { clickIcon, clickMenuRow, expect, fillVia, iconHosts, test } from './harness'

async function expectNotFilled(page: import('@playwright/test').Page): Promise<void> {
  await page.waitForTimeout(800)
  await expect(page.locator('#username')).toHaveValue('')
  await expect(page.locator('#password')).toHaveValue('')
}

test('a click in the first 500 ms after the menu appears is ignored', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await expect.poll(() => iconHosts(page)).toBe(1)
  const field = page.locator('#username')
  await clickIcon(page, field)
  await page.waitForTimeout(150)
  await clickMenuRow(page, field, 0)
  await expectNotFilled(page)
  // The same row, now that the menu is armed, works.
  await clickMenuRow(page, field, 0)
  await expect(page.locator('#password')).toHaveValue(LOGINS[0].password)
  expect(env.fake.urlOps.filter((o) => o.startsWith('fill'))).toHaveLength(1)
})

test('a transparent overlay over the password field blocks the fill', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/clickjack-overlay.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expectNotFilled(page)
  expect(env.fake.urlOps.filter((o) => o.startsWith('fill'))).toEqual([])
})

test('a form made invisible after the menu opens is not filled', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/clickjack-opacity.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expectNotFilled(page)
  expect(env.fake.urlOps.filter((o) => o.startsWith('fill'))).toEqual([])
})

test('a restyled (transparent) Qasa menu host refuses the click', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/clickjack-restyle.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expectNotFilled(page)
})

test('a decoy laid over the menu (pointer-events: none) refuses the click', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/clickjack-cover.html`)
  await fillVia(page, page.locator('#username'), 0)
  await expect(page.locator('#decoy').first()).toBeVisible()
  await expectNotFilled(page)
})

test('clicks the page makes up never fill', async ({ env }) => {
  const page = await env.context.newPage()
  await page.goto(`${env.servers.main}/login.html`)
  await expect.poll(() => iconHosts(page)).toBe(1)
  const field = page.locator('#username')
  const b = (await field.boundingBox())!
  // Synthetic clicks on the host where the icon and the first row are.
  await page.evaluate(({ x, y }) => {
    const host = [...document.documentElement.children].find((e) => e.tagName === 'DIV')!
    for (const [cx, cy] of [[x + 300, y + 22], [x + 140, y + 110]]) {
      host.dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true, clientX: cx, clientY: cy }))
    }
  }, b)
  await expectNotFilled(page)
  // A real icon click then a synthetic row click: still nothing.
  await clickIcon(page, field)
  await page.waitForTimeout(800)
  await page.evaluate(() => {
    const host = [...document.documentElement.children].find((e) => e.tagName === 'DIV')!
    ;(host as HTMLElement).click()
  })
  await expectNotFilled(page)
})
