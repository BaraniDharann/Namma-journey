// Turns a map place ("Kanchipuram Bus Stand, Kancheepuram, Tamil Nadu") into a bus town, so the
// booking page can show buses for the same From / To. Bus towns have only a name and district.

const STATES = new Set([
  'india', 'tamil nadu', 'tamilnadu', 'kerala', 'karnataka', 'andhra pradesh', 'telangana',
  'puducherry', 'pondicherry', 'maharashtra', 'goa',
])

// Words that name a spot inside a town, not the town.
const SPOT_WORDS = /\b(bus\s*stand|bus\s*stop|bus\s*station|cmbt|railway\s*station|junction|jn|central|new|old|main|road|rd|street|st|nagar)\b/g

/** Spelling-tolerant key: Kancheepuram / Kanchipuram, Thiruvallur / Tiruvallur end up equal. */
export function townKey(s) {
  return String(s || '')
    .toLowerCase()
    .replace(SPOT_WORDS, ' ')
    .replace(/[^a-z]/g, '')
    .replace(/th/g, 't')
    .replace(/ee/g, 'i')
    .replace(/oo/g, 'u')
    .replace(/aa/g, 'a')
    .replace(/(.)\1+/g, '$1')
}

/** The comma parts of a place worth trying as a town, most specific first. */
export function townCandidates(place) {
  return String(place || '')
    .split(',')
    .map((p) => p.trim())
    .filter((p) => p && !/^\d{6}$/.test(p) && !STATES.has(p.toLowerCase()))
    .filter((p) => townKey(p).length >= 3)
}

/** The bus town in `towns` that this part names, or null. */
export function pickTown(part, towns) {
  const key = townKey(part)
  if (!key) return null
  return (towns || []).find((t) => townKey(t.name) === key) || null
}

/**
 * Finds the bus town for a place by asking `search(text)` (the /bus/places lookup) for each
 * candidate part. The query uses the first few letters, because the stored spelling can differ
 * after them (Kanchi-puram vs Kanchee-puram); the match itself is on townKey.
 */
export async function findBusTown(place, search) {
  for (const part of townCandidates(place)) {
    const words = part.toLowerCase().replace(SPOT_WORDS, ' ').trim().split(/\s+/).filter(Boolean)
    const head = (words[0] || '').slice(0, 5)
    if (head.length < 3) continue
    let towns = []
    try { towns = (await search(head))?.data || [] } catch { towns = [] }
    const hit = pickTown(part, towns)
    if (hit) return hit
  }
  return null
}
