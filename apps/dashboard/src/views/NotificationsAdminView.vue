<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Send } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import FilterChips from '@/components/kit/FilterChips.vue'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  createAdminNotification,
  fetchAdminNotifications,
  type AdminNotificationRow,
} from '@/api/admin'
import { canWrite, isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const rows = ref<AdminNotificationRow[]>([])
const title_en = ref('')
const body_en = ref('')
const title_ckb = ref('')
const title_ar = ref('')
const body_ckb = ref('')
const body_ar = ref('')
const loading = ref(false)

type Audience = 'all' | 'subscribers' | 'free'
const audience = ref<Audience>('all')
const audienceOptions = [
  { value: 'all' as const, label: 'Everyone' },
  { value: 'subscribers' as const, label: 'Premium' },
  { value: 'free' as const, label: 'Free' },
]
const audienceLabel: Record<string, string> = {
  all: 'everyone',
  subscribers: 'Premium users',
  free: 'Free users',
}
const sendLabel = computed(() => `Send to ${audienceLabel[audience.value]}`)

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
    toast.error('Add an English title and message before sending')
    return
  }
  if (!isLiveApiEnabled()) {
    toast.message('Sent (demo mode, nothing delivered)')
    return
  }
  try {
    const res = await createAdminNotification({
      title_en: title_en.value,
      body_en: body_en.value,
      title_ckb: title_ckb.value || undefined,
      title_ar: title_ar.value || undefined,
      body_ckb: body_ckb.value || undefined,
      body_ar: body_ar.value || undefined,
      audience: audience.value,
    })
    toast.success(`Sent to ${res.notified_accounts} ${audienceLabel[audience.value]}`, {
      description: 'In their inboxes now; device pushes go out within a minute where FCM is set up.',
    })
    title_en.value = ''
    body_en.value = ''
    await load()
  } catch {
    toast.error('Not sent. Check your connection and try again.')
  }
}

onMounted(load)

type Locale = 'en' | 'ckb' | 'ar'
const previewLocale = ref<Locale>('en')
const localeOptions = [
  { value: 'en' as const, label: 'English' },
  { value: 'ckb' as const, label: 'Kurdish' },
  { value: 'ar' as const, label: 'Arabic' },
]

// Mirrors the app: a missing translation falls back to English.
const preview = computed(() => {
  const pick = (en: string, ckb: string, ar: string) =>
    (previewLocale.value === 'ckb' ? ckb : previewLocale.value === 'ar' ? ar : '') || en
  return {
    title: pick(title_en.value, title_ckb.value, title_ar.value) || 'Your title appears here',
    body: pick(body_en.value, body_ckb.value, body_ar.value) || 'And the message goes underneath it.',
    rtl: previewLocale.value !== 'en' && Boolean(previewLocale.value === 'ckb' ? title_ckb.value : title_ar.value),
  }
})

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <div class="grid gap-6 xl:grid-cols-[1fr_380px]">
    <Card class="gap-6 py-6">
      <CardHeader class="px-6">
        <CardTitle class="text-xl font-bold">New notification</CardTitle>
        <CardDescription>Goes to every user's inbox. English is required; add Kurdish and Arabic when you can.</CardDescription>
      </CardHeader>
      <CardContent class="space-y-6 px-6">
        <fieldset class="grid gap-3">
          <legend class="mb-2 text-sm font-semibold">English</legend>
          <div class="grid gap-2">
            <Label for="title-en">Title</Label>
            <Input id="title-en" v-model="title_en" />
          </div>
          <div class="grid gap-2">
            <Label for="body-en">Message</Label>
            <Textarea id="body-en" v-model="body_en" rows="3" />
          </div>
        </fieldset>

        <div class="grid gap-6 md:grid-cols-2">
          <fieldset class="grid gap-3" dir="rtl" lang="ckb">
            <legend class="mb-2 text-sm font-semibold" dir="ltr">Kurdish (Sorani), optional</legend>
            <Input v-model="title_ckb" aria-label="Kurdish title" placeholder="ناونیشان" />
            <Textarea v-model="body_ckb" aria-label="Kurdish message" rows="3" placeholder="پەیام" />
          </fieldset>
          <fieldset class="grid gap-3" dir="rtl" lang="ar">
            <legend class="mb-2 text-sm font-semibold" dir="ltr">Arabic, optional</legend>
            <Input v-model="title_ar" aria-label="Arabic title" placeholder="العنوان" />
            <Textarea v-model="body_ar" aria-label="Arabic message" rows="3" placeholder="الرسالة" />
          </fieldset>
        </div>

        <div class="grid gap-2">
          <Label>Who gets it</Label>
          <FilterChips v-model="audience" :options="audienceOptions" label="Audience" />
        </div>

        <div class="flex flex-wrap gap-2">
          <Button v-if="canWrite()" size="lg" class="rounded-xl" @click="send"><Send /> {{ sendLabel }}</Button>
          <p v-else class="text-muted-foreground text-sm">Your role is read-only, so you can't send notifications.</p>
        </div>
      </CardContent>
    </Card>

    <div class="space-y-6">
      <section class="phone bg-sidebar text-sidebar-foreground rounded-[2rem] p-5" aria-label="Notification preview">
        <div class="mb-6 flex items-center justify-between">
          <span class="text-sm font-semibold">Preview</span>
          <FilterChips v-model="previewLocale" :options="localeOptions" label="Preview language" class="[&_button]:h-7 [&_button]:px-2.5 [&_button]:text-xs" />
        </div>
        <p class="font-heading text-center text-5xl font-bold opacity-90">9:41</p>
        <div
          class="bg-card text-card-foreground mt-6 flex gap-3 rounded-2xl p-3.5 shadow-[0_4px_0_#08071a]"
          :dir="preview.rtl ? 'rtl' : 'ltr'"
        >
          <span class="bg-sun text-sun-ink font-heading grid size-9 shrink-0 place-items-center rounded-xl font-extrabold">q</span>
          <div class="min-w-0">
            <p class="truncate text-sm font-semibold">{{ preview.title }}</p>
            <p class="text-muted-foreground line-clamp-2 text-sm">{{ preview.body }}</p>
          </div>
        </div>
      </section>

      <Card class="gap-4 py-6">
        <CardHeader class="flex flex-row items-center justify-between px-6">
          <CardTitle class="text-lg font-bold">Recently sent</CardTitle>
          <Button variant="ghost" size="icon-sm" :disabled="loading" aria-label="Refresh" @click="load">
            <RefreshCw :class="{ 'animate-spin': loading }" />
          </Button>
        </CardHeader>
        <CardContent class="space-y-3 px-6">
          <div v-for="n in rows" :key="n.id" class="bg-muted/60 rounded-2xl px-4 py-3 text-sm">
            <p class="font-semibold">{{ n.title_en }}</p>
            <p class="text-muted-foreground">{{ n.body_en }}</p>
            <p class="text-muted-foreground mt-1 text-xs">
              {{ formatDate(n.created_at) }} · {{ audienceLabel[n.audience] ?? n.audience }}
            </p>
          </div>
          <p v-if="!rows.length" class="text-muted-foreground text-sm">Nothing sent yet. Your first notification will show up here.</p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.phone {
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.08),
    0 6px 0 #08071a;
  background-image: radial-gradient(circle at 50% 0%, color-mix(in oklch, var(--primary) 45%, transparent), transparent 70%);
}
</style>
