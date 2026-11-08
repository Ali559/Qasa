// The popup's calls into the service worker and the page.
import type { BgResponse, LoginMatch, PopupRequest, TabRequest } from '@ext/shared/messages'

export class PopupError extends Error {}

export async function ask<T>(request: PopupRequest): Promise<T> {
  const response = (await chrome.runtime.sendMessage(request)) as BgResponse<T> | undefined
  if (!response) throw new PopupError('noResponse')
  if (!response.ok) throw new PopupError(response.error)
  return response.value
}

/**
 * The tab the popup acts on: the active tab of its window. `?tab=<id>` overrides it when
 * the popup is opened as a page of its own (a detached window, or the e2e tests), where
 * the "active tab" would be the popup itself.
 */
export async function targetTab(): Promise<chrome.tabs.Tab | undefined> {
  const param = new URLSearchParams(location.search).get('tab')
  if (param) return chrome.tabs.get(Number(param)).catch(() => undefined)
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  return tab
}

export function isDetached(): boolean {
  return new URLSearchParams(location.search).has('tab')
}

/** No content script answers in the tab: not a web page, or a tab opened before the
 *  extension was installed (see `askTab`). */
export class NoPageError extends Error {}

async function sendToTop<T>(tabId: number, message: TabRequest): Promise<T> {
  const response = (await chrome.tabs.sendMessage(tabId, message, { frameId: 0 })) as BgResponse<T> | undefined
  if (!response) throw new NoPageError('noResponse')
  if (!response.ok) throw new PopupError(response.error)
  return response.value
}

/**
 * Sends to the tab's top-frame content script. Tabs that were open before the extension
 * was installed have none: then the script is injected (`scripting`, allowed by the
 * activeTab grant the toolbar click gives) and the message sent again.
 */
async function askTab<T>(tabId: number, message: TabRequest): Promise<T> {
  try {
    return await sendToTop<T>(tabId, message)
  } catch (error) {
    if (error instanceof PopupError) throw error
  }
  try {
    await chrome.scripting.executeScript({ target: { tabId, allFrames: true }, files: ['content.js'] })
  } catch {
    throw new NoPageError('noPage')
  }
  return sendToTop<T>(tabId, message)
}

export function tabMatches(tabId: number): Promise<LoginMatch[]> {
  return askTab<LoginMatch[]>(tabId, { kind: 'qasaMatches' })
}

/** True when the page filled something. */
export function fillInTab(tabId: number, itemId: string): Promise<boolean> {
  return askTab<boolean>(tabId, { kind: 'qasaFill', itemId })
}
