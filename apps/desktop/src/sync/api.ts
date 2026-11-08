// Wi‑Fi sync commands and events (src-tauri/src/sync.rs).
import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'

export type SyncEvent =
  | { stage: 'waiting'; code: string; expiresIn: number }
  | { stage: 'connecting' }
  | { stage: 'done'; peerName: string; peerPlatform: string; adopted: boolean; received: number; sent: number }
  | { stage: 'failed'; reason: 'wrongCode' | 'differentVault' | 'noVault' | 'version' | 'expired' | 'io' | 'other'; attemptsLeft: number }
  | { stage: 'stopped' }

export interface Peer {
  name: string
  platform: string
  address: string
}

const inTauri = '__TAURI_INTERNALS__' in window

/** iOS-style message key for a failed sync. */
export function failureKey(e: Extract<SyncEvent, { stage: 'failed' }>): { key: string; arg?: number } {
  switch (e.reason) {
    case 'wrongCode':
      return e.attemptsLeft > 0 ? { key: 'sync.wrongCode', arg: e.attemptsLeft } : { key: 'sync.wrongCodeFinal' }
    case 'differentVault':
      return { key: 'sync.differentVault' }
    case 'noVault':
      return { key: 'sync.noVault' }
    case 'version':
      return { key: 'sync.version' }
    case 'expired':
      return { key: 'sync.expired' }
    case 'io':
      return { key: 'sync.io' }
    default:
      return { key: 'sync.failed' }
  }
}

// Browser stand-in: one fake phone, code 123456 works.
let mockListeners: Array<(e: SyncEvent) => void> = []
let mockPeerListeners: Array<(p: Peer[]) => void> = []

export const syncApi = {
  async hostStart(): Promise<string> {
    if (inTauri) return invoke<string>('sync_host_start')
    const code = '482913'
    setTimeout(() => mockListeners.forEach((l) => l({ stage: 'waiting', code, expiresIn: 120 })), 50)
    return code
  },
  async browseStart(): Promise<void> {
    if (inTauri) return invoke('sync_browse_start')
    setTimeout(() => mockPeerListeners.forEach((l) => l([{ name: "Sara's iPhone", platform: 'ios', address: '192.168.1.20:51234' }])), 600)
  },
  async stop(): Promise<void> {
    if (inTauri) return invoke('sync_stop')
  },
  async join(address: string, code: string): Promise<SyncEvent> {
    if (inTauri) return invoke<SyncEvent>('sync_join', { address, code })
    await new Promise((r) => setTimeout(r, 800))
    return code === '123456'
      ? { stage: 'done', peerName: "Sara's iPhone", peerPlatform: 'ios', adopted: false, received: 3, sent: 1 }
      : { stage: 'failed', reason: 'wrongCode', attemptsLeft: 2 }
  },
  async onEvent(cb: (e: SyncEvent) => void): Promise<UnlistenFn> {
    if (inTauri) return listen<SyncEvent>('sync-event', (e) => cb(e.payload))
    mockListeners.push(cb)
    return () => (mockListeners = mockListeners.filter((l) => l !== cb))
  },
  async onPeers(cb: (p: Peer[]) => void): Promise<UnlistenFn> {
    if (inTauri) return listen<Peer[]>('sync-peers', (e) => cb(e.payload))
    mockPeerListeners.push(cb)
    return () => (mockPeerListeners = mockPeerListeners.filter((l) => l !== cb))
  },
}
