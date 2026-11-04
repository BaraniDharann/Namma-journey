import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import BusShell from '../../components/bus/BusShell'
import { SeatDecks, SeatLegend } from '../../components/bus/SeatDeck'
import CancellationTable from '../../components/bus/CancellationTable'
import { Notice } from '../../components/bus/busUi'
import Icon from '../../components/dash/Icon'
import { Skeleton } from '../../components/dash/ui'
import { useAuth } from '../../context/AuthContext'
import { getBusSeatMap, holdBusSeats, payBusTicket, releaseBusTicket, verifyBusPayment } from '../../utils/busApi'
import { errorText, inr, uploadUrl } from '../../utils/busText'
import { seatClassName, seatClassOf } from '../../utils/busLayouts'
import { clockText, dayShift, durationText, ratingText } from '../../utils/busSearch'
import {
  seatState, isSelectable, genderLock, toggleSeat, priceChips, fareBreakup, passengerRows, bookingProblems,
  cleanPhone, secondsUntil, formatCountdown, whenText, saveDraft, loadDraft, clearDraft,
} from '../../utils/busBooking'
import { actionKey } from '../../utils/idempotency'
import { openRazorpayCheckout } from '../../utils/razorpay'
import { forgetReturn, rememberReturn } from '../../utils/returnTo'

const STEPS = ['Select seats', 'Board/Drop point', 'Passenger info']
const LEGEND_ALWAYS = ['AVAILABLE', 'SELECTED', 'SOLD']
const LEGEND_MAYBE = ['BOOKED_F', 'FEMALE_ONLY', 'BOOKED_M', 'MALE_ONLY']
const SIDE_TABS = [['HIGHLIGHTS', 'Highlights'], ['POLICY', 'Cancellation policy'], ['ROUTE', 'Bus route'], ['POINTS', 'Boarding points'], ['AMENITIES', 'Amenities']]

/** One boarding or dropping point as a radio card. */
function PointOption({ name, point, checked, onPick, travelDate }) {
  const otherDay = point.at && travelDate && point.at.slice(0, 10) !== travelDate
  return (
    <label className="bus-point">
      <input type="radio" name={name} checked={checked} onChange={onPick} />
      <b>{clockText(point.at)}{otherDay && <small style={{ fontSize: 10.5, color: 'var(--pc-brand-deep)' }}>{whenText(point.at.slice(0, 10))}</small>}</b>
      <span>
        <strong>{point.name}</strong>
        {point.address && <small>{point.address}</small>}
        {point.landmark && <small>Landmark: {point.landmark}</small>}
      </span>
    </label>
  )
}

function SidePanel({ map }) {
  const [tab, setTab] = useState('HIGHLIGHTS')
  const shift = dayShift(map.departureAt, map.arrivalAt)
  const minutes = map.departureAt && map.arrivalAt ? Math.round((new Date(map.arrivalAt) - new Date(map.departureAt)) / 60000) : null
  return (
    <aside className="pc-panel bus-side" aria-label="About this bus">
      <h2 style={{ font: '700 17px var(--pc-fd)', margin: 0 }}>{map.travelsName}</h2>
      <div style={{ fontSize: 13, color: 'var(--pc-muted)', fontWeight: 700, marginTop: 3 }}>{map.busName} · {map.typeLabel}</div>
      <div style={{ marginTop: 8 }}>
        <span className={`bus-rate${Number(map.ratingCount) > 0 ? '' : ' is-new'}`}>{ratingText(map)}</span>
        {Number(map.ratingCount) > 0 && <span style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 700, marginLeft: 6 }}>{map.ratingCount} ratings</span>}
      </div>
      {(map.photos || []).length > 0 && (
        <div className="bus-side-photos">
          {map.photos.map((p, i) => <img key={p.id} src={uploadUrl(p.path)} alt={`${map.busName}, photo ${i + 1}`} loading="lazy" />)}
        </div>
      )}
      <div className="bus-tabs" role="tablist" aria-label="Bus details">
        {SIDE_TABS.map(([k, label]) => <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>{label}</button>)}
      </div>
      <div role="tabpanel">
        {tab === 'HIGHLIGHTS' && (
          <div style={{ display: 'grid', gap: 10, fontSize: 13.5 }}>
            <div className="bus-times">
              <div><b>{clockText(map.departureAt)}</b><small>{map.fromPlace}</small></div>
              <span className="bus-dur">{minutes != null ? durationText(minutes) : ''}</span>
              <div><b>{clockText(map.arrivalAt)}{shift > 0 && <sup>+{shift}</sup>}</b><small>{map.toPlace}</small></div>
            </div>
            <div>Journey on <b>{whenText(map.travelDate)}</b></div>
            <div>Book up to <b>{map.maxSeatsPerBooking}</b> seat{map.maxSeatsPerBooking === 1 ? '' : 's'} at a time.</div>
            <div>Seats next to a woman are marked <b style={{ color: '#9d174d' }}>Women</b>; next to a man, <b style={{ color: '#1e40af' }}>Men</b>.</div>
            <div>Seat prices are before GST; {map.gstPercent}% GST is added at checkout.</div>
          </div>
        )}
        {tab === 'POLICY' && <CancellationTable slabs={map.cancellationSlabs} note="Times are counted from the first boarding point. Your ticket shows the times for your own boarding point." />}
        {tab === 'ROUTE' && (
          <ol className="bus-stops">
            {(map.stops || []).map((s) => (
              <li key={s.seq} className={s.seq >= map.fromSeq && s.seq <= map.toSeq ? 'is-leg' : ''}>
                {s.placeName}{s.seq === map.fromSeq ? ' (you board)' : s.seq === map.toSeq ? ' (you get off)' : ''}
              </li>
            ))}
          </ol>
        )}
        {tab === 'POINTS' && (
          <div style={{ display: 'grid', gap: 12, fontSize: 13.5 }}>
            {[['Boarding at ' + map.fromPlace, map.boardingPoints], ['Dropping at ' + map.toPlace, map.droppingPoints]].map(([title, list]) => (
              <div key={title}>
                <b style={{ display: 'block', marginBottom: 6 }}>{title}</b>
                {(list || []).map((p) => (
                  <div key={p.id} style={{ display: 'flex', gap: 10, padding: '4px 0' }}>
                    <b style={{ fontVariantNumeric: 'tabular-nums' }}>{clockText(p.at)}</b>
                    <span>{p.name}{p.landmark ? <small style={{ display: 'block', color: 'var(--pc-muted)' }}>{p.landmark}</small> : null}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
        {tab === 'AMENITIES' && (
          (map.amenities || []).length
            ? <div className="pc-chips">{map.amenities.map((a) => <span key={a} className="pc-chip"><Icon name="check" size={13} />{a}</span>)}</div>
            : <p style={{ fontSize: 13, color: 'var(--pc-muted)' }}>The operator has not listed amenities.</p>
        )}
      </div>
    </aside>
  )
}

/** Public seat map and the three booking steps, ending in Razorpay Checkout. */
export default function BusTripPage() {
  const { tripId } = useParams()
  const [params] = useSearchParams()
  const from = params.get('from')
  const to = params.get('to')
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()

  const [map, setMap] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [step, setStep] = useState(0)
  const [selected, setSelected] = useState([])
  const [priceFilter, setPriceFilter] = useState(null)
  const [boardingId, setBoardingId] = useState(null)
  const [droppingId, setDroppingId] = useState(null)
  const [passengers, setPassengers] = useState([])
  const [contact, setContact] = useState({ phone: '', email: '', extra: '' })
  const [notice, setNotice] = useState(null)
  const [problems, setProblems] = useState([])
  const [breakup, setBreakup] = useState(false)
  const [busy, setBusy] = useState(false)
  const [holding, setHolding] = useState(null) // the PENDING ticket while Checkout is open
  const [left, setLeft] = useState(0)
  const key = useRef(actionKey())
  const restored = useRef(false)

  const fetchMap = useCallback(async () => {
    const res = await getBusSeatMap(tripId, from, to)
    return res.data
  }, [tripId, from, to])

  // Seats that stopped being available drop out of the choice, with a word about which.
  const applyMap = useCallback((data) => {
    setMap(data)
    setSelected((sel) => {
      const keep = sel.filter((l) => { const s = data.seats.find((x) => x.label === l); return s && isSelectable(s) })
      const gone = sel.filter((l) => !keep.includes(l))
      if (gone.length) toast.error(`Seat ${gone.join(', ')} is no longer available.`, { id: 'bus-gone' })
      return keep
    })
    if ((data.boardingPoints || []).length === 1) setBoardingId((b) => b ?? data.boardingPoints[0].id)
    if ((data.droppingPoints || []).length === 1) setDroppingId((d) => d ?? data.droppingPoints[0].id)
  }, [])

  const reload = useCallback(async () => {
    try { applyMap(await fetchMap()) } catch (e) { setLoadError(errorText(e, 'Could not load the seat map.')) }
  }, [fetchMap, applyMap])

  useEffect(() => {
    let live = true
    setMap(null); setLoadError('')
    fetchMap().then((data) => {
      if (!live) return
      // Back from signing in: put the choice back as it was.
      const draft = !restored.current && loadDraft(tripId, from, to)
      restored.current = true
      if (draft) {
        setSelected(draft.selected || [])
        setBoardingId(draft.boardingId ?? null)
        setDroppingId(draft.droppingId ?? null)
        setPassengers(draft.passengers || [])
        setContact((c) => ({ ...c, ...(draft.contact || {}) }))
        setStep(draft.step || 0)
        clearDraft()
      }
      applyMap(data)
    }).catch((e) => { if (live) setLoadError(errorText(e, 'Could not load the seat map.')) })
    return () => { live = false }
  }, [fetchMap, applyMap, tripId, from, to])

  // Contact details default to the signed-in traveller's own.
  useEffect(() => {
    if (!user) return
    setContact((c) => ({ ...c, phone: c.phone || user.mobile || '', email: c.email || user.email || '' }))
  }, [user])

  // Hold countdown while Checkout is open.
  useEffect(() => {
    if (!holding?.holdExpiresAt) return undefined
    const tick = () => setLeft(secondsUntil(holding.holdExpiresAt))
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [holding])

  const seats = useMemo(() => map?.seats || [], [map])
  const chosen = useMemo(() => selected.map((l) => seats.find((s) => s.label === l)).filter(Boolean), [selected, seats])
  const money = fareBreakup(chosen, map?.gstPercent)
  const rows = passengerRows(chosen, passengers)
  const chips = useMemo(() => priceChips(seats), [seats])
  const legend = useMemo(() => [...LEGEND_ALWAYS, ...LEGEND_MAYBE.filter((s) => seats.some((x) => seatState(x) === s))], [seats])
  const pointsChosen = boardingId != null && droppingId != null
  const canStep = (i) => i === 0 || (i === 1 && chosen.length > 0) || (i === 2 && chosen.length > 0 && pointsChosen)

  const onSeat = (seat) => {
    const r = toggleSeat(selected, seat, map.maxSeatsPerBooking)
    if (r.error) toast.error(r.error, { id: 'bus-seat' })
    setSelected(r.selected)
  }

  const editPassenger = (label, patch) => setPassengers(rows.map((p) => (p.seatLabel === label ? { ...p, ...patch } : p)))

  const draft = () => ({ tripId, from, to, selected, boardingId, droppingId, passengers: rows, contact, step: 2 })
  const backToSeats = (text) => { setHolding(null); setStep(0); setNotice({ tone: 'bad', text }); reload() }

  const release = async (ticketId) => { try { await releaseBusTicket(ticketId) } catch { /* the sweeper frees the seats anyway */ } }

  const proceed = async () => {
    const found = bookingProblems({ passengers: rows, seats: chosen, contactPhone: contact.phone, contactEmail: contact.email, extraPhone: contact.extra })
    setProblems(found)
    if (found.length) return
    if (!user) {
      saveDraft(draft())
      rememberReturn(location.pathname + location.search)
      navigate('/login')
      return
    }
    if (user.role !== 'ROLE_USER') { setNotice({ tone: 'bad', text: 'Sign in with a traveller account to book bus seats.' }); return }

    setBusy(true); setNotice(null)
    // If the session has expired the API layer sends us to /login; keep the choice for the way back.
    saveDraft(draft())
    rememberReturn(location.pathname + location.search)
    let ticket
    try {
      ticket = (await holdBusSeats({
        tripId: Number(tripId),
        fromPlaceId: Number(from),
        toPlaceId: Number(to),
        boardingPointId: boardingId,
        droppingPointId: droppingId,
        contactPhone: cleanPhone(contact.phone),
        contactEmail: contact.email.trim(),
        extraPhone: cleanPhone(contact.extra) || null,
        passengers: rows.map((p) => ({ seatLabel: p.seatLabel, name: p.name.trim(), age: Number(p.age), gender: p.gender })),
      }, key.current.get())).data
      key.current.reset()
      clearDraft(); forgetReturn()
    } catch (e) {
      setBusy(false)
      // 401: the API layer is already reloading into /login; the draft brings the customer back.
      if (e.response?.status === 401) return
      clearDraft(); forgetReturn()
      if (e.response) key.current.reset() // the server answered: a retry is a new request
      if (e.response?.status === 409) { backToSeats(errorText(e, 'One of your seats was just booked by someone else.')); return }
      setNotice({ tone: 'bad', text: e.response ? errorText(e, 'Could not hold these seats.') : 'Could not reach the server. Check your connection and try again.' })
      return
    }

    setHolding(ticket)
    try {
      const order = (await payBusTicket(ticket.id)).data
      let result
      try {
        result = await openRazorpayCheckout(order, { name: user.name || rows[0]?.name, email: contact.email.trim(), contact: cleanPhone(contact.phone) })
      } catch (e) {
        await release(ticket.id)
        backToSeats(`${e?.message || 'Payment failed'}. You have not been charged and the seats are released.`)
        return
      }
      if (!result) {
        await release(ticket.id)
        backToSeats('Payment cancelled. Your seats were released; pick them again to book.')
        return
      }
      try {
        await verifyBusPayment(ticket.id, {
          razorpayOrderId: result.razorpay_order_id,
          razorpayPaymentId: result.razorpay_payment_id,
          razorpaySignature: result.razorpay_signature,
        })
        toast.success('Ticket booked. It is on its way to your email.')
        navigate(`/user/bus-tickets/${ticket.id}`, { state: { justBooked: true } })
      } catch {
        // Razorpay has the money; the webhook or the sweeper confirms the ticket shortly.
        navigate(`/user/bus-tickets/${ticket.id}`, { state: { confirming: true } })
      }
    } catch (e) {
      await release(ticket.id)
      backToSeats(errorText(e, 'Could not start the payment. Your seats were released.'))
    } finally {
      setBusy(false)
    }
  }

  if (loadError && !map) {
    return (
      <BusShell>
        <div className="pc-panel">
          <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{loadError}</span><button type="button" className="pc-btn pc-btn-sm" onClick={() => window.location.reload()}>Retry</button></div>
          <p style={{ marginTop: 12 }}><button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => navigate(-1)}><Icon name="back" size={15} />Back to results</button></p>
        </div>
      </BusShell>
    )
  }
  if (!map) return <BusShell><div className="pc-panel"><Skeleton rows={8} height={20} /></div></BusShell>

  const cta = step === 0
    ? { text: 'Select boarding & dropping points', go: () => setStep(1), ok: chosen.length > 0 }
    : step === 1
      ? { text: 'Fill passenger details', go: () => setStep(2), ok: pointsChosen }
      : { text: busy ? 'Holding your seats…' : `Proceed to pay ${inr(money.total)}`, go: proceed, ok: !busy }

  return (
    <BusShell>
      <div className="bus-summary">
        <div>
          <h1>{map.fromPlace} <Icon name="arrow" size={18} /> {map.toPlace}</h1>
          <p>{whenText(map.travelDate)} · {map.travelsName}</p>
        </div>
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => navigate(-1)}><Icon name="back" size={15} />Back to buses</button>
      </div>

      <nav className="bus-steps" aria-label="Booking steps">
        {STEPS.map((label, i) => (
          <button key={label} type="button" aria-current={step === i ? 'step' : undefined} disabled={!canStep(i) || busy} onClick={() => setStep(i)}>
            <i>{i + 1}</i>{label}
          </button>
        ))}
      </nav>

      <Notice notice={notice} onClose={() => setNotice(null)} />
      {holding && (
        <div className="bus-hold" role="status" style={{ marginBottom: 14 }}>
          <Icon name="clock" size={18} />Seats held for you. Complete the payment within <b>{formatCountdown(left)}</b>
        </div>
      )}

      <div className="bus-trip">
        <section className="pc-panel" aria-label={STEPS[step]}>
          {step === 0 && (
            <>
              {chips.length > 1 && (
                <div className="bus-pricechips" role="group" aria-label="Show seats by price">
                  <button type="button" className="pc-chip" aria-pressed={priceFilter == null} onClick={() => setPriceFilter(null)}>All</button>
                  {chips.map((p) => <button key={p} type="button" className="pc-chip" aria-pressed={priceFilter === p} onClick={() => setPriceFilter(p)}>{inr(p)}</button>)}
                </div>
              )}
              <div className="bus-deck-scroll">
                <SeatDecks seats={seats} stateOf={seatState} selected={selected} onSeatClick={onSeat}
                  priceOf={(s) => (isSelectable(s) ? Number(s.fare) : null)}
                  dimOf={(s) => priceFilter != null && Number(s.fare) !== priceFilter} />
              </div>
              <SeatLegend states={legend} />
              <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 10 }}>
                Up to {map.maxSeatsPerBooking} seat{map.maxSeatsPerBooking === 1 ? '' : 's'} per booking. Seats marked Women or Men are next to a passenger already booked, so only a woman or a man can take them.
              </p>
            </>
          )}

          {step === 1 && (
            <div className="bus-points">
              <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
                <legend style={{ font: '700 15px var(--pc-fd)', marginBottom: 10 }}>Boarding point · {map.fromPlace}</legend>
                {(map.boardingPoints || []).map((p) => <PointOption key={p.id} name="boarding" point={p} travelDate={map.travelDate} checked={boardingId === p.id} onPick={() => setBoardingId(p.id)} />)}
              </fieldset>
              <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
                <legend style={{ font: '700 15px var(--pc-fd)', marginBottom: 10 }}>Dropping point · {map.toPlace}</legend>
                {(map.droppingPoints || []).map((p) => <PointOption key={p.id} name="dropping" point={p} travelDate={map.travelDate} checked={droppingId === p.id} onPick={() => setDroppingId(p.id)} />)}
              </fieldset>
            </div>
          )}

          {step === 2 && (
            <>
              <h2 style={{ font: '700 15px var(--pc-fd)', margin: '0 0 6px' }}>Passengers</h2>
              {rows.map((p) => {
                const seat = chosen.find((s) => s.label === p.seatLabel)
                const lock = genderLock(seat)
                const id = `pax-${p.seatLabel}`
                return (
                  <div key={p.seatLabel} className="bus-pax">
                    <div className="bus-pax-seat">{p.seatLabel}<small>{seatClassName(seatClassOf(seat))}</small></div>
                    <div>
                      <label htmlFor={`${id}-name`} style={{ display: 'block', fontSize: 12, fontWeight: 800, color: 'var(--pc-muted)', marginBottom: 4 }}>Name</label>
                      <input id={`${id}-name`} className="input-field" autoComplete="name" value={p.name} onChange={(e) => editPassenger(p.seatLabel, { name: e.target.value })} />
                    </div>
                    <div>
                      <label htmlFor={`${id}-age`} style={{ display: 'block', fontSize: 12, fontWeight: 800, color: 'var(--pc-muted)', marginBottom: 4 }}>Age</label>
                      <input id={`${id}-age`} className="input-field" type="number" inputMode="numeric" min={1} max={120} value={p.age} onChange={(e) => editPassenger(p.seatLabel, { age: e.target.value })} />
                    </div>
                    <div className="bus-gender" role="group" aria-label={`Gender for seat ${p.seatLabel}${lock ? `, ${lock === 'F' ? 'women' : 'men'} only` : ''}`}>
                      {[['M', 'Male'], ['F', 'Female']].map(([g, label]) => (
                        <button key={g} type="button" aria-pressed={p.gender === g} disabled={!!lock && lock !== g} onClick={() => editPassenger(p.seatLabel, { gender: g })}
                          title={lock && lock !== g ? `Seat ${p.seatLabel} is for ${lock === 'F' ? 'women' : 'men'} only` : undefined}>{label}</button>
                      ))}
                    </div>
                  </div>
                )
              })}

              <h2 style={{ font: '700 15px var(--pc-fd)', margin: '18px 0 10px' }}>Contact details</h2>
              <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', margin: '0 0 10px' }}>Your ticket and the bus details are sent here.</p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
                {[['phone', 'Mobile number', 'tel', 'tel'], ['email', 'Email', 'email', 'email'], ['extra', 'Also send bus details to (optional)', 'tel', 'off']].map(([k, label, type, ac]) => (
                  <div key={k}>
                    <label htmlFor={`contact-${k}`} style={{ display: 'block', fontSize: 12, fontWeight: 800, color: 'var(--pc-muted)', marginBottom: 4 }}>{label}</label>
                    <input id={`contact-${k}`} className="input-field" type={type} autoComplete={ac} inputMode={type === 'tel' ? 'tel' : undefined} value={contact[k]} onChange={(e) => setContact((c) => ({ ...c, [k]: e.target.value }))} />
                  </div>
                ))}
              </div>
              {!user && <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', marginTop: 12, fontWeight: 600 }}><Icon name="lock" size={14} /> You will sign in before paying; your seats and details stay as they are.</p>}
              {problems.length > 0 && (
                <ul className="pc-error" role="alert" style={{ display: 'block', marginTop: 14, paddingLeft: 30 }}>
                  {problems.map((p) => <li key={p}>{p}</li>)}
                </ul>
              )}
            </>
          )}
        </section>

        <SidePanel map={map} />
      </div>

      {chosen.length > 0 && (
        <div className="bus-foot">
          <div className="bus-foot-in">
            <div className="bus-foot-sum">
              <b>{chosen.length} seat{chosen.length === 1 ? '' : 's'} · {inr(money.fare)}</b>
              <small>Seat{chosen.length === 1 ? '' : 's'} {selected.join(', ')}</small>
            </div>
            <div className="bus-breakup">
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" aria-expanded={breakup} onClick={() => setBreakup((v) => !v)}>Fare breakup</button>
              {breakup && (
                <div className="bus-breakup-pop" role="dialog" aria-label="Fare breakup" onKeyDown={(e) => { if (e.key === 'Escape') setBreakup(false) }}>
                  {chosen.map((s) => <div key={s.label}><span>Seat {s.label}</span><span>{inr(s.fare)}</span></div>)}
                  <div><span>Base fare</span><span>{inr(money.fare)}</span></div>
                  <div><span>GST ({money.gstPercent}%)</span><span>{inr(money.gst)}</span></div>
                  <div className="is-total"><span>Total</span><span>{inr(money.total)}</span></div>
                </div>
              )}
            </div>
            <button type="button" className="pc-btn" disabled={!cta.ok} onClick={cta.go}>{cta.text}</button>
          </div>
        </div>
      )}
    </BusShell>
  )
}
