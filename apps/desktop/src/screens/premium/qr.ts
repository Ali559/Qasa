// A small QR Code encoder (byte mode, error correction M, versions 1–40), so a wallet
// deep link (`fib://…`) can be scanned with the phone. Follows ISO/IEC 18004 the way
// Nayuki's reference generator does; no dependency.

/** M-level error-correction codewords per block, indexed by version. */
const ECC_PER_BLOCK = [
  -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28,
  28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
]
/** M-level error-correction block count, indexed by version. */
const ECC_BLOCKS = [
  -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35,
  37, 38, 40, 43, 45, 47, 49,
]
/** Format-info bits for level M. */
const ECC_FORMAT_BITS = 0

function rawDataModules(ver: number): number {
  let result = (16 * ver + 128) * ver + 64
  if (ver >= 2) {
    const numAlign = Math.floor(ver / 7) + 2
    result -= (25 * numAlign - 10) * numAlign - 55
    if (ver >= 7) result -= 36
  }
  return result
}

function dataCodewords(ver: number): number {
  return Math.floor(rawDataModules(ver) / 8) - ECC_PER_BLOCK[ver] * ECC_BLOCKS[ver]
}

function gfMul(x: number, y: number): number {
  let z = 0
  for (let i = 7; i >= 0; i--) {
    z = (z << 1) ^ ((z >>> 7) * 0x11d)
    z ^= ((y >>> i) & 1) * x
  }
  return z
}

function rsDivisor(degree: number): number[] {
  const result = new Array<number>(degree).fill(0)
  result[degree - 1] = 1
  let root = 1
  for (let i = 0; i < degree; i++) {
    for (let j = 0; j < result.length; j++) {
      result[j] = gfMul(result[j], root)
      if (j + 1 < result.length) result[j] ^= result[j + 1]
    }
    root = gfMul(root, 0x02)
  }
  return result
}

function rsRemainder(data: number[], divisor: number[]): number[] {
  const result = divisor.map(() => 0)
  for (const b of data) {
    const factor = b ^ (result.shift() as number)
    result.push(0)
    divisor.forEach((coef, i) => (result[i] ^= gfMul(coef, factor)))
  }
  return result
}

function alignmentPositions(ver: number, size: number): number[] {
  if (ver === 1) return []
  const numAlign = Math.floor(ver / 7) + 2
  const step = ver === 32 ? 26 : Math.ceil((ver * 4 + 4) / (numAlign * 2 - 2)) * 2
  const result = [6]
  for (let pos = size - 7; result.length < numAlign; pos -= step) result.splice(1, 0, pos)
  return result
}

const bit = (x: number, i: number) => ((x >>> i) & 1) !== 0

/** The QR modules for `text` (true = dark), row by row, without the quiet zone. */
export function encodeQr(text: string): boolean[][] {
  const bytes = Array.from(new TextEncoder().encode(text))

  let ver = 1
  for (; ; ver++) {
    if (ver > 40) throw new Error('Text too long for a QR code')
    const countBits = ver <= 9 ? 8 : 16
    if (4 + countBits + bytes.length * 8 <= dataCodewords(ver) * 8) break
  }

  // Data bits: byte mode, length, payload, terminator, padding.
  const bits: number[] = []
  const push = (val: number, len: number) => {
    for (let i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1)
  }
  const capacity = dataCodewords(ver) * 8
  push(0b0100, 4)
  push(bytes.length, ver <= 9 ? 8 : 16)
  bytes.forEach((b) => push(b, 8))
  push(0, Math.min(4, capacity - bits.length))
  push(0, (8 - (bits.length % 8)) % 8)
  for (let pad = 0xec; bits.length < capacity; pad ^= 0xec ^ 0x11) push(pad, 8)
  const data: number[] = []
  for (let i = 0; i < bits.length; i += 8) data.push(bits.slice(i, i + 8).reduce((a, b) => (a << 1) | b, 0))

  // Split into blocks, add Reed–Solomon codewords and interleave.
  const numBlocks = ECC_BLOCKS[ver]
  const eccLen = ECC_PER_BLOCK[ver]
  const rawCodewords = Math.floor(rawDataModules(ver) / 8)
  const numShort = numBlocks - (rawCodewords % numBlocks)
  const shortLen = Math.floor(rawCodewords / numBlocks)
  const divisor = rsDivisor(eccLen)
  const blocks: number[][] = []
  for (let i = 0, k = 0; i < numBlocks; i++) {
    const dat = data.slice(k, k + shortLen - eccLen + (i < numShort ? 0 : 1))
    k += dat.length
    const ecc = rsRemainder(dat, divisor)
    if (i < numShort) dat.push(0)
    blocks.push(dat.concat(ecc))
  }
  const codewords: number[] = []
  for (let i = 0; i < blocks[0].length; i++) {
    blocks.forEach((block, j) => {
      if (i !== shortLen - eccLen || j >= numShort) codewords.push(block[i])
    })
  }

  const size = ver * 4 + 17
  const modules = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))
  const isFunction = Array.from({ length: size }, () => new Array<boolean>(size).fill(false))
  const setFunction = (x: number, y: number, dark: boolean) => {
    modules[y][x] = dark
    isFunction[y][x] = true
  }

  // Function patterns: timing, finders, alignment, format (placeholder), version.
  for (let i = 0; i < size; i++) {
    setFunction(6, i, i % 2 === 0)
    setFunction(i, 6, i % 2 === 0)
  }
  for (const [cx, cy] of [
    [3, 3],
    [size - 4, 3],
    [3, size - 4],
  ]) {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dx = -4; dx <= 4; dx++) {
        const dist = Math.max(Math.abs(dx), Math.abs(dy))
        const x = cx + dx
        const y = cy + dy
        if (x >= 0 && x < size && y >= 0 && y < size) setFunction(x, y, dist !== 2 && dist !== 4)
      }
    }
  }
  const align = alignmentPositions(ver, size)
  for (let i = 0; i < align.length; i++) {
    for (let j = 0; j < align.length; j++) {
      const last = align.length - 1
      if ((i === 0 && j === 0) || (i === 0 && j === last) || (i === last && j === 0)) continue
      for (let dy = -2; dy <= 2; dy++) {
        for (let dx = -2; dx <= 2; dx++) setFunction(align[i] + dx, align[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1)
      }
    }
  }

  const drawFormat = (mask: number) => {
    const d = (ECC_FORMAT_BITS << 3) | mask
    let rem = d
    for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537)
    const f = ((d << 10) | rem) ^ 0x5412
    for (let i = 0; i <= 5; i++) setFunction(8, i, bit(f, i))
    setFunction(8, 7, bit(f, 6))
    setFunction(8, 8, bit(f, 7))
    setFunction(7, 8, bit(f, 8))
    for (let i = 9; i < 15; i++) setFunction(14 - i, 8, bit(f, i))
    for (let i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(f, i))
    for (let i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(f, i))
    setFunction(8, size - 8, true)
  }
  drawFormat(0)

  if (ver >= 7) {
    let rem = ver
    for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25)
    const v = (ver << 12) | rem
    for (let i = 0; i < 18; i++) {
      const a = size - 11 + (i % 3)
      const b = Math.floor(i / 3)
      setFunction(a, b, bit(v, i))
      setFunction(b, a, bit(v, i))
    }
  }

  // Codewords in the zigzag order.
  let i = 0
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5
    for (let vert = 0; vert < size; vert++) {
      for (let j = 0; j < 2; j++) {
        const x = right - j
        const upward = ((right + 1) & 2) === 0
        const y = upward ? size - 1 - vert : vert
        if (!isFunction[y][x] && i < codewords.length * 8) {
          modules[y][x] = bit(codewords[i >>> 3], 7 - (i & 7))
          i++
        }
      }
    }
  }

  const applyMask = (mask: number) => {
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        let invert: boolean
        switch (mask) {
          case 0: invert = (x + y) % 2 === 0; break
          case 1: invert = y % 2 === 0; break
          case 2: invert = x % 3 === 0; break
          case 3: invert = (x + y) % 3 === 0; break
          case 4: invert = (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0; break
          case 5: invert = ((x * y) % 2) + ((x * y) % 3) === 0; break
          case 6: invert = (((x * y) % 2) + ((x * y) % 3)) % 2 === 0; break
          default: invert = (((x + y) % 2) + ((x * y) % 3)) % 2 === 0
        }
        if (!isFunction[y][x] && invert) modules[y][x] = !modules[y][x]
      }
    }
  }

  // Pick the mask with the lowest penalty (runs, 2×2 blocks, finder look-alikes, balance).
  let best = 0
  let bestPenalty = Infinity
  for (let mask = 0; mask < 8; mask++) {
    applyMask(mask)
    drawFormat(mask)
    const p = penalty(modules)
    if (p < bestPenalty) {
      best = mask
      bestPenalty = p
    }
    applyMask(mask) // XOR again to undo
  }
  applyMask(best)
  drawFormat(best)
  return modules
}

function penalty(m: boolean[][]): number {
  const size = m.length
  let result = 0
  const finderLike = (line: boolean[]) => {
    let count = 0
    const pattern = [true, false, true, true, true, false, true]
    for (let i = 0; i + 7 <= line.length; i++) {
      if (!pattern.every((p, k) => line[i + k] === p)) continue
      const before = i >= 4 && line.slice(i - 4, i).every((v) => !v)
      const after = i + 11 <= line.length && line.slice(i + 7, i + 11).every((v) => !v)
      if (before || after) count++
    }
    return count
  }
  const lines: boolean[][] = []
  for (let y = 0; y < size; y++) lines.push(m[y])
  for (let x = 0; x < size; x++) lines.push(m.map((row) => row[x]))
  for (const line of lines) {
    let run = 1
    for (let i = 1; i <= line.length; i++) {
      if (i < line.length && line[i] === line[i - 1]) run++
      else {
        if (run >= 5) result += run - 2
        run = 1
      }
    }
    result += finderLike(line) * 40
  }
  let dark = 0
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (m[y][x]) dark++
      if (x + 1 < size && y + 1 < size) {
        const c = m[y][x]
        if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) result += 3
      }
    }
  }
  const total = size * size
  const k = Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1
  result += Math.max(k, 0) * 10
  return result
}

/** An SVG path (one unit per module, quiet zone `margin`) for the dark modules. */
export function qrPath(modules: boolean[][], margin = 4): { path: string; size: number } {
  let path = ''
  modules.forEach((row, y) =>
    row.forEach((dark, x) => {
      if (dark) path += `M${x + margin} ${y + margin}h1v1h-1z`
    }),
  )
  return { path, size: modules.length + margin * 2 }
}
