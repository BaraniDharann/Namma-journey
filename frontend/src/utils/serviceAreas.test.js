import { describe, it, expect } from 'vitest'
import { INDIA, stateCode, cityCode, label, toggle, covered, search, validPlaceName } from './serviceAreas'
import { STATES } from '../data/indiaPlaces'

describe('owner service areas', () => {
  it('lists all 28 states and 8 union territories, each with at least one city', () => {
    expect(STATES).toHaveLength(36)
    expect(new Set(STATES.map((s) => s.name)).size).toBe(36)
    STATES.forEach((s) => expect(s.cities.length).toBeGreaterThan(0))
  })

  it('whole India replaces everything; a whole state replaces its cities', () => {
    const madurai = cityCode('Tamil Nadu', 'Madurai')
    const kochi = cityCode('Kerala', 'Kochi')
    let sel = toggle(toggle([], madurai), kochi)
    sel = toggle(sel, stateCode('Tamil Nadu'))
    expect(sel).toEqual([kochi, stateCode('Tamil Nadu')])
    expect(covered(sel, madurai)).toBe(true)
    expect(toggle(sel, madurai)).toBe(sel) // already covered by the state
    expect(toggle(sel, INDIA)).toEqual([INDIA])
    expect(toggle([INDIA], INDIA)).toEqual([])
  })

  it('labels read naturally', () => {
    expect(label(INDIA)).toBe('Whole of India')
    expect(label(stateCode('Kerala'))).toBe('All of Kerala')
    expect(label(cityCode('Tamil Nadu', 'Madurai'))).toBe('Madurai, Tamil Nadu')
    expect(label(cityCode('', 'Ooty Hills'))).toBe('Ooty Hills')
  })

  it('search finds states, cities by start or middle, and old names', () => {
    expect(search('tamil')[0].code).toBe(stateCode('Tamil Nadu'))
    expect(search('madu').map((r) => r.title)).toContain('Madurai')
    expect(search('madras')[0].code).toBe(cityCode('Tamil Nadu', 'Chennai'))
    expect(search('bangalore')[0].title).toBe('Bengaluru')
    expect(search('india')[0].code).toBe(INDIA)
    expect(search('k')).toEqual([])
  })

  it('a typed-in town must look like a place name', () => {
    expect(validPlaceName('Thiruvaiyaru')).toBe(true)
    expect(validPlaceName('மதுரை')).toBe(true)
    expect(validPlaceName('a,b')).toBe(false)
    expect(validPlaceName('x:y')).toBe(false)
  })
})
