// Encrypted vault backups in the Rust core (src-tauri/src/backup.rs). The core shows the
// file dialogs, reads and writes the files and holds the keys; the webview only gets the
// backup's header (date, device) and counts.
import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'

import { prefs } from '@/prefs'

export type BackupFrequency = 'off' | 'daily' | 'weekly' | 'monthly'
export const FREQUENCIES: readonly BackupFrequency[] = ['off', 'daily', 'weekly', 'monthly']

export interface BackupStatus {
  /** The backup file's full path. */
  location: string | null
  locationName: string | null
  frequency: BackupFrequency
  lastBackupMs: number | null
  /** The last attempt failed. */
  failed: boolean
  running: boolean
  /** Show the reminder card on Home. */
  remind: boolean
}

export interface BackupFileInfo {
  createdAtMs: number
  deviceName: string
  platform: string
}

export interface RestoreOutcome {
  /** This computer took the backup's vault and account and is now unlocked. */
  adopted: boolean
  written: number
  itemsInBackup: number
}

export type BackupErrorCode =
  | 'notABackup'
  | 'newerVersion'
  | 'wrongPassword'
  | 'differentVault'
  | 'noVault'
  | 'unreadable'
  | 'store'
  | 'locked'
  | 'busy'
  | 'noLocation'
  | 'noFile'

function code(e: unknown): BackupErrorCode | null {
  return typeof e === 'object' && e !== null && 'code' in e ? ((e as { code: BackupErrorCode }).code ?? null) : null
}

/** The message key for a failed restore (or reading a backup file). */
export function restoreErrorKey(e: unknown): string {
  switch (code(e)) {
    case 'notABackup':
      return 'backup.error.notABackup'
    case 'newerVersion':
      return 'backup.error.newerVersion'
    case 'wrongPassword':
      return 'backup.error.wrongPassword'
    case 'differentVault':
      return 'backup.error.differentVault'
    case 'noVault':
      return 'backup.error.noVault'
    default:
      return 'backup.error.unreadable'
  }
}

/** The message key for a failed backup. */
export function backupErrorKey(e: unknown): string {
  switch (code(e)) {
    case 'noVault':
      return 'backup.error.noVault'
    case 'noLocation':
      return 'backup.chooseLocationFirst'
    default:
      return 'backup.failed'
  }
}

/** Medium date, short time, in the UI language (as Linked devices and the extension list). */
export function formatBackupDate(ms: number): string {
  return new Date(ms).toLocaleString(prefs.language, { dateStyle: 'medium', timeStyle: 'short' })
}

const tauriBackupApi = {
  status: () => invoke<BackupStatus>('backup_status'),
  checkDue: () => invoke<void>('backup_check_due'),
  chooseLocation: () => invoke<BackupStatus>('backup_choose_location'),
  backUpNow: () => invoke<{ cancelled: boolean; status: BackupStatus }>('backup_now'),
  setFrequency: (frequency: BackupFrequency) => invoke<BackupStatus>('backup_set_frequency', { frequency }),
  snoozeReminder: () => invoke<BackupStatus>('backup_snooze_reminder'),
  pickRestore: () => invoke<BackupFileInfo | null>('backup_pick_restore'),
  restore: (password: string) => invoke<RestoreOutcome>('backup_restore', { password }),
  cancelRestore: () => invoke<void>('backup_cancel_restore'),
  onChanged: (cb: () => void): Promise<UnlistenFn> => listen('backup-changed', () => cb()),
}

// Browser stand-in for design work: one location, a fake backup from a phone; the
// password "password" opens it.
const mockStatus: BackupStatus = {
  location: null,
  locationName: null,
  frequency: 'off',
  lastBackupMs: null,
  failed: false,
  running: false,
  remind: true,
}
const mockBackupApi: typeof tauriBackupApi = {
  async status() {
    return { ...mockStatus }
  },
  async checkDue() {},
  async chooseLocation() {
    mockStatus.location = '/Users/me/Documents/Qasa backup - My Mac.qasabackup'
    mockStatus.locationName = 'Qasa backup - My Mac.qasabackup'
    return { ...mockStatus }
  },
  async backUpNow() {
    if (!mockStatus.location) await mockBackupApi.chooseLocation()
    await new Promise((r) => setTimeout(r, 600))
    Object.assign(mockStatus, { lastBackupMs: Date.now(), failed: false, remind: false })
    return { cancelled: false, status: { ...mockStatus } }
  },
  async setFrequency(frequency) {
    mockStatus.frequency = frequency
    return { ...mockStatus }
  },
  async snoozeReminder() {
    mockStatus.remind = false
    return { ...mockStatus }
  },
  async pickRestore() {
    return { createdAtMs: Date.now() - 86_400_000, deviceName: "Sara's iPhone", platform: 'ios' }
  },
  async restore(password) {
    await new Promise((r) => setTimeout(r, 800))
    if (password !== 'password') throw { code: 'wrongPassword', message: 'wrong password' }
    return { adopted: false, written: 2, itemsInBackup: 12 }
  },
  async cancelRestore() {},
  async onChanged() {
    return () => {}
  },
}

export const backupApi: typeof tauriBackupApi = '__TAURI_INTERNALS__' in window ? tauriBackupApi : mockBackupApi
