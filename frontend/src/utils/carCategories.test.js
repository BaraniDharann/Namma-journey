import { describe, it, expect } from 'vitest'
import { CAR_CATEGORIES, categoryLabel, estimateTripPrice } from './carCategories'

describe('CAR_CATEGORIES', () => {
  it('lists the four Trip car types in order, smallest first', () => {
    expect(CAR_CATEGORIES.map((c) => c.value)).toEqual(['HATCHBACK', 'SEDAN', 'SUV', 'TEMPO_TRAVELLER'])
    expect(CAR_CATEGORIES.map((c) => c.label)).toEqual(['Hatchback', 'Sedan', 'SUV', 'Tempo Traveller'])
  })
  it('gives each a typical seat count', () => {
    CAR_CATEGORIES.forEach((c) => expect(c.seats).toBeGreaterThan(0))
  })
})

describe('categoryLabel', () => {
  it('names each category', () => {
    expect(categoryLabel('TEMPO_TRAVELLER')).toBe('Tempo Traveller')
    expect(categoryLabel('SUV')).toBe('SUV')
  })
  it('falls back for missing or unknown values', () => {
    expect(categoryLabel(null)).toBe('Car')
    expect(categoryLabel('LIMO')).toBe('LIMO')
  })
})

describe('estimateTripPrice', () => {
  const rate = { perKm: 18, perHour: 250 }
  it('prices by distance', () => {
    expect(estimateTripPrice(rate, { km: 100 })).toBe(1800)
    expect(estimateTripPrice(rate, { km: 12.345 })).toBe(222.21)
  })
  it('prices by the hour when hours are given', () => {
    expect(estimateTripPrice(rate, { hours: 4 })).toBe(1000)
    expect(estimateTripPrice(rate, { km: 100, hours: 2 })).toBe(500)
  })
  it('accepts rates sent as strings', () => {
    expect(estimateTripPrice({ perKm: '18.50', perHour: '250' }, { km: 10 })).toBe(185)
  })
  it('is 0 without a rate or a distance', () => {
    expect(estimateTripPrice(null, { km: 100 })).toBe(0)
    expect(estimateTripPrice(rate, {})).toBe(0)
  })
})
