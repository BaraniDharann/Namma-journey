import { describe, it, expect, beforeEach } from 'vitest'
import { createCountPoller } from './notificationPoll'

// A hand-driven clock and timer queue, plus a document/window that only know about listeners.
function fakeEnv() {
  let clock = 0
  let nextId = 1
  const pending = new Map()
  const listeners = { doc: {}, win: {} }
  const target = (bag) => ({
    addEventListener: (t, fn) => { bag[t] = fn },
    removeEventListener: (t, fn) => { if (bag[t] === fn) delete bag[t] },
  })
  const doc = { visibilityState: 'visible', ...target(listeners.doc) }
  const win = target(listeners.win)
  const timers = {
    setTimeout: (fn, ms) => { const id = nextId++; pending.set(id, { fn, at: clock + ms }); return id },
    clearTimeout: (id) => { pending.delete(id) },
  }
  return {
    doc, win, timers, listeners, pending,
    now: () => clock,
    // Moves the clock forward, firing due timers in order.
    async advance(ms) {
      const end = clock + ms
      for (;;) {
        const due = [...pending.entries()].filter(([, t]) => t.at <= end).sort((a, b) => a[1].at - b[1].at)[0]
        if (!due) break
        pending.delete(due[0])
        clock = due[1].at
        due[1].fn()
        await flush()
      }
      clock = end
    },
  }
}

const flush = () => new Promise((r) => setTimeout(r, 0))

describe('notification count poller', () => {
  let env, calls, seen, answer
  const make = (extra = {}) => createCountPoller({
    fetchCount: () => { calls++; return answer() },
    onCount: (count, previous) => seen.push([count, previous]),
    intervalMs: 30000,
    maxBackoffMs: 120000,
    doc: env.doc, win: env.win, timers: env.timers, now: env.now,
    ...extra,
  })

  beforeEach(() => {
    env = fakeEnv()
    calls = 0
    seen = []
    answer = () => Promise.resolve(3)
  })

  it('asks once on start, then every 30 seconds - not every 10', async () => {
    const p = make()
    p.start()
    await flush()
    expect(calls).toBe(1)
    await env.advance(29999)
    expect(calls).toBe(1)
    await env.advance(1)
    expect(calls).toBe(2)
    await env.advance(60000)
    expect(calls).toBe(4)
    p.stop()
  })

  it('reports the previous count so the caller can tell the count moved', async () => {
    const values = [3, 3, 5]
    answer = () => Promise.resolve(values.shift())
    const p = make()
    p.start()
    await flush()
    await env.advance(60000)
    expect(seen).toEqual([[3, null], [3, 3], [5, 3]])
    p.stop()
  })

  it('makes no requests while the tab is hidden and checks at once when it is shown again', async () => {
    const p = make()
    p.start()
    await flush()
    env.doc.visibilityState = 'hidden'
    env.listeners.doc.visibilitychange()
    expect(env.pending.size).toBe(0)
    await env.advance(10 * 60000)
    expect(calls).toBe(1)

    env.doc.visibilityState = 'visible'
    env.listeners.doc.visibilitychange()
    await flush()
    expect(calls).toBe(2)
    p.stop()
  })

  it('does not start polling a tab that opens in the background', async () => {
    env.doc.visibilityState = 'hidden'
    const p = make()
    p.start()
    await env.advance(120000)
    expect(calls).toBe(0)
    p.stop()
  })

  it('refreshes on focus, but focus and visibilitychange together cost one request', async () => {
    const p = make()
    p.start()
    await flush()
    await env.advance(10000)
    env.listeners.win.focus()
    env.listeners.doc.visibilitychange()
    await flush()
    expect(calls).toBe(2)
    // And the focus check replaced the pending tick instead of adding a second timer.
    expect(env.pending.size).toBe(1)
    p.stop()
  })

  it('backs off after failures and returns to the normal pace after a success', async () => {
    let fail = true
    answer = () => (fail ? Promise.reject(new Error('down')) : Promise.resolve(1))
    const p = make()
    p.start()
    await flush()
    expect(calls).toBe(1)
    await env.advance(60000) // waits 60 s after the first failure
    expect(calls).toBe(2)
    await env.advance(119999) // then 120 s (the cap)
    expect(calls).toBe(2)
    await env.advance(1)
    expect(calls).toBe(3)
    fail = false
    await env.advance(120000)
    expect(calls).toBe(4)
    await env.advance(30000)
    expect(calls).toBe(5)
    p.stop()
  })

  it('stops cleanly: no timers, no listeners, no late results', async () => {
    let resolve
    answer = () => new Promise((r) => { resolve = r })
    const p = make()
    p.start()
    p.stop()
    resolve(9)
    await flush()
    expect(seen).toEqual([])
    expect(env.pending.size).toBe(0)
    expect(env.listeners.doc.visibilitychange).toBeUndefined()
    expect(env.listeners.win.focus).toBeUndefined()
  })
})
