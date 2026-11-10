// Browser extension bridge commands and events (src-tauri/src/bridge).
import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'

export interface BridgeClient {
  id: string
  name: string
  pairedAt: number
  lastUsedAt: number | null
}

/** "Allow <client> to use Qasa?" with the code both screens show. */
export interface PairPrompt {
  client: string
  code: string
}

export interface SavePrompt {
  id: string
  kind: 'save' | 'update'
  website: string
  username: string
  itemTitle: string | null
  client: string
}

/** "Confirm to fill <card>" when Touch ID / Hello can't (docs/autofill-plan.md §3.2). */
export interface ConfirmPrompt {
  id: string
  itemTitle: string
  client: string
}

const inTauri = '__TAURI_INTERNALS__' in window

export const bridgeApi = {
  clients: () => (inTauri ? invoke<BridgeClient[]>('bridge_clients') : Promise.resolve([])),
  revoke: (id: string) => invoke<boolean>('bridge_revoke', { id }),
  decidePair: (allow: boolean) => invoke<void>('bridge_pair_decide', { allow }),
  /** `true` when a login was written. */
  decideSave: (id: string, accept: boolean) => invoke<boolean>('bridge_save_decide', { id, accept }),
  /** `password: null` cancels. False for a wrong password (the prompt stays). */
  decideConfirm: (id: string, password: string | null) => invoke<boolean>('bridge_confirm_decide', { id, password }),
  setAuthReasons: (unlock: string, confirm: string) =>
    inTauri ? invoke<void>('bridge_set_auth_reasons', { unlock, confirm }) : Promise.resolve(),
  onConfirm: (fn: (p: ConfirmPrompt | null) => void): Promise<UnlistenFn> =>
    inTauri ? listen<ConfirmPrompt | null>('bridge-confirm', (e) => fn(e.payload)) : Promise.resolve(() => {}),
  /** The vault was unlocked with Touch ID / Hello from the browser. */
  onVaultUnlocked: (fn: () => void): Promise<UnlistenFn> =>
    inTauri ? listen('vault-unlocked', () => fn()) : Promise.resolve(() => {}),
  /** `null` closes the prompt (answered elsewhere, or expired). */
  onPair: (fn: (p: PairPrompt | null) => void): Promise<UnlistenFn> =>
    inTauri ? listen<PairPrompt | null>('bridge-pair', (e) => fn(e.payload)) : Promise.resolve(() => {}),
  onSave: (fn: (p: SavePrompt) => void): Promise<UnlistenFn> =>
    inTauri ? listen<SavePrompt>('bridge-save', (e) => fn(e.payload)) : Promise.resolve(() => {}),
}
