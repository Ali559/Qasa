// Pairing from a fresh profile, through the real nm-host, against the fake desktop.
import { EXTENSION_ID, expect, launch, openPopup, pairThroughPopup, test } from './harness'

test('pairs with matching codes on both sides', async ({ servers }) => {
  const env = await launch(servers, { pair: false })
  try {
    const popup = await openPopup(env)
    await expect(popup.getByText('Connect to Qasa')).toBeVisible()
    await popup.close()
    const shown = await pairThroughPopup(env)
    expect(shown).toMatch(/^\d{6}$/)
    expect(shown).toBe(env.fake.lastPairCode)
    expect(env.fake.clients.size).toBe(1)
    // nm-host introduced the caller with Chrome's origin argument.
    expect(env.fake.hellos[0]).toBe(`chrome-extension://${EXTENSION_ID}/`)
    // The pairing survives in storage.local, and nothing else is stored.
    const stored = await env.context.serviceWorkers()[0].evaluate(() => chrome.storage.local.get(null))
    expect(Object.keys(stored)).toEqual(['pairing'])
  } finally {
    await env.close()
  }
})

test('a denied pairing says so, and a revoked one asks to connect again', async ({ servers }) => {
  const env = await launch(servers, { pair: false })
  try {
    env.fake.approve = 'deny'
    const popup = await openPopup(env)
    await popup.getByRole('button', { name: 'Connect' }).click()
    await expect(popup.getByText("Qasa didn't allow this browser.")).toBeVisible()

    env.fake.approve = 'auto'
    await popup.close()
    await pairThroughPopup(env)
    // Revoked on the desktop: the next request gets notPaired and the key is dropped.
    env.fake.clients.clear()
    const again = await openPopup(env)
    await expect(again.getByRole('button', { name: 'Connect' })).toBeVisible()
    const stored = await env.context.serviceWorkers()[0].evaluate(() => chrome.storage.local.get(null))
    expect(stored).toEqual({})
  } finally {
    await env.close()
  }
})
