// The bell's background traffic. It used to fetch the whole notification list AND the unread
// count every 10 s on every signed-in screen, visible or not - the largest steady load on the
// database. Now only the cheap count is polled, every 30 s, and only while the tab is visible;
// the list is fetched when the panel is opened (or the count moves while it is open).

export const COUNT_POLL_MS = 30000
// After a failed poll the wait doubles up to this, so an outage is not hammered by every tab.
export const MAX_BACKOFF_MS = 5 * 60 * 1000
// Focus and visibilitychange usually fire together when a tab comes back: one request, not two.
const MIN_GAP_MS = 2000

/**
 * Polls fetchCount() and reports each result as onCount(count, previous) - previous is null on the
 * first result, so the caller can tell "the count moved" from "the first answer".
 *
 * Paused while the document is hidden; checks at once when it becomes visible again or the window
 * regains focus. doc, win, timers and now are injectable for tests.
 */
export function createCountPoller({
  fetchCount,
  onCount,
  intervalMs = COUNT_POLL_MS,
  maxBackoffMs = MAX_BACKOFF_MS,
  doc = typeof document !== 'undefined' ? document : null,
  win = typeof window !== 'undefined' ? window : null,
  timers = { setTimeout: (fn, ms) => setTimeout(fn, ms), clearTimeout: (id) => clearTimeout(id) },
  now = () => Date.now(),
}) {
  let timer = null
  let delay = intervalMs
  let previous = null
  let lastRun = -Infinity
  let running = false
  let inFlight = false

  const hidden = () => doc?.visibilityState === 'hidden'

  const schedule = () => {
    if (timer !== null) timers.clearTimeout(timer)
    timer = null
    if (running && !hidden()) timer = timers.setTimeout(tick, delay)
  }

  async function tick() {
    // Called early (focus, refresh)? Drop the pending timer so the poll never doubles up.
    if (timer !== null) timers.clearTimeout(timer)
    timer = null
    if (!running || hidden() || inFlight) return
    inFlight = true
    lastRun = now()
    try {
      const count = await fetchCount()
      delay = intervalMs
      if (running) {
        onCount(count, previous)
        previous = count
      }
    } catch {
      delay = Math.min(delay * 2, maxBackoffMs)
    } finally {
      inFlight = false
      schedule()
    }
  }

  const checkNow = () => {
    if (!running || hidden()) {
      schedule() // hidden: clears the timer, so a background tab makes no requests at all
      return
    }
    if (now() - lastRun < MIN_GAP_MS) return
    tick()
  }

  return {
    start() {
      if (running) return
      running = true
      doc?.addEventListener('visibilitychange', checkNow)
      win?.addEventListener('focus', checkNow)
      checkNow()
    },
    stop() {
      running = false
      doc?.removeEventListener('visibilitychange', checkNow)
      win?.removeEventListener('focus', checkNow)
      if (timer !== null) timers.clearTimeout(timer)
      timer = null
    },
    /** Ask now, e.g. right after marking notifications read. */
    refresh() {
      lastRun = -Infinity
      checkNow()
    },
  }
}
