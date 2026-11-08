// Builds dist/chrome and dist/firefox. Three Vite builds: the popup (Vue), and the
// background and content scripts as single self-contained IIFE files (content scripts
// can't import modules, and one file keeps the Firefox background simple too).
//   bun scripts/build.ts          one build
//   bun scripts/build.ts --watch  rebuild on change (both targets stay in sync)
//   bun scripts/build.ts --store  the store build: release/unpacked/{chrome,firefox}, with
//                                 no Chrome `key` (the store assigns its own id), plus the
//                                 list of apps/desktop/src files bundled in, for the source
//                                 zip (scripts/package.ts)
import vue from '@vitejs/plugin-vue'
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { build, type InlineConfig, type Plugin, type Rollup } from 'vite'

import { aliases, contentStrings, DESKTOP_SRC, ROOT } from './aliases'
import { chromeManifest, firefoxManifest } from './manifest'

const watch = process.argv.includes('--watch')
const store = process.argv.includes('--store')
const OUT = join(ROOT, store ? 'release/unpacked' : 'dist')
const CHROME = join(OUT, 'chrome')
const FIREFOX = join(OUT, 'firefox')
const version = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8')).version as string

/** After every (re)build: static files and manifests, then the Firefox copy. */
function finish(): void {
  cpSync(join(ROOT, 'static'), CHROME, { recursive: true })
  writeFileSync(join(CHROME, 'manifest.json'), JSON.stringify(chromeManifest(version, { store }), null, 2))
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

rmSync(OUT, { recursive: true, force: true })
mkdirSync(CHROME, { recursive: true })

const outputs: unknown[] = []
outputs.push(await build({
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
}))
outputs.push(await build(script('src/background/index.ts', 'background.js')))
outputs.push(await build(script('src/content/index.ts', 'content.js')))

if (store) {
  // Every apps/desktop/src module the bundles contain (scripts, .vue, CSS), for the source zip.
  const ids = new Set<string>()
  for (const out of outputs.flat() as Rollup.RollupOutput[]) {
    for (const item of out.output) {
      if (item.type !== 'chunk') continue
      for (const id of item.moduleIds) {
        const file = id.split('?')[0]
        if (!file.startsWith('\0') && file.startsWith(DESKTOP_SRC)) ids.add(relative(DESKTOP_SRC, file))
      }
    }
  }
  writeFileSync(join(ROOT, 'release/desktop-modules.json'), JSON.stringify([...ids].sort(), null, 2))
}
if (!watch) console.log(`Built ${relative(ROOT, CHROME)} and ${relative(ROOT, FIREFOX)}`)
