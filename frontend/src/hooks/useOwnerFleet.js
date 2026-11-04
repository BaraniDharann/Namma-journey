import { useEffect, useState } from 'react'
import { getOwnerMe } from '../utils/busApi'

// The owner's fleet type, fetched once per signed-in owner and shared by every screen. The
// Profile page announces a change so the rail updates without a reload.
let cache = { key: null, type: null, pending: null }
const listeners = new Set()

export function setCachedFleetType(type) {
  cache = { ...cache, type: type || 'CARS', pending: null }
  listeners.forEach((fn) => fn(cache.type))
}

/** `ownerKey` is the signed-in owner's id; null skips the fetch (not an owner). */
export default function useOwnerFleet(ownerKey) {
  const [type, setType] = useState(cache.key === ownerKey ? cache.type : null)

  useEffect(() => {
    if (ownerKey == null) return undefined
    listeners.add(setType)
    if (cache.key !== ownerKey) cache = { key: ownerKey, type: null, pending: null }
    if (cache.type) setType(cache.type)
    else if (!cache.pending) {
      cache.pending = getOwnerMe()
        .then((res) => { if (cache.key === ownerKey) setCachedFleetType(res.data?.fleetType) })
        .catch(() => { if (cache.key === ownerKey) cache.pending = null })
    }
    return () => { listeners.delete(setType) }
  }, [ownerKey])

  return type
}
