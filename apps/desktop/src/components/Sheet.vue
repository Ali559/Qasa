<script setup lang="ts">
// The iOS `.sheet` on desktop, on the chrome surface over a scrim, with Esc or a scrim click
// to close and focus kept inside while open. `placement="bottom"` is the bottom sheet
// (confirmations, short forms): full width up to `width`, sliding up from the bottom edge
// of the window or popup. The default is a centered dialog for the long editors.
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ open: boolean; label: string; width?: number; placement?: 'center' | 'bottom' }>(), {
  width: 560,
  placement: 'center',
})
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement | null>(null)
let restoreFocus: HTMLElement | null = null

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusables(): HTMLElement[] {
  return [...(panel.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])].filter((el) => el.offsetParent !== null || el === document.activeElement)
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('close')
  } else if (e.key === 'Tab') {
    // Focus trap: Tab and Shift+Tab cycle inside the sheet.
    const items = focusables()
    if (!items.length) {
      e.preventDefault()
      panel.value?.focus()
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    const active = document.activeElement
    if (e.shiftKey && (active === first || active === panel.value)) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && active === last) {
      e.preventDefault()
      first.focus()
    }
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      restoreFocus = document.activeElement as HTMLElement | null
      await nextTick()
      const first = panel.value?.querySelector<HTMLElement>('[autofocus], input, textarea, button, [tabindex="0"]')
      ;(first ?? panel.value)?.focus()
    } else {
      restoreFocus?.focus?.()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => restoreFocus?.focus?.())
</script>

<template>
  <Teleport to="body">
    <Transition :name="placement === 'bottom' ? 'bottom-sheet' : 'sheet'">
      <div v-if="open" class="scrim" :class="placement" @mousedown.self="emit('close')" @keydown="onKey">
        <div ref="panel" class="panel" role="dialog" aria-modal="true" :aria-label="label" tabindex="-1" :style="{ maxWidth: `${width}px` }">
          <span v-if="placement === 'bottom'" class="grabber" aria-hidden="true" />
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.scrim {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--scrim);
}

.scrim.bottom {
  place-items: end center;
  padding: 0;
  padding-top: 24px;
}

.panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: min(92vh, 860px);
  overflow: hidden;
  border: 1px solid var(--stroke-chrome);
  border-radius: 20px;
  background: var(--chrome);
  box-shadow: 0 24px 64px var(--shadow-color);
  outline: none;
}

.bottom .panel {
  max-height: calc(100vh - 24px);
  border-bottom: 0;
  border-radius: 20px 20px 0 0;
  box-shadow: 0 -12px 40px var(--shadow-color);
}

.grabber {
  flex-shrink: 0;
  align-self: center;
  width: 36px;
  height: 4px;
  margin-top: 8px;
  border-radius: 999px;
  background: var(--stroke-chrome);
}

.sheet-enter-active,
.sheet-leave-active,
.bottom-sheet-enter-active,
.bottom-sheet-leave-active {
  transition: opacity var(--motion-standard) ease-out;
}

.sheet-enter-active .panel,
.sheet-leave-active .panel {
  transition: transform var(--motion-standard) ease-out;
}

.bottom-sheet-enter-active .panel,
.bottom-sheet-leave-active .panel {
  transition: transform var(--motion-page) cubic-bezier(0.2, 0.8, 0.2, 1);
}

.sheet-enter-from,
.sheet-leave-to,
.bottom-sheet-enter-from,
.bottom-sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .panel,
.sheet-leave-to .panel {
  transform: translateY(12px) scale(0.98);
}

.bottom-sheet-enter-from .panel,
.bottom-sheet-leave-to .panel {
  transform: translateY(100%);
}
</style>
