import { useEffect, useState } from 'react'
import { getOwnerMe } from '../utils/api'

// The last answer, kept across screens: every owner page mounts its own sidebar, and starting
// from null made the platform items (Cities, Payouts, ...) vanish and reappear on each click.
let lastMe = null

/** The signed-in owner from GET /owner/me (cached for a minute), or null until it first arrives. */
export default function useOwnerMe(enabled = true) {
  const [me, setMe] = useState(enabled ? lastMe : null)
  useEffect(() => {
    if (!enabled) return undefined
    let live = true
    getOwnerMe()
      .then((res) => {
        lastMe = res.data || null
        if (live) setMe(lastMe)
      })
      .catch(() => {})
    return () => { live = false }
  }, [enabled])
  return me
}

/** Forget the cached owner (on logout), so the next owner never sees the previous one's menu. */
export function clearOwnerMe() {
  lastMe = null
}
