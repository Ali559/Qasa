// The two manifests (plan §8.3). Chrome runs the background as a service worker; Firefox
// MV3 needs `background.scripts` and a stable gecko id. Everything else is shared.

export const GECKO_ID = 'extension@qasa-app.com'

/**
 * Public half of the dev signing key (apps/extension/.dev-key.pem, not committed). It pins
 * the unpacked extension id to the one in EXTENSION_IDS.md. The Web Store assigns its own
 * key on upload, so the store build drops this field.
 */
export const DEV_PUBLIC_KEY =
  'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3UtPquTdgz3RMl5iN2sMT3b+BdQproMz1Vm3pgrKLJqqVqF3bJ8mf1g7ng3V8djJp0Si61zlXkyivsuk4olMqXVqSzl0q69QV40uQr+45HQjbSK+nDmBaesMpt0ZM+o7Tr+FX93Tz21v8iTt6WPCk28cQmlg1OXG5JMGA5wohGmCBlKnjZPaxYEWo0f7AK6c5rgN7BKxh62pev+p3AoStLUusyy+WIJN5+Q+6QLHzqgmKqx+YGe+P6dfHoe7qvPviIA1MsjhP3oDnL8B2+Iu8FH7SbSrMoOtlg3/d3TfYTmP4PcOHbnDsW0L7dhFxEwaYpl+QS+ubtp+OIRh9fxa9wIDAQAB'

const ICONS = { '16': 'icons/icon-16.png', '32': 'icons/icon-32.png', '48': 'icons/icon-48.png', '128': 'icons/icon-128.png' }

function base(version: string) {
  return {
    manifest_version: 3,
    name: '__MSG_extName__',
    description: '__MSG_extDescription__',
    version,
    default_locale: 'en',
    icons: ICONS,
    // Exactly these four (plan §8.3); each is justified in the store listing.
    permissions: ['nativeMessaging', 'storage', 'activeTab', 'scripting'],
    action: { default_popup: 'popup.html', default_title: '__MSG_extName__', default_icon: ICONS },
    content_scripts: [
      {
        matches: ['<all_urls>'],
        js: ['content.js'],
        run_at: 'document_idle',
        // Every http(s) frame runs its own copy and handles only its own document, so a
        // cross-origin frame can only ever be filled for its own origin. Frames without a
        // URL (about:blank, srcdoc) are handled by their parent's copy.
        all_frames: true,
      },
    ],
  }
}

export function chromeManifest(version: string) {
  return {
    ...base(version),
    key: DEV_PUBLIC_KEY,
    minimum_chrome_version: '116',
    background: { service_worker: 'background.js' },
  }
}

export function firefoxManifest(version: string) {
  return {
    ...base(version),
    background: { scripts: ['background.js'] },
    browser_specific_settings: {
      gecko: {
        id: GECKO_ID,
        strict_min_version: '128.0',
      },
    },
  }
}
