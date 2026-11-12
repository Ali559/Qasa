// @vitest-environment happy-dom
// Field detection on the e2e fixture pages (their markup, without their scripts) and on a
// few edge cases.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'

import { detect, FRAME_MARK, iconFields, isOtpField } from '@ext/content/detect'
import { setFieldValue } from '@ext/content/fill'
import { groupContaining, isSubmitControl, SaveDeduper, saveMessageFor } from '@ext/content/save'

function loadFixture(name: string): void {
  const html = readFileSync(join(process.cwd(), 'test/fixtures', name), 'utf8')
  document.body.innerHTML = /<body>([\s\S]*)<\/body>/.exec(html)![1].replace(/<script[\s\S]*?<\/script>/g, '')
}

const byId = (id: string) => document.getElementById(id) as HTMLInputElement

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('form kinds', () => {
  it('finds a login form', () => {
    loadFixture('login.html')
    const { groups, otpFields } = detect(document)
    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({ kind: 'login', username: byId('username'), password: byId('password') })
    expect(otpFields).toHaveLength(0)
  })

  it('finds a signup form, its confirm field, and an icon on the new password only', () => {
    loadFixture('signup.html')
    const d = detect(document)
    expect(d.groups[0]).toMatchObject({
      kind: 'signup',
      username: byId('username'),
      newPassword: byId('password'),
      confirmPassword: byId('confirm'),
    })
    expect(iconFields(d)).toEqual([byId('password')])
  })

  it('finds a signup form with one new password and no confirm field', () => {
    document.body.innerHTML = '<form><input name=email><input type=password autocomplete=new-password id=p></form>'
    const [g] = detect(document).groups
    expect(g).toMatchObject({ kind: 'signup', newPassword: byId('p') })
    expect(g.confirmPassword).toBeUndefined()
  })

  it('finds a change-password form with its hidden username', () => {
    loadFixture('change-password.html')
    const [g] = detect(document).groups
    expect(g.kind).toBe('change')
    expect(g.password).toBe(byId('current'))
    expect(g.newPassword).toBe(byId('new'))
    expect(g.hiddenUsername?.value).toBe('alice@127')
  })

  it('treats three unlabeled passwords as current / new / confirm', () => {
    document.body.innerHTML = '<form><input type=password id=a><input type=password id=b><input type=password id=c></form>'
    const d = detect(document)
    const [g] = d.groups
    expect(g).toMatchObject({ kind: 'change', password: byId('a'), newPassword: byId('b'), confirmPassword: byId('c') })
    // The current password fills from the vault; the new one suggests a strong password.
    expect(iconFields(d)).toEqual([byId('a'), byId('b')])
  })

  it('treats "old" + new password as a change form', () => {
    document.body.innerHTML = '<form><input name=user><input type=password name=old_password id=a><input type=password name=password id=b></form>'
    expect(detect(document).groups[0]).toMatchObject({ kind: 'change', password: byId('a'), newPassword: byId('b') })
  })

  it('finds step 1 and step 2 of a multi-step sign-in', () => {
    loadFixture('multistep-1.html')
    expect(detect(document).groups[0]).toMatchObject({ kind: 'usernameStep', username: byId('username') })
    loadFixture('multistep-2.html')
    expect(detect(document).groups[0]).toMatchObject({ kind: 'login', password: byId('password') })
    expect(detect(document).groups[0].username).toBeUndefined()
  })

  it('does not mistake a newsletter or search box for a sign-in step', () => {
    document.body.innerHTML = '<form><input type=search name=q><button>Search</button></form><form><input type=email name=newsletter><button>Subscribe</button></form>'
    expect(detect(document).groups).toHaveLength(0)
  })

  it('picks the username-looking field before the password, not any text field', () => {
    document.body.innerHTML = '<form><input name=user_login id=u><input name=promo id=p><input type=password id=pw></form>'
    expect(detect(document).groups[0].username).toBe(byId('u'))
  })

  it('groups formless fields by document', () => {
    document.body.innerHTML = '<div><input name=username id=u><input type=password id=pw><button>Go</button></div>'
    expect(detect(document).groups[0]).toMatchObject({ kind: 'login', username: byId('u'), password: byId('pw') })
  })

  it('keeps a password field a password after a reveal toggle', () => {
    loadFixture('login.html')
    detect(document)
    byId('password').type = 'text'
    expect(detect(document).groups[0].password).toBe(byId('password'))
  })
})

describe('one-time codes', () => {
  it('finds autocomplete=one-time-code', () => {
    loadFixture('otp.html')
    expect(detect(document).otpFields).toEqual([byId('code')])
  })

  it('finds a code field by name and length next to a login', () => {
    loadFixture('login-otp.html')
    const d = detect(document)
    expect(d.otpFields).toEqual([byId('mfa')])
    expect(d.groups[0]).toMatchObject({ kind: 'login', username: byId('username'), password: byId('password') })
  })

  it('needs a 4–8 character limit for name-only matches, and a real code-like name', () => {
    document.body.innerHTML =
      '<input id=a name=otp maxlength=6><input id=b name=otp><input id=c name=zip_code maxlength=5><input id=d name=promo maxlength=6><input id=e name=code maxlength=8>'
    expect(['a', 'b', 'c', 'd', 'e'].map((id) => isOtpField(byId(id)))).toEqual([true, false, false, false, true])
  })
})

describe('shadow roots and frames', () => {
  it('walks open shadow roots', () => {
    const host = document.createElement('div')
    document.body.append(host)
    const root = host.attachShadow({ mode: 'open' })
    root.innerHTML = '<form><input type=email autocomplete=username id=u><input type=password id=p></form>'
    const [g] = detect(document).groups
    expect(g.kind).toBe('login')
    expect(g.username).toBe(root.getElementById('u'))
    expect(g.password).toBe(root.getElementById('p'))
  })

  it('cannot see closed shadow roots', () => {
    const host = document.createElement('div')
    document.body.append(host)
    host.attachShadow({ mode: 'closed' }).innerHTML = '<input type=password>'
    expect(detect(document).groups).toHaveLength(0)
  })

  it('walks a same-origin frame without its own script, and skips one that has it', async () => {
    const frame = document.createElement('iframe')
    document.body.append(frame)
    const doc = frame.contentDocument!
    doc.body.innerHTML = '<form><input name=username><input type=password></form>'
    expect(detect(document).groups).toHaveLength(1)
    ;(frame.contentWindow as unknown as Record<string, boolean>)[FRAME_MARK] = true
    expect(detect(document).groups).toHaveLength(0)
  })
})

describe('save capture', () => {
  it('sends username and password from a login', () => {
    loadFixture('login.html')
    byId('username').value = 'me@example.com'
    byId('password').value = 'hunter2'
    expect(saveMessageFor(detect(document).groups[0])).toEqual({ kind: 'save', username: 'me@example.com', password: 'hunter2' })
  })

  it('sends the new password of a change form with the hidden username', () => {
    loadFixture('change-password.html')
    byId('current').value = 'old'
    byId('new').value = 'brand-new'
    expect(saveMessageFor(detect(document).groups[0])).toEqual({ kind: 'save', username: 'alice@127', password: 'brand-new' })
  })

  it('remembers the username of a first step', () => {
    loadFixture('multistep-1.html')
    byId('username').value = 'me@example.com'
    expect(saveMessageFor(detect(document).groups[0])).toEqual({ kind: 'rememberUsername', username: 'me@example.com' })
  })

  it('sends nothing for an empty password or for our own unchanged fill', () => {
    loadFixture('login.html')
    const [g] = detect(document).groups
    expect(saveMessageFor(g)).toBeNull()
    setFieldValue(byId('password'), 'filled-by-qasa')
    expect(saveMessageFor(g)).toBeNull()
    byId('password').value = 'edited-by-user'
    expect(saveMessageFor(g)).toMatchObject({ password: 'edited-by-user' })
  })

  it('recognises submit controls and finds their group', () => {
    loadFixture('login.html')
    const button = document.querySelector('button')!
    expect(isSubmitControl(button)).toBe(true)
    expect(groupContaining(detect(document).groups, button)?.kind).toBe('login')
    document.body.innerHTML = '<button type=button>Show password</button>'
    expect(isSubmitControl(document.querySelector('button')!)).toBe(false)
  })

  it('sends a submit seen twice only once', () => {
    const d = new SaveDeduper()
    const m = { kind: 'save' as const, username: 'a', password: 'b' }
    expect(d.shouldSend(m, 1000)).toBe(true)
    expect(d.shouldSend(m, 1500)).toBe(false)
    expect(d.shouldSend(m, 5000)).toBe(true)
  })
})

describe('fill', () => {
  it('uses the native setter and fires bubbling input and change events', () => {
    loadFixture('login.html')
    const field = byId('username')
    let tracked = ''
    // A React-style tracker on the instance must not swallow the change.
    Object.defineProperty(field, 'value', {
      configurable: true,
      get: () => Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.get!.call(field),
      set: (v: string) => {
        tracked = v
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(field, v)
      },
    })
    const events: string[] = []
    document.addEventListener('input', () => events.push('input'))
    document.addEventListener('change', () => events.push('change'))
    setFieldValue(field, 'me@example.com')
    expect(field.value).toBe('me@example.com')
    expect(tracked).toBe('')
    expect(events).toEqual(['input', 'change'])
  })
})
