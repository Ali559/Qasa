// Typed calls into the Rust core (src-tauri/src/vault/commands.rs).
import { invoke } from '@tauri-apps/api/core'

import type { BiometricStatus, Collection, ItemSummary, VaultPayload, VaultStatus } from './types'

const tauriVaultApi = {
  status: () => invoke<VaultStatus>('vault_status'),
  create: (password: string, biometrics = false) => invoke<void>('vault_create', { password, biometrics }),
  biometricStatus: () => invoke<BiometricStatus>('vault_biometric_status'),
  setBiometric: (enabled: boolean) => invoke<BiometricStatus>('vault_set_biometric', { enabled }),
  unlockBiometric: (reason: string) => invoke<void>('vault_unlock_biometric', { reason }),
  verifyBiometric: (reason: string) => invoke<boolean>('vault_verify_biometric', { reason }),
  unlock: (password: string) => invoke<void>('vault_unlock', { password }),
  lock: () => invoke<void>('vault_lock'),
  verifyPassword: (password: string) => invoke<boolean>('vault_verify_password', { password }),
  noteActivity: () => invoke<void>('vault_note_activity'),
  list: () => invoke<{ items: ItemSummary[]; unreadable: number }>('vault_list'),
  get: (id: string) => invoke<VaultPayload>('vault_get', { id }),
  save: (payload: VaultPayload, id?: string) => invoke<ItemSummary>('vault_save', { id: id ?? null, payload }),
  remove: (id: string) => invoke<void>('vault_delete', { id }),
  markUsed: (id: string) => invoke<void>('vault_mark_used', { id }),
  changePassword: (current: string, newPassword: string) =>
    invoke<void>('vault_change_password', { current, newPassword }),
  wipe: () => invoke<void>('vault_wipe'),

  collections: () => invoke<Collection[]>('collections_list'),
  createCollection: (name: string) => invoke<Collection>('collection_create', { name }),
  renameCollection: (id: string, name: string) => invoke<void>('collection_rename', { id, name }),
  deleteCollection: (id: string) => invoke<void>('collection_delete', { id }),
}

import { mockVaultApi } from './mock'

/** The real core inside the app; the in-memory mock in a plain browser (design work). */
export const vaultApi: typeof tauriVaultApi = '__TAURI_INTERNALS__' in window ? tauriVaultApi : mockVaultApi
