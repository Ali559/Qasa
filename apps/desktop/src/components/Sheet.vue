<script setup lang="ts">
// The iOS `.sheet` on desktop (design/README.md rule 4): the page background, `--radius-sheet`
// (28) corners, no stroke and no elevation tint, over a scrim. It closes with Esc or a click
// outside (no Cancel / Close / Done in the header: nothing to drag on desktop, so no grabber
// either); focus stays inside while open. `title` draws the shared `SheetHeader`: 17
// semibold, centered, 20 from the top edge, 16 to the content. Content sizes to fit, at most
// 92% of the window. `placement="bottom"` slides up from the bottom edge of the window or
// popup (confirmations, short forms); the default is a centered dialog for the long editors.
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

import SheetHeader from './SheetHeader.vue'

const props = withDefaults(defineProps<{ open: boolean; label: string; title?: string; width?: number; placement?: 'center' | 'bottom' }>(), {
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
          <SheetHeader v-if="title" :title="title"><template v-if="$slots['header-trailing']" #trailing><slot name="header-trailing" /></template></SheetHeader>
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
  padding: var(--space-screen-padding);
  background: var(--scrim);
}

.scrim.bottom {
  place-items: end center;
  padding: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 92vh;
  overflow: hidden;
  border-radius: var(--radius-sheet);
  background: var(--background);
  color: var(--text-primary);
  outline: none;
}

/* Attached to the bottom edge: only the top corners round, as an iOS sheet. */
.bottom .panel {
  border-end-start-radius: 0;
  border-end-end-radius: 0;
}

.sheet-enter-active,
.sheet-leave-active,
.bottom-sheet-enter-active,
.bottom-sheet-leave-active {
  transition: opacity var(--motion-standard);
}

.sheet-enter-active .panel,
.sheet-leave-active .panel,
.bottom-sheet-enter-active .panel,
.bottom-sheet-leave-active .panel {
  transition: transform var(--motion-standard);
}

.sheet-enter-from,
.sheet-leave-to,
.bottom-sheet-enter-from,
.bottom-sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .panel,
.sheet-leave-to .panel {
  transform: scale(var(--motion-press-scale));
}

.bottom-sheet-enter-from .panel,
.bottom-sheet-leave-to .panel {
  transform: translateY(100%);
}

/* Reduce motion: slides become fades (`motion.fade`). */
@media (prefers-reduced-motion: reduce) {
  .sheet-enter-active,
  .sheet-leave-active,
  .bottom-sheet-enter-active,
  .bottom-sheet-leave-active {
    transition: opacity var(--motion-fade);
  }

  .sheet-enter-from .panel,
  .sheet-leave-to .panel,
  .bottom-sheet-enter-from .panel,
  .bottom-sheet-leave-to .panel {
    transform: none;
  }
}
</style>
