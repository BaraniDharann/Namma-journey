import { useEffect, useState } from 'react'
import { getOwnerMe } from '../utils/api'

/** The signed-in owner from GET /owner/me (cached for a minute), or null until it arrives. */
export default function useOwnerMe(enabled = true) {
  const [me, setMe] = useState(null)
  useEffect(() => {
    if (!enabled) return undefined
    let live = true
    getOwnerMe().then((res) => { if (live) setMe(res.data || null) }).catch(() => {})
    return () => { live = false }
  }, [enabled])
  return me
}
