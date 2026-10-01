import { describe, it, expect } from 'vitest'
import { BIOMES, SEG, LOOP, segmentDistance, biomeAt, lookAt, mixHex } from './biomes'

describe('the loop', () => {
  it('is five places, one road segment each', () => {
    expect(BIOMES.map((b) => b.id)).toEqual(['valley', 'waterfall', 'beach', 'backwaters', 'temple'])
    expect(LOOP).toBe(SEG * 5)
  })
})

describe('segmentDistance', () => {
  it('lays the segments out ahead of the car in order', () => {
    expect(segmentDistance(0, 0)).toBe(0)
    expect(segmentDistance(1, 0)).toBe(SEG)
    expect(segmentDistance(4, 0)).toBe(4 * SEG)
  })
  it('lets the segment you are on slide behind you', () => {
    expect(segmentDistance(0, 100)).toBe(-100)
    expect(segmentDistance(1, SEG + 10)).toBe(-10)
  })
  it('recycles a passed segment to the far end of the road', () => {
    expect(segmentDistance(0, SEG + 10)).toBe(LOOP - SEG - 10)
    expect(segmentDistance(0, LOOP)).toBe(0)
  })
  it('never places a segment further than the loop allows', () => {
    for (let o = 0; o < LOOP * 2; o += 37) {
      for (let i = 0; i < BIOMES.length; i++) {
        const d = segmentDistance(i, o)
        expect(d).toBeGreaterThan(-SEG)
        expect(d).toBeLessThanOrEqual(LOOP - SEG)
      }
    }
  })
})

describe('biomeAt', () => {
  it('names the place you are driving through', () => {
    expect(biomeAt(0)).toMatchObject({ index: 0, local: 0 })
    expect(biomeAt(SEG + 24).index).toBe(1)
    expect(biomeAt(SEG + 24).local).toBeCloseTo(0.1)
    expect(biomeAt(LOOP + 5).index).toBe(0)
  })
})

describe('lookAt', () => {
  it('holds a place colour through the segment and blends into the next near its end', () => {
    expect(lookAt(SEG * 0.4).sky).toBe(BIOMES[0].sky)
    const late = lookAt(SEG * 0.97).sky
    expect(late).not.toBe(BIOMES[0].sky)
    expect(late).not.toBe(BIOMES[1].sky)
    expect(lookAt(LOOP - 1).sky).not.toBe(BIOMES[4].sky)
  })
})

describe('mixHex', () => {
  it('mixes two colours', () => {
    expect(mixHex('#000000', '#ffffff', 0.5)).toBe('#808080')
    expect(mixHex('#102030', '#102030', 0.3)).toBe('#102030')
  })
})
