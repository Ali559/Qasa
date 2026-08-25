<script setup lang="ts">
import { computed, ref } from 'vue'
import { Download, Filter } from '@lucide/vue'
import FadeIn from '@/components/FadeIn.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { events, resources, telemetryMetrics } from '@/data/mock'
import { toast } from 'vue-sonner'

const menaOnly = ref(true)
const hideDebug = ref(false)

const visibleEvents = computed(() =>
  hideDebug.value ? events.filter((e) => e.level !== 'DEBUG') : events,
)

function exportEvents() {
  toast.success('Telemetry export ready', {
    description: `${visibleEvents.value.length} events included.`,
  })
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-end">
      <Badge variant="outline" class="gap-1.5 px-3 py-1.5">
        <span class="bg-primary size-1.5 animate-pulse rounded-full" />
        SYSTEM OPERATIONAL
      </Badge>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <FadeIn v-for="(m, i) in telemetryMetrics" :key="m.label" :delay="i * 0.05">
        <Card>
          <CardHeader class="pb-2">
            <CardDescription class="font-mono text-[11px] tracking-wider uppercase">
              {{ m.label }}
            </CardDescription>
            <CardTitle class="text-3xl font-semibold tracking-tight">{{ m.value }}</CardTitle>
          </CardHeader>
          <CardContent>
            <Badge :variant="m.tone === 'warn' ? 'outline' : 'secondary'" class="font-mono text-[11px]">
              {{ m.meta }}
            </Badge>
          </CardContent>
        </Card>
      </FadeIn>
    </div>

    <div class="grid gap-4 xl:grid-cols-3">
      <FadeIn :delay="0.15" class="xl:col-span-2">
        <Card class="h-full">
          <CardHeader class="flex flex-row items-center justify-between gap-3">
            <div>
              <CardTitle>Node Distribution & Load</CardTitle>
              <CardDescription>Threat and latency overview</CardDescription>
            </div>
            <div class="flex items-center gap-2">
              <Label for="region" class="text-muted-foreground text-xs">{{ menaOnly ? 'MENA' : 'GLOBAL' }}</Label>
              <Switch
                id="region"
                :checked="menaOnly"
                @update:checked="(v: boolean) => (menaOnly = v)"
              />
            </div>
          </CardHeader>
          <CardContent>
            <div class="from-card to-muted/40 relative overflow-hidden rounded-xl border bg-gradient-to-br p-6">
              <div class="absolute inset-0 bg-[radial-gradient(circle_at_55%_45%,oklch(0.45_0.08_170/0.2),transparent_45%)]" />
              <div class="relative grid gap-3 sm:grid-cols-3">
                <div class="bg-background/70 rounded-lg border p-3 backdrop-blur">
                  <p class="text-muted-foreground text-xs">Threats (1h)</p>
                  <p class="text-xl font-semibold">{{ menaOnly ? '12,491' : '28,104' }}</p>
                </div>
                <div class="bg-background/70 rounded-lg border p-3 backdrop-blur">
                  <p class="text-muted-foreground text-xs">Active Connections</p>
                  <p class="text-xl font-semibold">{{ menaOnly ? '45,103' : '91,220' }}</p>
                </div>
                <div class="bg-background/70 rounded-lg border p-3 backdrop-blur">
                  <p class="text-muted-foreground text-xs">Latency</p>
                  <p class="text-xl font-semibold">{{ menaOnly ? '28ms' : '42ms' }}</p>
                </div>
              </div>
              <div class="text-muted-foreground relative mt-6 flex gap-4 font-mono text-[11px]">
                <span class="inline-flex items-center gap-2"><i class="size-2 rounded-full bg-emerald-400" /> Optimal (&lt;50ms)</span>
                <span class="inline-flex items-center gap-2"><i class="size-2 rounded-full bg-amber-400" /> Moderate (50-150ms)</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </FadeIn>

      <FadeIn :delay="0.2">
        <Card class="h-full">
          <CardHeader>
            <CardTitle>Resource Usage</CardTitle>
            <CardDescription>Cluster capacity</CardDescription>
          </CardHeader>
          <CardContent class="space-y-5">
            <div v-for="r in resources" :key="r.label" class="space-y-2">
              <div class="flex justify-between text-sm">
                <span class="text-muted-foreground">{{ r.label }}</span>
                <span class="font-mono text-xs">{{ r.value }}</span>
              </div>
              <Progress :model-value="r.width" class="h-2" />
            </div>
          </CardContent>
        </Card>
      </FadeIn>
    </div>

    <FadeIn :delay="0.25">
      <Card>
        <CardHeader class="flex flex-row items-center justify-between gap-3">
          <div>
            <CardTitle>Live Event Telemetry</CardTitle>
            <CardDescription>Streaming service logs</CardDescription>
          </div>
          <div class="flex items-center gap-2">
            <Button variant="outline" size="sm" @click="hideDebug = !hideDebug">
              <Filter />
              {{ hideDebug ? 'Show debug' : 'Hide debug' }}
            </Button>
            <Button variant="outline" size="sm" @click="exportEvents">
              <Download />
              Export
            </Button>
          </div>
        </CardHeader>
        <Separator />
        <CardContent class="p-0">
          <ScrollArea class="h-[280px]">
            <ul class="divide-y font-mono text-xs">
              <li
                v-for="event in visibleEvents"
                :key="event.time + event.message"
                class="flex flex-col gap-1 px-6 py-3 sm:flex-row sm:gap-3"
                :class="{ 'bg-destructive/5': event.level === 'ERROR' }"
              >
                <span class="text-muted-foreground shrink-0">{{ event.time }}</span>
                <span
                  class="w-16 shrink-0"
                  :class="{
                    'text-sky-300': event.level === 'INFO',
                    'text-amber-300': event.level === 'WARN',
                    'text-emerald-300': event.level === 'DEBUG',
                    'text-red-300': event.level === 'ERROR',
                  }"
                >
                  [{{ event.level }}]
                </span>
                <span>{{ event.message }}</span>
              </li>
            </ul>
          </ScrollArea>
        </CardContent>
      </Card>
    </FadeIn>
  </div>
</template>
