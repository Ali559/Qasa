// @vitest-environment happy-dom
// R1 (docs/autofill-hardening.md) on forms A–H of docs/autofill-test-pages/signup.html, and
// the rest of the hardening that can run without a browser: shadow-root grouping, hidden
// fields, multi-step sign-up, split one-time codes, R2 constraints and R6 fill events.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'

import { detect, iconFields, type FieldGroup } from '@ext/content/detect'
import { fillGenerated, fillOtpRun, setFieldValue } from '@ext/content/fill'
import { generateConstraints } from '@ext/content/constraints'

const PAGE = readFileSync(join(process.cwd(), '../../docs/autofill-test-pages/signup.html'), 'utf8')

function loadSignupPage(): void {
  document.body.innerHTML = /<body>([\s\S]*)<\/body>/.exec(PAGE)![1].replace(/<script[\s\S]*?<\/script>/g, '')
}

function groupOfForm(id: string): FieldGroup {
  const form = document.getElementById(id)!
  const g = detect(document).groups.find((x) => x.container === form)
  if (!g) throw new Error(`no group for form ${id}`)
  return g
}

const field = (form: string, name: string) => document.querySelector(`#${form} [name="${name}"]`) as HTMLInputElement

beforeEach(loadSignupPage)

describe('R1 on the sign-up test page', () => {
  it('A: new-password on both fields', () => {
    expect(groupOfForm('a')).toMatchObject({ kind: 'signup', username: field('a', 'email'), newPassword: field('a', 'password'), confirmPassword: field('a', 'password_confirmation') })
  })

  it('B: a confirm without a hint after a new-password field is its confirm', () => {
    expect(groupOfForm('b')).toMatchObject({ kind: 'signup', newPassword: field('b', 'password'), confirmPassword: field('b', 'password_repeat') })
  })

  it('C: two plain password fields are new + confirm', () => {
    expect(groupOfForm('c')).toMatchObject({ kind: 'signup', username: field('c', 'user'), newPassword: field('c', 'pw1'), confirmPassword: field('c', 'pw2') })
  })

  it('D: one plain password next to name fields is new', () => {
    const d = detect(document)
    const g = groupOfForm('d')
    expect(g).toMatchObject({ kind: 'signup', username: field('d', 'email'), newPassword: field('d', 'password') })
    expect(g.confirmPassword).toBeUndefined()
    expect(iconFields(d)).toContain(field('d', 'password'))
  })

  it('E: change form fills the unhinted confirm too', () => {
    expect(groupOfForm('e')).toMatchObject({ kind: 'change', password: field('e', 'current'), newPassword: field('e', 'new'), confirmPassword: field('e', 'confirm') })
  })

  it('F: maxlength=12 form, and its constraints', () => {
    const g = groupOfForm('f')
    expect(g).toMatchObject({ kind: 'signup', newPassword: field('f', 'password'), confirmPassword: field('f', 'confirm_password') })
    expect(generateConstraints(g)).toEqual({ maxLength: 12 })
  })

  it('G: Kurdish labels only', () => {
    expect(groupOfForm('g')).toMatchObject({ kind: 'signup', username: field('g', 'f1'), newPassword: field('g', 'f2'), confirmPassword: field('g', 'f3') })
  })

  it('H: controlled inputs', () => {
    expect(groupOfForm('h')).toMatchObject({ kind: 'signup', newPassword: field('h', 'password'), confirmPassword: field('h', 'confirmPassword') })
  })
})

describe('R1 edge cases', () => {
  it('one plain password with a "Sign in" button stays a login', () => {
    document.body.innerHTML = '<form id=x><input name=email type=email><input type=password id=p><button>Sign in</button></form>'
    expect(groupOfForm('x')).toMatchObject({ kind: 'login', password: document.getElementById('p') })
  })

  it('one plain password with a "Create account" button is new', () => {
    document.body.innerHTML = '<form id=x><input name=email type=email><input type=password id=p><button>Create account</button></form>'
    expect(groupOfForm('x')).toMatchObject({ kind: 'signup', newPassword: document.getElementById('p') })
  })

  it('a phone number that is the username does not make a login a sign-up', () => {
    document.body.innerHTML = '<form id=x><input name=phone type=tel autocomplete=tel><input type=password id=p><button>Go</button></form>'
    expect(groupOfForm('x').kind).toBe('login')
  })

  it('reads <label for> text and Arabic words', () => {
    document.body.innerHTML =
      '<form id=x><label for=a>كلمة المرور الحالية</label><input type=password id=a><label for=b>كلمة مرور جديدة</label><input type=password id=b>' +
      '<label for=c>تأكيد كلمة المرور</label><input type=password id=c></form>'
    expect(groupOfForm('x')).toMatchObject({ kind: 'change', password: document.getElementById('a'), newPassword: document.getElementById('b'), confirmPassword: document.getElementById('c') })
  })

  it('skips hidden password fields (honeypots) when classifying', () => {
    document.body.innerHTML = '<form id=x><input name=username><input type=password id=p><input type=password id=h style="display:none"></form>'
    expect(groupOfForm('x')).toMatchObject({ kind: 'login', password: document.getElementById('p') })
  })

  it('a formless page header search box is never the username', () => {
    document.body.innerHTML = '<header><input type=text name=q></header><div><input type=password id=p><button>Go</button></div>'
    const [g] = detect(document).groups
    expect(g.kind).toBe('login')
    expect(g.username).toBeUndefined()
  })

  it('groups fields in per-field shadow roots by their form', () => {
    document.body.innerHTML = '<form id=x><x-field id=h1></x-field><x-field id=h2></x-field><x-field id=h3></x-field><button>Sign up</button></form>'
    const roots = ['h1', 'h2', 'h3'].map((id) => document.getElementById(id)!.attachShadow({ mode: 'open' }))
    roots[0].innerHTML = '<input type=email name=email>'
    roots[1].innerHTML = '<input type=password name=password autocomplete=new-password>'
    roots[2].innerHTML = '<input type=password name=confirm>'
    const groups = detect(document).groups
    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({ kind: 'signup', newPassword: roots[1].querySelector('input'), confirmPassword: roots[2].querySelector('input') })
    expect(groups[0].container).toBe(document.getElementById('x'))
  })

  it('finds step 1 of a multi-step sign-up', () => {
    document.body.innerHTML = '<form><input type=email name=email id=e><button>Sign up</button></form>'
    expect(detect(document).groups[0]).toMatchObject({ kind: 'usernameStep', username: document.getElementById('e') })
    document.body.innerHTML = '<form><input type=email name=email id=e><button>إنشاء حساب</button></form>'
    expect(detect(document).groups[0]?.kind).toBe('usernameStep')
  })
})

describe('split one-time codes', () => {
  it('finds a run of one-character boxes and fills one digit each', () => {
    document.body.innerHTML = '<form><input name=d1 maxlength=1><input name=d2 maxlength=1><input name=d3 maxlength=1><input name=d4 maxlength=1><input name=d5 maxlength=1><input name=d6 maxlength=1></form>'
    const d = detect(document)
    const boxes = [...document.querySelectorAll('input')]
    expect(d.otpRuns).toEqual([boxes])
    expect(d.otpFields).toEqual([boxes[0]])
    fillOtpRun(boxes, '123456')
    expect(boxes.map((b) => b.value).join('')).toBe('123456')
  })

  it('needs at least four boxes', () => {
    document.body.innerHTML = '<form><input maxlength=1><input maxlength=1><input maxlength=1></form>'
    expect(detect(document).otpRuns).toEqual([])
  })
})

describe('R2 constraints sent with generate', () => {
  it('sends nothing for an unconstrained field', () => {
    expect(generateConstraints(groupOfForm('a'))).toEqual({})
  })

  it('sends the smallest maxlength, the minlength and passwordrules', () => {
    document.body.innerHTML =
      '<form id=x><input type=password id=p autocomplete=new-password maxlength=20 minlength=10 passwordrules="required: upper; max-consecutive: 2">' +
      '<input type=password id=c autocomplete=new-password maxlength=18></form>'
    expect(generateConstraints(groupOfForm('x'))).toEqual({ maxLength: 18, minLength: 10, rules: 'required: upper; max-consecutive: 2' })
  })

  it('asks for digits only on a short numeric field', () => {
    document.body.innerHTML = '<form id=x><input type=password id=p autocomplete=new-password maxlength=6 inputmode=numeric></form>'
    expect(generateConstraints(groupOfForm('x'))).toEqual({ maxLength: 6, rules: 'required: digit' })
  })
})

describe('R6 fill events', () => {
  it('fires keydown, beforeinput, input (insertReplacementText), keyup, change in order', () => {
    document.body.innerHTML = '<form><input id=f type=password></form>'
    const f = document.getElementById('f') as HTMLInputElement
    const events: string[] = []
    for (const type of ['keydown', 'beforeinput', 'input', 'keyup', 'change']) {
      f.addEventListener(type, (e) => events.push(type === 'input' ? `input:${(e as InputEvent).inputType}:${f.value}` : type))
    }
    setFieldValue(f, 'secret')
    expect(events).toEqual(['keydown', 'beforeinput', 'input:insertReplacementText:secret', 'keyup', 'change'])
  })

  it('a generated password into new and confirm gives both the same value', () => {
    const g = groupOfForm('h')
    fillGenerated(g.newPassword!, 'Same-Value-123!')
    fillGenerated(g.confirmPassword!, 'Same-Value-123!')
    expect(g.newPassword!.value).toBe(g.confirmPassword!.value)
  })
})
