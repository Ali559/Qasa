<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Eye, EyeOff } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { toast } from 'vue-sonner'
import { adminLogin } from '@/api/admin'
import { vpnNodes } from '@/data/mock'
import { errorMessage, isLiveApiEnabled, setSession } from '@/api/client'

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
    toast.error('Enter your email and password to sign in')
    return
  }
  loading.value = true
  try {
    if (isLiveApiEnabled()) {
      const res = await adminLogin(email.value.trim(), password.value)
      setSession(res)
    } else {
      await new Promise((r) => setTimeout(r, 650))
    }
    loading.value = false
    toast.success('Signed in')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (err) {
    loading.value = false
    toast.error('Sign-in failed', {
      description: errorMessage(err, 'Check your email and password, then try again.'),
    })
  }
}
</script>

<template>
  <div class="grid min-h-svh lg:grid-cols-[1.1fr_1fr]">
    <aside class="bg-primary text-primary-foreground m-3 hidden flex-col rounded-2xl p-10 lg:flex">
      <div class="flex items-center gap-2.5">
        <img src="/favicon.svg" alt="" class="size-9 rounded-lg ring-1 ring-white/25" />
        <span class="text-lg font-semibold">Qasa</span>
      </div>

      <div class="my-auto max-w-md">
        <h1 class="text-5xl leading-[1.1] font-medium">Keep the tunnels humming</h1>
        <p class="mt-5 max-w-sm text-base leading-relaxed opacity-80">
          Subscribers, payments and VPN nodes for Qasa, in one console
        </p>
      </div>

      <!-- The one moment on this page: the real node list, each with its status. -->
      <ul class="grid gap-2" aria-label="VPN nodes">
        <li
          v-for="node in vpnNodes"
          :key="node.name"
          class="flex items-center gap-3 rounded-lg bg-white/8 px-4 py-2.5 text-sm"
        >
          <span class="size-2 rounded-full" :class="node.status === 'online' ? 'bg-ok' : 'bg-warn'" />
          <span class="font-medium">{{ node.city }}</span>
          <span class="ms-auto tabular-nums opacity-70">{{ node.peers }} peers</span>
        </li>
      </ul>
    </aside>

    <main class="flex items-center justify-center p-6 sm:p-10">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex items-center gap-2.5 lg:hidden">
          <img src="/favicon.svg" alt="" class="size-9 rounded-lg" />
          <span class="text-lg font-semibold">Qasa</span>
        </div>

        <h2 class="text-2xl font-medium">Sign in to the admin console</h2>
        <p class="text-muted-foreground mt-2 text-sm">Only Qasa staff accounts can sign in here</p>

        <form class="mt-8 grid gap-5" @submit.prevent="authenticate">
          <div class="grid gap-2">
            <Label for="email">Email</Label>
            <Input
              id="email"
              v-model="email"
              type="email"
              autocomplete="username"
              placeholder="admin@qasa.io"
              class="h-10"
            />
          </div>

          <div class="grid gap-2">
            <Label for="password">Password</Label>
            <div class="relative">
              <Input
                id="password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                class="h-10 pe-11"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                class="absolute top-1/2 end-1.5 -translate-y-1/2"
                :aria-label="showPassword ? 'Hide password' : 'Show password'"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" class="size-4" />
                <Eye v-else class="size-4" />
              </Button>
            </div>
          </div>

          <label class="flex items-center gap-2.5 text-sm">
            <Checkbox v-model="remember" />
            Keep me signed in on this computer
          </label>

          <Button type="submit" size="lg" class="h-10 w-full" :disabled="loading || !canSubmit">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </Button>
        </form>

        <p class="text-muted-foreground mt-8 text-xs">Every sign-in attempt is logged</p>
      </div>
    </main>
  </div>
</template>
