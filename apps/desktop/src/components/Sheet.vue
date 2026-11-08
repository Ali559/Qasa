<script setup lang="ts">
// The iOS `.sheet` on desktop: a centered dialog on the chrome surface with the sheet's
// 20pt radius, a scrim, Esc to close and focus kept inside while open.
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ open: boolean; label: string; width?: number }>(), { width: 560 })
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement | null>(null)
let restoreFocus: HTMLElement | null = null

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('close')
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
    <Transition name="sheet">
      <div v-if="open" class="scrim" @mousedown.self="emit('close')" @keydown="onKey">
        <div ref="panel" class="panel" role="dialog" aria-modal="true" :aria-label="label" tabindex="-1" :style="{ maxWidth: `${width}px` }">
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
  background: rgb(10 8 24 / 0.45);
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
  box-shadow: 0 24px 64px rgb(0 0 0 / 0.25);
  outline: none;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity var(--motion-standard) ease-out;
}

.sheet-enter-active .panel,
.sheet-leave-active .panel {
  transition: transform var(--motion-standard) ease-out;
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .panel,
.sheet-leave-to .panel {
  transform: translateY(12px) scale(0.98);
}
</style>
