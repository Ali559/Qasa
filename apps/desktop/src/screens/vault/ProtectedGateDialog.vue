<script setup lang="ts">
// The unlock sheet for a protected note copied from the list (see vault/protectedGate.ts):
// the rule-6b `ProtectedUnlockSheet`, answered once for that one copy.
import { finishProtectedAccess, protectedGate } from '@/vault/protectedGate'
import ProtectedUnlockSheet from './ProtectedUnlockSheet.vue'
</script>

<template>
  <ProtectedUnlockSheet :open="protectedGate.open" @unlocked="finishProtectedAccess(true)" @close="finishProtectedAccess(false)" />
</template>
