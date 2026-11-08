// Builds dist/chrome and dist/firefox. Three Vite builds: the popup (Vue), and the
// background and content scripts as single self-contained IIFE files (content scripts
// can't import modules, and one file keeps the Firefox background simple too).
//   bun scripts/build.ts          one build
//   bun scripts/build.ts --watch  rebuild on change (both targets stay in sync)
import vue from '@vitejs/plugin-vue'
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { build, type InlineConfig, type Plugin } from 'vite'

import { aliases, contentStrings, ROOT } from './aliases'
import { chromeManifest, firefoxManifest } from './manifest'

const watch = process.argv.includes('--watch')
const CHROME = join(ROOT, 'dist/chrome')
const FIREFOX = join(ROOT, 'dist/firefox')
const version = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8')).version as string

/** After every (re)build: static files and manifests, then the Firefox copy. */
function finish(): void {
  cpSync(join(ROOT, 'static'), CHROME, { recursive: true })
  writeFileSync(join(CHROME, 'manifest.json'), JSON.stringify(chromeManifest(version), null, 2))
  rmSync(FIREFOX, { recursive: true, force: true })
  cpSync(CHROME, FIREFOX, { recursive: true })
  writeFileSync(join(FIREFOX, 'manifest.json'), JSON.stringify(firefoxManifest(version), null, 2))
}

const syncPlugin: Plugin = { name: 'qasa-sync-targets', closeBundle: finish }

const shared: InlineConfig = {
  configFile: false,
  logLevel: 'warn',
  resolve: { alias: aliases, dedupe: ['vue', 'lucide-vue-next'] },
  define: { 'process.env.NODE_ENV': JSON.stringify(watch ? 'development' : 'production') },
}

function script(entry: string, fileName: string): InlineConfig {
  return {
    ...shared,
    plugins: [contentStrings(), syncPlugin],
    build: {
      outDir: CHROME,
      emptyOutDir: false,
      target: 'es2022',
      minify: !watch,
      watch: watch ? {} : null,
      lib: { entry: join(ROOT, entry), formats: ['iife'], name: fileName.replace('.js', ''), fileName: () => fileName },
    },
  }
}

rmSync(join(ROOT, 'dist'), { recursive: true, force: true })
mkdirSync(CHROME, { recursive: true })

await build({
  ...shared,
  root: join(ROOT, 'src/popup'),
  base: './',
  plugins: [vue(), syncPlugin],
  build: {
    outDir: CHROME,
    emptyOutDir: false,
    target: 'es2022',
    watch: watch ? {} : null,
    rollupOptions: { input: join(ROOT, 'src/popup/popup.html') },
  },
})
await build(script('src/background/index.ts', 'background.js'))
await build(script('src/content/index.ts', 'content.js'))
if (!watch) console.log('Built dist/chrome and dist/firefox')
