// Builds nm-host (apps/desktop/nm-host) for the target Tauri is building and puts it where
// `bundle.externalBin` expects it: src-tauri/binaries/qasa-nm-host-<target triple>[.exe].
// Tauri then ships it next to the app's executable, where the app finds it to register
// with the browsers (src-tauri/src/bridge/manifest.rs). Runs before `tauri dev` and
// `tauri build` (tauri.conf.json), which pass the target in TAURI_ENV_TARGET_TRIPLE.
import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const HOST = join(ROOT, 'nm-host')
const OUT = join(ROOT, 'src-tauri', 'binaries')

const hostTriple = () =>
  execFileSync('rustc', ['-vV'], { encoding: 'utf8' })
    .split('\n')
    .find((l) => l.startsWith('host:'))!
    .slice(5)
    .trim()

const triple = process.env.TAURI_ENV_TARGET_TRIPLE || hostTriple()
const release = process.env.TAURI_ENV_DEBUG !== 'true' && !process.argv.includes('--debug')
const profile = release ? 'release' : 'debug'
const exe = triple.includes('windows') ? '.exe' : ''
const env = { ...process.env, CARGO_INCREMENTAL: '0' }

function build(target: string): string {
  const args = ['build', '--target', target, ...(release ? ['--release'] : [])]
  execFileSync('cargo', args, { cwd: HOST, stdio: 'inherit', env })
  return join(HOST, 'target', target, profile, `qasa-nm-host${exe}`)
}

mkdirSync(OUT, { recursive: true })
const dest = join(OUT, `qasa-nm-host-${triple}${exe}`)
if (triple === 'universal-apple-darwin') {
  // One binary for Apple silicon and Intel, like the app itself. Tauri compiles the app
  // once per architecture, and each of those builds checks for its own sidecar, so the
  // single-architecture copies are needed too.
  const archs = ['aarch64-apple-darwin', 'x86_64-apple-darwin']
  const parts = archs.map(build)
  archs.forEach((arch, i) => copyFileSync(parts[i], join(OUT, `qasa-nm-host-${arch}`)))
  execFileSync('lipo', ['-create', '-output', dest, ...parts], { stdio: 'inherit' })
} else {
  copyFileSync(build(triple), dest)
}
console.log(`nm-host → ${dest}`)
