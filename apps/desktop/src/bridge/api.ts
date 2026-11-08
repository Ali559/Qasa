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

const inTauri = '__TAURI_INTERNALS__' in window

export const bridgeApi = {
  clients: () => (inTauri ? invoke<BridgeClient[]>('bridge_clients') : Promise.resolve([])),
  revoke: (id: string) => invoke<boolean>('bridge_revoke', { id }),
  decidePair: (allow: boolean) => invoke<void>('bridge_pair_decide', { allow }),
  /** `true` when a login was written. */
  decideSave: (id: string, accept: boolean) => invoke<boolean>('bridge_save_decide', { id, accept }),
  /** `null` closes the prompt (answered elsewhere, or expired). */
  onPair: (fn: (p: PairPrompt | null) => void): Promise<UnlistenFn> =>
    inTauri ? listen<PairPrompt | null>('bridge-pair', (e) => fn(e.payload)) : Promise.resolve(() => {}),
  onSave: (fn: (p: SavePrompt) => void): Promise<UnlistenFn> =>
    inTauri ? listen<SavePrompt>('bridge-save', (e) => fn(e.payload)) : Promise.resolve(() => {}),
}
