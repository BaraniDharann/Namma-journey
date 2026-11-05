import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'

const toastMock = vi.hoisted(() => {
  const t = vi.fn()
  t.error = vi.fn()
  return t
})
vi.mock('react-hot-toast', () => ({ default: toastMock }))

import api, { busyRetryDelayMs } from './api'

// Node test environment: give the interceptor the bits of the browser it touches.
function installBrowser() {
  const store = new Map([['nj_token', 'tok'], ['nj_user', '{"id":"u-1"}']])
  globalThis.localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
  }
  globalThis.window = { location: { pathname: '/dashboard', href: '/dashboard' }, dispatchEvent: vi.fn() }
  return store
}

// Answers each request with the next status in the list (200 once they run out).
function respondWith(statuses) {
  const calls = []
  api.defaults.adapter = (config) => {
    calls.push(config)
    const status = statuses.shift() ?? 200
    const response = { status, data: status === 200 ? { ok: true } : { error: 'busy' }, headers: { 'retry-after': '1' }, config }
    if (status === 200) return Promise.resolve(response)
    return Promise.reject(new AxiosError('fail', 'ERR_BAD_RESPONSE', config, null, response))
  }
  return calls
}

describe('api 503 handling', () => {
  let store
  beforeEach(() => {
    vi.useFakeTimers()
    store = installBrowser()
    toastMock.mockClear()
    toastMock.error.mockClear()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not log the user out on 503, and retries the read until it succeeds', async () => {
    const calls = respondWith([503, 503])
    const pending = api.get('/user/u-1/bookings')
    await vi.advanceTimersByTimeAsync(5000)
    const res = await pending

    expect(res.data).toEqual({ ok: true })
    expect(calls).toHaveLength(3)
    expect(store.get('nj_token')).toBe('tok')
    expect(window.dispatchEvent).not.toHaveBeenCalled()
    expect(window.location.href).toBe('/dashboard')
    // One calm toast with a shared id, not one per attempt or an error toast.
    expect(toastMock).toHaveBeenCalledWith('Server is busy, retrying...', expect.objectContaining({ id: 'api-busy' }))
    expect(toastMock.error).not.toHaveBeenCalled()
  })

  it('gives up after two retries without clearing the session', async () => {
    const calls = respondWith([503, 503, 503])
    const pending = api.get('/owner/bookings').catch((e) => e)
    await vi.advanceTimersByTimeAsync(5000)
    const err = await pending

    expect(err.response.status).toBe(503)
    expect(calls).toHaveLength(3)
    expect(store.get('nj_token')).toBe('tok')
    expect(window.dispatchEvent).not.toHaveBeenCalled()
  })

  it('never resends a write on 503, and still keeps the session', async () => {
    const calls = respondWith([503])
    const err = await api.post('/user/u-1/bookings', {}).catch((e) => e)

    expect(err.response.status).toBe(503)
    expect(calls).toHaveLength(1)
    expect(store.get('nj_token')).toBe('tok')
  })

  it('a 401 still ends the session (unchanged)', async () => {
    respondWith([401])
    await api.get('/user/u-1/bookings').catch(() => {})

    expect(store.has('nj_token')).toBe(false)
    expect(window.location.href).toBe('/login')
  })
})

describe('busyRetryDelayMs', () => {
  it('waits the Retry-After seconds plus up to a second of jitter', () => {
    expect(busyRetryDelayMs('5', () => 0)).toBe(5000)
    expect(busyRetryDelayMs('5', () => 0.999)).toBe(5999)
  })

  it('clamps silly values and falls back when the header is missing', () => {
    expect(busyRetryDelayMs('0', () => 0)).toBe(1000)
    expect(busyRetryDelayMs('3600', () => 0)).toBe(10000)
    expect(busyRetryDelayMs(undefined, () => 0)).toBe(2000)
  })
})
