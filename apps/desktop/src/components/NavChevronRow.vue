<script setup lang="ts">
// iOS `NavChevronRow`: 28pt icon column, 15 medium title, 12 subtitle, trailing text and
// a forward chevron (mirrored in RTL); destructive rows are red with no chevron.
import { ChevronRight } from 'lucide-vue-next'
import type { Component } from 'vue'

defineProps<{
  icon: Component
  title: string
  subtitle?: string
  trailingText?: string
  destructive?: boolean
  iconColor?: string
  disabled?: boolean
}>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="row" :class="{ destructive }" :disabled="disabled" @click="$emit('click')">
    <component :is="icon" class="icon" :size="18" :style="iconColor ? { color: iconColor } : undefined" aria-hidden="true" />
    <span class="text">
      <span class="title">{{ title }}</span>
      <span v-if="subtitle" class="subtitle">{{ subtitle }}</span>
    </span>
    <span v-if="trailingText" class="trailing">{{ trailingText }}</span>
    <ChevronRight v-if="!destructive" class="chevron mirror-rtl" :size="14" aria-hidden="true" />
  </button>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 0;
  border: 0;
  background: transparent;
  text-align: start;
  cursor: pointer;
}

.row:disabled {
  opacity: 0.5;
  cursor: default;
}

.row:hover:not(:disabled) .title {
  color: var(--accent);
}

.icon {
  width: 28px;
  flex-shrink: 0;
  color: var(--text-primary);
}

.text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  transition: color var(--motion-fast);
}

.subtitle,
.trailing {
  font-size: 12px;
  color: var(--text-secondary);
}

.chevron {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.destructive .icon,
.destructive .title,
.destructive:hover:not(:disabled) .title {
  color: var(--danger);
}
</style>
