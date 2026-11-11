<script setup lang="ts">
// A dropdown like the iOS context `Menu`: opens under its trigger (aligned to the end
// edge), closes on Esc, outside click or a pick. Items with `destructive` are red.
import { nextTick, onBeforeUnmount, ref } from 'vue'

export interface MenuItem {
  label: string
  action: () => void
  destructive?: boolean
}

defineProps<{ items: MenuItem[]; label: string }>()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function onDocDown(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) close()
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    document.addEventListener('mousedown', onDocDown)
    await nextTick()
    root.value?.querySelector<HTMLElement>('.item')?.focus()
  } else {
    document.removeEventListener('mousedown', onDocDown)
  }
}

function close() {
  open.value = false
  document.removeEventListener('mousedown', onDocDown)
}

function pick(item: MenuItem) {
  close()
  item.action()
}

function onKey(e: KeyboardEvent) {
  const items = [...(root.value?.querySelectorAll<HTMLElement>('.item') ?? [])]
  const i = items.indexOf(document.activeElement as HTMLElement)
  if (e.key === 'Escape') {
    e.stopPropagation()
    close()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    items[(i + 1) % items.length]?.focus()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    items[(i - 1 + items.length) % items.length]?.focus()
  }
}

onBeforeUnmount(() => document.removeEventListener('mousedown', onDocDown))
</script>

<template>
  <div ref="root" class="menu-root" @keydown="onKey" @click.stop>
    <button type="button" class="trigger" :aria-label="label" :title="label" aria-haspopup="menu" :aria-expanded="open" @click="toggle">
      <slot />
    </button>
    <Transition name="menu">
      <div v-if="open" class="menu" role="menu">
        <button
          v-for="item in items"
          :key="item.label"
          type="button"
          role="menuitem"
          class="item"
          :class="{ destructive: item.destructive }"
          @click="pick(item)"
        >
          {{ item.label }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.menu-root {
  position: relative;
}

.trigger {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.trigger:hover {
  background: var(--card-muted);
}

.menu {
  position: absolute;
  top: calc(100% + 4px);
  inset-inline-end: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  min-width: 220px;
  padding: 6px;
  border: var(--size-stroke) solid var(--stroke-chrome);
  border-radius: var(--radius-control);
  background: var(--chrome);
  box-shadow: 0 12px 32px var(--shadow-color);
}

.item {
  padding: 9px 12px;
  border: 0;
  border-radius: calc(var(--radius-control) - 4px);
  background: transparent;
  color: var(--text-primary);
  font-size: var(--type-body-size);
  font-weight: var(--type-body-weight);
  text-align: start;
  white-space: nowrap;
  cursor: pointer;
}

.item:hover,
.item:focus-visible {
  background: var(--card-muted);
  outline: none;
}

.item.destructive {
  color: var(--danger);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity var(--motion-fast),
    transform var(--motion-fast);
}

@media (prefers-reduced-motion: reduce) {
  .menu-enter-from,
  .menu-leave-to {
    transform: none;
  }
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
