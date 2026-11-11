<script setup lang="ts">
// iOS `PremiumUpgradeView` (embedded in the shell): the plan carousel, auto-renew, the
// wallet rows and the pinned pay button. Desktop change (plan §7): Wayl's hosted page opens
// in the browser; a wallet app link (`fib://…`) is shown as a QR code to scan with the
// phone. Then the payment is polled 30 × 2 s, with "Check again" after a timeout.
import { ArrowLeftRight, Circle, Clock, Lock, RefreshCw } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { accountApi } from '@/account/api'
import { account, isPremium, refreshLicense } from '@/account/store'
import type { PaymentIntent, PricingPlan } from '@/account/types'
import AppMenuHeader from '@/components/AppMenuHeader.vue'
import BottomActionBar from '@/components/BottomActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { openUrl } from '@/opener'
import { useT } from '@/prefs'
import FeatureRow from './FeatureRow.vue'
import FilledCheck from './FilledCheck.vue'
import { errorText, FALLBACK_PLANS, formatIqd, isYearly, pollUntilComplete } from './shared'
import WalletQr from './WalletQr.vue'

defineEmits<{ back: [] }>()
const t = useT()
const isDev = import.meta.env.DEV

interface PaymentMethod {
  name: string
  subtitle: string
  badgeText: string
  gateway: string
}

// One option: Wayl's hosted page offers FIB, Qi Card and the other Iraqi wallets itself.
const methods = computed<PaymentMethod[]>(() => [
  { name: t('premium.wayl'), subtitle: t('premium.waylSub'), badgeText: 'Wayl', gateway: 'wayl' },
])

const plans = ref<PricingPlan[]>(FALLBACK_PLANS)
const autoRenewModes = ref<Record<string, string>>({})
const selectedPlanId = ref('premium_monthly')
const autoRenew = ref(true)
const selectedGateway = ref('wayl')
const isPaying = ref(false)
const statusMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)

/** The wallet link shown as a QR code while waiting for the phone. */
const qrLink = ref<string | null>(null)
/** The payment a "Check again" would look at, after a timeout or failure. */
const recheckPaymentId = ref<string | null>(null)
/** Development only: the stub order a "Simulate payment" would confirm. */
const stubIntent = ref<PaymentIntent | null>(null)

let unmounted = false
onBeforeUnmount(() => (unmounted = true))

const premium = computed(() => account.status?.isPremium ?? false)
const selectedMethod = computed(() => methods.value.find((m) => m.gateway === selectedGateway.value) ?? methods.value[0])
const selectedPlan = computed(() => plans.value.find((p) => p.id === selectedPlanId.value) ?? plans.value[0])

const periodLabel = (plan: PricingPlan) => (isYearly(plan) ? t('premium.periodYear') : t('premium.periodMonth'))
const planTitle = (plan: PricingPlan) => (plan.id === 'premium_yearly' ? t('sub.planYearly') : t('sub.planMonthly'))
/** iOS `localizedFeatures`: always from the catalog, never the API's English. */
function features(plan: PricingPlan): string[] {
  const items = [t('premium.featureDevices'), t('premium.featureBreach'), t('premium.featureVPN')]
  if (isYearly(plan)) items.push(t('premium.featureSave'))
  return items
}

/** Only wallets the server can charge get the "we'll charge it" wording. */
const autoRenewDetail = computed(() =>
  autoRenewModes.value[selectedGateway.value]?.toLowerCase() === 'charge'
    ? t('premium.autoRenewDetailCharge')
    : t('premium.autoRenewDetail'),
)

const payTitle = computed(() =>
  isPaying.value ? t('premium.processing') : t('premium.payVia', formatIqd(selectedPlan.value.amount_iqd), selectedMethod.value.badgeText),
)

onMounted(async () => {
  try {
    const pricing = await accountApi.pricing()
    const sorted = [...pricing.plans].sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
    autoRenewModes.value = pricing.auto_renew ?? {}
    if (sorted.length > 0) {
      plans.value = sorted
      if (sorted.every((p) => p.id !== selectedPlanId.value)) selectedPlanId.value = sorted[0].id
    }
  } catch (e) {
    // iOS keeps the fallback plans when pricing can't be fetched.
    console.error('Could not fetch pricing', e)
  }
})

// Plan carousel: a scroll-snapped row, one card per view, kept in step with the dots.
const track = ref<HTMLElement | null>(null)
function onTrackScroll() {
  const el = track.value
  if (!el || el.clientWidth === 0) return
  const index = Math.round(Math.abs(el.scrollLeft) / el.clientWidth)
  const plan = plans.value[Math.min(index, plans.value.length - 1)]
  if (plan && plan.id !== selectedPlanId.value) selectedPlanId.value = plan.id
}
function selectPlan(id: string) {
  selectedPlanId.value = id
  const index = plans.value.findIndex((p) => p.id === id)
  const card = track.value?.children[index] as HTMLElement | undefined
  card?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
}
function onTrackKey(e: KeyboardEvent) {
  const index = plans.value.findIndex((p) => p.id === selectedPlanId.value)
  const rtl = document.documentElement.dir === 'rtl'
  const step = e.key === 'ArrowRight' ? (rtl ? -1 : 1) : e.key === 'ArrowLeft' ? (rtl ? 1 : -1) : 0
  const next = plans.value[index + step]
  if (step !== 0 && next) {
    e.preventDefault()
    selectPlan(next.id)
  }
}

async function finish(paymentId: string, attempts: number, intervalMs: number) {
  const result = await pollUntilComplete(paymentId, { attempts, intervalMs, cancelled: () => unmounted, t })
  if (unmounted) return
  if (result.outcome === 'success') {
    try {
      if (!isPremium()) await refreshLicense()
    } catch (e) {
      console.error('License refresh failed', e)
    }
    qrLink.value = null
    stubIntent.value = null
    recheckPaymentId.value = null
    if (isPremium()) {
      statusMessage.value = t('premium.activated')
    } else {
      statusMessage.value = null
      errorMessage.value = t('premium.licenseRefreshFailed')
    }
    return
  }
  statusMessage.value = null
  recheckPaymentId.value = paymentId
  errorMessage.value =
    result.error ??
    (result.outcome === 'failed'
      ? t('premium.paymentFailed')
      : t('premium.timedOut'))
  if (result.outcome === 'failed') {
    qrLink.value = null
    stubIntent.value = null
  }
}

/** Wallet payments aren't open yet: the pay button shows "Coming soon" and sends nothing
 *  to the server. `startPayment` stays for when they open (flip this). */
const PAYMENTS_OPEN = false
const comingSoon = ref(false)
function onPay() {
  if (isPaying.value || premium.value) return
  if (PAYMENTS_OPEN) void startPayment()
  else comingSoon.value = true
}

async function startPayment() {
  if (isPaying.value || premium.value) return
  const method = selectedMethod.value
  const plan = selectedPlan.value
  isPaying.value = true
  errorMessage.value = null
  statusMessage.value = null
  qrLink.value = null
  stubIntent.value = null
  recheckPaymentId.value = null
  try {
    const intent = await accountApi.createIntent(method.gateway, plan.id, plan.amount_iqd, autoRenew.value)
    if (/^https:\/\//i.test(intent.deep_link)) {
      await openUrl(intent.deep_link)
    } else {
      qrLink.value = intent.deep_link
      if (isDev) stubIntent.value = intent
    }
    statusMessage.value = t('premium.completeInWallet', method.badgeText)
    await finish(intent.payment_id, 30, 2000)
  } catch (e) {
    errorMessage.value = errorText(t, e)
  } finally {
    isPaying.value = false
  }
}

/** One more status check after a timeout (plan §7). */
async function checkAgain() {
  const id = recheckPaymentId.value
  if (!id || isPaying.value) return
  isPaying.value = true
  errorMessage.value = null
  statusMessage.value = t('premium.processing')
  try {
    await finish(id, 1, 0)
  } finally {
    isPaying.value = false
  }
}

/** iOS Debug `simulateStubPayment`; the poll already running picks up the result. */
async function simulateStub() {
  const intent = stubIntent.value
  if (!isDev || !intent) return
  statusMessage.value = t('premium.simulatingPayment', selectedMethod.value.badgeText)
  try {
    // The server may route to another gateway; its webhook is the one to hit.
    await accountApi.simulateStub(intent.gateway, intent.gateway_order_id)
    stubIntent.value = null
    if (!isPaying.value && recheckPaymentId.value) await checkAgain()
  } catch (e) {
    errorMessage.value = errorText(t, e)
  }
}
</script>

<template>
  <div class="page">
    <AppMenuHeader :title="t('nav.upgrade')" back @back="$emit('back')" />

    <div class="scroll">
      <div class="column">
        <!-- Plan carousel -->
        <div class="carousel">
          <div
            ref="track"
            class="track"
            role="radiogroup"
            :aria-label="t('premium.title')"
            tabindex="0"
            @scroll.passive="onTrackScroll"
            @keydown="onTrackKey"
          >
            <div
              v-for="plan in plans"
              :key="plan.id"
              class="plan-card"
              :class="{ selected: plan.id === selectedPlanId }"
              role="radio"
              :aria-checked="plan.id === selectedPlanId"
              @click="selectPlan(plan.id)"
            >
              <span class="glow" aria-hidden="true" />
              <div class="plan-head">
                <span class="eyebrow">{{ isYearly(plan) ? t('premium.bestValue') : t('premium.upgradeToPro') }}</span>
                <div class="price">
                  <span class="amount">{{ formatIqd(plan.amount_iqd) }}</span>
                  <span class="period">{{ periodLabel(plan) }}</span>
                </div>
                <span class="plan-title">{{ planTitle(plan) }}</span>
              </div>
              <div class="features">
                <FeatureRow v-for="feature in features(plan)" :key="feature" :text="feature" />
              </div>
            </div>
          </div>
          <div class="dots">
            <button
              v-for="plan in plans"
              :key="plan.id"
              type="button"
              class="dot"
              :class="{ on: plan.id === selectedPlanId }"
              :aria-label="planTitle(plan)"
              tabindex="-1"
              @click="selectPlan(plan.id)"
            />
          </div>
        </div>

        <!-- Auto-renew: iOS `ToggleRow` in a card, 14 in. -->
        <div class="row-card">
          <SettingsRow :icon="RefreshCw" :title="t('premium.autoRenew')" :subtitle="autoRenewDetail">
            <template #trailing>
              <ToggleSwitch v-model="autoRenew" :label="t('premium.autoRenew')" :disabled="isPaying" />
            </template>
          </SettingsRow>
        </div>

        <!-- Payment method -->
        <section class="payment">
          <h2 class="select-label">{{ t('premium.selectPayment') }}</h2>
          <div class="methods" role="radiogroup" :aria-label="t('premium.selectPayment')">
            <button
              v-for="method in methods"
              :key="method.gateway"
              type="button"
              role="radio"
              class="method"
              :class="{ selected: method.gateway === selectedGateway }"
              :aria-checked="method.gateway === selectedGateway"
              :disabled="isPaying"
              @click="selectedGateway = method.gateway"
            >
              <span class="logo" aria-hidden="true">{{ method.badgeText.charAt(0) }}</span>
              <span class="method-text">
                <span class="method-name">{{ method.name }}</span>
                <span class="method-sub">{{ method.subtitle }}</span>
              </span>
              <FilledCheck v-if="method.gateway === selectedGateway" :size="22" />
              <Circle v-else :size="22" class="unchecked" aria-hidden="true" />
            </button>
          </div>
        </section>

        <WalletQr v-if="qrLink" :link="qrLink" :wallet="selectedMethod.badgeText" />

        <p v-if="statusMessage" class="status" role="status">{{ statusMessage }}</p>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

        <div v-if="recheckPaymentId && !isPaying" class="secondary-actions">
          <button type="button" class="qasa-button secondary" @click="checkAgain">
            <RefreshCw :stroke-width="2.5" aria-hidden="true" />
            <span>{{ t('premium.checkAgain') }}</span>
          </button>
        </div>
        <div v-if="isDev && stubIntent" class="secondary-actions">
          <!-- Dev builds only, never shipped: not translated. -->
          <button type="button" class="qasa-button secondary dev" @click="simulateStub">
            <ArrowLeftRight :stroke-width="2.5" aria-hidden="true" />
            <span>Simulate payment (dev)</span>
          </button>
        </div>
      </div>
    </div>

    <BottomActionBar stack :max-width="680">
      <PrimaryButton :title="payTitle" :icon="Lock" :disabled="isPaying || premium" @click="onPay" />
      <p v-if="premium" class="footnote success">{{ t('premium.alreadyPremium') }}</p>
      <p v-else class="footnote">{{ t('premium.opensWalletDesktop') }}</p>
    </BottomActionBar>

    <ConfirmDialog
      :open="comingSoon"
      :title="t('premium.comingSoonTitle')"
      :icon="Clock"
      :message="t('premium.comingSoonBody')"
      :confirm-label="t('common.ok')"
      no-cancel
      @confirm="comingSoon = false"
      @cancel="comingSoon = false"
    />
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--chrome);
}

.scroll {
  flex: 1;
  overflow-y: auto;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 720px;
  margin-inline: auto;
  padding: 20px var(--space-screen-padding) 40px;
}

.accent {
  color: var(--accent);
  flex-shrink: 0;
}

/* Carousel (iOS `planCarousel`: cards then 7pt dots, 12 apart) */
.carousel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* The track clips (it scrolls), so it keeps 2px of room on every side: the cards' borders
   and rounded corners never touch the clip edge. Cards step by exactly one track width
   (card + gap = content + padding), which onTrackScroll relies on. */
.track {
  display: flex;
  gap: 4px;
  padding: 2px;
  scroll-padding-inline: 2px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  border-radius: var(--radius-card);
  outline: none;
}

.track::-webkit-scrollbar {
  display: none;
}

.track:focus-visible {
  box-shadow: 0 0 0 2px var(--accent);
}

.plan-card {
  position: relative;
  flex: 0 0 100%;
  min-height: 240px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 25px;
  overflow: hidden;
  scroll-snap-align: start;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background-light);
  cursor: pointer;
}

.plan-card.selected {
  border-color: var(--accent);
}

.glow {
  position: absolute;
  top: -40px;
  inset-inline-end: -40px;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--gold) 10%, transparent);
  filter: blur(20px);
  pointer-events: none;
}

.plan-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}

/* `Font.qasaLabel(12)` (13 medium), tracking 1.2. */
.eyebrow {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  letter-spacing: 1.2px;
  color: var(--gold);
}

.price {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.amount {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.period {
  font-size: 14px;
  color: var(--text-secondary);
}

.plan-title {
  font-size: var(--type-field-error-size);
  font-weight: 600;
  color: var(--accent);
}

:root:lang(ar) .eyebrow,
:root:lang(ckb) .eyebrow {
  letter-spacing: 0;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dots {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.dot {
  width: 7px;
  height: 7px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--stroke);
  cursor: pointer;
  transition: background-color var(--motion-fade);
}

.dot.on {
  background: var(--accent);
}

/* Auto-renew row */
.row-card {
  padding-inline: var(--space-section-inset-horizontal);
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

/* Wallet rows (iOS `paymentSection` / `paymentRow`) */
.payment {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.select-label {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.methods {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.method {
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
  min-height: var(--size-row-min-height);
  padding: 12px 16px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.method.selected {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 6%, var(--card-background));
}

.method:active:not(:disabled) {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.method:disabled {
  opacity: var(--motion-disabled-opacity);
  cursor: default;
}

/* No wallet logo for Wayl: a text badge, the method's initial on an accent tint. */
.logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  margin-inline-end: var(--space-row-icon-gap);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font-size: var(--type-field-size);
  font-weight: 700;
}

.method-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-row-text-gap);
  min-width: 0;
  margin-inline-end: 12px;
}

.method-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

/* `Font.qasaLabel(12)`. */
.method-sub {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  color: var(--text-secondary);
}

.unchecked {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

/* Messages */
.status,
.error {
  margin: 0;
  font-size: 13px;
}

.status {
  color: var(--accent);
}

.error {
  color: var(--danger);
}

.secondary-actions {
  display: flex;
}

.qasa-button.dev {
  border-style: dashed;
  border-color: var(--gold);
  color: var(--gold);
}

.footnote {
  margin: 0;
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  text-align: center;
  color: var(--text-secondary);
}

.footnote.success {
  color: var(--success);
}
</style>
