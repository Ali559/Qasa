<script setup lang="ts">
// iOS `OnboardingTourView`, direction B "Live demo" (design/README.md rule 6d): four pages
// about free features, each playing a tiny version of the app above its title and body.
// Story bars and Skip at the top, Next (Continue on the last page) at the bottom. A page
// moves on by itself after `--motion-tour-dwell`; clicking the trailing or leading half,
// swiping, or the arrow keys go forward or back, mirrored in RTL. With reduce motion on,
// pages fade, the demos show their end state and nothing moves on by itself.
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'

import BottomActionBar from '@/components/BottomActionBar.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { isRtl } from '@/i18n'
import { prefs, useT } from '@/prefs'
import CodeDemo from './demos/CodeDemo.vue'
import HealthDemo from './demos/HealthDemo.vue'
import SignUpDemo from './demos/SignUpDemo.vue'
import VaultDemo from './demos/VaultDemo.vue'
import { prefersReducedMotion, tokenMs } from './motion'
import { advance, gestureDelta, keyDelta, TOUR_PAGE_COUNT } from './welcome'

const emit = defineEmits<{ skip: []; done: [] }>()
const t = useT()

/** The desktop's own copy where the phone's would be wrong ("this computer", the extension). */
const PAGES: { demo: Component; title: string; body: string }[] = [
  { demo: VaultDemo, title: 'onboarding.tour1Title', body: 'onboarding.tour1BodyDesktop' },
  { demo: SignUpDemo, title: 'onboarding.tour2Title', body: 'onboarding.tour2BodyDesktop' },
  { demo: CodeDemo, title: 'onboarding.tour3Title', body: 'onboarding.tour3Body' },
  { demo: HealthDemo, title: 'onboarding.tour4Title', body: 'onboarding.tour4Body' },
]

const page = ref(0)
const reduceMotion = prefersReducedMotion()
const rtl = computed(() => isRtl(prefs.language))
const isLast = computed(() => page.value === TOUR_PAGE_COUNT - 1)
const current = computed(() => PAGES[page.value])

function move(delta: number) {
  const next = advance(page.value, delta)
  if (next === 'done') emit('done')
  else if (next !== 'stay') page.value = next
}

// The story bar: fills over `tourDwell`, then the page moves on. Reduce motion: none.
let dwellTimer: ReturnType<typeof setTimeout> | undefined
function startDwell() {
  clearTimeout(dwellTimer)
  if (reduceMotion) return
  dwellTimer = setTimeout(() => move(1), tokenMs('--motion-tour-dwell', 4.2))
}
watch(page, startDwell)

// A click or a swipe on the page (not on a button).
let down: { x: number; y: number } | null = null
const stage = ref<HTMLElement | null>(null)

function onPointerDown(e: PointerEvent) {
  if ((e.target as HTMLElement).closest('button')) return
  down = { x: e.clientX, y: e.clientY }
}

function onPointerUp(e: PointerEvent) {
  if (!down || !stage.value) return
  const rect = stage.value.getBoundingClientRect()
  const delta = gestureDelta({
    dx: e.clientX - down.x,
    dy: e.clientY - down.y,
    x: e.clientX,
    left: rect.left,
    width: rect.width,
    rtl: rtl.value,
  })
  down = null
  if (delta) move(delta)
}

function onKey(e: KeyboardEvent) {
  if (e.defaultPrevented || e.altKey || e.metaKey || e.ctrlKey) return
  const delta = keyDelta(e.key, rtl.value)
  if (!delta) return
  e.preventDefault()
  move(delta)
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  startDwell()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  clearTimeout(dwellTimer)
})
</script>

<template>
  <div class="tour">
    <div class="top">
      <div class="bars" aria-hidden="true">
        <i v-for="i in TOUR_PAGE_COUNT" :key="i" :class="i - 1 < page ? 'done' : i - 1 === page ? (reduceMotion ? 'done' : 'run') : ''" />
      </div>
      <button type="button" class="skip" @click="emit('skip')">{{ t('common.skip') }}</button>
    </div>

    <main
      ref="stage"
      class="stage"
      @pointerdown="onPointerDown"
      @pointerup="onPointerUp"
      @pointercancel="down = null"
    >
      <Transition name="page" mode="out-in">
        <section :key="page" class="page" aria-live="polite">
          <div class="demo"><component :is="current.demo" /></div>
          <div class="copy">
            <h1 class="title">{{ t(current.title) }}</h1>
            <p class="body">{{ t(current.body) }}</p>
          </div>
        </section>
      </Transition>
    </main>

    <BottomActionBar :max-width="520">
      <PrimaryButton :title="t(isLast ? 'common.continue' : 'onboarding.next')" @click="move(1)" />
    </BottomActionBar>
  </div>
</template>

<style scoped>
.tour {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--background);
  user-select: none;
}

/* iOS: an HStack(spacing: 12) of the bars and Skip, screen padding, 8 from the top. */
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 560px;
  margin-inline: auto;
  padding: 8px var(--space-screen-padding) 0;
}

.bars {
  display: flex;
  flex: 1;
  gap: 4px;
}

.bars i {
  flex: 1;
  height: 3px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--stroke-chrome);
}

.bars i::after {
  content: '';
  display: block;
  width: 0;
  height: 100%;
  background: var(--accent);
}

.bars i.done::after {
  width: 100%;
}

.bars i.run::after {
  animation: fill calc(var(--motion-tour-dwell) * 1s) linear forwards;
}

@keyframes fill {
  to {
    width: 100%;
  }
}

.skip {
  min-height: 44px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.stage {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  cursor: pointer;
  touch-action: pan-y;
}

.page {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 560px;
  min-height: 100%;
  margin-inline: auto;
  padding: 12px var(--space-screen-padding) 16px;
}

/* The demo sits centred in the space above the copy (iOS Spacers 12 / 20). */
.demo {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  padding-bottom: 20px;
}

.copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  text-wrap: balance;
}

.body {
  margin: 0;
  font-size: 15px;
  color: var(--text-secondary);
}

/* Pages cross-fade (`motion.fade`); the copy rises 14 into place, the body 0.08 later. */
.page-enter-active,
.page-leave-active {
  transition: opacity var(--motion-fade);
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: no-preference) {
  .title,
  .body {
    animation: rise 400ms var(--ease-ease-out) both;
  }

  .body {
    animation-delay: 80ms;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
}
</style>
