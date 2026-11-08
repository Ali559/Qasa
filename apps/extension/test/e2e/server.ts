// Serves test/fixtures on two origins: http://127.0.0.1:<a> and http://localhost:<b>.
// `{{OTHER_ORIGIN}}` in a page becomes the localhost origin, for the cross-origin frame.
import { readFile } from 'node:fs/promises'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { extname, join, normalize } from 'node:path'

const FIXTURES = join(import.meta.dirname, '../fixtures')
const TYPES: Record<string, string> = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' }

export interface FixtureServers {
  main: string
  other: string
  close(): Promise<void>
}

function serve(other: () => string): Server {
  return createServer(async (req, res) => {
    const path = normalize(new URL(req.url ?? '/', 'http://x').pathname).replace(/^\/+/, '')
    try {
      let body: Buffer | string = await readFile(join(FIXTURES, path || 'login.html'))
      if (path.endsWith('.html')) body = body.toString('utf8').replaceAll('{{OTHER_ORIGIN}}', other())
      res.writeHead(200, { 'content-type': TYPES[extname(path)] ?? 'application/octet-stream', 'cache-control': 'no-store' })
      res.end(body)
    } catch {
      res.writeHead(404).end('not found')
    }
  })
}

export async function startFixtureServers(): Promise<FixtureServers> {
  let other = ''
  const a = serve(() => other)
  const b = serve(() => other)
  await new Promise<void>((r) => a.listen(0, '127.0.0.1', r))
  await new Promise<void>((r) => b.listen(0, '127.0.0.1', r))
  other = `http://localhost:${(b.address() as AddressInfo).port}`
  return {
    main: `http://127.0.0.1:${(a.address() as AddressInfo).port}`,
    other,
    close: async () => {
      await new Promise((r) => a.close(r))
      await new Promise((r) => b.close(r))
    },
  }
}
