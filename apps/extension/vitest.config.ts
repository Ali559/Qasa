import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

import { aliases, contentStrings } from './scripts/aliases'

export default defineConfig({
  plugins: [vue(), contentStrings()],
  resolve: { alias: aliases, dedupe: ['vue', 'lucide-vue-next'] },
  test: {
    include: ['test/unit/**/*.test.ts'],
    environment: 'node',
  },
})
