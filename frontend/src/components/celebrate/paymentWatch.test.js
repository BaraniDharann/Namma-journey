import { describe, it, expect, beforeEach } from 'vitest'
import { createPaymentWatch } from './paymentWatch'

function memoryStorage() {
  const m = new Map()
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => m.set(k, String(v)),
    removeItem: (k) => m.delete(k),
  }
}

describe('payment watch', () => {
  let store, watch
  beforeEach(() => {
    store = memoryStorage()
    watch = createPaymentWatch(() => store, 'u1')
  })

  it('remembers payments the traveller says they sent', () => {
    watch.remember({ paymentId: 'p1', amount: 6000 })
    watch.remember({ paymentId: 'p2', amount: 1500 })
    expect(watch.pending().map((p) => p.paymentId)).toEqual(['p1', 'p2'])
  })

  it('does not remember the same payment twice', () => {
    watch.remember({ paymentId: 'p1', amount: 6000 })
    watch.remember({ paymentId: 'p1', amount: 6000 })
    expect(watch.pending()).toHaveLength(1)
  })

  it('hands back newly verified payments exactly once', () => {
    watch.remember({ paymentId: 'p1', amount: 6000 })
    watch.remember({ paymentId: 'p2', amount: 1500 })
    const first = watch.settle([
      { paymentId: 'p1', status: 'VERIFIED', amount: 6000 },
      { paymentId: 'p2', status: 'PENDING', amount: 1500 },
    ])
    expect(first.map((p) => p.paymentId)).toEqual(['p1'])
    expect(watch.pending().map((p) => p.paymentId)).toEqual(['p2'])
    expect(watch.settle([{ paymentId: 'p1', status: 'VERIFIED' }])).toEqual([])
  })

  it('keeps payments per traveller', () => {
    watch.remember({ paymentId: 'p1', amount: 1 })
    expect(createPaymentWatch(() => store, 'u2').pending()).toEqual([])
  })

  it('survives broken or blocked storage', () => {
    const broken = createPaymentWatch(() => { throw new Error('blocked') }, 'u1')
    expect(() => broken.remember({ paymentId: 'x', amount: 1 })).not.toThrow()
    expect(broken.pending()).toEqual([])
    store.setItem('nj_paid_u1', '{not json')
    expect(watch.pending()).toEqual([])
  })
})
