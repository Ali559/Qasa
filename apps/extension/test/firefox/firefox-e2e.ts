// The extension in real Firefox (a temporary add-on), through the real nm-host, against the
// desktop's real bridge (`bridge_demo_server`), driven over geckodriver's WebDriver API.
// Playwright can't load extensions into Firefox, hence plain WebDriver.
//
//   FIREFOX_BIN=/path/Firefox.app/Contents/MacOS/firefox GECKODRIVER=/path/geckodriver bun run e2e:firefox
//
// Firefox finds native messaging hosts only in a per-user folder (on macOS
// ~/Library/Application Support/Mozilla/NativeMessagingHosts), not in the profile: run the
// desktop app once (`make desktop`) so it registers nm-host there first.
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'
import { createInterface } from 'node:readline'

import { ICON_INSET, ICON_SIZE, MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from '../../src/content/layout'
import { startFixtureServers } from '../e2e/server'

const REPO = join(import.meta.dirname, '../../../..')
const FIREFOX = process.env.FIREFOX_BIN
const GECKODRIVER = process.env.GECKODRIVER
if (!FIREFOX || !GECKODRIVER) throw new Error('Set FIREFOX_BIN and GECKODRIVER')
const HOST_MANIFEST = process.platform === 'darwin'
  ? join(homedir(), 'Library/Application Support/Mozilla/NativeMessagingHosts/com.qasa_app.desktop.json')
  : join(homedir(), '.mozilla/native-messaging-hosts/com.qasa_app.desktop.json')
if (!existsSync(HOST_MANIFEST)) throw new Error(`No native host registration at ${HOST_MANIFEST}: run the desktop app once`)
const UUID = '6f1c2a7e-0b4d-4c55-9a51-3f2e8d7c1b90'
// Short: macOS caps Unix socket paths near 104 bytes.
const SOCKET = `/tmp/qasa-ff-${process.pid}.sock`
const results: Array<[string, boolean, string]> = []
const check = (name: string, ok: boolean, detail = '') => { results.push([name, ok, detail]); console.log(ok ? 'PASS' : 'FAIL', name, detail) }
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

// The desktop's real bridge, in demo mode.
const lines: string[] = []
const desktop = spawn('cargo', ['test', '-q', '--lib', 'bridge_demo_server', '--', '--ignored', '--nocapture'], {
  cwd: `${REPO}/apps/desktop/src-tauri`, env: { ...process.env, QASA_BRIDGE_SOCKET: SOCKET, CARGO_INCREMENTAL: '0' }, stdio: ['ignore', 'pipe', 'ignore'],
})
createInterface({ input: desktop.stdout! }).on('line', (l) => { lines.push(l); console.log('  desktop:', l) })
while (!lines.some((l) => l.startsWith('LISTENING'))) await sleep(200)
const servers = await startFixtureServers()
const gd = spawn(GECKODRIVER, ['--port', '4455', '--allow-system-access'], { stdio: 'ignore', env: { ...process.env, QASA_BRIDGE_SOCKET: SOCKET } }) // Firefox, then nm-host, inherit it
await sleep(1500)

async function wd(method: string, path: string, body?: unknown): Promise<any> {
  const r = await fetch(`http://127.0.0.1:4455${path}`, { method, headers: { 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) })
  const j = await r.json()
  if (j.value?.error) throw new Error(`${path}: ${j.value.error}: ${j.value.message}`)
  return j.value
}

try {
  const session = await wd('POST', '/session', { capabilities: { alwaysMatch: { browserName: 'firefox', 'moz:firefoxOptions': {
    binary: FIREFOX, args: ['-headless', '-width', '1000', '-height', '800'],
    prefs: { 'extensions.webextensions.uuids': JSON.stringify({ 'extension@qasa-app.com': UUID }) },
  } } } })
  const S = `/session/${session.sessionId}`
  console.log('Firefox', session.capabilities.browserVersion)
  const addon = await wd('POST', `${S}/moz/addon/install`, { path: `${REPO}/apps/extension/dist/firefox`, temporary: true })
  check('add-on installs', addon === 'extension@qasa-app.com', String(addon))

  const exec = (script: string, args: unknown[] = []) => wd('POST', `${S}/execute/sync`, { script, args })
  const go = async (url: string) => { await wd('POST', `${S}/url`, { url }); await sleep(800) }
  const find = (css: string) => wd('POST', `${S}/element`, { using: 'css selector', value: css })
  const click = async (x: number, y: number) => wd('POST', `${S}/actions`, { actions: [{ type: 'pointer', id: 'mouse', parameters: { pointerType: 'mouse' }, actions: [
    { type: 'pointerMove', x: Math.round(x), y: Math.round(y), origin: 'viewport' }, { type: 'pointerDown', button: 0 }, { type: 'pointerUp', button: 0 }] }] })
  const waitFor = async (fn: () => Promise<boolean>, ms = 8000) => { const end = Date.now() + ms; while (Date.now() < end) { if (await fn()) return true; await sleep(250) } return false }
  const hosts = () => exec(`return [...document.documentElement.children].filter((e) => e.tagName === 'DIV' && (e.getAttribute('style') ?? '').includes('2147483647')).length`)
  /** Field rect in viewport coordinates; `frame` is an iframe selector on the top page. */
  async function rect(js: string, frame?: string) {
    if (!frame) return exec(`const r = (${js}).getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }`)
    const f = await exec(`const e = document.querySelector('${frame}'); const r = e.getBoundingClientRect(); return { x: r.x + e.clientLeft, y: r.y + e.clientTop }`)
    await wd('POST', `${S}/frame`, { id: await find(frame) })
    const r = await exec(`const r = (${js}).getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }`)
    await wd('POST', `${S}/frame`, { id: null })
    return { x: f.x + r.x, y: f.y + r.y, width: r.width, height: r.height }
  }
  const value = async (js: string, frame?: string) => {
    if (frame) await wd('POST', `${S}/frame`, { id: await find(frame) })
    const v = await exec(`return (${js}).value`)
    if (frame) await wd('POST', `${S}/frame`, { id: null })
    return v
  }
  /** geckodriver dispatches pointer actions in the current browsing context only, so a
   *  frame's fields are clicked from inside the frame, in its own coordinates. */
  async function fillVia(js: string, frame?: string, opts = { armDelay: 800 }) {
    if (frame) await wd('POST', `${S}/frame`, { id: await find(frame) })
    const b = await rect(js)
    await click(b.x + b.width - ICON_INSET - ICON_SIZE / 2, b.y + b.height / 2)
    await sleep(opts.armDelay)
    await click(b.x + MENU_WIDTH / 2, b.y + b.height + MENU_GAP + 1 + MENU_PADDING + MENU_HEADER + ROW_HEIGHT / 2)
    await sleep(800)
    if (frame) await wd('POST', `${S}/frame`, { id: null })
  }

  // Pairing through the popup page. WebDriver may not navigate to extension pages, so the
  // privileged context opens it in a new tab, which then becomes the window we drive.
  const before: string[] = await wd('GET', `${S}/window/handles`)
  await wd('POST', `${S}/moz/context`, { context: 'chrome' })
  await exec(`const w = Services.wm.getMostRecentWindow('navigator:browser'); w.gBrowser.selectedTab = w.gBrowser.addTab(arguments[0], { triggeringPrincipal: Services.scriptSecurityManager.getSystemPrincipal() })`, [`moz-extension://${UUID}/popup.html`])
  await wd('POST', `${S}/moz/context`, { context: 'content' })
  await sleep(1000)
  const after: string[] = await wd('GET', `${S}/window/handles`)
  await wd('POST', `${S}/window`, { handle: after.find((h) => !before.includes(h)) })
  await waitFor(async () => (await exec(`return [...document.querySelectorAll('button')].some((b) => b.textContent.trim() === 'Connect')`)) === true)
  console.log('popup:', await exec(`return location.href + ' | ' + (document.querySelector('main')?.dataset.state ?? 'no main') + ' | ' + document.body.innerText.slice(0, 300).replace(/\\s+/g, ' ')`))
  await exec(`[...document.querySelectorAll('button')].find((b) => b.textContent.trim() === 'Connect').click()`)
  const paired = await waitFor(async () => (await exec(`return document.querySelector('main')?.dataset.state`)) === 'unlocked', 15000)
  const code = await exec(`return document.querySelector('[data-testid=pair-code]')?.textContent ?? ''`)
  check('pairs (popup reaches unlocked)', paired && lines.some((l) => l === 'PAIRED Firefox'), `client=${lines.find((l) => l.startsWith('PAIRED'))} code=${code}`)

  await go(`${servers.main}/login.html`)
  check('field icon appears', await waitFor(async () => (await hosts()) === 1))
  await fillVia(`document.querySelector('#username')`)
  check('fills 127.0.0.1 login', (await value(`document.querySelector('#username')`)) === 'alice' && (await value(`document.querySelector('#password')`)) === 'alice-pass-127')

  await go(`${servers.other}/login.html`)
  await waitFor(async () => (await hosts()) === 1)
  await fillVia(`document.querySelector('#password')`)
  check('fills localhost login (other site)', (await value(`document.querySelector('#password')`)) === 'bob-pass-localhost')

  await go(`${servers.main}/shadow.html`)
  await waitFor(async () => (await hosts()) >= 1)
  await fillVia(`document.querySelector('login-box').shadowRoot.querySelector('#username')`)
  check('fills inside an open shadow root', (await value(`document.querySelector('login-box').shadowRoot.querySelector('#password')`)) === 'alice-pass-127')

  await go(`${servers.main}/react.html`)
  await waitFor(async () => (await hosts()) === 1)
  await fillVia(`document.querySelector('#username')`)
  const state = await exec(`return document.querySelector('#state').textContent`)
  check('controlled inputs see the fill', state === JSON.stringify({ email: 'alice', password: 'alice-pass-127' }), state)

  await go(`${servers.main}/iframe-cross.html`)
  await sleep(1500)
  await fillVia(`document.querySelector('#username')`, '#cross')
  check('cross-origin frame gets its own site\'s login', (await value(`document.querySelector('#password')`, '#cross')) === 'bob-pass-localhost')
  await fillVia(`document.querySelector('#username')`)
  check('top page still gets its own', (await value(`document.querySelector('#password')`)) === 'alice-pass-127' && (await value(`document.querySelector('#password')`, '#cross')) === 'bob-pass-localhost')

  await go(`${servers.main}/login.html`)
  await waitFor(async () => (await hosts()) === 1)
  await fillVia(`document.querySelector('#username')`, undefined, { armDelay: 150 })
  check('a click in the first 500 ms is ignored', (await value(`document.querySelector('#password')`)) === '')

  for (const page of ['clickjack-overlay', 'clickjack-opacity', 'clickjack-restyle']) {
    await go(`${servers.main}/${page}.html`)
    await sleep(1500)
    try { await fillVia(`document.querySelector('#username')`) } catch {}
    check(`${page} refuses to fill`, (await value(`document.querySelector('#password')`)) === '' && (await value(`document.querySelector('#username')`)) === '')
  }

  await go(`${servers.main}/login.html`)
  await waitFor(async () => (await hosts()) === 1)
  await fillVia(`document.querySelector('#username')`)
  const pw = await find('#password')
  console.log('pw ref', JSON.stringify(pw))
  await wd('POST', `${S}/element/${pw['element-6066-11e4-a52e-4f735466cecf']}/clear`, {})
  await wd('POST', `${S}/element/${pw['element-6066-11e4-a52e-4f735466cecf']}/value`, { text: 'alice-firefox-pass' })
  const saved = await waitFor(async () => lines.some((l) => l.startsWith('SAVED')), 10000)
  check('submit offers an update, saved by the desktop', saved && lines.includes('SAVED update 127.0.0.1 alice'), lines.find((l) => l.startsWith('SAVED')) ?? '')

  await wd('DELETE', S)
} catch (e) {
  check('run', false, String(e))
} finally {
  gd.kill(); desktop.kill(); await servers.close()
  const failed = results.filter((r) => !r[1])
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  process.exit(failed.length ? 1 : 0)
}
