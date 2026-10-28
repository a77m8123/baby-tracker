// Рисует иконку приложения (месяц и звезда, как в Android-версии) и сохраняет PNG.
// Без зависимостей: растеризация с антиалиасингом + простой PNG-кодировщик на zlib.
// Запуск: npm run icons

import { writeFileSync, mkdirSync } from 'node:fs'
import { deflateSync } from 'node:zlib'

const BG = [0xb8, 0xc8, 0xf0]
const FG = [0xff, 0xf8, 0xe7]

// Координаты из ic_launcher_foreground.xml (холст 108×108), но крупнее:
// на iPhone иконка без отступов адаптивной иконки Android.
const SCALE = 1.25
const C = 54

const tx = (x) => (x - C) * SCALE + C

// месяц: большой круг минус смещённый круг
const inMoon = (x, y) => {
  const outer = (x - tx(60)) ** 2 + (y - tx(54)) ** 2 <= (24 * SCALE) ** 2
  const inner = (x - tx(66)) ** 2 + (y - tx(50)) ** 2 <= (20 * SCALE) ** 2
  return outer && !inner
}

// звезда из pathData: M38,34 l3.2,6.6 7.3,1.0 ...
const starRel = [[3.2, 6.6], [7.3, 1.0], [-5.3, 5.1], [1.3, 7.2], [-6.5, -3.4], [-6.5, 3.4], [1.3, -7.2], [-5.3, -5.1], [7.3, -1.0]]
const star = [[38, 34]]
for (const [dx, dy] of starRel) {
  const [px, py] = star[star.length - 1]
  star.push([px + dx, py + dy])
}
const starPts = star.map(([x, y]) => [tx(x), tx(y)])

const inPoly = (x, y, pts) => {
  let inside = false
  for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i]
    const [xj, yj] = pts[j]
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

function render(size) {
  const ss = 4 // 4×4 субпикселя на пиксель
  const rows = []
  for (let py = 0; py < size; py++) {
    const row = Buffer.alloc(1 + size * 3)
    for (let px = 0; px < size; px++) {
      let hits = 0
      for (let sy = 0; sy < ss; sy++) {
        for (let sx = 0; sx < ss; sx++) {
          const x = ((px + (sx + 0.5) / ss) / size) * 108
          const y = ((py + (sy + 0.5) / ss) / size) * 108
          if (inMoon(x, y) || inPoly(x, y, starPts)) hits++
        }
      }
      const a = hits / (ss * ss)
      for (let c = 0; c < 3; c++) row[1 + px * 3 + c] = Math.round(BG[c] * (1 - a) + FG[c] * a)
    }
    rows.push(row)
  }
  return png(size, size, Buffer.concat(rows))
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})
const crc32 = (buf) => {
  let c = 0xffffffff
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

function png(w, h, raw) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8 // бит на канал
  ihdr[9] = 2 // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

const out = new URL('../public/icons/', import.meta.url)
mkdirSync(out, { recursive: true })
for (const size of [180, 192, 512, 1024]) {
  writeFileSync(new URL(`icon-${size}.png`, out), render(size))
  console.log(`icon-${size}.png`)
}
