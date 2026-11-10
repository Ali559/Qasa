// `bun run package`: the store uploads, in release/.
//   qasa-chrome-<ver>.zip            Chrome Web Store and Edge Add-ons (same zip)
//   qasa-firefox-<ver>.zip           Firefox Add-ons (AMO)
//   qasa-extension-source-<ver>.zip  the source AMO asks for, since the code is bundled
// Type-checks, runs the store build (scripts/build.ts --store: no Chrome `key`, version from
// package.json), then zips with the `zip` CLI (preinstalled on macOS and the CI runners).
import { spawnSync } from 'node:child_process'
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'

import { DESKTOP_SRC, ROOT } from './aliases'

const version = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8')).version as string
const RELEASE = join(ROOT, 'release')
const UNPACKED = join(RELEASE, 'unpacked')
const bunVersion = process.versions.bun ?? '1.x'

function run(cmd: string, args: string[], cwd = ROOT): string {
  const r = spawnSync(cmd, args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })
  if (r.status !== 0) {
    process.stdout.write(r.stdout ?? '')
    throw new Error(`${cmd} ${args.join(' ')} failed (${r.status ?? r.error})`)
  }
  return r.stdout
}

/** Zips the contents of `dir` (not the folder itself) into `zipPath`; -X drops extra file attributes. */
function zipDir(dir: string, zipPath: string): void {
  rmSync(zipPath, { force: true })
  run('zip', ['-X', '-r', '-q', zipPath, '.', '-x', '.DS_Store', '*/.DS_Store'], dir)
}

// 1. Type-check, and collect the desktop files the type-checker reads (type-only imports
//    are erased from the bundles, but `bun run build` needs them to type-check).
const listed = run('bunx', ['vue-tsc', '--noEmit', '-p', 'tsconfig.json', '--listFiles'])
const desktopFiles = new Set<string>()
for (const line of listed.split('\n')) {
  const file = resolve(ROOT, line.trim())
  if (file.startsWith(DESKTOP_SRC)) desktopFiles.add(relative(DESKTOP_SRC, file))
}

// 2. The store build, into release/unpacked/{chrome,firefox}.
rmSync(RELEASE, { recursive: true, force: true })
mkdirSync(RELEASE, { recursive: true })
run('bun', ['scripts/build.ts', '--store'])
for (const f of JSON.parse(readFileSync(join(RELEASE, 'desktop-modules.json'), 'utf8')) as string[]) desktopFiles.add(f)
rmSync(join(RELEASE, 'desktop-modules.json'))

// Fonts and other files the bundled CSS pulls in with url(...).
for (const f of [...desktopFiles]) {
  if (!/\.(css|vue)$/.test(f)) continue
  const text = readFileSync(join(DESKTOP_SRC, f), 'utf8')
  for (const m of text.matchAll(/url\(\s*['"]?([^'")]+)['"]?\s*\)/g)) {
    const ref = m[1]
    if (/^(data:|https?:|#)/.test(ref)) continue
    const target = ref.startsWith('@/') ? join(DESKTOP_SRC, ref.slice(2)) : resolve(DESKTOP_SRC, dirname(f), ref)
    if (target.startsWith(DESKTOP_SRC) && existsSync(target)) desktopFiles.add(relative(DESKTOP_SRC, target))
  }
}
// The content script's strings are read straight from the catalogs (scripts/aliases.ts).
for (const lang of ['en', 'ar', 'ckb']) desktopFiles.add(`i18n/${lang}.json`)

// 3. The two store zips.
const chromeZip = join(RELEASE, `qasa-chrome-${version}.zip`)
const firefoxZip = join(RELEASE, `qasa-firefox-${version}.zip`)
zipDir(join(UNPACKED, 'chrome'), chromeZip)
zipDir(join(UNPACKED, 'firefox'), firefoxZip)

// 4. The source zip: apps/extension (minus build output, dependencies and the dev key) and
//    the apps/desktop/src files it aliases in, laid out as in the monorepo.
const EXCLUDE = new Set(['node_modules', 'dist', 'release', 'test-results', 'playwright-report', '.dev-key.pem', '.DS_Store'])
const stage = join(RELEASE, 'source')
const extOut = join(stage, 'apps/extension')

function copyTree(from: string, to: string): void {
  for (const name of readdirSync(from)) {
    if (EXCLUDE.has(name) || name.endsWith('.tsbuildinfo')) continue
    const src = join(from, name)
    if (statSync(src).isDirectory()) copyTree(src, join(to, name))
    else {
      mkdirSync(to, { recursive: true })
      cpSync(src, join(to, name))
    }
  }
}
copyTree(ROOT, extOut)
const desktopList = [...desktopFiles].sort()
for (const f of desktopList) {
  const to = join(stage, 'apps/desktop/src', f)
  mkdirSync(dirname(to), { recursive: true })
  cpSync(join(DESKTOP_SRC, f), to)
}

writeFileSync(
  join(stage, 'SOURCE-BUILD.md'),
  `# Building the Qasa extension ${version} from source

The extension's code is TypeScript and Vue, bundled with Vite. The popup reuses components,
styles, fonts and translations from the Qasa desktop app, so this archive keeps the
monorepo layout:

- \`apps/extension/\`: the extension (build scripts in \`apps/extension/scripts/\`).
- \`apps/desktop/src/\`: only the desktop files the extension imports (the \`@/\` alias in
  \`apps/extension/scripts/aliases.ts\` and \`tsconfig.json\`).

## Requirements

- macOS or Linux (x64 or arm64).
- [Bun](https://bun.sh) ${bunVersion} (\`curl -fsSL https://bun.sh/install | bash -s "bun-v${bunVersion}"\`).
  Bun installs the exact dependency versions from \`apps/extension/bun.lock\`.
- No other tools; the build needs no network access after \`bun install\`.

## Steps

\`\`\`sh
cd apps/extension
bun install --frozen-lockfile
bun run build:store
\`\`\`

\`build:store\` type-checks (\`vue-tsc\`), then runs \`bun scripts/build.ts --store\`, which
writes:

- \`apps/extension/release/unpacked/firefox/\`: the contents of \`qasa-firefox-${version}.zip\`.
- \`apps/extension/release/unpacked/chrome/\`: the contents of \`qasa-chrome-${version}.zip\`.

To compare with the submitted package:

\`\`\`sh
mkdir /tmp/qasa-submitted && unzip -q qasa-firefox-${version}.zip -d /tmp/qasa-submitted
diff -r /tmp/qasa-submitted apps/extension/release/unpacked/firefox
\`\`\`

## What is bundled

- \`popup.html\` and \`assets/popup-*.js|css\`: the toolbar popup (\`src/popup/\`, Vue 3).
- \`background.js\`: the background script (\`src/background/\`).
- \`content.js\`: the content script (\`src/content/\`).
- \`fonts/*.ttf\`: the IBM Plex Sans Arabic fonts, four weights (\`apps/desktop/src/assets/fonts/\`).
- \`_locales/\`, \`icons/\`: copied from \`apps/extension/static/\` unchanged.

Third-party code in the bundles: \`vue\` and \`lucide-vue-next\` (versions in \`bun.lock\`).
Nothing is fetched at run time; the extension talks only to the Qasa desktop app through
native messaging.
`,
)

const sourceZip = join(RELEASE, `qasa-extension-source-${version}.zip`)
zipDir(stage, sourceZip)
rmSync(stage, { recursive: true, force: true })

console.log(`\nrelease/ (${version}):`)
for (const z of [chromeZip, firefoxZip, sourceZip]) {
  console.log(`  ${relative(ROOT, z)}  ${(statSync(z).size / 1024).toFixed(0)} KB`)
}
console.log(`  apps/desktop/src files in the source zip: ${desktopList.length}`)
