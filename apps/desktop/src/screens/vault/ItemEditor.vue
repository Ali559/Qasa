<script setup lang="ts">
// iOS `VaultItemEditor` + `VaultFormChrome`: Cancel / "New Login" / Save (Update when
// editing), every field of all 13 types, the password generator, 2FA, folders, colours,
// countries and dates. Field rules come from the core (`validate_item`): checked live,
// shown under each field the user changed (every field after a Save attempt, and on an
// existing item), and Save refuses while any remain — as the core does on its side.
import { Eye, EyeOff, WandSparkles } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import Sheet from '@/components/Sheet.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { fieldsApi, issueMessage, issuesByField, type FieldIssue } from '@/vault/fields'
import {
  COUNTRIES,
  dialCode,
  EMAIL_KINDS,
  emptyPayload,
  HONORIFICS,
  PHONE_KINDS,
  VARIANT_TYPE_KEY,
} from '@/vault/present'
import { reloadCollections, session, upsertItem } from '@/vault/session'
import { isCommandError, type ItemSummary, type Variant, type VaultPayload } from '@/vault/types'
import CollectionPicker from './form/CollectionPicker.vue'
import ColorPicker from './form/ColorPicker.vue'
import DateField from './form/DateField.vue'
import FieldError from './form/FieldError.vue'
import FormField from './form/FormField.vue'
import SelectField from './form/SelectField.vue'
import TotpField from './form/TotpField.vue'
import PasswordGeneratorSheet from './PasswordGeneratorSheet.vue'

const props = defineProps<{ itemId?: string; variant?: Variant; password?: string }>()
const emit = defineEmits<{ saved: [row: ItemSummary]; close: [] }>()
const t = useT()

const payload = ref<VaultPayload | null>(props.variant ? emptyPayload(props.variant) : null)
const saveError = ref<string | null>(null)
const showGenerator = ref(false)
const passwordVisible = ref(false)
const busy = ref(false)

// MARK: field rules

const issues = ref<FieldIssue[]>([])
/** Set by a Save attempt: from then on every issue shows, not only edited fields'. */
const attempted = ref(false)
const edited = ref(new Set<string>())
let opened: Record<string, string> = {}
let validateTimer: ReturnType<typeof setTimeout> | undefined
let validateSeq = 0

const byField = computed(() => issuesByField(issues.value))
const showFixToSave = computed(() => attempted.value && issues.value.length > 0)

/** The message under `field`, once the user changed it (or tried to save, or reopened an
 * existing item, whose remaining issues are what they came to fix). */
function err(field: string): string | null {
  const problem = byField.value[field]
  if (!problem || !(attempted.value || props.itemId || edited.value.has(field))) return null
  return issueMessage(problem, t)
}

function snapshot(p: VaultPayload): Record<string, string> {
  return Object.fromEntries(Object.entries(p.fields).map(([k, v]) => [k, JSON.stringify(v ?? null)]))
}

async function runValidation(): Promise<FieldIssue[]> {
  if (!payload.value) return []
  const mine = ++validateSeq
  const found = await fieldsApi.validate(payload.value)
  if (mine === validateSeq) issues.value = found
  return found
}

watch(
  payload,
  (p) => {
    if (!p) return
    for (const [k, v] of Object.entries(snapshot(p))) if (opened[k] !== v) edited.value.add(k)
    clearTimeout(validateTimer)
    validateTimer = setTimeout(() => runValidation().catch((e) => console.error('Could not check the item', e)), 120)
  },
  { deep: true },
)
onBeforeUnmount(() => clearTimeout(validateTimer))

// MARK: as-you-type help (the core's formatters, so every app groups numbers alike)

/** Rewrites a card field with the core's formatter, unless the user typed on meanwhile. */
function formatCard(key: 'number' | 'expiryDate', format: (raw: string) => Promise<string>) {
  watch(
    () => (payload.value?.variant === 'card' ? payload.value.fields[key] : null),
    async (raw) => {
      if (raw == null) return
      const formatted = await format(raw).catch(() => raw)
      const p = payload.value
      if (p?.variant === 'card' && p.fields[key] === raw && formatted !== raw) p.fields[key] = formatted
    },
  )
}
formatCard('number', fieldsApi.formatCardNumber)
formatCard('expiryDate', fieldsApi.formatCardExpiry)

/** Security code: digits only, at most 4 (ASCII or Arabic-Indic; the core makes them ASCII). */
watch(
  () => (payload.value?.variant === 'card' ? payload.value.fields.securityCode : null),
  (raw) => {
    const p = payload.value
    if (raw == null || p?.variant !== 'card') return
    const digits = raw.replace(/[^0-9٠-٩۰-۹]/g, '').slice(0, 4)
    if (digits !== raw) p.fields.securityCode = digits
  },
)

onMounted(async () => {
  // An existing item opens in its stored canonical form (ISO dates, MM/YY, grouped card
  // number): what the next save writes anyway.
  if (props.itemId) payload.value = await fieldsApi.normalize(await vaultApi.get(props.itemId))
  if (props.password && payload.value?.variant === 'login') payload.value.fields.password = props.password
  if (payload.value) opened = snapshot(payload.value)
  edited.value = new Set()
})

const title = computed(() => {
  if (!payload.value) return ''
  const base = t(VARIANT_TYPE_KEY[payload.value.variant])
  return props.itemId ? t('vault.editFmt', base) : t('vault.newFmt', base)
})
// Save stays enabled so a press can say what to fix (`validation.fixToSave`).
const canSave = computed(() => !!payload.value && !busy.value)

const countryOptions = COUNTRIES.map((c) => ({ value: c.code, label: c.name }))
const phoneCountryOptions = COUNTRIES.map((c) => ({ value: c.code, label: `${c.name} (${c.dialCode})` }))
const honorificOptions = computed(() => HONORIFICS.map((h) => ({ value: h.value, label: t(h.key) })))
const emailOptions = computed(() => EMAIL_KINDS.map((h) => ({ value: h.value, label: t(h.key) })))
const phoneOptions = computed(() => PHONE_KINDS.map((h) => ({ value: h.value, label: t(h.key) })))
const phoneItems = computed(() => [
  { value: null as string | null, label: t('common.none') },
  ...session.items.filter((i) => i.variant === 'phone').map((i) => ({ value: i.id as string | null, label: i.displayName })),
])

async function save() {
  if (!payload.value || !canSave.value) return
  attempted.value = true
  busy.value = true
  try {
    clearTimeout(validateTimer)
    if ((await runValidation()).length > 0) return
    const row = await vaultApi.save(payload.value, props.itemId)
    upsertItem(row)
    await reloadCollections()
    emit('saved', row)
  } catch (e) {
    // The core checks again and refuses with the same issues: show them inline.
    if (isCommandError(e) && e.code === 'invalidFields' && e.issues) {
      validateSeq++
      issues.value = e.issues
      return
    }
    saveError.value = typeof e === 'object' && e && 'message' in e ? String((e as { message: string }).message) : String(e)
  } finally {
    busy.value = false
  }
}

function usePassword(password: string) {
  if (payload.value?.variant === 'login') payload.value.fields.password = password
  showGenerator.value = false
}

function onKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    save()
  }
}
</script>

<template>
  <Sheet :open="true" :label="title" :width="600" @close="emit('close')">
    <div class="editor" @keydown="onKey">
      <header class="head">
        <button type="button" class="link" @click="emit('close')">{{ t('common.cancel') }}</button>
        <h2>{{ title }}</h2>
        <button type="button" class="link strong" :disabled="!canSave" @click="save">
          {{ itemId ? t('common.update') : t('common.save') }}
        </button>
      </header>

      <div v-if="payload" class="scroll">
        <p v-if="showFixToSave" class="fix-banner" role="alert">{{ t('validation.fixToSave') }}</p>
        <div class="fields">
          <!-- Login -->
          <template v-if="payload.variant === 'login'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <FormField v-model="payload.fields.email" :label="t('field.email')" inputmode="email" :error="err('email')" />
            <FormField v-model="payload.fields.username" :label="t('field.username')" :error="err('username')" />
            <FormField v-model="payload.fields.password" :label="t('field.password')" :secure="!passwordVisible" mono :error="err('password')">
              <template #trailing>
                <button type="button" class="field-icon accent" :aria-label="t('vault.generatePassword')" :title="t('vault.generatePassword')" @click="showGenerator = true">
                  <WandSparkles :size="17" />
                </button>
                <button
                  type="button"
                  class="field-icon"
                  :aria-label="passwordVisible ? t('vault.hidePassword') : t('vault.showPassword')"
                  @click="passwordVisible = !passwordVisible"
                >
                  <EyeOff v-if="passwordVisible" :size="17" />
                  <Eye v-else :size="17" />
                </button>
              </template>
            </FormField>
            <div class="with-error">
              <TotpField v-model="payload.fields.totp" />
              <FieldError :message="err('totp')" />
            </div>
            <FormField v-model="payload.fields.website" :label="t('field.website')" inputmode="url" :error="err('website')" />
            <FormField v-model="payload.fields.note" :label="t('field.note')" multiline :error="err('note')" />
            <CollectionPicker v-model="payload.fields.collectionId" />
          </template>

          <!-- Secure note -->
          <template v-else-if="payload.variant === 'secureNote'">
            <FormField v-model="payload.fields.title" :label="t('field.title')" :error="err('title')" />
            <FormField v-model="payload.fields.content" :label="t('field.content')" multiline :error="err('content')" />
            <ColorPicker v-model="payload.fields.color" />
            <div class="toggle-row">
              <span>{{ t('vault.protectItem') }}</span>
              <ToggleSwitch v-model="payload.fields.protectItem" :label="t('vault.protectItem')" />
            </div>
            <CollectionPicker v-model="payload.fields.collectionId" />
          </template>

          <!-- Card (NFC scanning is phone-only) -->
          <template v-else-if="payload.variant === 'card'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <FormField v-model="payload.fields.accountHolder" :label="t('field.accountHolder')" :error="err('accountHolder')" />
            <FormField v-model="payload.fields.number" :label="t('field.number')" inputmode="numeric" :maxlength="23" mono :error="err('number')" />
            <FormField
              v-model="payload.fields.securityCode"
              :label="t('field.securityCode')"
              secure
              inputmode="numeric"
              :maxlength="4"
              :error="err('securityCode')"
            />
            <FormField
              v-model="payload.fields.expiryDate"
              :label="t('field.expiryDate')"
              :placeholder="t('field.expiryPlaceholder')"
              inputmode="numeric"
              :maxlength="5"
              mono
              :error="err('expiryDate')"
            />
            <FormField v-model="payload.fields.note" :label="t('field.note')" multiline :error="err('note')" />
            <ColorPicker v-model="payload.fields.color" />
          </template>

          <!-- Bank account -->
          <template v-else-if="payload.variant === 'bankAccount'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <FormField v-model="payload.fields.accountHolder" :label="t('field.accountHolder')" :error="err('accountHolder')" />
            <FormField v-model="payload.fields.accountNumber" :label="t('field.accountNumber')" mono :error="err('accountNumber')" />
            <FormField v-model="payload.fields.routingNumber" :label="t('field.routingNumber')" mono :error="err('routingNumber')" />
            <SelectField v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
            <FormField v-model="payload.fields.bankName" :label="t('field.bankName')" :error="err('bankName')" />
          </template>

          <!-- Name -->
          <template v-else-if="payload.variant === 'name'">
            <SelectField v-model="payload.fields.type" :label="t('field.type')" :options="honorificOptions" :error="err('type')" />
            <FormField v-model="payload.fields.firstName" :label="t('field.firstName')" :error="err('firstName')" />
            <FormField v-model="payload.fields.middleName" :label="t('field.middleName')" :error="err('middleName')" />
            <FormField v-model="payload.fields.lastName" :label="t('field.lastName')" :error="err('lastName')" />
            <FormField v-model="payload.fields.defaultUsername" :label="t('field.defaultUsername')" :error="err('defaultUsername')" />
            <DateField v-model="payload.fields.birthDate" :label="t('field.birthDate')" :error="err('birthDate')" />
            <FormField v-model="payload.fields.birthPlace" :label="t('field.birthPlace')" :error="err('birthPlace')" />
          </template>

          <!-- Email -->
          <template v-else-if="payload.variant === 'personalEmail'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <SelectField v-model="payload.fields.type" :label="t('field.type')" :options="emailOptions" :error="err('type')" />
            <FormField v-model="payload.fields.email" :label="t('field.email')" inputmode="email" :error="err('email')" />
          </template>

          <!-- Phone -->
          <template v-else-if="payload.variant === 'phone'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <div class="pair">
              <SelectField v-model="payload.fields.type" :label="t('field.type')" :options="phoneOptions" :error="err('type')" />
              <SelectField v-model="payload.fields.countryCode" :label="t('vault.country')" :options="phoneCountryOptions" :error="err('countryCode')" />
            </div>
            <FormField v-model="payload.fields.number" :label="t('field.phoneNumber')" inputmode="tel" :error="err('number')">
              <template #trailing>
                <span class="dial" dir="ltr">{{ dialCode(payload.fields.countryCode) }}</span>
              </template>
            </FormField>
          </template>

          <!-- Address -->
          <template v-else-if="payload.variant === 'address'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <FormField v-model="payload.fields.address" :label="t('field.address')" :error="err('address')" />
            <FormField v-model="payload.fields.city" :label="t('field.city')" :error="err('city')" />
            <FormField v-model="payload.fields.zipCode" :label="t('field.zipCode')" :error="err('zipCode')" />
            <SelectField v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
            <FormField v-model="payload.fields.recipient" :label="t('field.recipient')" :error="err('recipient')" />
            <FormField v-model="payload.fields.building" :label="t('field.building')" :error="err('building')" />
            <FormField v-model="payload.fields.floor" :label="t('field.floor')" :error="err('floor')" />
            <FormField v-model="payload.fields.apartment" :label="t('field.apartment')" :error="err('apartment')" />
            <FormField v-model="payload.fields.doorCode" :label="t('field.doorCode')" :error="err('doorCode')" />
            <SelectField
              :model-value="payload.fields.phoneItemId ?? null"
              :label="t('field.phoneNumber')"
              :options="phoneItems"
              @update:model-value="(v) => payload?.variant === 'address' && (payload.fields.phoneItemId = v ?? undefined)"
            />
          </template>

          <!-- Company -->
          <template v-else-if="payload.variant === 'company'">
            <FormField v-model="payload.fields.companyName" :label="t('field.companyName')" :error="err('companyName')" />
            <FormField v-model="payload.fields.jobTitle" :label="t('field.jobTitle')" :error="err('jobTitle')" />
          </template>

          <!-- Website -->
          <template v-else-if="payload.variant === 'website'">
            <FormField v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
            <FormField v-model="payload.fields.websiteURL" :label="t('field.websiteURL')" inputmode="url" :error="err('websiteURL')" />
          </template>

          <!-- ID documents -->
          <template v-else>
            <FormField v-model="payload.fields.name" :label="t('field.name')" :error="err('name')" />
            <SelectField v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
            <FormField v-model="payload.fields.number" :label="t('field.number')" mono :error="err('number')" />
            <DateField v-model="payload.fields.issueDate" :label="t('field.issueDate')" :error="err('issueDate')" />
            <FormField v-if="payload.variant === 'passport'" v-model="payload.fields.issuePlace" :label="t('field.issuePlace')" :error="err('issuePlace')" />
            <DateField v-model="payload.fields.expiryDate" :label="t('field.expiryDate')" :error="err('expiryDate')" />
          </template>
        </div>
      </div>
    </div>

    <PasswordGeneratorSheet :open="showGenerator" @use="usePassword" @close="showGenerator = false" />

    <Sheet :open="!!saveError" :label="t('vault.couldNotSave')" :width="380" @close="saveError = null">
      <div class="alert">
        <h3>{{ t('vault.couldNotSave') }}</h3>
        <p>{{ saveError }}</p>
        <button type="button" @click="saveError = null">{{ t('common.ok') }}</button>
      </div>
    </Sheet>
  </Sheet>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: min(92vh, 860px);
}

.head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 56px;
  padding-inline: 16px;
  border-bottom: 1px solid var(--stroke);
  flex-shrink: 0;
}

.head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.link {
  justify-self: start;
  padding: 6px 4px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 16px;
  cursor: pointer;
}

.link.strong {
  justify-self: end;
  font-weight: 600;
}

.link:disabled {
  color: var(--text-tertiary);
  cursor: default;
}

.scroll {
  overflow-y: auto;
  background: var(--background);
}

.fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: var(--screen-padding);
}

.pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.field-icon.accent {
  color: var(--accent);
}

.dial {
  order: -1;
  padding-inline-end: 10px;
  border-inline-end: 1px solid var(--stroke);
  color: var(--accent);
  font-weight: 600;
}

.fix-banner {
  margin: var(--screen-padding) var(--screen-padding) 0;
  padding: 12px 14px;
  border-radius: var(--corner-radius);
  background: var(--danger-background);
  color: var(--danger-text);
  font-size: 14px;
  font-weight: 500;
}

.with-error {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
}

.alert {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 24px;
  text-align: center;
}

.alert h3 {
  margin: 0;
  font-size: 17px;
}

.alert p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.alert button {
  margin-top: 8px;
  padding: 12px;
  border: 0;
  border-radius: 14px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
</style>
