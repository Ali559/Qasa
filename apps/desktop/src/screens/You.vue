<script setup lang="ts">
// iOS `YouView`: profile card and grouped rows. Notifications and VPN join Tools, and the
// upgrade button the profile card, when the account features arrive (phase 2).
import { FileText, Folder, Hand, HeartPulse, Settings as SettingsIcon, User, WandSparkles } from 'lucide-vue-next'
import { computed, type Component } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import NavChevronRow from '@/components/NavChevronRow.vue'
import PageFrame from '@/components/PageFrame.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import { openPage, type AppPage } from '@/nav'
import { useT } from '@/prefs'

const t = useT()

const PAGE_TITLE: Record<AppPage, string> = {
  generator: 'nav.generator',
  passwordHealth: 'nav.passwordHealth',
  collections: 'nav.collections',
  settings: 'nav.settings',
  terms: 'nav.terms',
  privacy: 'nav.privacy',
}

const PAGE_ICON: Record<AppPage, Component> = {
  generator: WandSparkles,
  passwordHealth: HeartPulse,
  collections: Folder,
  settings: SettingsIcon,
  terms: FileText,
  privacy: Hand,
}

const groups = computed(() => [
  { label: t('nav.tools'), pages: ['passwordHealth', 'generator', 'collections'] as AppPage[] },
  { label: t('you.account'), pages: ['settings'] as AppPage[] },
  { label: t('you.about'), pages: ['terms', 'privacy'] as AppPage[] },
])
</script>

<template>
  <PageFrame :title="t('tab.you')">
    <CardContainer>
      <div class="profile">
        <span class="avatar"><User :size="26" fill="currentColor" /></span>
        <div class="who">
          <span class="name">{{ t('session.qasaUser') }}</span>
          <span class="plan">{{ t('settings.freeAccount') }}</span>
        </div>
      </div>
    </CardContainer>

    <section v-for="group in groups" :key="group.label" class="group">
      <SectionLabel :text="group.label" />
      <CardContainer>
        <NavChevronRow
          v-for="page in group.pages"
          :key="page"
          :icon="PAGE_ICON[page]"
          :title="t(PAGE_TITLE[page])"
          @click="openPage(page)"
        />
      </CardContainer>
    </section>
  </PageFrame>
</template>

<style scoped>
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
