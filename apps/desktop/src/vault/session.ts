// The unlocked-vault session the screens share: status, the item list, collections,
// and auto-lock. Mirrors iOS `AppSession` + `VaultMetadataCache`.
import { listen } from '@tauri-apps/api/event'
import { reactive } from 'vue'

import { vaultApi } from './api'
import { resetProtectedAccess } from './protectedGate'
import type { Collection, ItemSummary, VaultStatus } from './types'

export const session = reactive({
  status: null as VaultStatus | null,
  items: [] as ItemSummary[],
  unreadable: 0,
  collections: [] as Collection[],
})

/** Case-insensitive, locale-aware name order, as iOS sorts `VaultMetadataCache`. */
const collator = new Intl.Collator(undefined, { sensitivity: 'base' })

export function sortByName(rows: ItemSummary[]): ItemSummary[] {
  return [...rows].sort((a, b) => collator.compare(a.displayName, b.displayName))
}

export async function refreshStatus(): Promise<VaultStatus> {
  session.status = await vaultApi.status()
  return session.status
}

export async function loadVault(): Promise<void> {
  const [list, collections] = await Promise.all([vaultApi.list(), vaultApi.collections()])
  session.items = sortByName(list.items)
  session.unreadable = list.unreadable
  session.collections = collections
}

export async function reloadCollections(): Promise<void> {
  session.collections = await vaultApi.collections()
}

/** Replaces or adds one row after a save. */
export function upsertItem(row: ItemSummary): void {
  session.items = sortByName([...session.items.filter((i) => i.id !== row.id), row])
}

export function removeItem(id: string): void {
  session.items = session.items.filter((i) => i.id !== id)
}

function clearUnlockedState() {
  resetProtectedAccess()
  session.items = []
  session.collections = []
  session.unreadable = 0
  if (session.status) session.status = { ...session.status, unlocked: false }
}

export async function lockVault(): Promise<void> {
  await vaultApi.lock()
  clearUnlockedState()
}

let activityAttached = false

/**
 * Reports keyboard and pointer use to the core (at most every 10 s) so auto-lock measures
 * real inactivity, and clears the screen when the core locks on its own.
 */
export function attachAutoLock(onLocked: () => void): void {
  if (activityAttached) return
  activityAttached = true
  let last = 0
  const note = () => {
    const now = Date.now()
    if (now - last < 10_000 || !session.status?.unlocked) return
    last = now
    vaultApi.noteActivity().catch(() => {})
  }
  for (const type of ['pointerdown', 'keydown', 'wheel', 'pointermove'] as const) {
    window.addEventListener(type, note, { passive: true, capture: true })
  }
  if ('__TAURI_INTERNALS__' in window) {
    listen('vault-locked', () => {
      clearUnlockedState()
      onLocked()
    }).catch((e) => console.error('Could not listen for auto-lock', e))
  }
}
