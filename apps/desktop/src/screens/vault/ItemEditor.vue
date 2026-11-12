<script setup lang="ts">
// iOS `VaultItemEditor` + `VaultFormChrome`: the shared sheet header ("New Login", no
// Cancel: Esc or a click outside closes), Save (Update when editing) pinned at the bottom,
// fields in grouped cards 16 apart with 20 padding (design/README.md rule 4), every field of all 13 types, the password generator, 2FA, folders, colours,
// countries and dates. Field rules come from the core (`validate_item`): checked live,
// shown under each field the user changed (every field after a Save attempt, and on an
// existing item), and Save refuses while any remain — as the core does on its side.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import BottomActionBar from '@/components/BottomActionBar.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldIconButton from '@/components/FieldIconButton.vue'
import FieldRow from '@/components/FieldRow.vue'
import NotesCard from '@/components/NotesCard.vue'
import PickerRow from '@/components/PickerRow.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { useT } from '@/prefs'
import { showToast } from '@/toast'
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
import TotpField from './form/TotpField.vue'
import PasswordGeneratorSheet from './PasswordGeneratorSheet.vue'

const props = defineProps<{ itemId?: string; variant?: Variant; password?: string }>()
const emit = defineEmits<{ saved: [row: ItemSummary]; close: [] }>()
const t = useT()

const payload = ref<VaultPayload | null>(props.variant ? emptyPayload(props.variant) : null)
const saveError = ref<string | null>(null)
const showGenerator = ref(false)
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

const countryOptions = COUNTRIES.map((c) => ({ value: c.code, label: c.name, search: c.dialCode }))
const phoneCountryOptions = COUNTRIES.map((c) => ({ value: c.code, label: `${c.name} (${c.dialCode})`, search: c.dialCode }))
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
    const creating = !props.itemId
    const row = await vaultApi.save(payload.value, props.itemId)
    upsertItem(row)
    await reloadCollections()
    const title = row.displayName.trim()
    if (creating) showToast(title ? t('toast.itemCreatedFmt', title) : t('toast.itemCreated'))
    else showToast(title ? t('toast.itemUpdatedFmt', title) : t('toast.itemUpdated'))
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
  <Sheet :open="true" :label="title" :title="title" :width="600" @close="emit('close')">
    <div class="editor" @keydown="onKey">
      <div v-if="payload" class="scroll">
        <div class="fields">
          <p v-if="showFixToSave" class="fix-banner" role="alert">{{ t('validation.fixToSave') }}</p>
          <!-- Login -->
          <template v-if="payload.variant === 'login'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <FieldRow v-model="payload.fields.email" :label="t('field.email')" :placeholder="t('field.emailPlaceholder')" inputmode="email" :error="err('email')" />
              <FieldRow v-model="payload.fields.username" :label="t('field.username')" :error="err('username')" />
              <FieldRow v-model="payload.fields.password" :label="t('field.password')" secure revealable mono :error="err('password')">
                <template #trailing>
                  <FieldIconButton icon="generate" :label="t('vault.generatePassword')" accent @click="showGenerator = true" />
                </template>
              </FieldRow>
              <TotpField v-model="payload.fields.totp" :error="err('totp')" />
              <FieldRow v-model="payload.fields.website" :label="t('field.website')" :placeholder="t('field.websitePlaceholder')" inputmode="url" :error="err('website')" />
            </FieldGroup>
            <NotesCard v-model="payload.fields.note" :label="t('field.note')" :error="err('note')" />
            <CollectionPicker v-model="payload.fields.collectionId" />
          </template>

          <!-- Secure note -->
          <template v-else-if="payload.variant === 'secureNote'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.title" :label="t('field.title')" :error="err('title')" />
            </FieldGroup>
            <NotesCard v-model="payload.fields.content" :label="t('field.content')" :error="err('content')" />
            <ColorPicker v-model="payload.fields.color" />
            <div class="toggle-row">
              <span>{{ t('vault.protectItem') }}</span>
              <ToggleSwitch v-model="payload.fields.protectItem" :label="t('vault.protectItem')" />
            </div>
            <CollectionPicker v-model="payload.fields.collectionId" />
          </template>

          <!-- Card: expiry and security code are rows of their own, like every other field. -->
          <template v-else-if="payload.variant === 'card'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <FieldRow v-model="payload.fields.accountHolder" :label="t('field.accountHolder')" :error="err('accountHolder')" />
              <FieldRow v-model="payload.fields.number" :label="t('field.number')" inputmode="numeric" :maxlength="23" mono :error="err('number')" />
              <FieldRow
                v-model="payload.fields.expiryDate"
                :label="t('field.expiryDate')"
                :placeholder="t('field.expiryPlaceholder')"
                inputmode="numeric"
                :maxlength="5"
                mono
                :error="err('expiryDate')"
              />
              <FieldRow
                v-model="payload.fields.securityCode"
                :label="t('field.securityCode')"
                secure
                inputmode="numeric"
                :maxlength="4"
                :error="err('securityCode')"
              />
            </FieldGroup>
            <NotesCard v-model="payload.fields.note" :label="t('field.note')" :error="err('note')" />
            <ColorPicker v-model="payload.fields.color" />
          </template>

          <!-- Bank account -->
          <template v-else-if="payload.variant === 'bankAccount'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <FieldRow v-model="payload.fields.accountHolder" :label="t('field.accountHolder')" :error="err('accountHolder')" />
              <FieldRow v-model="payload.fields.accountNumber" :label="t('field.accountNumber')" mono :error="err('accountNumber')" />
              <FieldRow v-model="payload.fields.routingNumber" :label="t('field.routingNumber')" mono :error="err('routingNumber')" />
              <PickerRow v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
              <FieldRow v-model="payload.fields.bankName" :label="t('field.bankName')" :error="err('bankName')" />
            </FieldGroup>
          </template>

          <!-- Name -->
          <template v-else-if="payload.variant === 'name'">
            <FieldGroup>
              <PickerRow v-model="payload.fields.type" :label="t('field.type')" :options="honorificOptions" :error="err('type')" />
              <FieldRow v-model="payload.fields.firstName" :label="t('field.firstName')" :error="err('firstName')" />
              <FieldRow v-model="payload.fields.middleName" :label="t('field.middleName')" :error="err('middleName')" />
              <FieldRow v-model="payload.fields.lastName" :label="t('field.lastName')" :error="err('lastName')" />
              <FieldRow v-model="payload.fields.defaultUsername" :label="t('field.defaultUsername')" :error="err('defaultUsername')" />
              <DateField v-model="payload.fields.birthDate" :label="t('field.birthDate')" :error="err('birthDate')" />
              <FieldRow v-model="payload.fields.birthPlace" :label="t('field.birthPlace')" :error="err('birthPlace')" />
            </FieldGroup>
          </template>

          <!-- Email -->
          <template v-else-if="payload.variant === 'personalEmail'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <PickerRow v-model="payload.fields.type" :label="t('field.type')" :options="emailOptions" :error="err('type')" />
              <FieldRow v-model="payload.fields.email" :label="t('field.email')" :placeholder="t('field.emailPlaceholder')" inputmode="email" :error="err('email')" />
            </FieldGroup>
          </template>

          <!-- Phone: the number's value line starts with the country's dial code. -->
          <template v-else-if="payload.variant === 'phone'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <PickerRow v-model="payload.fields.type" :label="t('field.type')" :options="phoneOptions" :error="err('type')" />
              <PickerRow v-model="payload.fields.countryCode" :label="t('vault.country')" :options="phoneCountryOptions" :error="err('countryCode')" />
              <FieldRow v-model="payload.fields.number" :label="t('field.phoneNumber')" inputmode="tel" :error="err('number')">
                <template #prefix>
                  <span class="dial" dir="ltr">{{ dialCode(payload.fields.countryCode) }}</span>
                </template>
              </FieldRow>
            </FieldGroup>
          </template>

          <!-- Address -->
          <template v-else-if="payload.variant === 'address'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <FieldRow v-model="payload.fields.address" :label="t('field.address')" :error="err('address')" />
              <FieldRow v-model="payload.fields.city" :label="t('field.city')" :error="err('city')" />
              <FieldRow v-model="payload.fields.zipCode" :label="t('field.zipCode')" :error="err('zipCode')" />
              <PickerRow v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
              <FieldRow v-model="payload.fields.recipient" :label="t('field.recipient')" :error="err('recipient')" />
              <FieldRow v-model="payload.fields.building" :label="t('field.building')" :error="err('building')" />
              <FieldRow v-model="payload.fields.floor" :label="t('field.floor')" :error="err('floor')" />
              <FieldRow v-model="payload.fields.apartment" :label="t('field.apartment')" :error="err('apartment')" />
              <FieldRow v-model="payload.fields.doorCode" :label="t('field.doorCode')" :error="err('doorCode')" />
              <PickerRow
                :model-value="payload.fields.phoneItemId ?? null"
                :label="t('field.phoneNumber')"
                :options="phoneItems"
                @update:model-value="(v) => payload?.variant === 'address' && (payload.fields.phoneItemId = v ?? undefined)"
              />
            </FieldGroup>
          </template>

          <!-- Company -->
          <template v-else-if="payload.variant === 'company'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.companyName" :label="t('field.companyName')" :error="err('companyName')" />
              <FieldRow v-model="payload.fields.jobTitle" :label="t('field.jobTitle')" :error="err('jobTitle')" />
            </FieldGroup>
          </template>

          <!-- Website -->
          <template v-else-if="payload.variant === 'website'">
            <FieldGroup>
              <FieldRow v-model="payload.fields.itemName" :label="t('field.itemName')" :error="err('itemName')" />
              <FieldRow v-model="payload.fields.websiteURL" :label="t('field.websiteURL')" :placeholder="t('field.websitePlaceholder')" inputmode="url" :error="err('websiteURL')" />
            </FieldGroup>
          </template>

          <!-- ID documents -->
          <template v-else>
            <FieldGroup>
              <FieldRow v-model="payload.fields.name" :label="t('field.name')" :error="err('name')" />
              <PickerRow v-model="payload.fields.countryCode" :label="t('vault.country')" :options="countryOptions" :error="err('countryCode')" />
              <FieldRow v-model="payload.fields.number" :label="t('field.number')" mono :error="err('number')" />
              <DateField v-model="payload.fields.issueDate" :label="t('field.issueDate')" :error="err('issueDate')" />
              <FieldRow v-if="payload.variant === 'passport'" v-model="payload.fields.issuePlace" :label="t('field.issuePlace')" :error="err('issuePlace')" />
              <DateField v-model="payload.fields.expiryDate" :label="t('field.expiryDate')" :error="err('expiryDate')" />
            </FieldGroup>
          </template>
        </div>
      </div>

      <BottomActionBar v-if="payload">
        <PrimaryButton :title="itemId ? t('common.update') : t('common.save')" :disabled="!canSave" @click="save" />
      </BottomActionBar>
    </div>

    <PasswordGeneratorSheet :open="showGenerator" @use="usePassword" @close="showGenerator = false" />

    <ConfirmDialog
      :open="!!saveError"
      :title="t('vault.couldNotSave')"
      icon="warning"
      :message="saveError ?? undefined"
      :confirm-label="t('common.ok')"
      no-cancel
      @confirm="saveError = null"
      @cancel="saveError = null"
    />
  </Sheet>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: var(--background);
}

/* iOS `VaultFormChrome`: a VStack 16 apart (`space.formGap`), 20 padding. */
.fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
  padding: var(--space-screen-padding);
}

/* The phone number's dial code: a non-editable accent prefix on the value line. */
.dial {
  flex-shrink: 0;
  color: var(--accent);
  font-weight: var(--type-button-weight);
}

/* iOS: "Fix the fields below to save", 13 semibold danger, above the fields. */
.fix-banner {
  margin: 0;
  color: var(--danger);
  font-size: 13px;
  font-weight: 600;
}

/* iOS `Toggle` with a 15 medium label. */
.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--type-row-title-size);
  font-weight: var(--type-row-title-weight);
}
</style>
