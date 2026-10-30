import React, { useCallback, useEffect, useState } from 'react'
import QRCode from 'qrcode'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { getDriverDues, submitDriverDuesPayment } from '../../utils/api'
import { inr } from '../../dash/metrics'

const UTR = /^[A-Za-z0-9]{6,64}$/

const STATUS = {
  PENDING: ['Waiting for owner', 'var(--pc-warn)'],
  CONFIRMED: ['Received', 'var(--pc-good)'],
  REJECTED: ['Not found', 'var(--pc-bad)'],
}

/**
 * Paying the commission owed on cash rides. The driver pays the owner's UPI ID from their own UPI
 * app (the button opens it with the amount filled in; on a laptop, scan the QR), then enters the
 * UTR. The balance changes once the owner finds the money and confirms - the app cannot see the
 * owner's bank account.
 */
export default function PayDuesCard({ driverId }) {
  const [dues, setDues] = useState(null)
  const [qr, setQr] = useState('')
  const [amount, setAmount] = useState('')
  const [utr, setUtr] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    try {
      const { data } = await getDriverDues(driverId)
      setDues(data)
      setAmount(Number(data.payable) > 0 ? Number(data.payable).toFixed(2) : '')
      setQr(data.upiLink
        ? await QRCode.toDataURL(data.upiLink, { margin: 1, width: 180, color: { dark: '#0f2a2b', light: '#ffffff' } })
        : '')
    } catch { /* toasted */ }
  }, [driverId])

  useEffect(() => { load() }, [load])

  if (!dues || (Number(dues.owed) <= 0 && !dues.recent?.length)) return null

  const payable = Number(dues.payable)
  const value = Number(amount)
  const valid = value > 0 && value <= payable && UTR.test(utr.trim())

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const { data } = await submitDriverDuesPayment(driverId, value.toFixed(2), utr.trim())
      setDues(data)
      setUtr('')
      toast.success('Sent to the owner. Your balance updates once they confirm it.')
      load()
    } catch { /* toasted */ } finally {
      setSaving(false)
    }
  }

  const copyUpi = async () => {
    try {
      await navigator.clipboard.writeText(dues.ownerUpiId)
      toast.success('UPI ID copied')
    } catch { /* clipboard blocked: the ID is on screen */ }
  }

  const box = { padding: 16, borderRadius: 16, background: 'var(--pc-wash)' }

  return (
    <div style={{ display: 'grid', gap: 14 }}>
      {payable > 0 && dues.ownerUpiId && (
        <div style={{ ...box, display: 'flex', gap: 18, flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 240px', minWidth: 0 }}>
            <div style={{ fontWeight: 800, color: 'var(--pc-ink)', fontSize: 15 }}>Pay {inr(payable)} commission</div>
            <ol style={{ margin: '8px 0 12px', paddingLeft: 18, fontSize: 13, color: 'var(--pc-ink-2)', lineHeight: 1.7 }}>
              <li>Tap <b>Pay with UPI app</b> (or scan the code). The amount is filled in.</li>
              <li>After paying, copy the <b>UTR / UPI reference</b> from your payment app.</li>
              <li>Enter it below. The owner confirms it, then your balance updates.</li>
            </ol>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              <a className="pc-btn pc-btn-sm" href={dues.upiLink}><Icon name="wallet" size={15} />Pay with UPI app</a>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={copyUpi}>
                <span style={{ fontFamily: 'monospace' }}>{dues.ownerUpiId}</span>
              </button>
            </div>
          </div>
          {qr && (
            <div style={{ textAlign: 'center' }}>
              <img src={qr} alt={`UPI QR code to pay ${inr(payable)} to ${dues.ownerName}`} width={180} height={180} style={{ borderRadius: 12 }} />
              <div style={{ fontSize: 11.5, color: 'var(--pc-muted)' }}>Scan with any UPI app</div>
            </div>
          )}
        </div>
      )}

      {payable > 0 && (
        <form onSubmit={submit} style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label style={{ flex: '0 1 140px', fontSize: 12.5, fontWeight: 700, color: 'var(--pc-ink-2)' }}>
            Amount paid (₹)
            <input className="input-field" type="number" min="0.01" step="0.01" inputMode="decimal" value={amount}
              onChange={(e) => setAmount(e.target.value)} style={{ width: '100%', marginTop: 4, padding: '8px 10px' }} />
          </label>
          <label style={{ flex: '1 1 200px', fontSize: 12.5, fontWeight: 700, color: 'var(--pc-ink-2)' }}>
            UTR / UPI reference
            <input className="input-field" placeholder="e.g. 431245678901" value={utr} autoComplete="off"
              onChange={(e) => setUtr(e.target.value)} style={{ width: '100%', marginTop: 4, padding: '8px 10px' }} />
          </label>
          <button type="submit" className="pc-btn pc-btn-sm" disabled={saving || !valid}><Icon name="send" size={15} />{saving ? 'Sending…' : 'I have paid'}</button>
        </form>
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
                <span>{new Date(p.submittedAt).toLocaleDateString()} · {inr(p.amount)} · <span style={{ fontFamily: 'monospace' }}>UTR {p.utr}</span></span>
                <strong style={{ color }}>{label}</strong>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
