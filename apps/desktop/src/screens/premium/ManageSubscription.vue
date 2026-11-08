<script setup lang="ts">
// iOS `ManageSubscriptionView` (embedded in the shell): the details card, the renewal link
// card when one is out, auto-renew with "Remove saved payment method", and one cancel
// button behind a confirm, then the reason sheet.
import { Lock, RefreshCw, XCircle } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { accountApi } from '@/account/api'
import { refreshAccount, refreshLicense } from '@/account/store'
import type { Subscription } from '@/account/types'
import AppMenuHeader from '@/components/AppMenuHeader.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { openUrl } from '@/opener'
import { useT } from '@/prefs'
import SheetHeader from '@/screens/settings/SheetHeader.vue'
import AlertDialog from './AlertDialog.vue'
import { errorText, formatDate, formatIqd, gatewayName, pollUntilComplete, subscriptionFrom, useTr } from './shared'

defineEmits<{ back: [] }>()
const t = useT()
const tr = useTr()

const subscription = ref<Subscription | null>(null)
const autoRenew = ref(true)
const isLoading = ref(true)
const isSaving = ref(false)
const errorMessage = ref<string | null>(null)
const statusMessage = ref<string | null>(null)

const showCancelConfirm = ref(false)
const showCancelReason = ref(false)
const cancelReason = ref('')
const isCancelling = ref(false)

const showRemovePaymentConfirm = ref(false)
const isRemovingPaymentMethod = ref(false)
const isPayingRenewal = ref(false)

let unmounted = false
onBeforeUnmount(() => (unmounted = true))

const isCancelled = computed(() => (subscription.value?.status ?? '').toLowerCase() === 'cancelled')
const planName = computed(() => (subscription.value?.plan_id === 'premium_yearly' ? t('sub.planYearly') : t('sub.planMonthly')))

/** iOS `RenewalDTO.chargesSavedWallet`: the mode, or `will_renew` on older servers. */
const chargesSavedWallet = computed(() => {
  const renewal = subscription.value?.renewal
  if (!renewal) return false
  return renewal.mode ? renewal.mode.toLowerCase() === 'charge' : renewal.will_renew
})

/** Cancelled / off / older servers keep the original wording. */
const autoRenewSubtitle = computed(() => {
  const sub = subscription.value
  if (isCancelled.value) return t('sub.autoRenewOffCancelled')
  if (!sub || !sub.renewal || !sub.auto_renew) return t('sub.autoRenewOn')
  const amount = sub.renewal.amount_iqd ?? sub.amount_iqd
  if (sub.renewal.mode?.toLowerCase() === 'link') {
    return t('sub.renewsByLink', formatDate(sub.expires_at), formatIqd(amount))
  }
  if (!chargesSavedWallet.value) return t('sub.renewalNotSetUp')
  return t('sub.renewsVia', formatDate(sub.expires_at), formatIqd(amount), gatewayName(t, sub.renewal.gateway))
})

function apply(sub: Subscription | null) {
  subscription.value = sub
  autoRenew.value = sub?.auto_renew ?? true
}

/** The subscription a write returned, or a fresh fetch when the shape is unexpected. */
async function applyResponse(value: unknown) {
  const sub = subscriptionFrom(value)
  apply(sub ?? (await accountApi.subscription()))
}

async function load() {
  isLoading.value = true
  errorMessage.value = null
  try {
    apply(await accountApi.subscription())
  } catch (e) {
    errorMessage.value = errorText(tr, e)
  } finally {
    isLoading.value = false
  }
}

onMounted(load)

async function updateAutoRenew(enabled: boolean) {
  if (isCancelled.value || isSaving.value || !subscription.value) return
  autoRenew.value = enabled
  isSaving.value = true
  errorMessage.value = null
  statusMessage.value = null
  try {
    await applyResponse(await accountApi.setAutoRenew(enabled, subscription.value?.id ?? null))
    statusMessage.value = enabled ? t('sub.autoRenewEnabled') : t('sub.autoRenewDisabled')
  } catch (e) {
    autoRenew.value = subscription.value?.auto_renew ?? true
    errorMessage.value = errorText(tr, e)
  } finally {
    isSaving.value = false
  }
}

async function removePaymentMethod() {
  showRemovePaymentConfirm.value = false
  isRemovingPaymentMethod.value = true
  errorMessage.value = null
  statusMessage.value = null
  try {
    await accountApi.removePaymentMethod()
    apply(await accountApi.subscription())
    statusMessage.value = t('sub.paymentMethodRemoved')
  } catch (e) {
    errorMessage.value = errorText(tr, e)
  } finally {
    isRemovingPaymentMethod.value = false
  }
}

/** Same confirmation as checkout: open the hosted page, poll the payment, then reload. */
async function payRenewal() {
  const link = subscription.value?.renewal?.link
  if (!link || isPayingRenewal.value) return
  isPayingRenewal.value = true
  errorMessage.value = null
  statusMessage.value = null
  try {
    if (!/^https:\/\//i.test(link.url)) {
      errorMessage.value = t('premium.paymentFailed')
      return
    }
    await openUrl(link.url)
    const result = await pollUntilComplete(link.payment_id, { cancelled: () => unmounted, tr })
    if (unmounted) return
    if (result.outcome === 'success') {
      await refreshLicense().catch((e) => console.error('License refresh failed', e))
    }
    const sub = await accountApi.subscription()
    apply(sub)
    // Still open means they came back without paying (or it hasn't landed yet), not that
    // the payment failed.
    if (result.outcome !== 'success') {
      if (sub?.renewal?.link) statusMessage.value = t('sub.renewalNotPaidYet')
      else errorMessage.value = (result.outcome === 'timeout' ? result.error : null) ?? t('premium.paymentFailed')
    }
  } catch (e) {
    errorMessage.value = errorText(tr, e)
  } finally {
    isPayingRenewal.value = false
  }
}

function askCancel() {
  showCancelConfirm.value = true
}

function continueCancel() {
  showCancelConfirm.value = false
  cancelReason.value = ''
  showCancelReason.value = true
}

async function confirmCancel(reason: string) {
  if (isCancelling.value) return
  isCancelling.value = true
  errorMessage.value = null
  statusMessage.value = null
  try {
    const trimmed = reason.trim()
    await applyResponse(await accountApi.cancelSubscription(trimmed || null, subscription.value?.id ?? null))
    showCancelReason.value = false
    statusMessage.value = t('sub.cancelledUntil', formatDate(subscription.value?.expires_at))
    await refreshAccount().catch(() => {})
  } catch (e) {
    errorMessage.value = errorText(tr, e)
  } finally {
    isCancelling.value = false
  }
}
</script>

<template>
  <div class="page">
    <AppMenuHeader :title="t('settings.subscription')" back @back="$emit('back')" />

    <div class="scroll">
      <div class="column">
        <div v-if="isLoading" class="loading"><span class="spinner" aria-hidden="true" /></div>

        <template v-else-if="subscription">
          <!-- Details -->
          <div class="details">
            <div class="details-head">
              <span class="plan-name">{{ planName }}</span>
              <span class="badge" :class="{ cancelled: isCancelled }">
                {{ isCancelled ? t('sub.cancelling') : t('sub.active') }}
              </span>
            </div>
            <div class="detail-row">
              <span class="label">{{ t('sub.amount') }}</span>
              <span class="value">{{ formatIqd(subscription.amount_iqd) }} {{ t('sub.iqd') }}</span>
            </div>
            <div class="detail-row">
              <span class="label">{{ t('sub.started') }}</span>
              <span class="value">{{ formatDate(subscription.started_at) }}</span>
            </div>
            <div class="detail-row">
              <span class="label">{{ t('sub.renewsExpires') }}</span>
              <span class="value">{{ formatDate(subscription.expires_at) }}</span>
            </div>
            <div v-if="isCancelled && subscription.cancelled_at" class="detail-row">
              <span class="label">{{ t('sub.cancelled') }}</span>
              <span class="value">{{ formatDate(subscription.cancelled_at) }}</span>
            </div>
            <p class="note">{{ isCancelled ? t('sub.accessContinues') : t('sub.premiumIncludes') }}</p>
          </div>

          <!-- Renewal link -->
          <div v-if="subscription.renewal?.link" class="row-card link-card">
            <p class="link-text">
              {{ t('sub.renewalLinkReady', formatIqd(subscription.renewal.link.amount_iqd), formatDate(subscription.renewal.link.pay_by)) }}
            </p>
            <PrimaryButton
              class="small-radius"
              :title="isPayingRenewal ? t('premium.processing') : t('sub.payRenewal')"
              :icon="Lock"
              :disabled="isPayingRenewal"
              @click="payRenewal"
            />
          </div>

          <!-- Auto-renew -->
          <div class="row-card">
            <div class="renew-row">
              <RefreshCw :size="18" class="accent" aria-hidden="true" />
              <div class="row-text">
                <span class="row-title">{{ t('sub.autoRenew') }}</span>
                <span class="row-detail">{{ autoRenewSubtitle }}</span>
              </div>
              <ToggleSwitch
                :model-value="autoRenew"
                :label="t('sub.autoRenew')"
                :disabled="isCancelled || isSaving"
                @update:model-value="updateAutoRenew"
              />
            </div>
            <button
              v-if="chargesSavedWallet"
              type="button"
              class="remove"
              :disabled="isRemovingPaymentMethod"
              @click="showRemovePaymentConfirm = true"
            >
              {{ t('sub.removePaymentMethod') }}
            </button>
          </div>

          <button v-if="!isCancelled" type="button" class="cancel-button" :disabled="isCancelling" @click="askCancel">
            {{ t('sub.cancelSubscription') }}
          </button>
        </template>

        <p v-else class="none">{{ t('sub.noneActive') }}</p>

        <p v-if="statusMessage" class="status" role="status">{{ statusMessage }}</p>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </div>
    </div>

    <AlertDialog
      :open="showCancelConfirm"
      :title="t('sub.cancelConfirmTitle')"
      :message="t('sub.cancelConfirmMessage', formatDate(subscription?.expires_at))"
      :cancel-label="t('sub.keepPremium')"
      :confirm-label="t('sub.continue')"
      destructive
      @cancel="showCancelConfirm = false"
      @confirm="continueCancel"
    />

    <ConfirmDialog
      :open="showRemovePaymentConfirm"
      :title="t('sub.removePaymentMethod')"
      :message="t('sub.removePaymentMethodConfirm')"
      :confirm-label="t('sub.removePaymentMethod')"
      destructive
      @cancel="showRemovePaymentConfirm = false"
      @confirm="removePaymentMethod"
    />

    <Sheet :open="showCancelReason" :label="t('sub.cancelReasonTitle')" :width="520" @close="showCancelReason = false">
      <SheetHeader :title="t('sub.cancelReasonTitle')" :cancel-label="t('sub.skip')" @cancel="confirmCancel('')" />
      <div class="reason">
        <p class="prompt">{{ t('sub.cancelReasonPrompt') }}</p>
        <TextField v-model="cancelReason" multiline :label="t('sub.cancelReasonTitle')" :disabled="isCancelling" />
        <p class="optional">{{ t('sub.cancelReasonOptional') }}</p>
        <p v-if="errorMessage && showCancelReason" class="error" role="alert">{{ errorMessage }}</p>
        <PrimaryButton
          class="small-radius"
          :title="isCancelling ? t('sub.cancellingProgress') : t('sub.confirmCancel')"
          :icon="XCircle"
          :disabled="isCancelling"
          @click="confirmCancel(cancelReason)"
        />
      </div>
    </Sheet>
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
  padding: 20px var(--screen-padding) 40px;
}

.loading {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 3px solid color-mix(in srgb, var(--accent) 25%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}

.none {
  margin: 0;
  padding-top: 40px;
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

/* Details card */
.details {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background-light);
}

.details-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.plan-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.badge {
  padding: 4px 8px;
  border: 1px solid var(--accent);
  border-radius: 999px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 11px;
  white-space: nowrap;
}

.badge.cancelled {
  border-color: var(--gold);
  color: var(--gold);
}

:root:lang(ar) .badge,
:root:lang(ckb) .badge {
  font-family: var(--font-body);
  font-weight: 600;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.detail-row .label {
  color: var(--text-secondary);
}

.detail-row .value {
  font-weight: 500;
  color: var(--text-primary);
  text-align: end;
}

.note {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* Row cards */
.row-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--stroke);
  border-radius: 8px;
  background: var(--chrome);
}

.link-card {
  border-color: var(--accent);
}

.link-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-primary);
}

.renew-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.accent {
  flex-shrink: 0;
  color: var(--accent);
}

.row-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.row-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.row-detail {
  font-size: 12px;
  color: var(--text-secondary);
}

.remove {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.remove:disabled {
  opacity: 0.5;
  cursor: default;
}

.cancel-button {
  width: 100%;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--danger) 35%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--danger) 8%, transparent);
  color: var(--danger);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.cancel-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.column .small-radius,
.reason .small-radius {
  border-radius: 8px;
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

/* Reason sheet */
.reason {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: var(--screen-padding);
}

.prompt {
  margin: 0;
  font-size: 15px;
  color: var(--text-secondary);
}

.reason :deep(.field) {
  min-height: 140px;
  border-radius: 8px;
  background: var(--card-background-light);
}

.reason :deep(textarea) {
  min-height: 108px;
  font-size: 15px;
}

.optional {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-secondary);
}

:root:lang(ar) .optional,
:root:lang(ckb) .optional {
  font-family: var(--font-body);
}
</style>
