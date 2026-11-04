import React, { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../components/dash/ui'
import { BusPill, Notice } from '../../components/bus/busUi'
import CancellationTable from '../../components/bus/CancellationTable'
import { cancelBusSeats, getBusCancelQuote, getBusTicket, reviewBusTicket } from '../../utils/busApi'
import { errorText, inr } from '../../utils/busText'
import { seatClassName } from '../../utils/busLayouts'
import { clockText } from '../../utils/busSearch'
import { PASSENGER_STATUS, REFUND_STATUS, TICKET_STATUS, cancellableSeats, whenText } from '../../utils/busBooking'
import { userNavItems } from '../../utils/userMenu'
import '../../styles/bus.css'

const GENDER = { M: 'Male', F: 'Female' }

function KV({ label, value, sub }) {
  return <div className="bus-kv"><small>{label}</small><b>{value || '—'}</b>{sub && <span>{sub}</span>}</div>
}

function PointBlock({ label, point }) {
  if (!point) return <KV label={label} value="—" />
  return (
    <div className="bus-kv">
      <small>{label}</small>
      <b>{clockText(point.at)} · {point.name}</b>
      {point.at && <span>{whenText(point.at)}</span>}
      {point.address && <span>{point.address}</span>}
      {point.landmark && <span>Landmark: {point.landmark}</span>}
    </div>
  )
}

/** Pick seats → see the refund for each → confirm. */
function CancelDialog({ ticket, onClose, onDone }) {
  const options = cancellableSeats(ticket)
  const [seats, setSeats] = useState(options.length === 1 ? options : [])
  const [quote, setQuote] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const esc = (e) => { if (e.key === 'Escape' && !busy) onClose() }
    document.addEventListener('keydown', esc)
    return () => document.removeEventListener('keydown', esc)
  }, [busy, onClose])

  const flip = (label) => { setQuote(null); setSeats((s) => (s.includes(label) ? s.filter((x) => x !== label) : [...s, label])) }

  const getQuote = async () => {
    setBusy(true); setError('')
    try { setQuote((await getBusCancelQuote(ticket.id, seats)).data) } catch (e) { setError(errorText(e, 'Could not work out the refund.')) } finally { setBusy(false) }
  }
  const confirm = async () => {
    setBusy(true); setError('')
    try { onDone((await cancelBusSeats(ticket.id, seats)).data) } catch (e) { setError(errorText(e, 'Could not cancel these seats.')); setBusy(false) }
  }

  return (
    <div className="bus-modal-scrim" onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose() }}>
      <div className="bus-modal" role="dialog" aria-modal="true" aria-labelledby="cancel-title" style={{ background: '#fff' }}>
        <h2 id="cancel-title">Cancel seats</h2>
        <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
          <legend style={{ fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>Which seats?</legend>
          {(ticket.passengers || []).filter((p) => p.status === 'CONFIRMED').map((p) => (
            <label key={p.seatLabel} className="bus-check">
              <input type="checkbox" checked={seats.includes(p.seatLabel)} onChange={() => flip(p.seatLabel)} disabled={busy} />
              <span><b>{p.seatLabel}</b> · {p.name} · {inr(p.fare)}</span>
            </label>
          ))}
        </fieldset>

        {quote && (
          <div className="bus-table-wrap" style={{ marginTop: 14 }}>
            <table className="bus-table">
              <thead><tr><th>Seat</th><th className="num">Fare</th><th className="num">Charge</th><th className="num">Refund</th></tr></thead>
              <tbody>
                {quote.seats.map((s) => (
                  <tr key={s.seatLabel}>
                    <td>{s.seatLabel}</td>
                    <td className="num">{inr(s.fare)}<small style={{ display: 'block', color: 'var(--pc-muted)' }}>+ GST {inr(s.gst)}</small></td>
                    <td className="num">{inr(s.charge)}<small style={{ display: 'block', color: 'var(--pc-muted)' }}>{s.chargePercent}%</small></td>
                    <td className="num"><b>{inr(s.refund)}</b></td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr><td colSpan={3}>You get back</td><td className="num">{inr(quote.totalRefund)}</td></tr></tfoot>
            </table>
            {quote.hoursBeforeBoarding != null && (
              <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 8 }}>
                {Math.floor(quote.hoursBeforeBoarding)} h before your boarding time. The refund goes back to the card or UPI you paid with.
              </p>
            )}
          </div>
        )}

        {error && <div className="pc-error" role="alert" style={{ marginTop: 12 }}><Icon name="alert" size={16} />{error}</div>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16, flexWrap: 'wrap' }}>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={onClose} disabled={busy}>Keep my seats</button>
          {quote
            ? <button type="button" className="pc-btn pc-btn-danger" onClick={confirm} disabled={busy}>{busy ? 'Cancelling…' : `Cancel ${seats.length} seat${seats.length === 1 ? '' : 's'}, refund ${inr(quote.totalRefund)}`}</button>
            : <button type="button" className="pc-btn" onClick={getQuote} disabled={busy || !seats.length}>{busy ? 'Checking…' : 'See refund'}</button>}
        </div>
      </div>
    </div>
  )
}

function ReviewBox({ ticket, onDone }) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (ticket.review) {
    return (
      <div>
        <div className="bus-stars" aria-label={`You rated ${ticket.review.rating} out of 5`}>
          {[1, 2, 3, 4, 5].map((n) => <Icon key={n} name="star" size={20} style={{ color: n <= ticket.review.rating ? '#eda100' : '#d6c9b8', fill: n <= ticket.review.rating ? 'currentColor' : undefined }} />)}
        </div>
        {ticket.review.comment && <p style={{ margin: '8px 0 0', fontSize: 14 }}>{ticket.review.comment}</p>}
      </div>
    )
  }

  const send = async (e) => {
    e.preventDefault()
    if (!rating) { setError('Pick 1 to 5 stars.'); return }
    setBusy(true); setError('')
    try { onDone((await reviewBusTicket(ticket.id, rating, comment.trim())).data) } catch (err) { setError(errorText(err, 'Could not save your review.')) } finally { setBusy(false) }
  }

  return (
    <form onSubmit={send}>
      <div className="bus-stars" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} star${n === 1 ? '' : 's'}`} className={n <= rating ? 'is-on' : ''} onClick={() => setRating(n)}>
            <Icon name="star" size={26} className={n <= rating ? 'is-on' : ''} />
          </button>
        ))}
      </div>
      <label htmlFor="review-comment" className="pc-sr">Comment</label>
      <textarea id="review-comment" className="input-field" rows={3} maxLength={1000} placeholder="How was the bus, the crew and the timing?" value={comment} onChange={(e) => setComment(e.target.value)} style={{ marginTop: 10 }} />
      {error && <div className="pc-error" role="alert" style={{ marginTop: 10 }}>{error}</div>}
      <button type="submit" className="pc-btn" style={{ marginTop: 10 }} disabled={busy}>{busy ? 'Saving…' : 'Submit review'}</button>
    </form>
  )
}

/** One bus ticket: printable, with cancel (refund quote first) and a review after the trip. */
export default function UserBusTicketDetail() {
  const { id } = useParams()
  const location = useLocation()
  const [ticket, setTicket] = useState(null)
  const [error, setError] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [notice, setNotice] = useState(() => (location.state?.confirming
    ? { tone: 'ok', text: 'Payment received. We are confirming it with Razorpay; this page updates in a moment.' }
    : location.state?.justBooked ? { tone: 'ok', text: 'Your seats are booked. The ticket is on its way to your email.' } : null))

  const load = useCallback(async () => {
    try { setTicket((await getBusTicket(id)).data); setError('') } catch (e) { setError(errorText(e, 'Could not load this ticket.')) }
  }, [id])
  useEffect(() => { load() }, [load])

  // A payment still being confirmed: look again every few seconds for a minute.
  useEffect(() => {
    if (ticket?.status !== 'PENDING') return undefined
    let n = 0
    const t = setInterval(() => { n++; if (n > 12) clearInterval(t); else load() }, 5000)
    return () => clearInterval(t)
  }, [ticket?.status, load])

  if (!ticket) {
    return (
      <DashboardLayout navItems={userNavItems} role="ROLE_USER">
        <PageHead title="Bus ticket" />
        <div className="pc-grid">
          <Panel span={12}>
            {error
              ? <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{error}</span><button type="button" className="pc-btn pc-btn-sm" onClick={load}>Retry</button></div>
              : <Skeleton rows={6} height={18} />}
          </Panel>
        </div>
      </DashboardLayout>
    )
  }

  const crew = [
    ticket.driverPhone && ['Driver', ticket.driverName, ticket.driverPhone],
    ticket.conductorPhone && ['Conductor', ticket.conductorName, ticket.conductorPhone],
  ].filter(Boolean)

  return (
    <DashboardLayout navItems={userNavItems} role="ROLE_USER">
      <div className="bus-noprint">
        <PageHead title={`${ticket.fromPlace} → ${ticket.toPlace}`} sub={`${whenText(ticket.travelDate)} · ${ticket.travelsName}`}>
          <Link to="/user/bus-tickets" className="pc-btn pc-btn-ghost"><Icon name="back" />All tickets</Link>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={() => window.print()}><Icon name="download" />Print</button>
          {ticket.canCancel && cancellableSeats(ticket).length > 0 && <button type="button" className="pc-btn pc-btn-danger" onClick={() => setCancelling(true)}><Icon name="x" />Cancel seats</button>}
        </PageHead>
        <Notice notice={notice} onClose={() => setNotice(null)} />
      </div>

      <div className="pc-grid">
        <Panel span={12} className="bus-print" title={`Ticket ${ticket.ticketNo}`} meta={<BusPill table={TICKET_STATUS} status={ticket.status} />}>
          <div className="bus-ticket-grid">
            <KV label="Travels" value={ticket.travelsName} sub={`${ticket.busName} · ${ticket.typeLabel}`} />
            <KV label="Journey" value={`${ticket.fromPlace} → ${ticket.toPlace}`} sub={whenText(ticket.travelDate)} />
            <PointBlock label="Boarding" point={ticket.boardingPoint} />
            <PointBlock label="Dropping" point={ticket.droppingPoint} />
            {ticket.registrationNumber && <KV label="Bus number" value={ticket.registrationNumber} />}
            {crew.map(([role, name, phone]) => (
              <div key={role} className="bus-kv"><small>{role}</small><b><a href={`tel:${phone}`} style={{ color: 'inherit' }}>{phone}</a></b>{name && <span>{name}</span>}</div>
            ))}
            <KV label="Contact" value={ticket.contactPhone} sub={[ticket.contactEmail, ticket.extraPhone && `also ${ticket.extraPhone}`].filter(Boolean).join(' · ')} />
          </div>
          {!ticket.registrationNumber && ticket.status === 'CONFIRMED' && (
            <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 12 }}>The bus number and crew phones are sent about 2 hours before boarding.</p>
          )}

          <h3 style={{ font: '700 14px var(--pc-fd)', margin: '20px 0 8px' }}>Passengers</h3>
          <div className="bus-table-wrap">
            <table className="bus-table">
              <thead><tr><th>Seat</th><th>Name</th><th>Age</th><th>Gender</th><th>Status</th><th className="num">Fare</th></tr></thead>
              <tbody>
                {(ticket.passengers || []).map((p) => (
                  <tr key={p.seatLabel}>
                    <td><b>{p.seatLabel}</b><small style={{ display: 'block', color: 'var(--pc-muted)' }}>{seatClassName(p.seatClass)}</small></td>
                    <td>{p.name}</td>
                    <td>{p.age}</td>
                    <td>{GENDER[p.gender] || p.gender}</td>
                    <td>
                      <BusPill table={PASSENGER_STATUS} status={p.status} />
                      {p.status === 'CANCELLED' && p.refundAmount != null && <small style={{ display: 'block', color: 'var(--pc-muted)', marginTop: 3 }}>Charge {inr(p.cancelCharge)} · refund {inr(p.refundAmount)}</small>}
                    </td>
                    <td className="num">{inr(p.fare)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr><td colSpan={5} style={{ fontWeight: 600 }}>Base fare</td><td className="num">{inr(ticket.fareTotal)}</td></tr>
                <tr><td colSpan={5} style={{ fontWeight: 600, borderTop: 0 }}>GST ({ticket.gstPercent}%)</td><td className="num" style={{ borderTop: 0 }}>{inr(ticket.gstTotal)}</td></tr>
                <tr><td colSpan={5}>Total paid</td><td className="num">{inr(ticket.totalAmount)}</td></tr>
              </tfoot>
            </table>
          </div>

          <h3 style={{ font: '700 14px var(--pc-fd)', margin: '20px 0 8px' }}>Cancellation policy</h3>
          <CancellationTable slabs={ticket.cancellationSlabs} note="Times are for your boarding point. GST on a cancelled seat is always refunded." />
        </Panel>

        {(ticket.refunds || []).length > 0 && (
          <Panel span={6} title="Refunds">
            <div className="pc-rows">
              {ticket.refunds.map((r) => (
                <div key={r.id} className="pc-row" style={{ gridTemplateColumns: 'minmax(0,1fr) auto auto' }}>
                  <div><b>{inr(r.amount)}</b><small>{whenText(r.createdAt)}</small></div>
                  <span />
                  <BusPill table={REFUND_STATUS} status={r.status} />
                </div>
              ))}
            </div>
          </Panel>
        )}

        {(ticket.canReview || ticket.review) && (
          <Panel span={6} title={ticket.review ? 'Your review' : 'How was the journey?'} className="bus-noprint">
            <ReviewBox ticket={ticket} onDone={(t) => { setTicket(t); toast.success('Thanks for the review.') }} />
          </Panel>
        )}
      </div>

      {cancelling && (
        <CancelDialog ticket={ticket} onClose={() => setCancelling(false)}
          onDone={(t) => { setTicket(t); setCancelling(false); setNotice({ tone: 'ok', text: 'Seats cancelled. Your refund is on its way.' }) }} />
      )}
    </DashboardLayout>
  )
}
