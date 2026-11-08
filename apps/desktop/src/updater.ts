// App updates (plan §10): tauri-plugin-updater reads https://qasa-app.com/desktop/latest.json,
// and only installs a bundle signed with Qasa's updater key (the public key is in
// tauri.conf.json). One quiet check after launch; Settings → Check for updates on demand.
import { getVersion } from '@tauri-apps/api/app'
import { relaunch } from '@tauri-apps/plugin-process'
import { check, type Update } from '@tauri-apps/plugin-updater'
import { reactive } from 'vue'

export type UpdateStage = 'idle' | 'checking' | 'upToDate' | 'available' | 'downloading' | 'failed'

export const updater = reactive({
  stage: 'idle' as UpdateStage,
  /** This build's version, for Settings. */
  current: '',
  /** The version on offer. */
  next: '',
  /** 0…1 while downloading, when the server sends a length. */
  progress: null as number | null,
  /** The prompt was dismissed with Later for this launch. */
  dismissed: false,
})

const inTauri = '__TAURI_INTERNALS__' in window
let pending: Update | null = null

export async function loadVersion(): Promise<void> {
  if (inTauri) updater.current = await getVersion()
}

/** `quiet`: the launch check, which never shows an error. */
export async function checkForUpdate(quiet = false): Promise<void> {
  if (!inTauri || updater.stage === 'checking' || updater.stage === 'downloading') return
  updater.stage = 'checking'
  try {
    pending = await check()
    if (pending) {
      updater.next = pending.version
      updater.stage = 'available'
      if (!quiet) updater.dismissed = false
    } else {
      updater.stage = quiet ? 'idle' : 'upToDate'
    }
  } catch (e) {
    console.warn('Update check failed', e)
    updater.stage = quiet ? 'idle' : 'failed'
  }
}

/** Downloads, verifies the signature, installs and restarts. The vault locks with the app. */
export async function installUpdate(): Promise<void> {
  if (!pending) return
  updater.stage = 'downloading'
  updater.progress = null
  let total = 0
  let done = 0
  try {
    await pending.downloadAndInstall((event) => {
      if (event.event === 'Started') total = event.data.contentLength ?? 0
      if (event.event === 'Progress') {
        done += event.data.chunkLength
        if (total > 0) updater.progress = Math.min(1, done / total)
      }
    })
    await relaunch()
  } catch (e) {
    console.error('Update failed', e)
    updater.stage = 'failed'
  }
}

/** The quiet check, a little after launch so it never slows the first screen. Dev builds
 *  skip it: they have no release to compare with. */
export function scheduleUpdateCheck(): void {
  loadVersion()
  if (import.meta.env.DEV) return
  setTimeout(() => checkForUpdate(true), 10_000)
}
