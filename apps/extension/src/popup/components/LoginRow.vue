<script setup lang="ts">
// A login row like the desktop's `VaultRow` (iOS `VaultItemRow`): site icon or letter tile, title and
// username, then the copy actions. The desktop does the copying and clears the clipboard;
// the popup never holds the secret.
import { KeyRound, ShieldCheck, User } from 'lucide-vue-next'

import { useT } from '@/prefs'
import ItemTile from '@/screens/vault/ItemTile.vue'
import type { CopyField, LoginMatch } from '@ext/shared/messages'

defineProps<{ item: LoginMatch; canFill: boolean }>()
const emit = defineEmits<{ copy: [field: CopyField]; fill: [] }>()
const t = useT()
</script>

<template>
  <div class="row" data-testid="login-row">
    <ItemTile variant="login" :name="item.title" :website="item.website" :size="40" />
    <span class="text">
      <span class="name" dir="auto">{{ item.title }}</span>
      <span class="subtitle" dir="auto">{{ item.username }}</span>
    </span>
    <span class="actions">
      <button v-if="item.username" type="button" class="icon-button" :aria-label="t('copy.username')" :title="t('copy.username')" @click="emit('copy', 'username')">
        <User :size="17" />
      </button>
      <button type="button" class="icon-button" :aria-label="t('copy.password')" :title="t('copy.password')" @click="emit('copy', 'password')">
        <KeyRound :size="17" />
      </button>
      <button v-if="item.hasTotp" type="button" class="icon-button" :aria-label="t('copy.totp')" :title="t('copy.totp')" @click="emit('copy', 'totp')">
        <ShieldCheck :size="17" />
      </button>
      <button v-if="canFill" type="button" class="fill" @click="emit('fill')">{{ t('ext.fill') }}</button>
    </span>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
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
  font-size: 15px;
  font-weight: 600;
}

.subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.icon-button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.icon-button:hover {
  background: var(--card-muted);
  color: var(--accent);
}

.fill {
  margin-inline-start: 4px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.fill:active {
  transform: scale(0.97);
}
</style>
