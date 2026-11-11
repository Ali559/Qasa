<script setup lang="ts">
// iOS `VaultItemDetailView`, as the Passwords detail pane: hero tile, name, Details
// (tap a row to copy, eye to reveal secrets, 2FA code), Organization and History, with
// Edit and Delete in the header. Protected note content needs the main password first.
// Stored dates show in the UI language; an item that breaks a field rule (made by an
// older version, an import or a browser capture) gets the "needs attention" banner.
import { computed, ref, watch } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import { copySecret, PAYMENT_CLIPBOARD_MS } from '@/clipboard'
import { prefs, useT } from '@/prefs'
import { showToast } from '@/toast'
import { vaultApi } from '@/vault/api'
import { fieldsApi, formatCivilDate, type CivilDate } from '@/vault/fields'
import {
  detailFields,
  displayDate,
  isProtectedNote,
  NOTE_COLOR_VALUE,
  organizationColor,
  vaultDates,
  type DetailField,
} from '@/vault/present'
import { protectedGate, requestProtectedAccess } from '@/vault/protectedGate'
import { removeItem, session } from '@/vault/session'
import type { VaultPayload } from '@/vault/types'
import { VARIANT_ICON } from '@/icons'
import { siteIconPath } from '@/vault/siteIcon'
import TotpCodeRow from './TotpCodeRow.vue'

const props = defineProps<{ itemId: string }>()
const emit = defineEmits<{ edit: []; deleted: [] }>()
const t = useT()

const payload = ref<VaultPayload | null>(null)
const revealed = ref(new Set<string>())
const confirmDelete = ref(false)
/** Stored date → the day it means (core `parse_date`), for showing it localized. */
const dates = ref(new Map<string, CivilDate | null>())
const needsAttention = ref(false)

const row = computed(() => session.items.find((i) => i.id === props.itemId) ?? null)
function showDate(raw: string): string {
  const d = dates.value.get(raw)
  return d ? formatCivilDate(d, prefs.language) : raw
}
const fields = computed(() => (payload.value ? detailFields(payload.value, t, showDate) : []))
const isProtected = computed(() => !!payload.value && isProtectedNote(payload.value))
const unlocked = computed(() => !isProtected.value || protectedGate.confirmed)
const color = computed(() => (payload.value ? organizationColor(payload.value) : null))
const collectionName = computed(() => {
  const id = row.value?.collectionId
  return id ? (session.collections.find((c) => c.id === id)?.name ?? null) : null
})

async function load() {
  revealed.value = new Set()
  try {
    payload.value = await vaultApi.get(props.itemId)
    await inspect(payload.value)
    await vaultApi.markUsed(props.itemId)
    if (isProtected.value) await requestProtectedAccess()
  } catch (e) {
    console.error('Could not open the item', e)
    payload.value = null
  }
}

/** Reads the item's dates and field rules in two calls; a failure only skips the extras. */
async function inspect(p: VaultPayload) {
  try {
    const raws = vaultDates(p)
    const [parsed, issues] = await Promise.all([raws.length ? fieldsApi.parseDates(raws) : [], fieldsApi.validate(p)])
    dates.value = new Map(raws.map((raw, i) => [raw, parsed[i] ?? null]))
    needsAttention.value = issues.length > 0
  } catch (e) {
    console.error('Could not check the item', e)
    dates.value = new Map()
    needsAttention.value = false
  }
}

watch(() => [props.itemId, row.value?.updatedAt], load, { immediate: true })

function toggleReveal(field: DetailField) {
  const next = new Set(revealed.value)
  if (next.has(field.label)) next.delete(field.label)
  else next.add(field.label)
  revealed.value = next
}

const isPayment = computed(() => payload.value?.variant === 'card' || payload.value?.variant === 'bankAccount')

async function copyField(field: DetailField) {
  if (!field.value) return
  if (field.secret && isProtected.value && !(await requestProtectedAccess())) return
  await copySecret(field.value, isPayment.value ? PAYMENT_CLIPBOARD_MS : undefined)
  await vaultApi.markUsed(props.itemId)
  showToast(t('vault.copiedFmt', field.label))
}

async function copyTotp(code: string, secondsLeft: number) {
  await copySecret(code, secondsLeft * 1000)
  await vaultApi.markUsed(props.itemId)
  showToast(t('totp.copied'))
}

function masked(value: string) {
  return '•'.repeat(Math.min(Math.max([...value].length, 8), 16))
}

async function remove() {
  confirmDelete.value = false
  await vaultApi.remove(props.itemId)
  removeItem(props.itemId)
  emit('deleted')
}

const COLOR_KEY = (c: string) => `color.${c}`
/** The hero's bundled site icon (logins), else the type symbol (iOS `heroGlyph`). */
const heroSitePath = computed(() => (payload.value?.variant === 'login' ? siteIconPath(payload.value.fields.website) : null))
</script>

<template>
  <div class="detail">
    <header class="bar">
      <button type="button" class="icon-button accent" :aria-label="t('common.edit')" :title="t('common.edit')" @click="emit('edit')">
        <QasaIcon name="edit" :size="17" />
      </button>
      <button type="button" class="icon-button danger" :aria-label="t('common.delete')" :title="t('common.delete')" @click="confirmDelete = true">
        <QasaIcon name="delete" :size="17" />
      </button>
    </header>

    <div v-if="payload" class="scroll">
      <div class="column">
        <div class="hero">
          <span class="hero-icon" aria-hidden="true">
            <svg v-if="heroSitePath" viewBox="0 0 24 24" width="36" height="36" fill="currentColor" focusable="false"><path :d="heroSitePath" /></svg>
            <QasaIcon v-else :name="VARIANT_ICON[payload.variant]" :size="36" />
          </span>
          <h1 class="name">{{ row?.displayName ?? t('vault.untitled') }}</h1>
        </div>

        <p v-if="needsAttention" class="attention" role="status">
          <QasaIcon name="warning" :size="17" class="attention-icon" />
          <span>{{ t('validation.needsAttention') }}</span>
        </p>

        <section class="section">
          <h2 class="section-title">{{ t('vault.details') }}</h2>
          <div class="card">
            <p v-if="fields.length === 0" class="muted">{{ t('vault.noDetails') }}</p>
            <template v-for="field in fields" :key="field.label">
              <div class="field" role="button" tabindex="0" @click="copyField(field)" @keydown.enter="copyField(field)">
                <div class="field-head">
                  <span class="field-label">{{ field.label }}</span>
                  <button
                    v-if="field.secret && unlocked"
                    type="button"
                    class="reveal"
                    :aria-label="revealed.has(field.label) ? t('vault.hidePassword') : t('vault.showPassword')"
                    @click.stop="toggleReveal(field)"
                  >
                    <QasaIcon :name="revealed.has(field.label) ? 'hide' : 'show'" :size="14" />
                  </button>
                </div>
                <span v-if="field.secret && !unlocked" class="field-value muted">{{ t('vault.lockedAuthenticate') }}</span>
                <span v-else-if="field.secret && !revealed.has(field.label)" class="field-value mono">{{ masked(field.value) }}</span>
                <span v-else class="field-value" :class="{ mono: field.secret, multiline: field.multiline }">{{ field.value }}</span>
              </div>
            </template>
            <TotpCodeRow v-if="row?.hasTotp" :item-id="itemId" @copy="copyTotp" />
          </div>
        </section>

        <section class="section">
          <h2 class="section-title">{{ t('vault.organization') }}</h2>
          <div class="card">
            <div class="pair">
              <span class="field-label">{{ t('vault.color') }}</span>
              <span v-if="color" class="pair-value">
                <span class="swatch" :style="{ background: NOTE_COLOR_VALUE[color] }" />
                {{ t(COLOR_KEY(color)) }}
              </span>
              <span v-else class="pair-value muted">{{ t('common.none') }}</span>
            </div>
            <div class="pair">
              <span class="field-label">{{ t('vault.collection') }}</span>
              <span class="pair-value" :class="{ muted: !collectionName }">{{ collectionName ?? t('common.none') }}</span>
            </div>
          </div>
        </section>

        <section class="section">
          <h2 class="section-title">{{ t('vault.history') }}</h2>
          <div class="card">
            <div class="pair">
              <span class="field-label">{{ t('vault.created') }}</span>
              <span class="pair-value">{{ displayDate(row?.createdAt ?? null, t, prefs.language) }}</span>
            </div>
            <div class="pair">
              <span class="field-label">{{ t('vault.lastUpdated') }}</span>
              <span class="pair-value">{{ displayDate(row?.updatedAt ?? null, t, prefs.language) }}</span>
            </div>
            <div class="pair">
              <span class="field-label">{{ t('vault.lastUsed') }}</span>
              <span class="pair-value">{{ displayDate(row?.lastUsedAt ?? null, t, prefs.language) }}</span>
            </div>
          </div>
        </section>
      </div>
    </div>

    <ConfirmDialog
      :open="confirmDelete"
      :title="t('vault.deleteItemTitle')"
      icon="delete"
      :message="t('vault.deleteItemMessage', row?.displayName ?? t('vault.thisItem'))"
      :confirm-label="t('common.delete')"
      destructive
      @confirm="remove"
      @cancel="confirmDelete = false"
    />
  </div>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--background);
}

.bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  height: var(--size-header-height);
  align-items: center;
  padding-inline: var(--space-header-horizontal);
  flex-shrink: 0;
}

.icon-button {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: var(--card-background);
  cursor: pointer;
}

.icon-button:hover {
  background: var(--card-muted);
}

.icon-button.accent {
  color: var(--accent);
}

.icon-button.danger {
  color: var(--danger);
}

.scroll {
  flex: 1;
  overflow-y: auto;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 640px;
  margin-inline: auto;
  padding: 0 var(--space-screen-padding) 40px;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

/* iOS `heroIcon`: 88 square, chrome fill, text-primary 36 glyph, radius 20, 1px strokeChrome,
   12 above. */
.hero-icon {
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  margin-top: 12px;
  border: var(--size-stroke) solid var(--stroke-chrome);
  border-radius: var(--radius-card);
  background: var(--chrome);
  color: var(--text-primary);
}

.name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  user-select: text;
}

/* iOS `attentionBanner`: danger 12% fill, 40% stroke, card radius, 14 padding; a danger
   triangle 10 from 13 medium text-primary text. */
.attention {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0;
  padding: 14px;
  border: var(--size-stroke) solid color-mix(in srgb, var(--danger) 40%, transparent);
  border-radius: var(--radius-card);
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
}

.attention-icon {
  flex-shrink: 0;
  width: 17px;
  height: calc(13px * var(--line-height));
  color: var(--danger);
}

.section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.card {
  display: flex;
  flex-direction: column;
  padding: 4px 16px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.card > * + * {
  border-top: var(--size-stroke) solid var(--stroke);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0;
  cursor: copy;
}

.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-label {
  font-size: var(--type-row-value-size);
  color: var(--text-secondary);
}

.pair .field-label {
  font-size: 14px;
}

.field-value {
  font-size: 15px;
  font-weight: 500;
  overflow-wrap: anywhere;
  user-select: text;
}

.field-value.multiline {
  white-space: pre-wrap;
}

.mono {
  font-family: var(--font-mono);
  direction: ltr;
  text-align: start;
}

.muted {
  color: var(--text-secondary);
}

.reveal {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.pair {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
}

.pair-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  text-align: end;
}

.swatch {
  width: 14px;
  height: 14px;
  border-radius: 50%;
}
</style>
