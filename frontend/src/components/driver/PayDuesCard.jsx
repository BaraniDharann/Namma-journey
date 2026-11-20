import React, { useCallback, useEffect, useState } from 'react'
import Icon from '../dash/Icon'
import { getDriverDues, payDriverDuesOnline } from '../../utils/api'
import { inr } from '../../dash/metrics'

const STATUS = {
  PENDING: ['Waiting for owner', 'var(--pc-warn)'],
  CONFIRMED: ['Received', 'var(--pc-good)'],
  REJECTED: ['Not found', 'var(--pc-bad)'],
}

/**
 * Paying the commission owed on cash rides. "Pay online" opens a Razorpay payment page for the
 * full amount; the driver pays with GPay, PhonePe, any UPI app or a card, and Razorpay confirms it
 * to the server, so the balance updates by itself - no UTR to type, no owner to wait for.
 */
export default function PayDuesCard({ driverId }) {
  const [dues, setDues] = useState(null)
  const [opening, setOpening] = useState(false)

  const load = useCallback(async () => {
    try {
      const { data } = await getDriverDues(driverId)
      setDues(data)
    } catch { /* toasted */ }
  }, [driverId])

  useEffect(() => { load() }, [load])

  // Back from Razorpay's page (another tab): show the new balance.
  useEffect(() => {
    const onFocus = () => load()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [load])

  if (!dues || (Number(dues.owed) <= 0 && !dues.recent?.length)) return null

  const payable = Number(dues.payable)

  const payOnline = async () => {
    // Open the tab inside the tap, so pop-up blockers allow it; point it at Razorpay once we have the link.
    const tab = window.open('', '_blank')
    setOpening(true)
    try {
      const { data } = await payDriverDuesOnline(driverId)
      if (tab) tab.location.href = data.url
      else window.location.href = data.url
    } catch {
      tab?.close()
    } finally {
      setOpening(false)
    }
  }

  const box = { padding: 16, borderRadius: 16, background: 'var(--pc-wash)' }

  return (
    <div style={{ display: 'grid', gap: 14 }}>
      {payable > 0 && (
        <div style={{ ...box, display: 'grid', gap: 10 }}>
          <div style={{ fontWeight: 800, color: 'var(--pc-ink)', fontSize: 15 }}>Pay {inr(payable)} commission</div>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--pc-ink-2)', lineHeight: 1.6 }}>
            Pay securely through Razorpay with GPay, PhonePe, any UPI app or a card. Your balance updates as soon as the payment goes through.
          </p>
          {dues.payOnline ? (
            <div>
              <button type="button" className="pc-btn pc-btn-sm" onClick={payOnline} disabled={opening}>
                <Icon name="wallet" size={15} />{opening ? 'Opening…' : `Pay ${inr(payable)} online`}
              </button>
            </div>
          ) : (
            <p role="status" style={{ margin: 0, fontSize: 13, color: 'var(--pc-warn)' }}>Online payment is not available right now. Please try again later.</p>
          )}
        </div>
      )}

      {Number(dues.pending) > 0 && (
        <div role="status" style={{ fontSize: 13, color: 'var(--pc-ink-2)' }}>
          <Icon name="clock" size={14} /> {inr(dues.pending)} is waiting for the owner to confirm.
        </div>
      )}

      {dues.recent?.length > 0 && (
        <div style={{ display: 'grid', gap: 6 }}>
          <div style={{ fontSize: 12.5, fontWeight: 800, color: 'var(--pc-ink-2)' }}>Your payments</div>
          {dues.recent.map((p) => {
            const [label, color] = STATUS[p.status] || [p.status, 'var(--pc-muted)']
            return (
              <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', fontSize: 13 }}>
                <span>{new Date(p.submittedAt).toLocaleDateString()} · {inr(p.amount)} · <span style={{ fontFamily: 'monospace' }}>{p.utr?.startsWith('pay_') ? 'Razorpay' : 'UTR'} {p.utr}</span></span>
                <strong style={{ color }}>{label}</strong>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
