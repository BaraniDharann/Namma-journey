import { describe, it, expect } from 'vitest'
import {
  stopPairs, roundFare, fillMiddleFares, fareKey, faresToGrid, gridToFares, remapFareGrid,
  DEFAULT_POLICY, slabText, normalisePolicy, policyProblems, routeProblems, runDaysText,
} from './busFares'
import { ownerMenu, BUS_NAV_ITEMS } from './ownerFleet'
import { expiringDocs, daysUntil } from './busText'

describe('fare grid', () => {
  it('lists every from<to stop pair', () => {
    expect(stopPairs(3)).toEqual([{ fromSeq: 0, toSeq: 1 }, { fromSeq: 0, toSeq: 2 }, { fromSeq: 1, toSeq: 2 }])
    expect(stopPairs(4)).toHaveLength(6)
    expect(stopPairs(1)).toEqual([])
  })

  it('rounds to the nearest ₹10, at least ₹10', () => {
    expect(roundFare(333.3)).toBe(330)
    expect(roundFare(335)).toBe(340)
    expect(roundFare(2)).toBe(10)
  })

  it('fills empty middle fares by share of the stops and keeps typed ones', () => {
    // 4 stops = 3 legs; full route ₹1000 seater.
    const grid = { [fareKey(0, 3, 'SEATER')]: '1000', [fareKey(1, 2, 'SEATER')]: '400' }
    const out = fillMiddleFares(grid, 4, ['SEATER'])
    expect(out[fareKey(0, 1, 'SEATER')]).toBe(330) // 1 of 3 legs
    expect(out[fareKey(0, 2, 'SEATER')]).toBe(670) // 2 of 3 legs
    expect(out[fareKey(1, 3, 'SEATER')]).toBe(670)
    expect(out[fareKey(2, 3, 'SEATER')]).toBe(330)
    expect(out[fareKey(1, 2, 'SEATER')]).toBe('400') // typed by the owner, untouched
    expect(out[fareKey(0, 3, 'SEATER')]).toBe('1000')
  })

  it('skips a class without a full-route fare', () => {
    const out = fillMiddleFares({ [fareKey(0, 2, 'SEATER')]: 900 }, 3, ['SEATER', 'UPPER_SINGLE'])
    expect(out[fareKey(0, 1, 'UPPER_SINGLE')]).toBeUndefined()
    expect(out[fareKey(0, 1, 'SEATER')]).toBe(450)
  })

  it('round-trips the API list and drops empty cells and unknown classes', () => {
    const fares = [{ fromSeq: 0, toSeq: 1, seatClass: 'SEATER', fare: 500 }, { fromSeq: 0, toSeq: 1, seatClass: 'UPPER_DOUBLE', fare: 900 }]
    const grid = { ...faresToGrid(fares), [fareKey(0, 1, 'LOWER_SINGLE')]: '' }
    expect(gridToFares(grid, 2, ['SEATER', 'LOWER_SINGLE'])).toEqual([{ fromSeq: 0, toSeq: 1, seatClass: 'SEATER', fare: 500 }])
  })

  it('keeps fares with their towns when stops are reordered or removed', () => {
    const grid = { [fareKey(0, 1, 'SEATER')]: 100, [fareKey(0, 2, 'LOWER_SINGLE')]: 300, [fareKey(1, 2, 'SEATER')]: 200 }
    // Places 10, 20, 30 → remove 20.
    expect(remapFareGrid(grid, [10, 20, 30], [10, 30])).toEqual({ [fareKey(0, 1, 'LOWER_SINGLE')]: 300 })
    // Swap 20 and 30: 10→20 becomes 0→2; 20→30 now runs backwards and is dropped.
    expect(remapFareGrid(grid, [10, 20, 30], [10, 30, 20])).toEqual({ [fareKey(0, 2, 'SEATER')]: 100, [fareKey(0, 1, 'LOWER_SINGLE')]: 300 })
  })
})

describe('cancellation slabs', () => {
  it('describes the default policy in plain words', () => {
    const lines = DEFAULT_POLICY.map((s, i, all) => slabText(s, i, all))
    expect(lines).toEqual([
      'More than 48 h before boarding: 10% charge',
      '24 to 48 h before boarding: 25% charge',
      '12 to 24 h before boarding: 50% charge',
      'Less than 12 h before boarding: 100% charge (no refund)',
    ])
  })
  it('sorts slabs highest hours first and checks them', () => {
    expect(normalisePolicy([{ minHours: '0', percent: '100' }, { minHours: '24', percent: '20' }]))
      .toEqual([{ minHours: 24, percent: 20 }, { minHours: 0, percent: 100 }])
    expect(policyProblems(DEFAULT_POLICY)).toEqual([])
    expect(policyProblems([{ minHours: 24, percent: 20 }]).join(' ')).toMatch(/0 hours/)
    expect(policyProblems([{ minHours: 0, percent: 120 }]).join(' ')).toMatch(/0 to 100/)
  })
})

describe('route checks', () => {
  const stop = (placeId, placeName, points) => ({ placeId, placeName, points })
  const pt = (kind) => ({ kind, name: 'Stand', time: '21:00', dayOffset: 0 })
  const good = {
    busId: 1, runDays: [1, 5], activeFrom: '2026-11-10', activeTo: '', maxSeatsPerBooking: 6,
    stops: [stop(1, 'Chennai', [pt('BOARDING')]), stop(2, 'Madurai', [pt('DROPPING')])],
  }
  it('accepts a complete route', () => { expect(routeProblems(good)).toEqual([]) })
  it('needs boarding at the first stop and dropping at the last', () => {
    const r = { ...good, stops: [stop(1, 'Chennai', [pt('DROPPING')]), stop(2, 'Madurai', [pt('BOARDING')])] }
    expect(routeProblems(r)).toEqual(['Add a boarding point at Chennai.', 'Add a dropping point at Madurai.'])
  })
  it('refuses a repeated town, no days and a missing time', () => {
    const r = { ...good, runDays: [], stops: [stop(1, 'A', [pt('BOTH')]), stop(1, 'A', [{ ...pt('BOTH'), time: '' }])] }
    const p = routeProblems(r).join(' ')
    expect(p).toMatch(/twice/)
    expect(p).toMatch(/at least one day/)
    expect(p).toMatch(/time/)
  })
  it('names run days', () => {
    expect(runDaysText([1, 2, 3, 4, 5, 6, 7])).toBe('Daily')
    expect(runDaysText([5, 1])).toBe('Mon, Fri')
  })
})

describe('owner menu by fleet type', () => {
  const items = [
    { path: '/owner/dashboard', label: 'Dashboard' },
    { path: '/owner/drivers', label: 'Drivers' },
    { path: '/owner/profile', label: 'Profile' },
  ]
  const paths = (list) => list.map((i) => i.path)
  it('cars (or no type) keep the car menu', () => {
    expect(paths(ownerMenu(items, 'CARS'))).toEqual(paths(items))
    expect(paths(ownerMenu(items, null))).toEqual(paths(items))
  })
  it('buses drop car items and add bus items before Profile', () => {
    expect(paths(ownerMenu(items, 'BUSES'))).toEqual(['/owner/dashboard', ...paths(BUS_NAV_ITEMS), '/owner/profile'])
  })
  it('both show everything', () => {
    expect(paths(ownerMenu(items, 'BOTH'))).toEqual(['/owner/dashboard', '/owner/drivers', ...paths(BUS_NAV_ITEMS), '/owner/profile'])
  })
})

describe('document expiry', () => {
  it('lists papers expired or expiring within 15 days, soonest first', () => {
    const docs = [
      { type: 'RC', expiresOn: '2027-06-01' },
      { type: 'PERMIT', expiresOn: '2026-11-15' },
      { type: 'INSURANCE', expiresOn: '2026-11-01' },
      { type: 'FITNESS', expiresOn: '2026-11-19' },
    ]
    expect(expiringDocs(docs, '2026-11-04').map((d) => d.type)).toEqual(['INSURANCE', 'PERMIT', 'FITNESS'])
    expect(daysUntil('2026-11-19', '2026-11-04')).toBe(15)
    expect(daysUntil('2026-11-01', '2026-11-04')).toBe(-3)
  })
})
