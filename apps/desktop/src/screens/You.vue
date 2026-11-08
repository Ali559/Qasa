<script setup lang="ts">
// iOS `YouView`: profile card (plan, and Upgrade on Free) and grouped rows. The VPN row
// is left out on desktop (plan decision 2).
import { Bell, FileText, Folder, Hand, HeartPulse, Settings as SettingsIcon, User, WandSparkles } from 'lucide-vue-next'
import { computed, type Component } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import NavChevronRow from '@/components/NavChevronRow.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import { account } from '@/account/store'
import { openPage, type AppPage } from '@/nav'
import { useT } from '@/prefs'

const t = useT()

const ROWS: Partial<Record<AppPage, { key: string; icon: Component }>> = {
  notifications: { key: 'nav.notifications', icon: Bell },
  passwordHealth: { key: 'nav.passwordHealth', icon: HeartPulse },
  generator: { key: 'nav.generator', icon: WandSparkles },
  collections: { key: 'nav.collections', icon: Folder },
  settings: { key: 'nav.settings', icon: SettingsIcon },
  terms: { key: 'nav.terms', icon: FileText },
  privacy: { key: 'nav.privacy', icon: Hand },
}

const groups = computed(() => [
  { label: t('nav.tools'), pages: ['notifications', 'passwordHealth', 'generator', 'collections'] as AppPage[] },
  { label: t('you.account'), pages: ['settings'] as AppPage[] },
  { label: t('you.about'), pages: ['terms', 'privacy'] as AppPage[] },
])

const premium = computed(() => account.status?.isPremium ?? false)
</script>

<template>
  <PageFrame :title="t('tab.you')">
    <CardContainer>
      <div class="profile-card">
        <div class="profile">
          <span class="avatar"><User :size="26" fill="currentColor" /></span>
          <div class="who">
            <span class="name">{{ t('session.qasaUser') }}</span>
            <span class="plan">{{ t(premium ? 'settings.premiumAccount' : 'settings.freeAccount') }}</span>
          </div>
        </div>
        <PrimaryButton v-if="!premium" :title="t('settings.upgradePremium')" @click="openPage('premiumUpgrade')" />
      </div>
    </CardContainer>

    <section v-for="group in groups" :key="group.label" class="group">
      <SectionLabel :text="group.label" />
      <CardContainer>
        <NavChevronRow
          v-for="page in group.pages"
          :key="page"
          :icon="ROWS[page]!.icon"
          :title="t(ROWS[page]!.key)"
          :trailing-text="page === 'notifications' && account.unread > 0 ? String(account.unread) : undefined"
          @click="openPage(page)"
        />
      </CardContainer>
    </section>
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
  font-size: 13px;
  color: var(--text-secondary);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
