<script setup lang="ts">
// iOS `LinkedDevicesView.deviceCard`: platform icon, name with THIS DEVICE / PRIMARY
// badges, platform, location, activation date, and the action row. Premium gets
// Sign out + Unlink chips; Free gets a lock (the card opens Upgrade) and, on another
// device, a single Unlink to free the slot. A 4pt accent bar marks this device.
import { computed } from 'vue'

import PillBadge from '@/components/PillBadge.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'
import type { RemoteDevice } from '@/account/types'
import { formatLocale, useT } from '@/prefs'

const props = defineProps<{
  device: RemoteDevice
  isCurrent: boolean
  premium: boolean
  /** Primary with other devices still linked: Unlink is blocked (iOS `cannotUnlinkPrimary`). */
  primaryBlocked: boolean
  busy: boolean
}>()
const emit = defineEmits<{ upgrade: []; logout: []; unlink: []; blocked: [] }>()
const t = useT()

/** iOS `platformIcon`: a phone for iOS and Android, a laptop for macOS, else a desktop. */
function platformIcon(platform: string): QasaIconName {
  const p = platform.toLowerCase()
  if (p.includes('ios') || p.includes('iphone') || p.includes('ipad') || p.includes('android')) return 'device'
  if (p.includes('mac')) return 'laptop'
  return 'desktop'
}

const icon = computed(() => platformIcon(props.device.platform))
const location = computed(() => props.device.location?.trim() || null)

/** iOS `formatDate(activatedAt)`: medium date, short time; hidden when unparseable. */
const activated = computed(() => {
  const iso = props.device.created_at
  if (!iso) return null
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return null
  return new Date(ms).toLocaleString(formatLocale(), { dateStyle: 'medium', timeStyle: 'short' })
})

function onUnlink() {
  if (props.primaryBlocked) emit('blocked')
  else emit('unlink')
}
</script>

<template>
  <article class="device" :class="{ current: isCurrent }">
    <component
      :is="premium ? 'div' : 'button'"
      :type="premium ? undefined : 'button'"
      class="summary"
      :class="{ tappable: !premium }"
      @click="!premium && emit('upgrade')"
    >
      <span class="platform" aria-hidden="true"><QasaIcon :name="icon" :size="16" /></span>
      <span class="text">
        <span class="title-line">
          <span class="name">{{ device.device_name }}</span>
          <PillBadge v-if="isCurrent" :text="t('devices.thisDevice')" color="var(--accent-dark)" solid />
          <PillBadge v-if="device.is_primary" :text="t('devices.primary')" color="var(--gold-muted)" />
        </span>
        <span class="meta">{{ device.platform.toUpperCase() }}</span>
        <span v-if="location" class="meta location"><QasaIcon name="address" :size="13" />{{ location }}</span>
        <span v-if="activated" class="activated">{{ t('devices.activatedAt', activated) }}</span>
      </span>
      <QasaIcon v-if="!premium" name="lock" :size="12" class="lock" />
    </component>

    <div v-if="premium" class="chips">
      <button type="button" class="chip" :disabled="busy" @click="emit('logout')">
        <QasaIcon name="sign-out" :size="15" /><span>{{ t('devices.logout') }}</span>
      </button>
      <button
        type="button"
        class="chip destructive"
        :class="{ blocked: primaryBlocked }"
        :aria-disabled="primaryBlocked || undefined"
        :disabled="busy"
        @click="onUnlink"
      >
        <QasaIcon name="link" :size="15" /><span>{{ t('devices.unlink') }}</span>
      </button>
    </div>
    <button v-else-if="!isCurrent" type="button" class="qasa-button secondary" :disabled="busy" @click="emit('unlink')">
      <QasaIcon name="link" :size="15" /><span>{{ t('devices.unlink') }}</span>
    </button>
  </article>
</template>

<style scoped>
.device {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 21px;
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.device.current::before {
  content: '';
  position: absolute;
  inset-block: 0;
  inset-inline-start: 0;
  width: 4px;
  background: var(--accent);
}

.summary {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 0;
  padding-inline-start: 8px;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: start;
}

.summary.tappable {
  cursor: pointer;
}

/* iOS `IconBadge`. */
.platform {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.platform svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.text {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.title-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.name {
  font-size: var(--type-field-size);
  font-weight: 700;
  color: var(--text-primary);
}

.meta {
  font-size: var(--type-row-value-size);
  color: var(--text-secondary);
}

.location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* iOS `Font.qasaLabel(12)`: 13 medium. */
.activated {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  color: var(--text-tertiary);
}

.lock {
  flex-shrink: 0;
  color: var(--gold-muted);
}

.chips {
  display: flex;
  gap: 10px;
}

/* iOS `actionChip`: 48 tall, card fill, 1px stroke (danger 35% when destructive), control
   radius, 14 semibold, `PressableStyle`. */
.chip {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--space-button-icon-gap);
  min-width: 0;
  height: var(--size-button-height);
  padding: 0 8px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  background: var(--card-background);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.chip svg {
  flex-shrink: 0;
  width: var(--size-button-icon-glyph);
  height: var(--size-button-icon-glyph);
}

.chip span {
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip:active:not(:disabled) {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.chip.destructive {
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
  color: var(--danger);
}

.chip.blocked {
  color: var(--text-tertiary);
}

.chip:disabled {
  cursor: default;
  opacity: var(--motion-disabled-opacity);
}
</style>
