import { describe, expect, it } from 'vitest'
import { findBusTown, pickTown, townCandidates, townKey } from './busTownMatch'

describe('townKey', () => {
  it('treats common Tamil spelling variants as the same town', () => {
    expect(townKey('Kancheepuram')).toBe(townKey('Kanchipuram'))
    expect(townKey('Thiruvallur')).toBe(townKey('Tiruvallur'))
    expect(townKey('Coimbatore')).toBe(townKey('coimbatore'))
  })

  it('drops spot words like bus stand', () => {
    expect(townKey('Kanchipuram Bus Stand')).toBe(townKey('Kanchipuram'))
  })

  it('keeps different towns apart', () => {
    expect(townKey('Chennai')).not.toBe(townKey('Chengalpattu'))
  })
})

describe('townCandidates', () => {
  it('skips state, country and PIN code parts', () => {
    expect(townCandidates('Iyangarkulam Road, Kancheepuram, Tamil Nadu, 631501, India'))
      .toEqual(['Iyangarkulam Road', 'Kancheepuram'])
  })
})

describe('pickTown', () => {
  it('finds the town whatever the spelling', () => {
    const towns = [{ id: 1, name: 'Kanchipuram' }, { id: 2, name: 'Kanchi Kamakshi' }]
    expect(pickTown('Kancheepuram', towns)).toEqual({ id: 1, name: 'Kanchipuram' })
  })
})

describe('findBusTown', () => {
  it('walks the parts until one is a bus town', async () => {
    const db = [{ id: 7, name: 'Kanchipuram', district: 'Kanchipuram' }, { id: 8, name: 'Chennai' }]
    const asked = []
    const search = async (q) => {
      asked.push(q)
      return { data: db.filter((t) => t.name.toLowerCase().startsWith(q)) }
    }
    const town = await findBusTown('Iyangarkulam Road, Kancheepuram, Tamil Nadu', search)
    expect(town?.id).toBe(7)
    expect(asked).toEqual(['iyang', 'kanch'])
  })

  it('returns null when no part is a bus town', async () => {
    expect(await findBusTown('Somewhere, Tamil Nadu', async () => ({ data: [] }))).toBeNull()
  })
})
