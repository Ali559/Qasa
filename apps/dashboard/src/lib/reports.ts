import type { Timeseries } from '@/api/admin'

const GATEWAY_META: Record<string, { name: string; color: string }> = {
  fib: { name: 'FIB', color: 'var(--chart-1)' },
  fastpay: { name: 'FastPay', color: 'var(--chart-3)' },
  superqi: { name: 'SuperQi', color: 'var(--chart-2)' },
  wayl: { name: 'Wayl', color: 'var(--chart-4)' },
}

export function gatewayLabel(gateway: string) {
  return GATEWAY_META[gateway.toLowerCase()]?.name ?? gateway
}

export function gatewayColor(gateway: string) {
  const key = gateway.toLowerCase()
  return GATEWAY_META[key]?.color ?? 'var(--muted-foreground)'
}

/** Revenue share per gateway, in whole percents, for the GatewayMix bar. */
export function gatewayShares(mix: Timeseries['gateway_mix']) {
  const total = mix.reduce((sum, g) => sum + g.total_iqd, 0)
  if (total <= 0) return []
  return [...mix]
    .sort((a, b) => b.total_iqd - a.total_iqd)
    .map((g) => ({
      name: gatewayLabel(g.gateway),
      value: Math.round((g.total_iqd / total) * 100),
      color: gatewayColor(g.gateway),
    }))
}

/** New paid subscriptions per calendar month, last `months` months (oldest first). */
export function monthlyNewPaid(ts: Timeseries, months = 7) {
  const now = new Date()
  const buckets: { key: string; month: string; subscribers: number }[] = []
  for (let i = months - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    buckets.push({
      key: `${d.getFullYear()}-${d.getMonth()}`,
      month: d.toLocaleDateString('en-US', { month: 'short' }),
      subscribers: 0,
    })
  }
  for (const row of ts.new_paid_daily) {
    const d = new Date(row.day)
    const b = buckets.find((x) => x.key === `${d.getFullYear()}-${d.getMonth()}`)
    if (b) b.subscribers += row.count
  }
  return buckets.map(({ month, subscribers }) => ({ month, subscribers }))
}

/** Download rows as a CSV file. */
export function downloadCsv(filename: string, rows: Record<string, unknown>[]) {
  if (rows.length === 0) return
  const headers = Object.keys(rows[0]!)
  const escape = (v: unknown) => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const csv = [headers.join(','), ...rows.map((r) => headers.map((h) => escape(r[h])).join(','))].join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
