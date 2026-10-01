/**
 * The landing road as data: five real places, each one road segment long, in an endless loop.
 * The car never moves; the world slides towards it, and a segment that has passed behind is
 * recycled to the far end, beyond the fog. Pure functions, so the loop maths is unit-tested.
 */

export const SEG = 240

export const BIOMES = [
  { id: 'valley', name: 'Kashmir Valley', sky: '#8ec9ef', fog: '#cfe6ee', ground: '#5b9e4e', light: 1.15 },
  { id: 'waterfall', name: 'Nohkalikai Falls', sky: '#a9cfdc', fog: '#d6e7e3', ground: '#3f8a45', light: 1.0 },
  { id: 'beach', name: 'Varkala Beach', sky: '#6cc0ec', fog: '#f4e6cf', ground: '#e8cf9a', light: 1.25 },
  { id: 'backwaters', name: 'Alappuzha backwaters', sky: '#9fd3c4', fog: '#dcefe2', ground: '#4f9a4a', light: 1.1 },
  { id: 'temple', name: 'Temple town at dusk', sky: '#f2a65a', fog: '#f6c895', ground: '#8a6a45', light: 0.85 },
]

export const LOOP = SEG * BIOMES.length

const mod = (a, n) => ((a % n) + n) % n

/**
 * How far ahead of the car segment `i`'s near edge is, at road offset `offset` (world units
 * driven). Negative while the car is still on it; once it is fully behind it jumps to the far end.
 */
export function segmentDistance(i, offset) {
  let d = mod(i * SEG - offset, LOOP)
  if (d > LOOP - SEG) d -= LOOP
  return d
}

/** The place under the car and how far through it (0..1). */
export function biomeAt(offset) {
  const o = mod(offset, LOOP)
  const index = Math.floor(o / SEG)
  return { index, biome: BIOMES[index], local: (o - index * SEG) / SEG }
}

const toRgb = (hex) => [0, 2, 4].map((i) => parseInt(hex.replace('#', '').slice(i, i + 2), 16))
const toHex = (rgb) => '#' + rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')

export function mixHex(a, b, t) {
  const x = toRgb(a), y = toRgb(b)
  const k = Math.min(1, Math.max(0, t))
  return toHex(x.map((v, i) => v + (y[i] - v) * k))
}

/** Last share of a segment over which its sky and fog blend into the next place's. */
const BLEND = 0.12

/** Sky, fog and ground colour (and light level) the car sees at `offset`. */
export function lookAt(offset) {
  const { index, local } = biomeAt(offset)
  const a = BIOMES[index]
  const b = BIOMES[(index + 1) % BIOMES.length]
  const t = local < 1 - BLEND ? 0 : (local - (1 - BLEND)) / BLEND
  if (t <= 0) return { sky: a.sky, fog: a.fog, ground: a.ground, light: a.light }
  return {
    sky: mixHex(a.sky, b.sky, t),
    fog: mixHex(a.fog, b.fog, t),
    ground: mixHex(a.ground, b.ground, t),
    light: a.light + (b.light - a.light) * t,
  }
}
