import { describe, it, expect, beforeEach, vi } from 'vitest'
import { wasSaveOfferSkipped, skipSaveOffer, offerToSaveLogin } from './rememberLogin'

function memoryStorage() {
  const m = new Map()
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k) }
}

describe('save-password offer memory', () => {
  beforeEach(() => { vi.stubGlobal('localStorage', memoryStorage()) })

  it('asks a traveller who has not answered yet', () => {
    expect(wasSaveOfferSkipped('a@example.com')).toBe(false)
  })

  it('does not ask again after "Not now", whatever the email casing', () => {
    skipSaveOffer('Asha@Example.com')
    expect(wasSaveOfferSkipped('asha@example.com')).toBe(true)
    expect(wasSaveOfferSkipped('someone@example.com')).toBe(false)
  })

  it('keeps asking when storage is blocked rather than crashing', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('blocked') } })
    expect(() => skipSaveOffer('a@example.com')).not.toThrow()
    expect(wasSaveOfferSkipped('a@example.com')).toBe(false)
  })
})

describe('offerToSaveLogin', () => {
  it('does nothing where the browser has no credential API', async () => {
    vi.stubGlobal('window', {})
    expect(await offerToSaveLogin('a@example.com', 'Journey@2026')).toBe(false)
  })

  it('hands the credential to the browser password manager', async () => {
    const store = vi.fn().mockResolvedValue(undefined)
    class PasswordCredential { constructor(d) { Object.assign(this, d) } }
    vi.stubGlobal('window', { PasswordCredential })
    vi.stubGlobal('navigator', { credentials: { store } })
    expect(await offerToSaveLogin('a@example.com', 'Journey@2026', 'Asha')).toBe(true)
    expect(store).toHaveBeenCalledWith(expect.objectContaining({ id: 'a@example.com', password: 'Journey@2026', name: 'Asha' }))
  })
})
