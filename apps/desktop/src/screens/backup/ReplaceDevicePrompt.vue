<script setup lang="ts">
// "Replace your old device?": a restored backup hit the device limit, and its restore key
// lets this computer take the place of the device that made it. Opened automatically
// after a setup restore and from the Linked devices banner. The key stays in the core.
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { account, confirmReplace, dismissReplace } from '@/account/store'
import { useT } from '@/prefs'

const t = useT()
</script>

<template>
  <ConfirmDialog
    :open="account.replace.open"
    :title="t('backup.replace.title')"
    icon="sync"
    :message="t('backup.replace.bodyFmt', account.status?.replaceableDeviceName ?? '')"
    :confirm-label="t('backup.replace.button')"
    :cancel-label="t('backup.reminder.dismiss')"
    :busy="account.replace.busy"
    :error="account.replace.failed ? t('backup.replace.failed') : null"
    destructive
    @confirm="confirmReplace"
    @cancel="dismissReplace"
  />
</template>
