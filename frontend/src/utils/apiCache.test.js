import { describe, it, expect, vi, afterEach } from 'vitest'
import api, { getOwnerMe, clearApiCache } from './api'

describe('clearApiCache', () => {
  afterEach(() => vi.restoreAllMocks())

  it('drops cached answers so the next signed-in owner never sees the previous one', async () => {
    const get = vi.spyOn(api, 'get')
      .mockResolvedValueOnce({ data: { ownerCode: 'NJO-0001', platform: true } })
      .mockResolvedValueOnce({ data: { ownerCode: 'NJO-0002', platform: false } })

    expect((await getOwnerMe()).data.ownerCode).toBe('NJO-0001')
    expect((await getOwnerMe()).data.ownerCode).toBe('NJO-0001') // cached for a minute

    clearApiCache()

    expect((await getOwnerMe()).data.ownerCode).toBe('NJO-0002')
    expect(get).toHaveBeenCalledTimes(2)
  })
})
