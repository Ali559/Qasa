// The backup status the Backups screen and the Home reminder share. Re-read whenever the
// core says a backup started or ended (`backup-changed`), so an automatic backup shows up
// without a reload.
import { reactive } from 'vue'

import { backupApi, type BackupStatus } from './api'

export const backup = reactive({ status: null as BackupStatus | null })

export async function refreshBackup(): Promise<void> {
  try {
    backup.status = await backupApi.status()
  } catch (e) {
    console.error('Could not read the backup status', e)
  }
}

export function setBackupStatus(status: BackupStatus): void {
  backup.status = status
}

let listening = false

/** After every unlock: start listening once, read the status, and run a due backup. */
export function onVaultUnlocked(): void {
  if (!listening) {
    listening = true
    backupApi.onChanged(refreshBackup).catch((e) => console.error('Could not listen for backups', e))
  }
  refreshBackup()
  backupApi.checkDue().catch((e) => console.error('Could not check for a due backup', e))
}

export function forgetBackup(): void {
  backup.status = null
}
