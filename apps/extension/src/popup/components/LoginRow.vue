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
    <ItemTile variant="login" :name="item.title" :website="item.website" :size="48" />
    <span class="text">
      <span class="name" dir="auto">{{ item.title }}</span>
      <span class="subtitle" dir="auto">{{ item.username }}</span>
    </span>
    <span class="actions">
      <button v-if="item.username" type="button" class="icon-button" :aria-label="t('copy.username')" :title="t('copy.username')" @click="emit('copy', 'username')">
        <User :stroke-width="2.5" />
      </button>
      <button type="button" class="icon-button" :aria-label="t('copy.password')" :title="t('copy.password')" @click="emit('copy', 'password')">
        <KeyRound :stroke-width="2.5" />
      </button>
      <button v-if="item.hasTotp" type="button" class="icon-button" :aria-label="t('copy.totp')" :title="t('copy.totp')" @click="emit('copy', 'totp')">
        <ShieldCheck :stroke-width="2.5" />
      </button>
      <button v-if="canFill" type="button" class="fill" @click="emit('fill')">{{ t('ext.fill') }}</button>
    </span>
  </div>
</template>

<style scoped>
/* iOS `VaultItemRow` in a card: 48 tile, 16 to the text, 16 semibold title, 14 subtitle.
   The popup is 360 wide and carries more actions, so the card pads 12 and the copy
   buttons are `FieldIconButton`s (32, 16 glyph). */
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
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
  font-size: 16px;
  font-weight: 600;
}

.subtitle {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.icon-button {
  display: grid;
  place-items: center;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.icon-button svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.icon-button:hover {
  color: var(--accent);
}

/* A compact accent pill (iOS `HomePill` shape), with `PressableStyle`. */
.fill {
  margin-inline-start: 4px;
  padding: 4px 12px;
  border: 0;
  border-radius: 999px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: var(--type-pill-size);
  font-weight: 600;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.fill:active {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}
</style>
