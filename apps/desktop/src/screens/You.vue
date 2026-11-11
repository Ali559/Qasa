<script setup lang="ts">
// iOS `YouView`: profile card (plan, and Upgrade on Free) and grouped rows. The VPN row
// is left out on desktop (plan decision 2). About ends with the icon library's credit
// (Solar is CC BY 4.0, design/README.md "Icons").
import { computed } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { account } from '@/account/store'
import { openPage, type AppPage } from '@/nav'
import { openUrl } from '@/opener'
import { prefs, useT } from '@/prefs'
import type { QasaIconName } from '@/icons/qasaIcons'

const t = useT()

/** Rows: app pages, plus Terms and Privacy, which open the website in the browser. */
type Row = AppPage | 'terms' | 'privacy'
const ROWS: Partial<Record<Row, { key: string; icon: QasaIconName }>> = {
  notifications: { key: 'nav.notifications', icon: 'alerts' },
  passwordHealth: { key: 'nav.passwordHealth', icon: 'health' },
  generator: { key: 'nav.generator', icon: 'generate' },
  collections: { key: 'nav.collections', icon: 'folder' },
  settings: { key: 'nav.settings', icon: 'settings' },
  terms: { key: 'nav.terms', icon: 'note' },
  privacy: { key: 'nav.privacy', icon: 'privacy' },
}

const groups = computed(() => [
  { label: t('nav.tools'), pages: ['notifications', 'passwordHealth', 'generator', 'collections'] as Row[] },
  { label: t('you.account'), pages: ['settings'] as Row[] },
  { label: t('you.about'), pages: ['terms', 'privacy'] as Row[] },
])

/** qasa-app.com/terms or /privacy, in the app's language. */
function openRow(row: Row) {
  if (row === 'terms' || row === 'privacy') {
    const prefix = prefs.language === 'en' ? '' : `/${prefs.language}`
    void openUrl(`https://qasa-app.com${prefix}/${row}`)
  } else {
    openPage(row)
  }
}

const premium = computed(() => account.status?.isPremium ?? false)
</script>

<template>
  <PageFrame :title="t('tab.you')">
    <CardContainer>
      <div class="profile-card">
        <div class="profile">
          <span class="avatar"><QasaIcon name="person" :size="26" /></span>
          <div class="who">
            <span class="name">{{ t('session.qasaUser') }}</span>
            <span class="plan">{{ t(premium ? 'settings.premiumAccount' : 'settings.freeAccount') }}</span>
          </div>
        </div>
        <PrimaryButton v-if="!premium" :title="t('settings.upgradePremium')" @click="openPage('premiumUpgrade')" />
      </div>
    </CardContainer>

    <SettingsSection v-for="group in groups" :key="group.label" :title="group.label">
      <SettingsRow
        v-for="page in group.pages"
        :key="page"
        :icon="ROWS[page]!.icon"
        :title="t(ROWS[page]!.key)"
        :trailing-text="page === 'notifications' && account.unread > 0 ? String(account.unread) : undefined"
        @click="openRow(page)"
      />
    </SettingsSection>
    <p class="credit">{{ t('you.iconsCredit') }}</p>
  </PageFrame>
</template>

<style scoped>
.profile-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: 2px solid var(--accent);
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--text-secondary);
}

.who {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.plan {
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  color: var(--text-secondary);
}

/* The licence credit under About: a caption, 4 in like the section labels. */
.credit {
  margin: 0;
  padding-inline: 4px;
  font-size: var(--type-caption-size);
  color: var(--text-tertiary);
}
</style>
