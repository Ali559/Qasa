<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Eye, EyeOff, KeyRound, Mail, ShieldCheck } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import FadeIn from '@/components/FadeIn.vue'
import { toast } from 'vue-sonner'
import { adminLogin } from '@/api/admin'
import { isLiveApiEnabled, setAuthToken } from '@/api/client'

const router = useRouter()
const route = useRoute()
const email = ref('admin@qasa.io')
const password = ref('admin123')
const showPassword = ref(false)
const remember = ref(true)
const loading = ref(false)

const canSubmit = computed(() => email.value.trim().length > 3 && password.value.length > 3)

async function authenticate() {
  if (!canSubmit.value) {
    toast.error('Enter admin identity and security key')
    return
  }
  loading.value = true
  try {
    if (isLiveApiEnabled()) {
      const res = await adminLogin(email.value.trim(), password.value)
      setAuthToken(res.token)
    } else {
      await new Promise((r) => setTimeout(r, 650))
    }
    loading.value = false
    toast.success('Authenticated', { description: 'Welcome back, System Admin.' })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (err) {
    loading.value = false
    toast.error('Authentication failed', {
      description: err instanceof Error ? err.message : 'Invalid credentials',
    })
  }
}
</script>

<template>
  <div class="relative flex min-h-svh items-center justify-center overflow-hidden p-6">
    <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.35_0.08_170/0.25),transparent_55%)]" />
    <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(to_bottom,transparent,var(--background)_80%)]" />

    <FadeIn class="relative z-10 w-full max-w-md">
      <Card class="border-border/70 shadow-2xl shadow-black/20">
        <CardHeader class="items-center text-center">
          <div class="bg-primary/15 text-primary mb-2 flex size-12 items-center justify-center rounded-xl border border-primary/20">
            <ShieldCheck class="size-6" />
          </div>
          <CardTitle class="text-2xl tracking-tight">QASA ADMIN</CardTitle>
          <CardDescription>Secure console access for authorized personnel.</CardDescription>
          <Badge variant="outline" class="mt-2 gap-1.5">
            <span class="bg-primary size-1.5 animate-pulse rounded-full" />
            PLATFORM SECURE
          </Badge>
        </CardHeader>

        <CardContent>
          <form class="grid gap-4" @submit.prevent="authenticate">
            <div class="grid gap-2">
              <Label for="email" class="inline-flex items-center gap-2">
                <Mail class="text-muted-foreground size-3.5" />
                Admin Identity
              </Label>
              <Input
                id="email"
                v-model="email"
                type="email"
                autocomplete="username"
                placeholder="admin@qasa.io"
              />
            </div>

            <div class="grid gap-2">
              <Label for="password" class="inline-flex items-center gap-2">
                <KeyRound class="text-muted-foreground size-3.5" />
                Security Key
              </Label>
              <div class="relative">
                <Input
                  id="password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  class="pr-10"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  class="absolute top-1/2 right-1.5 -translate-y-1/2"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff v-if="showPassword" class="size-4" />
                  <Eye v-else class="size-4" />
                </Button>
              </div>
            </div>

            <label class="flex items-center gap-2 text-sm">
              <Checkbox
                :checked="remember"
                @update:checked="(v: boolean | 'indeterminate') => (remember = v === true)"
              />
              Keep this workstation trusted
            </label>

            <Button type="submit" class="w-full" :disabled="loading || !canSubmit">
              {{ loading ? 'Authenticating…' : 'Authenticate' }}
            </Button>
          </form>
        </CardContent>

        <CardFooter class="text-muted-foreground justify-center text-center font-mono text-[11px] tracking-wide uppercase">
          Authorized personnel only. All attempts are logged.
        </CardFooter>
      </Card>
    </FadeIn>
  </div>
</template>
