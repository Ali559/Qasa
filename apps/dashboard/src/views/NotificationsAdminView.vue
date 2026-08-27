<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw, Send } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  createAdminNotification,
  fetchAdminNotifications,
  type AdminNotificationRow,
} from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const rows = ref<AdminNotificationRow[]>([])
const title_en = ref('')
const body_en = ref('')
const title_ckb = ref('')
const title_ar = ref('')
const body_ckb = ref('')
const body_ar = ref('')
const loading = ref(false)

async function load() {
  if (!isLiveApiEnabled()) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const data = await fetchAdminNotifications()
    rows.value = data.notifications
  } catch {
    toast.error('Failed to load notifications')
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!title_en.value.trim() || !body_en.value.trim()) {
    toast.error('English title and body required')
    return
  }
  if (!isLiveApiEnabled()) {
    toast.message('Mock push queued')
    return
  }
  try {
    await createAdminNotification({
      title_en: title_en.value,
      body_en: body_en.value,
      title_ckb: title_ckb.value || undefined,
      title_ar: title_ar.value || undefined,
      body_ckb: body_ckb.value || undefined,
      body_ar: body_ar.value || undefined,
      audience: 'all',
    })
    toast.success('Notification sent to inbox (+ push when FCM configured)')
    title_en.value = ''
    body_en.value = ''
    await load()
  } catch {
    toast.error('Failed to send')
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <Card>
      <CardHeader>
        <CardTitle>Compose push / inbox notification</CardTitle>
      </CardHeader>
      <CardContent class="grid gap-3 sm:grid-cols-2">
        <Input v-model="title_en" placeholder="Title (EN)" />
        <Input v-model="title_ckb" placeholder="Title (CKB optional)" />
        <Input v-model="title_ar" placeholder="Title (AR optional)" />
        <Input v-model="body_en" placeholder="Body (EN)" class="sm:col-span-2" />
        <Input v-model="body_ckb" placeholder="Body (CKB optional)" />
        <Input v-model="body_ar" placeholder="Body (AR optional)" />
        <div class="flex gap-2 sm:col-span-2">
          <Button size="sm" @click="send"><Send /> Send</Button>
          <Button size="sm" variant="outline" :disabled="loading" @click="load">
            <RefreshCw /> Refresh
          </Button>
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle>Recent</CardTitle>
      </CardHeader>
      <CardContent class="space-y-2">
        <div
          v-for="n in rows"
          :key="n.id"
          class="bg-muted/40 rounded-lg border px-3 py-2 text-sm"
        >
          <p class="font-medium">{{ n.title_en }}</p>
          <p class="text-muted-foreground">{{ n.body_en }}</p>
          <p class="text-muted-foreground font-mono text-xs">{{ n.created_at }}</p>
        </div>
        <p v-if="!rows.length" class="text-muted-foreground text-sm">No notifications yet.</p>
      </CardContent>
    </Card>
  </div>
</template>
