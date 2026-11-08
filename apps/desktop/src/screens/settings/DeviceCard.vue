<script setup lang="ts">
// iOS `LinkedDevicesView.deviceCard`: platform icon, name with THIS DEVICE / PRIMARY
// badges, platform, location, activation date, and the action row. Premium gets
// Sign out + Unlink chips; Free gets a lock (the card opens Upgrade) and, on another
// device, a single Unlink to free the slot. A 4pt accent bar marks this device.
import { Computer, Laptop, Link, Lock, LogOut, MapPin, Monitor, Smartphone, TabletSmartphone, type LucideIcon } from 'lucide-vue-next'
import { computed } from 'vue'

import PillBadge from '@/components/PillBadge.vue'
import type { RemoteDevice } from '@/account/types'
import { prefs, useT } from '@/prefs'

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

/** iOS `platformIcon`, plus the three desktop platforms (plan §5.3). */
function platformIcon(platform: string): LucideIcon {
  const p = platform.toLowerCase()
  if (p.includes('ios') || p.includes('iphone') || p.includes('ipad')) return Smartphone
  if (p.includes('android')) return TabletSmartphone
  if (p.includes('mac')) return Laptop
  if (p.includes('windows')) return Monitor
  return Computer
}

const icon = computed(() => platformIcon(props.device.platform))
const location = computed(() => props.device.location?.trim() || null)

/** iOS `formatDate(activatedAt)`: medium date, short time; hidden when unparseable. */
const activated = computed(() => {
  const iso = props.device.created_at
  if (!iso) return null
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return null
  return new Date(ms).toLocaleString(prefs.language, { dateStyle: 'medium', timeStyle: 'short' })
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
      <component :is="icon" class="platform" :size="28" />
      <span class="text">
        <span class="title-line">
          <span class="name">{{ device.device_name }}</span>
          <PillBadge v-if="isCurrent" :text="t('devices.thisDevice')" color="var(--accent-dark)" solid />
          <PillBadge v-if="device.is_primary" :text="t('devices.primary')" color="var(--gold-muted)" />
        </span>
        <span class="meta">{{ device.platform.toUpperCase() }}</span>
        <span v-if="location" class="meta location"><MapPin :size="13" />{{ location }}</span>
        <span v-if="activated" class="activated">{{ t('devices.activatedAt', activated) }}</span>
      </span>
      <Lock v-if="!premium" class="lock" :size="12" :stroke-width="2.5" />
    </component>

    <div v-if="premium" class="chips">
      <button type="button" class="chip" :disabled="busy" @click="emit('logout')">
        <LogOut :size="15" />{{ t('devices.logout') }}
      </button>
      <button
        type="button"
        class="chip destructive"
        :class="{ blocked: primaryBlocked }"
        :aria-disabled="primaryBlocked || undefined"
        :disabled="busy"
        @click="onUnlink"
      >
        <Link :size="15" />{{ t('devices.unlink') }}
      </button>
    </div>
    <button v-else-if="!isCurrent" type="button" class="free-unlink" :disabled="busy" @click="emit('unlink')">
      <Link :size="16" />{{ t('devices.unlink') }}
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
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
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
  text-align: start;
}

.summary.tappable {
  cursor: pointer;
}

.platform {
  flex-shrink: 0;
  width: 40px;
  color: var(--accent);
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
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.meta {
  font-size: 13px;
  color: var(--text-secondary);
}

.location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.activated {
  font-size: 12px;
  letter-spacing: var(--label-tracking);
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

.chip,
.free-unlink {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px;
  border: 1px solid var(--stroke-chrome);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.free-unlink {
  gap: 8px;
  font-size: 14px;
  font-weight: 400;
}

.chip.destructive {
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
  color: var(--danger);
}

.chip.blocked {
  color: var(--text-tertiary);
}

.chip:hover:not(:disabled),
.free-unlink:hover:not(:disabled) {
  background: var(--card-background-light);
}

.chip:disabled,
.free-unlink:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
