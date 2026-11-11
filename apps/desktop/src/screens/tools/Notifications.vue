<script setup lang="ts">
// iOS `NotificationsView`, server inbox only (the desktop keeps no on-device notes):
// the "From Qasa" list with unread dots, the detail sheet, Mark All Read in the header,
// per-row delete (the iOS swipe; a row menu or the Delete key here), the empty state, the
// red action-failed line, and cursor paging that loads the next page when the end of the
// list scrolls into view. Every change is optimistic and rolls back if the server refuses.
import { BellOff, EllipsisVertical } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import Menu from '@/components/Menu.vue'
import PageFrame from '@/components/PageFrame.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import Sheet from '@/components/Sheet.vue'
import { accountApi } from '@/account/api'
import { account, refreshUnread } from '@/account/store'
import { isApiError, localized, type InboxNotification } from '@/account/types'
import { prefs, useT } from '@/prefs'
import { displayDate } from '@/vault/present'

defineEmits<{ back: [] }>()
const t = useT()

const items = ref<InboxNotification[]>([])
const nextBefore = ref<string | null>(null)
const firstLoad = ref(true)
const loadingPage = ref(false)
const selected = ref<InboxNotification | null>(null)
/** iOS `errorMessage`: a translation key, so it follows a language change. */
const errorKey = ref<string | null>(null)
const sentinel = ref<HTMLElement | null>(null)

const hasUnread = computed(() => account.unread > 0 || items.value.some((n) => !n.read_at))

function text(n: InboxNotification) {
  return localized(n, prefs.language)
}

function date(iso: string): string {
  const ms = Date.parse(iso)
  return Number.isNaN(ms) ? iso : displayDate(ms, t, prefs.language)
}

/** A 404 means the row is already gone from this inbox, which is what the user wanted. */
function isGone(e: unknown): boolean {
  return isApiError(e) && e.code === 'server' && e.status === 404
}

function failureKey(e: unknown, fallback: string): string {
  return isApiError(e) && e.code === 'unauthorized' ? 'error.unauthorized' : fallback
}

/** First page. A failure keeps what is already on screen (iOS `fetchList`). */
async function fetchList() {
  loadingPage.value = true
  try {
    const page = await accountApi.notifications()
    items.value = page.notifications
    nextBefore.value = page.next_before ?? null
    if (page.unread_count !== undefined) account.unread = page.unread_count
    errorKey.value = null
  } catch (e) {
    errorKey.value = failureKey(e, 'notifications.actionFailed')
  } finally {
    loadingPage.value = false
    firstLoad.value = false
  }
}

/** Next page; on failure the cursor stays and the next sighting of the end retries. */
async function loadMore() {
  const cursor = nextBefore.value
  if (loadingPage.value || !cursor) return
  loadingPage.value = true
  try {
    const page = await accountApi.notifications(cursor)
    const known = new Set(items.value.map((n) => n.id))
    items.value.push(...page.notifications.filter((n) => !known.has(n.id)))
    nextBefore.value = page.next_before ?? null
    if (page.unread_count !== undefined) account.unread = page.unread_count
  } catch {
    retryLater()
  } finally {
    loadingPage.value = false
  }
}

// The end of the list in view loads the next page, as the iOS `onAppear` on the last row.
let observer: IntersectionObserver | null = null
let retryTimer: ReturnType<typeof setTimeout> | undefined

function retryLater() {
  clearTimeout(retryTimer)
  retryTimer = setTimeout(() => {
    // Re-observing reports the sentinel's current visibility, so a still-visible end retries.
    if (observer && sentinel.value) {
      observer.unobserve(sentinel.value)
      observer.observe(sentinel.value)
    }
  }, 5000)
}

watch(sentinel, (el, old) => {
  if (old) observer?.unobserve(old)
  if (el) observer?.observe(el)
})

onMounted(() => {
  observer = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) loadMore()
  })
  if (sentinel.value) observer.observe(sentinel.value)
  fetchList()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  clearTimeout(retryTimer)
})

function setReadAt(ids: Set<string>, readAt: string | null) {
  for (const n of items.value) if (ids.has(n.id)) n.read_at = readAt
}

/** Show the row we already have; opening it on the server marks it read. */
async function open(n: InboxNotification) {
  selected.value = n
  const wasUnread = !n.read_at
  if (wasUnread) {
    setReadAt(new Set([n.id]), new Date().toISOString())
    account.unread = Math.max(0, account.unread - 1)
  }
  try {
    const fresh = await accountApi.openNotification(n.id)
    // The core may pass the server's `{ notification }` envelope through unchanged.
    const detail = ((fresh as unknown as { notification?: InboxNotification }).notification ?? fresh) as InboxNotification
    const i = items.value.findIndex((x) => x.id === n.id)
    if (detail?.id === n.id && i >= 0) items.value[i] = { ...detail, read_at: detail.read_at ?? items.value[i].read_at }
    if (selected.value?.id === n.id && detail?.id === n.id) selected.value = items.value[i] ?? detail
    errorKey.value = null
  } catch (e) {
    if (!isGone(e) && wasUnread) setReadAt(new Set([n.id]), null)
    if (!isGone(e)) errorKey.value = failureKey(e, 'notifications.openFailed')
  } finally {
    await refreshUnread()
  }
}

async function markAllRead() {
  const ids = new Set(items.value.filter((n) => !n.read_at).map((n) => n.id))
  const previous = account.unread
  setReadAt(ids, new Date().toISOString())
  account.unread = 0
  try {
    await accountApi.readAll()
    errorKey.value = null
  } catch (e) {
    setReadAt(ids, null)
    account.unread = previous
    errorKey.value = failureKey(e, 'notifications.actionFailed')
  } finally {
    await refreshUnread()
  }
}

/** Optimistic per-user delete; the row comes back if the server call fails. */
async function remove(n: InboxNotification) {
  const index = items.value.findIndex((x) => x.id === n.id)
  if (index < 0) return
  if (selected.value?.id === n.id) selected.value = null
  const [removed] = items.value.splice(index, 1)
  try {
    await accountApi.deleteNotification(n.id)
    errorKey.value = null
  } catch (e) {
    if (!isGone(e)) {
      if (!items.value.some((x) => x.id === n.id)) items.value.splice(Math.min(index, items.value.length), 0, removed)
      errorKey.value = failureKey(e, 'notifications.actionFailed')
    }
  } finally {
    await refreshUnread()
  }
}
</script>

<template>
  <PageFrame :title="t('notifications.title')" back :gap="8" @back="$emit('back')">
    <template #header-trailing>
      <button v-if="hasUnread" type="button" class="mark-all" @click="markAllRead">{{ t('notifications.markAllRead') }}</button>
    </template>

    <div v-if="items.length === 0 && firstLoad" class="loading"><span class="spinner" aria-hidden="true" /></div>

    <div v-else-if="items.length === 0" class="empty">
      <BellOff :size="40" />
      <strong>{{ t('notifications.empty') }}</strong>
      <span>{{ t('notifications.emptyDesc') }}</span>
    </div>

    <template v-else>
      <SectionLabel :text="t('notifications.fromQasa')" class="section" />
      <CardContainer :padding="0">
        <template v-for="(n, i) in items" :key="n.id">
          <hr v-if="i > 0" class="divider" />
          <div class="row">
            <button type="button" class="open" @click="open(n)" @keydown.delete.prevent="remove(n)">
              <span class="dot" :class="{ unread: !n.read_at }" />
              <span class="text">
                <span class="title">{{ text(n).title }}</span>
                <span class="body">{{ text(n).body }}</span>
                <span class="date">{{ date(n.created_at) }}</span>
              </span>
            </button>
            <Menu :label="t('a11y.moreActions')" :items="[{ label: t('common.delete'), destructive: true, action: () => remove(n) }]">
              <EllipsisVertical :size="17" />
            </Menu>
          </div>
        </template>
        <div v-if="nextBefore" ref="sentinel" class="more"><span class="spinner" aria-hidden="true" /></div>
      </CardContainer>
    </template>

    <p v-if="errorKey" class="error" role="alert">{{ t(errorKey) }}</p>

    <template #overlay>
      <Sheet :open="!!selected" :label="t('notifications.detailTitle')" :title="t('notifications.detailTitle')" @close="selected = null">
        <div v-if="selected" class="detail">
          <h3>{{ text(selected).title }}</h3>
          <span class="date">{{ date(selected.created_at) }}</span>
          <p>{{ text(selected).body }}</p>
        </div>
      </Sheet>
    </template>
  </PageFrame>
</template>

<style scoped>
.mark-all {
  padding: 6px 8px;
  border: 0;
  border-radius: calc(var(--radius-control) - 2px);
  background: transparent;
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.mark-all:hover {
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.section {
  padding-inline: 4px;
}

.divider {
  margin: 0;
  margin-inline-start: 36px;
  border: 0;
  border-top: var(--size-stroke) solid var(--stroke);
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  padding-inline-end: 8px;
}

.open {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  padding: 12px 16px;
  padding-inline-end: 4px;
  border: 0;
  background: transparent;
  text-align: start;
  cursor: pointer;
}

.row > :last-child {
  margin-top: 8px;
}

.dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--stroke);
}

.dot.unread {
  background: var(--accent);
}

.text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.title {
  font-size: var(--type-row-title-size);
  font-weight: 600;
  color: var(--text-primary);
}

.body {
  font-size: var(--type-caption-size);
  color: var(--text-secondary);
}

.date {
  font-size: var(--type-field-error-size);
  color: var(--text-tertiary);
}

.more,
.loading {
  display: flex;
  justify-content: center;
  padding: 14px;
}

.loading {
  padding-top: 48px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 72px;
  color: var(--text-secondary);
  text-align: center;
}

.empty strong {
  color: var(--text-primary);
  font-size: var(--type-sheet-title-size);
  font-weight: 700;
}

.error {
  margin: 0;
  font-size: var(--type-caption-size);
  color: var(--danger);
  text-align: center;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--space-screen-padding);
  overflow-y: auto;
  user-select: text;
}

.detail h3 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.detail .date {
  font-size: var(--type-caption-size);
}

.detail p {
  margin: 0;
  font-size: var(--type-bar-button-size);
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid color-mix(in srgb, var(--accent) 25%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
