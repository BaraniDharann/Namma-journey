import React, { useCallback, useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { getTripSafety, answerSafetyCheck, pressSos } from '../../utils/api'

const POLL_MS = 15000

/**
 * Safety controls on a live trip card (CONFIRMED or STARTED): SOS, call 112, share the trip,
 * and - when the server has asked - the "Are you safe?" question with Safe / Unsafe.
 *
 * The same question also arrives on Telegram for passengers who connected it; answering in
 * either place closes it in both.
 */
export default function TripSafetyBar({ userId, booking }) {
  const [view, setView] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const { data } = await getTripSafety(userId, booking.bookingId)
      setView(data)
    } catch { /* keep the last state on a blip */ }
  }, [userId, booking.bookingId])

  useEffect(() => {
    load()
    const t = setInterval(load, POLL_MS)
    return () => clearInterval(t)
  }, [load])

  const answer = async (safe) => {
    setBusy(true)
    try {
      await answerSafetyCheck(userId, booking.bookingId, safe)
      if (safe) toast.success('Thanks - glad you are safe.')
      await load()
    } catch { /* toasted */ } finally {
      setBusy(false)
    }
  }

  const sos = async () => {
    if (!confirm('Send SOS? This alerts our safety team and your emergency contacts with your live location.')) return
    setBusy(true)
    try {
      const { data } = await pressSos(userId, booking.bookingId)
      setView(data)
    } catch { /* toasted */ } finally {
      setBusy(false)
    }
  }

  const share = async () => {
    const text = `I'm on a Namma Journey ride from ${booking.fromPlace} to ${booking.toPlace}. Watch it live: ${view.shareUrl}`
    if (navigator.share) {
      try { await navigator.share({ text }); return } catch { /* cancelled: fall back to copy */ }
    }
    try {
      await navigator.clipboard.writeText(text)
      toast.success('Trip link copied. Paste it to family on WhatsApp or SMS.')
    } catch {
      toast.error('Could not copy the link.')
    }
  }

  const box = { marginTop: 12, padding: '12px 14px', borderRadius: 14, display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }

  return (
    <div>
      {view?.sosActive && (
        <div role="alert" style={{ ...box, background: 'var(--pc-bad-soft)', color: 'var(--pc-bad)' }}>
          <Icon name="alert" size={18} style={{ flexShrink: 0 }} />
          <div style={{ flex: '1 1 200px', fontSize: 13, color: 'var(--pc-ink)' }}>
            <strong style={{ color: 'var(--pc-bad)' }}>Help is being alerted.</strong> Our safety team and your
            emergency contacts have your live location. <strong>If you are in danger, call 112 now.</strong>
          </div>
          <a href="tel:112" className="pc-btn pc-btn-sm" style={{ background: 'var(--pc-bad)' }}><Icon name="phone" size={15} />Call 112</a>
        </div>
      )}

      {!view?.sosActive && view?.pendingCheck && (
        <div role="alert" style={{ ...box, background: 'var(--pc-warn-soft)' }}>
          <Icon name="shield" size={20} style={{ flexShrink: 0, color: 'var(--pc-warn)' }} />
          <div style={{ flex: '1 1 160px', fontSize: 13.5, color: 'var(--pc-ink)' }}>
            <strong>Are you safe?</strong>{' '}
            {view.pendingCheck === 'STOPPED' && 'Your car has been stopped for a few minutes.'}
            {view.pendingCheck === 'GPS_SILENT' && "We've lost your driver's location."}
            {view.pendingCheck === 'OVERDUE' && 'Your ride is taking longer than expected.'}
            {view.pendingCheck === 'SCHEDULED' && 'Quick check on your ride.'}
          </div>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={() => answer(true)}><Icon name="check" size={15} />I&apos;m safe</button>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={() => answer(false)} style={{ background: 'var(--pc-bad)' }}><Icon name="alert" size={15} />Unsafe</button>
        </div>
      )}

      <div className="pc-chips" style={{ marginTop: 10 }}>
        <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={sos} style={{ background: 'var(--pc-bad)' }}>
          <Icon name="alert" size={15} />SOS
        </button>
        <a href="tel:112" className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="phone" size={15} />Call 112</a>
        {view?.shareUrl && (
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={share}><Icon name="link" size={15} />Share trip</button>
        )}
      </div>
    </div>
  )
}
