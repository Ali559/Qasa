// @vitest-environment happy-dom
// "Use a strong password" on signup forms (design/README.md rule 6c): the generated password
// fills the new and confirm fields, and submitting still offers the login for saving.
import { beforeEach, describe, expect, it } from 'vitest'

import { detect } from '@ext/content/detect'
import { fillGenerated, setFieldValue } from '@ext/content/fill'
import { saveMessageFor } from '@ext/content/save'

const byId = (id: string) => document.getElementById(id) as HTMLInputElement

describe('strong password at signup', () => {
  beforeEach(() => {
    document.body.innerHTML =
      '<form><input id=email name=email type=email><input id=p type=password autocomplete=new-password>' +
      '<input id=c type=password autocomplete=new-password><button type=submit>Sign up</button></form>'
  })

  it('offers a generated password for saving with the typed username', () => {
    const [group] = detect(document).groups
    setFieldValue(byId('email'), 'ali@example.com')
    fillGenerated(group.newPassword!, 'Gen-3rated-Pass!')
    fillGenerated(group.confirmPassword!, 'Gen-3rated-Pass!')
    expect(byId('c').value).toBe('Gen-3rated-Pass!')
    expect(saveMessageFor(group)).toEqual({ kind: 'save', username: 'ali@example.com', password: 'Gen-3rated-Pass!' })
  })

  it('still never offers a saved login it filled itself', () => {
    document.body.innerHTML = '<form><input id=u name=username><input id=pw type=password></form>'
    const [group] = detect(document).groups
    setFieldValue(byId('u'), 'ali')
    setFieldValue(byId('pw'), 'from-the-vault')
    expect(saveMessageFor(group)).toBeNull()
  })
})
