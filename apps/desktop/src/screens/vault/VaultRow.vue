<script setup lang="ts">
// iOS `VaultItemRow`: tile, name and subtitle, a copy button for the primary value and
// the ⋯ action sheet titled with the item's name (copy actions, 2FA code, open website,
// edit, delete). Clicking the row selects it for the detail pane. Rows sit in a
// `VaultSection` card (design/README.md rule 6e): no card of their own, a divider between
// them, and the hover, press and selected tints clipped to the card's corners.
import { computed, ref } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ActionSheet, { type ActionItem } from '@/components/ActionSheet.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import { copySecret, PAYMENT_CLIPBOARD_MS } from '@/clipboard'
import { useT } from '@/prefs'
import { showToast } from '@/toast'
import { vaultApi } from '@/vault/api'
import { copyActions, isProtectedNote, localizedSubtitle, primaryCopy, websiteUrl } from '@/vault/present'
import { requestProtectedAccess } from '@/vault/protectedGate'
import { removeItem } from '@/vault/session'
import { toolsApi } from '@/vault/tools'
import type { ItemSummary, VaultPayload } from '@/vault/types'
import { openUrl } from '@/opener'
import ItemTile from './ItemTile.vue'

const props = defineProps<{ row: ItemSummary; selected: boolean }>()
const emit = defineEmits<{ select: []; edit: []; deleted: [] }>()
const t = useT()
const confirmDelete = ref(false)
const payload = ref<VaultPayload | null>(null)

async function load(): Promise<VaultPayload> {
  payload.value = await vaultApi.get(props.row.id)
  return payload.value
}

async function copy(value: string, payment: boolean, message: string, protectedValue = false) {
  if (protectedValue && !(await requestProtectedAccess())) return
  await copySecret(value, payment ? PAYMENT_CLIPBOARD_MS : undefined)
  await vaultApi.markUsed(props.row.id)
  showToast(message)
}

async function copyPrimary() {
  const p = await load()
  const primary = primaryCopy(p)
  if (!primary) return
  await copy(primary.value, !!primary.payment, t('vault.copiedFmt', props.row.displayName), isProtectedNote(p))
}

const menuItems = ref<ActionItem[]>([])
const showMenu = ref(false)

/** Built when the sheet opens, from the decrypted payload. */
async function openMenu() {
  const p = await load()
  const items: ActionItem[] = copyActions(p, t).map((a) => ({
    label: a.label,
    icon: 'copy',
    action: () =>
      copy(a.value, !!a.payment, t('gen.copied'), isProtectedNote(p) && a.label === t('copy.content')),
  }))
  if (props.row.hasTotp) {
    items.push({
      label: t('copy.totp'),
      icon: 'copy',
      action: async () => {
        const code = await toolsApi.totpCode(props.row.id)
        await copySecret(code.code, code.secondsRemaining * 1000)
        await vaultApi.markUsed(props.row.id)
        showToast(t('totp.copied'))
      },
    })
  }
  const url = websiteUrl(p)
  if (url) items.push({ label: t('vault.openWebsite'), icon: 'website', action: () => openUrl(url) })
  items.push({ label: t('vault.editItem'), icon: 'edit', action: () => emit('edit') })
  items.push({ label: t('vault.deleteItem'), icon: 'delete', destructive: true, action: () => (confirmDelete.value = true) })
  menuItems.value = items
  showMenu.value = true
}

async function remove() {
  confirmDelete.value = false
  await vaultApi.remove(props.row.id)
  removeItem(props.row.id)
  emit('deleted')
}

const subtitle = computed(() => localizedSubtitle(props.row.subtitle, t))
</script>

<template>
  <div class="row" :class="{ selected }" role="button" tabindex="0" @click="emit('select')" @keydown.enter.self="emit('select')">
    <!-- size.vaultTile: 44. -->
    <ItemTile :variant="row.variant" :name="row.displayName" :website="row.siteHost" :color="row.color" :size="44" />
    <span class="text">
      <span class="name">{{ row.displayName }}</span>
      <!-- bdi: a phone number, card digits or email keeps its own order in Arabic and Kurdish (iOS does the same). -->
      <span class="subtitle"><bdi>{{ subtitle }}</bdi></span>
    </span>
    <button v-if="row.hasPrimaryCopy" type="button" class="icon-button" :aria-label="t('a11y.copy')" :title="t('a11y.copy')" @click.stop="copyPrimary">
      <QasaIcon name="copy" :size="20" />
    </button>
    <button type="button" class="icon-button" :aria-label="t('a11y.moreActions')" :title="t('a11y.moreActions')" aria-haspopup="dialog" @click.stop="openMenu">
      <QasaIcon name="more-vertical" :size="20" />
    </button>
    <ActionSheet v-model:open="showMenu" :title="row.displayName" :items="menuItems" />
    <ConfirmDialog
      :open="confirmDelete"
      :title="t('vault.deleteItemTitle')"
      icon="delete"
      :message="t('vault.deleteItemMessage', row.displayName)"
      :confirm-label="t('common.delete')"
      destructive
      @confirm="remove"
      @cancel="confirmDelete = false"
    />
  </div>
</template>

<style scoped>
/* design/README.md rule 6e: 10 top and bottom, 14 at the sides, the 44 tile, 14 to the
   text; 64 high. */
.row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-row-icon-gap);
  padding: var(--space-vault-row-vertical) var(--space-vault-row-horizontal);
  cursor: pointer;
  transition: background-color var(--motion-fast);
}

/* The divider: 1px stroke from the text's leading edge (14 + 44 + 14 = 72) to the card's
   trailing edge; none above the first row. */
.row + .row::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: calc(var(--space-vault-row-horizontal) + var(--size-vault-tile) + var(--space-row-icon-gap));
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
  pointer-events: none;
}

/* The hover and press wash (rowPressedOpacity) covers this row only; the card clips it. */
.row::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--text-primary);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion-press);
}

.row:hover::after,
.row:active::after {
  opacity: var(--motion-row-pressed-opacity);
}

.row.selected {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}

.row:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

/* The selected fill and focus ring follow the card's inner corners. */
.row:first-child {
  border-start-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-start-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

.row:last-child {
  border-end-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-end-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

.text {
  display: flex;
  flex-direction: column;
  gap: var(--space-row-text-gap);
  flex: 1;
  min-width: 0;
}

.name,
.subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name {
  font-size: var(--type-vault-row-title-size);
  font-weight: var(--type-vault-row-title-weight);
}

.subtitle {
  font-size: var(--type-vault-row-subtitle-size);
  font-weight: var(--type-vault-row-subtitle-weight);
  color: var(--text-secondary);
}

/* size.rowIconButton 36, glyph 20. */
.icon-button {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-row-icon-button);
  height: var(--size-row-icon-button);
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.icon-button:hover {
  background: var(--card-muted);
}
</style>
