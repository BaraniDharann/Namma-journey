import React, { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Skeleton, Empty } from '../../../components/dash/ui'
import { SeatDecks, SeatLegend } from '../../../components/bus/SeatDeck'
import { BusPill, Notice, ownerNavItems, labelStyle, capsStyle, wellStyle, fieldGrid, mutedStyle, dangerBtn } from '../../../components/bus/busUi'
import {
  getBusTrip, setTripCrew, blockTripSeats, unblockTripSeats, cancelBusTrip, getTripManifest,
} from '../../../utils/busApi'
import { liveAllocations, tripSeatState } from '../../../utils/busLayouts'
import { TRIP_STATUS, dateText, errorText, inr, timeText } from '../../../utils/busText'

const CREW = [
  ['driverName', 'Driver name'], ['driverPhone', 'Driver phone'],
  ['conductorName', 'Conductor name'], ['conductorPhone', 'Conductor phone'],
]

const pickCrew = (t) => Object.fromEntries(CREW.map(([k]) => [k, t[k] || '']))

/** One trip: the seat chart with passengers, seat blocks, crew, the manifest and cancelling. */
export default function OwnerBusTripDetail() {
  const { id } = useParams()
  const [trip, setTrip] = useState(null)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState([])
  const [focus, setFocus] = useState(null)
  const [crew, setCrew] = useState(pickCrew({}))
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')

  const take = useCallback((t) => { setTrip(t); setCrew(pickCrew(t)) }, [])

  const load = useCallback(async () => {
    try { take((await getBusTrip(id)).data); setError('') } catch (e) { setError(errorText(e, 'Could not load this trip.')) }
  }, [id, take])
  useEffect(() => { load() }, [load])

  const run = async (fn, ok) => {
    setBusy(true); setNotice(null)
    try { take((await fn()).data); setNotice({ tone: 'ok', text: ok }); return true }
    catch (e) { setNotice({ tone: 'error', text: errorText(e, 'Could not save that.') }); return false }
    finally { setBusy(false) }
  }

  const stopName = (seq) => trip?.stops?.find((s) => s.seq === seq)?.placeName || `Stop ${seq + 1}`

  const toggle = (seat) => {
    setFocus(seat.label)
    setSelected((s) => (s.includes(seat.label) ? s.filter((x) => x !== seat.label) : [...s, seat.label]))
  }

  const block = async () => { if (await run(() => blockTripSeats(id, selected), `Blocked ${selected.join(', ')}.`)) setSelected([]) }
  const unblock = async () => { if (await run(() => unblockTripSeats(id, selected), `Released ${selected.join(', ')}.`)) setSelected([]) }
  const saveCrew = (e) => { e.preventDefault(); run(() => setTripCrew(id, crew), 'Crew saved. Passengers get these numbers in their reminder.') }
  const cancel = async () => {
    if (await run(() => cancelBusTrip(id, reason.trim()), 'Trip cancelled. Every passenger is being refunded in full and told.')) setCancelling(false)
  }

  const download = async () => {
    try {
      const res = await getTripManifest(id)
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url
      a.download = `manifest-${trip.travelDate}-${trip.registrationNumber || id}.csv`
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch { /* already surfaced by the api error toast */ }
  }

  if (!trip) {
    return (
      <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
        <p style={{ marginBottom: 8 }}><Link to="/owner/bus-trips">← All trips</Link></p>
        {error ? <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div>
          : <Panel span={12}><Skeleton rows={6} height={18} /></Panel>}
      </DashboardLayout>
    )
  }

  const seats = trip.seats || []
  const open = trip.status === 'SCHEDULED'
  const focusSeat = seats.find((s) => s.label === focus)
  const selSeats = seats.filter((s) => selected.includes(s.label))
  const canBlock = selSeats.length > 0 && selSeats.every((s) => !liveAllocations(s).length)
  const canUnblock = selSeats.length > 0 && selSeats.every((s) => tripSeatState(s) === 'BLOCKED')
  const passengers = seats.flatMap((s) => (s.allocations || []).filter((a) => a.status === 'CONFIRMED').map((a) => ({ ...a, seat: s.label })))
    .sort((a, b) => a.fromSeq - b.fromSeq || a.seat.localeCompare(b.seat, undefined, { numeric: true }))

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <p style={{ marginBottom: 8 }}><Link to="/owner/bus-trips">← All trips</Link></p>
      <PageHead title={`${trip.routeName} · ${dateText(trip.travelDate)}`}
        sub={`${trip.busName} · ${trip.registrationNumber} · departs ${timeText(trip.departureAt)} · arrives ${timeText(trip.arrivalAt)}`}>
        <BusPill table={TRIP_STATUS} status={trip.status} />
        <button type="button" className="pc-btn pc-btn-ghost" onClick={download}><Icon name="download" />Download CSV</button>
      </PageHead>

      <Notice notice={notice} onClose={() => setNotice(null)} />
      {trip.status === 'CANCELLED' && (
        <div className="pc-error" role="status" style={{ marginBottom: 16 }}><Icon name="alert" size={16} /><span>Cancelled{trip.cancelReason ? `: ${trip.cancelReason}` : ''}.</span></div>
      )}

      <div className="pc-grid">
        <Panel span={8} title="Seat chart" meta={`${trip.seatsSold} of ${trip.seatsTotal} sold · ${inr(trip.amountCollected)}`}>
          <SeatDecks seats={seats} stateOf={tripSeatState} selected={selected} onSeatClick={toggle} />
          <SeatLegend states={['AVAILABLE', 'SELECTED', 'BOOKED_F', 'BOOKED_M', 'SOLD', 'BLOCKED']} />
          <p style={{ ...mutedStyle, marginTop: 10 }}>A seat sold for part of the route shows as booked; open it to see which stops.</p>
        </Panel>

        <Panel span={4} title="Selected seats">
          {!selected.length ? (
            <p style={{ fontSize: 13, margin: 0 }}>Click seats to see who sits there, or to hold them back from sale.</p>
          ) : (
            <>
              <div className="pc-chips" style={{ marginBottom: 12 }}>{selected.map((l) => <span key={l} className="pc-chip">{l}</span>)}</div>
              {open && (
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
                  <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !canBlock} onClick={block}><Icon name="lock" size={14} />Block</button>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={busy || !canUnblock} onClick={unblock}>Unblock</button>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setSelected([])}>Clear</button>
                </div>
              )}
              {!canBlock && !canUnblock && open && <p style={{ ...mutedStyle, margin: '0 0 12px' }}>Block works on free seats only, unblock on blocked seats only.</p>}
            </>
          )}
          {focusSeat && (
            <div style={wellStyle}>
              <span style={capsStyle}>Seat {focusSeat.label}</span>
              {!liveAllocations(focusSeat).length ? <p style={{ fontSize: 13, margin: '6px 0 0' }}>Free for the whole route.</p>
                : liveAllocations(focusSeat).map((a, i) => (
                  <div key={i} style={{ fontSize: 13, marginTop: 8, paddingTop: i ? 8 : 0, borderTop: i ? '1px solid var(--pc-line)' : 'none' }}>
                    {a.status === 'BLOCKED' ? <b>Blocked by you</b> : (
                      <>
                        <b>{a.passengerName || 'Passenger'}</b>{a.age ? `, ${a.age}` : ''}{a.gender ? ` (${a.gender})` : ''}{a.status === 'HELD' ? ' · paying now' : ''}
                        <div style={mutedStyle}>{stopName(a.fromSeq)} → {stopName(a.toSeq)}{a.ticketNo ? ` · ${a.ticketNo}` : ''}</div>
                        {a.phone && <div><a href={`tel:${a.phone}`}>{a.phone}</a></div>}
                        {(a.boardingPoint || a.droppingPoint) && <div style={mutedStyle}>{a.boardingPoint} → {a.droppingPoint}</div>}
                      </>
                    )}
                  </div>
                ))}
            </div>
          )}
        </Panel>

        <Panel span={12} pad={false} title="Passengers" meta={`${passengers.length}`}>
          {!passengers.length ? <Empty icon="users" title="No passengers yet">Confirmed bookings appear here.</Empty> : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead><tr><th>Seat</th><th>Passenger</th><th>Phone</th><th>Travels</th><th>Boarding</th><th>Dropping</th><th>Ticket</th></tr></thead>
                <tbody>
                  {passengers.map((p, i) => (
                    <tr key={`${p.ticketNo}-${p.seat}-${i}`}>
                      <td><strong>{p.seat}</strong></td>
                      <td>{p.passengerName}<div style={mutedStyle}>{[p.age, p.gender].filter(Boolean).join(' · ')}</div></td>
                      <td style={{ whiteSpace: 'nowrap' }}>{p.phone ? <a href={`tel:${p.phone}`}>{p.phone}</a> : '—'}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>{stopName(p.fromSeq)} → {stopName(p.toSeq)}</td>
                      <td>{p.boardingPoint || '—'}</td>
                      <td>{p.droppingPoint || '—'}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: 12.5 }}>{p.ticketNo}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>

        <Panel span={8} title="Crew" meta="Sent to passengers about 2 hours before boarding">
          <form onSubmit={saveCrew}>
            <fieldset disabled={busy || trip.status === 'CANCELLED' || trip.status === 'COMPLETED'} style={{ border: 'none', padding: 0, margin: 0 }}>
              <div style={fieldGrid}>
                {CREW.map(([k, l]) => (
                  <div key={k}>
                    <label htmlFor={`crew-${k}`} style={labelStyle}>{l}</label>
                    <input id={`crew-${k}`} className="input-field" value={crew[k]} maxLength={k.endsWith('Phone') ? 10 : 80}
                      inputMode={k.endsWith('Phone') ? 'numeric' : undefined}
                      onChange={(e) => setCrew((c) => ({ ...c, [k]: k.endsWith('Phone') ? e.target.value.replace(/\D/g, '') : e.target.value }))} />
                  </div>
                ))}
              </div>
              <button type="submit" className="pc-btn" style={{ marginTop: 16 }}><Icon name="check" />Save crew</button>
            </fieldset>
          </form>
        </Panel>

        {open && (
          <Panel span={4} title="Cancel this trip">
            {!cancelling ? (
              <>
                <p style={{ fontSize: 13, margin: '0 0 12px' }}>Only if the bus cannot run. Every passenger gets a full refund, including GST.</p>
                <button type="button" className="pc-btn pc-btn-ghost" style={{ color: 'var(--pc-bad)' }} onClick={() => setCancelling(true)}>Cancel trip</button>
              </>
            ) : (
              <div role="alertdialog" aria-labelledby="cancel-title" aria-describedby="cancel-warn">
                <p id="cancel-title" style={{ fontWeight: 800, margin: '0 0 6px' }}>Cancel the {dateText(trip.travelDate)} trip?</p>
                <p id="cancel-warn" style={{ fontSize: 13, color: 'var(--pc-bad)', margin: '0 0 10px' }}>
                  All {passengers.length} passenger{passengers.length === 1 ? '' : 's'} get a full refund (fare and GST) and a message. You get no payout for this trip. This cannot be undone.
                </p>
                <label htmlFor="cancel-reason" style={labelStyle}>Reason passengers will see *</label>
                <textarea id="cancel-reason" className="input-field" rows={3} maxLength={300} value={reason}
                  onChange={(e) => setReason(e.target.value)} style={{ width: '100%', resize: 'vertical' }} />
                <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
                  <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={() => setCancelling(false)}>Keep the trip</button>
                  <button type="button" className="pc-btn" style={dangerBtn} disabled={busy || reason.trim().length < 5} onClick={cancel}>
                    {busy ? 'Cancelling…' : 'Cancel and refund everyone'}
                  </button>
                </div>
              </div>
            )}
          </Panel>
        )}
      </div>
    </DashboardLayout>
  )
}
