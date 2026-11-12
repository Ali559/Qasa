<script setup lang="ts">
// iOS `VaultItemRow`: tile, name and subtitle, a copy button for the primary value and
// the ⋯ menu (copy actions, 2FA code, open website, edit, delete). Clicking the row
// selects it for the detail pane.
import { computed, ref } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Menu, { type MenuItem } from '@/components/Menu.vue'
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

const menuItems = ref<MenuItem[]>([])

/** Built when the menu opens, from the decrypted payload. */
async function prepareMenu() {
  const p = await load()
  const items: MenuItem[] = copyActions(p, t).map((a) => ({
    label: a.label,
    action: () =>
      copy(a.value, !!a.payment, t('gen.copied'), isProtectedNote(p) && a.label === t('copy.content')),
  }))
  if (props.row.hasTotp) {
    items.push({
      label: t('copy.totp'),
      action: async () => {
        const code = await toolsApi.totpCode(props.row.id)
        await copySecret(code.code, code.secondsRemaining * 1000)
        await vaultApi.markUsed(props.row.id)
        showToast(t('totp.copied'))
      },
    })
  }
  const url = websiteUrl(p)
  if (url) items.push({ label: t('vault.openWebsite'), action: () => openUrl(url) })
  items.push({ label: t('vault.editItem'), action: () => emit('edit') })
  items.push({ label: t('vault.deleteItem'), destructive: true, action: () => (confirmDelete.value = true) })
  menuItems.value = items
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
    <ItemTile :variant="row.variant" :name="row.displayName" :website="row.siteHost" />
    <span class="text">
      <span class="name">{{ row.displayName }}</span>
      <span class="subtitle">{{ subtitle }}</span>
    </span>
    <button v-if="row.hasPrimaryCopy" type="button" class="icon-button" :aria-label="t('a11y.copy')" :title="t('a11y.copy')" @click.stop="copyPrimary">
      <QasaIcon name="copy" :size="17" />
    </button>
    <span @pointerdown.capture="prepareMenu" @keydown.enter.capture="prepareMenu">
      <Menu :items="menuItems" :label="t('a11y.moreActions')">
        <QasaIcon name="more-vertical" :size="16" />
      </Menu>
    </span>
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
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 17px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
  cursor: pointer;
  transition:
    border-color var(--motion-fast),
    background-color var(--motion-fast);
}

.row:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--stroke));
}

.row.selected {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 6%, var(--card-background));
}

.text {
  display: flex;
  flex-direction: column;
  gap: 2px;
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
  font-size: 16px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.icon-button {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
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
