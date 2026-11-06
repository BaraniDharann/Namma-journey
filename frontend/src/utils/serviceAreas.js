// Where a travels owner runs trips, as the codes the server stores (ServiceAreas.java):
// INDIA, STATE:<state>, CITY:<state>:<city> (state may be empty for a town typed in).
import { STATES, ALIASES } from '../data/indiaPlaces'

export const INDIA = 'INDIA'
export const stateCode = (state) => `STATE:${state}`
export const cityCode = (state, city) => `CITY:${state || ''}:${city}`

export function parse(code) {
  if (code === INDIA) return { kind: 'INDIA' }
  if (code.startsWith('STATE:')) return { kind: 'STATE', state: code.slice(6) }
  const [state, city] = code.slice(5).split(':')
  return { kind: 'CITY', state, city }
}

export function label(code) {
  const p = parse(code)
  if (p.kind === 'INDIA') return 'Whole of India'
  if (p.kind === 'STATE') return `All of ${p.state}`
  return p.state ? `${p.city}, ${p.state}` : p.city
}

/** A town name the server will accept: letters (any script), spaces and . ' ( ) - */
export const validPlaceName = (s) => /^[\p{L}\p{M}][\p{L}\p{M} .'()-]{0,59}$/u.test(String(s ?? '').trim())

/** Is this place already covered by what is chosen (whole India, or its whole state)? */
export function covered(selected, code) {
  if (selected.includes(INDIA)) return true
  const p = parse(code)
  return p.kind === 'CITY' && !!p.state && selected.includes(stateCode(p.state))
}

/**
 * Adds or removes one place. Whole India replaces everything; a whole state replaces its cities.
 */
export function toggle(selected, code) {
  if (selected.includes(code)) return selected.filter((c) => c !== code)
  if (code === INDIA) return [INDIA]
  if (covered(selected, code)) return selected
  const p = parse(code)
  const rest = p.kind === 'STATE'
    ? selected.filter((c) => { const q = parse(c); return !(q.kind === 'CITY' && q.state === p.state) })
    : selected
  return [...rest, code]
}

// Lower-case, Latin accents dropped; Latin, Tamil and Malayalam letters and spaces kept.
const norm = (s) => s.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, (m) => (/\p{Script=Latin}|\p{Script=Common}|\p{Script=Inherited}/u.test(m) ? '' : m))
  .normalize('NFC').replace(/[^a-z\p{Script=Tamil}\p{Script=Malayalam} ]/gu, '')

/**
 * Places matching what the owner typed: states first, then cities whose name starts with the text,
 * then cities containing it. "Madras" finds Chennai through ALIASES.
 */
export function search(query, limit = 8) {
  const q = norm(query.trim())
  if (q.length < 2) return []
  const aliased = ALIASES[q.replace(/ /g, '')]
  const out = []
  const seen = new Set()
  const push = (item) => { if (!seen.has(item.code)) { seen.add(item.code); out.push(item) } }

  if ('india'.startsWith(q) || q === 'all india' || q === 'whole india') push({ code: INDIA, title: 'Whole of India', sub: 'Every state' })
  for (const s of STATES) {
    if (norm(s.name).startsWith(q) || norm(s.name).split(' ').some((w) => w.startsWith(q)) || s.name === aliased) {
      push({ code: stateCode(s.name), title: `All of ${s.name}`, sub: `${s.cities.length} cities and towns` })
    }
  }
  const starts = []
  const contains = []
  for (const s of STATES) {
    for (const c of s.cities) {
      const n = norm(c)
      const item = { code: cityCode(s.name, c), title: c, sub: s.name }
      if (c === aliased) starts.unshift(item)
      else if (n.startsWith(q)) starts.push(item)
      else if (n.includes(q)) contains.push(item)
    }
  }
  starts.forEach(push)
  contains.forEach(push)
  return out.slice(0, limit)
}
