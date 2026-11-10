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
    <aside class="slab bg-primary text-primary-foreground relative isolate hidden overflow-hidden p-10 lg:flex lg:flex-col">
      <div class="flex items-center gap-3">
        <img src="/favicon.svg" alt="" class="size-11 -rotate-6 rounded-2xl shadow-[0_4px_0_var(--sun-lip)]" />
        <span class="font-heading text-xl font-bold">Qasa</span>
      </div>

      <div class="my-auto max-w-md">
        <h1 class="font-heading text-[3.5rem] leading-[0.95] font-extrabold [font-variation-settings:'wdth'_82]">
          Keep the tunnels humming.
        </h1>
        <p class="mt-5 max-w-sm text-base leading-relaxed opacity-80">
          Subscribers, payments and VPN nodes for Qasa, all in one console.
        </p>
      </div>

      <!-- Live node keycaps: the one decorative moment, built from real node data. -->
      <ul class="flex flex-wrap gap-3" aria-label="VPN nodes">
        <li
          v-for="(node, i) in vpnNodes"
          :key="node.name"
          class="keycap bg-card text-card-foreground flex items-center gap-2 rounded-2xl px-4 py-3 text-sm font-semibold"
          :style="{ rotate: `${[-4, 3, -2][i % 3]}deg` }"
        >
          <span class="size-2 rounded-full" :class="node.status === 'online' ? 'bg-ok' : 'bg-warn'" />
          {{ node.city }}
          <span class="text-muted-foreground font-normal">{{ node.peers }} peers</span>
        </li>
      </ul>
    </aside>

    <main class="flex items-center justify-center p-6 sm:p-10">
      <div class="w-full max-w-sm">
        <div class="mb-8 flex items-center gap-3 lg:hidden">
          <img src="/favicon.svg" alt="" class="size-10 -rotate-6 rounded-2xl shadow-[0_3px_0_var(--sun-lip)]" />
          <span class="font-heading text-lg font-bold">Qasa</span>
        </div>

        <h2 class="font-heading text-3xl font-bold">Sign in to the admin console</h2>
        <p class="text-muted-foreground mt-2 text-sm">Only Qasa staff accounts can sign in here.</p>

        <form class="mt-8 grid gap-5" @submit.prevent="authenticate">
          <div class="grid gap-2">
            <Label for="email">Email</Label>
            <Input
              id="email"
              v-model="email"
              type="email"
              autocomplete="username"
              placeholder="admin@qasa.io"
              class="h-11"
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
                class="h-11 pr-11"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                class="absolute top-1/2 right-2 -translate-y-1/2"
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

          <Button type="submit" size="lg" class="h-11 w-full rounded-xl text-base" :disabled="loading || !canSubmit">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </Button>
        </form>

        <p class="text-muted-foreground mt-8 text-xs">Every sign-in attempt is logged.</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.slab {
  border-radius: 0 2.5rem 2.5rem 0;
  box-shadow: 6px 0 0 color-mix(in oklch, var(--primary) 55%, black);
}

.slab::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(rgb(255 255 255 / 0.12) 1.2px, transparent 1.2px);
  background-size: 18px 18px;
  mask-image: linear-gradient(to top right, black, transparent 70%);
}

.keycap {
  box-shadow:
    inset 0 1px 0 var(--sheen),
    0 4px 0 color-mix(in oklch, var(--primary) 55%, black);
}
</style>
