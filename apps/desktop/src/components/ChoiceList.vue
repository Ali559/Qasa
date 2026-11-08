<script setup lang="ts" generic="T extends string">
// The divided, bordered row group used by Welcome (and later Settings pickers):
// iOS card with `Divider().background(stroke)` between rows, a checkmark on the selected
// row and an 8% accent wash behind it.
import { Check } from 'lucide-vue-next'
import type { Component } from 'vue'

export interface Choice<V extends string> {
  value: V
  label: string
  icon?: Component
  /** Set for a language name, so it is laid out in its own direction. */
  dir?: 'ltr' | 'rtl'
  lang?: string
}

defineProps<{ choices: Choice<T>[]; label: string }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="choice-list" role="radiogroup" :aria-label="label">
    <button
      v-for="choice in choices"
      :key="choice.value"
      class="choice"
      :class="{ selected: model === choice.value }"
      type="button"
      role="radio"
      :aria-checked="model === choice.value"
      @click="model = choice.value"
    >
      <component
        :is="choice.icon"
        v-if="choice.icon"
        class="choice-icon"
        :size="18"
        :stroke-width="2.25"
        aria-hidden="true"
      />
      <span class="choice-label" :dir="choice.dir" :lang="choice.lang">{{ choice.label }}</span>
      <span class="spacer" />
      <Transition name="check">
        <Check v-if="model === choice.value" class="check" :size="15" :stroke-width="3" aria-hidden="true" />
      </Transition>
    </button>
  </div>
</template>

<style scoped>
.choice-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
}

.choice {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px;
  border: 0;
  background: transparent;
  text-align: start;
  cursor: pointer;
  transition: background-color var(--motion-fast) ease-in-out;
}

.choice + .choice {
  border-top: 1px solid var(--stroke);
}

.choice:hover {
  background: color-mix(in srgb, var(--accent) 4%, transparent);
}

.choice.selected {
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.choice:focus-visible {
  outline-offset: -2px;
}

.choice-icon {
  width: 28px;
  flex-shrink: 0;
  color: var(--text-secondary);
  transition: color var(--motion-fast) ease-in-out;
}

.selected .choice-icon {
  color: var(--accent);
}

/* The label keeps its own width so a language name in its own direction still sits at
   the row's start; the spacer takes the rest. */
.choice-label {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.spacer {
  flex: 1;
  min-width: 8px;
}

.check {
  flex-shrink: 0;
  color: var(--accent);
}

.check-enter-active,
.check-leave-active {
  transition:
    opacity var(--motion-fast) ease-in-out,
    transform var(--motion-fast) ease-in-out;
}

.check-enter-from,
.check-leave-to {
  opacity: 0;
  transform: scale(0.5);
}
</style>
