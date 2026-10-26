import React, { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import PlaceAutocomplete from '../../components/PlaceAutocomplete'
import RouteMap from '../../components/RouteMap'
import { useAuth } from '../../context/AuthContext'
import { createBooking, getCurrentPricing, getCityFareEstimate } from '../../utils/api'
import { actionKey } from '../../utils/idempotency'
import { MobileRequiredPanel, needsMobileNumber } from '../../components/MobileNumberGate'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty } from '../../components/dash/ui'
import { inr, shortPlace } from '../../dash/metrics'
import { VEHICLES, VEHICLE_ORDER, vehicleLabel, modeLabel } from '../../dash/vehicles'
import { useCelebrate } from '../../components/celebrate/Celebration'
import RideSearchStatus from '../../components/RideSearchStatus'
import { useCollectAdvance } from '../../hooks/usePayOnline'

const navItems = [
  { path: '/user/dashboard', icon: '', label: 'Dashboard' },
  { path: '/user/bookings', icon: '', label: 'My Bookings' },
  { path: '/user/bookings/new', icon: '', label: 'New Booking' },
  { path: '/user/payments', icon: '', label: 'Payments' },
  { path: '/user/package-bookings', icon: '', label: 'My Packages' },
  { path: '/user/profile', icon: '', label: 'Profile' },
]

/** Today as YYYY-MM-DD in the browser's own calendar (toISOString would give the UTC day). */
const localToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const temples = ['Tirupati Balaji', 'Shirdi Sai Baba', 'Vaishno Devi', 'Kedarnath', 'Badrinath', 'Somnath', 'Rameshwaram', 'Kashi Vishwanath', 'Mahakaleshwar', 'Jagannath Puri']

const MODES = [
  { key: 'CITY', icon: 'bike', label: 'City ride', desc: 'Bike, auto or car, right now' },
  { key: 'TRIP', icon: 'temple', label: 'Trip', desc: 'Temple, holiday or outstation, by car' },
]

const emptyForm = (mode = 'TRIP') => ({
  mode, vehicleType: 'AUTO',
  fromPlace: '', toPlace: '', fromLat: null, fromLon: null, toLat: null, toLon: null,
  fromDate: '', toDate: '', travelMembers: 1, acType: 'AC', bookingType: 'DISTANCE_BASED', bookingHours: 2,
})

export default function NewBooking() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const celebrate = useCelebrate()
  const collectAdvance = useCollectAdvance()
  const [searchParams] = useSearchParams()
  // One key per booking attempt, reused if the request is retried, so it can only book once.
  const bookingKey = useRef(actionKey())

  // The home page's City card sends the picked places with their coordinates, so the fares show straight away.
  const coord = (key) => {
    const n = parseFloat(searchParams.get(key))
    return Number.isFinite(n) ? n : null
  }
  const [form, setForm] = useState(() => ({
    ...emptyForm(searchParams.get('mode') === 'city' ? 'CITY' : 'TRIP'),
    ...(VEHICLES[searchParams.get('vehicle')] ? { vehicleType: searchParams.get('vehicle') } : {}),
    fromPlace: searchParams.get('from') || '', toPlace: searchParams.get('to') || '',
    fromLat: coord('fromLat'), fromLon: coord('fromLon'), toLat: coord('toLat'), toLon: coord('toLon'),
    fromDate: searchParams.get('date') || '', travelMembers: Number(searchParams.get('members')) || 1,
  }))
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(null)
  const [pricing, setPricing] = useState({ pricePerKm: 10, pricePerHour: 150 })
  const [estimate, setEstimate] = useState({ loading: false, data: null, failed: false })

  const city = form.mode === 'CITY'

  useEffect(() => {
    getCurrentPricing().then(res => {
      if (res.data) setPricing(res.data)
    }).catch(() => {})
  }, [])

  // City fares for bike, auto and car, from the real road route, once both places are picked.
  useEffect(() => {
    if (!city || !form.fromLat || !form.toLat) return
    let stale = false
    setEstimate((e) => ({ ...e, loading: true, failed: false }))
    getCityFareEstimate(form)
      .then((r) => { if (!stale) setEstimate({ loading: false, data: r.data, failed: false }) })
      .catch(() => { if (!stale) setEstimate({ loading: false, data: null, failed: true }) })
    return () => { stale = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only a new route needs new fares
  }, [city, form.fromLat, form.fromLon, form.toLat, form.toLon])

  const set = (k, v) => setForm(p => ({ ...p, [k]: v }))

  const handleFromChange = ({ name, lat, lon }) => {
    setForm(p => ({ ...p, fromPlace: name, fromLat: lat, fromLon: lon }))
  }

  const handleToChange = ({ name, lat, lon }) => {
    setForm(p => ({ ...p, toPlace: name, toLat: lat, toLon: lon }))
  }

  const fareFor = (type) => estimate.data?.options?.find((o) => o.vehicleType === type)
  const tooManyFor = (type) => Number(form.travelMembers) > (VEHICLES[type]?.maxPassengers ?? 1)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.fromLat || !form.toLat) {
      setError('Please select both locations from the dropdown suggestions for accurate distance calculation.')
      return
    }
    if (city) {
      if (tooManyFor(form.vehicleType)) {
        setError(`A ${vehicleLabel(form.vehicleType).toLowerCase()} can't take ${form.travelMembers} passengers. Choose a bigger vehicle.`)
        return
      }
      if (estimate.data && !estimate.data.withinCity) {
        setError(`City rides go up to ${estimate.data.maxKm} km. Book this one as a Trip instead.`)
        return
      }
    } else {
      if (!form.fromDate || !form.toDate) {
        setError('Please pick both a start date and an end date.')
        return
      }
      if (new Date(form.toDate) < new Date(form.fromDate)) {
        setError('End date cannot be before the start date.')
        return
      }
      if (form.bookingType === 'HOUR_BASED' && (!form.bookingHours || Number(form.bookingHours) < 1)) {
        setError('Please enter a valid number of hours for an hour-based booking.')
        return
      }
    }
    setLoading(true)
    setError('')
    try {
      const res = await createBooking(user.userId, {
        userName: user.name,
        userPhone: user.mobile || '',
        fromPlace: form.fromPlace,
        toPlace: form.toPlace,
        fromLat: form.fromLat,
        fromLon: form.fromLon,
        toLat: form.toLat,
        toLon: form.toLon,
        serviceMode: form.mode,
        vehicleType: city ? form.vehicleType : 'CAR',
        // A City ride is for today in India, set by the server; these only satisfy the form contract.
        fromDate: city ? localToday() : form.fromDate,
        toDate: city ? localToday() : form.toDate,
        travelMembers: Number(form.travelMembers),
        acType: city ? 'NON_AC' : form.acType,
        bookingType: city ? 'DISTANCE_BASED' : form.bookingType,
        bookingHours: !city && form.bookingType === 'HOUR_BASED' ? Number(form.bookingHours) : null,
      }, bookingKey.current.get())
      // Done: the next booking is a new action and needs its own key.
      bookingKey.current.reset()
      setSuccess(res.data)
      // A City ride gets its own live "finding your driver" panel; the celebration is for planned trips.
      if (res.data?.serviceMode === 'CITY') return
      if (res.data?.advanceDue) {
        await collectAdvance(res.data)
        return
      }
      celebrate({
        title: 'Trip booked',
        message: `${shortPlace(res.data?.fromPlace || form.fromPlace)} → ${shortPlace(res.data?.toPlace || form.toPlace)}. A verified driver will be assigned shortly.`,
        actionLabel: 'View my bookings',
        onAction: () => navigate('/user/bookings'),
      })
    } catch (err) {
      setError(err.response?.data?.message || Object.values(err.response?.data || {}).join(', ') || 'Booking failed')
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    const cityRide = success.serviceMode === 'CITY'
    return (
      <DashboardLayout navItems={navItems} role="ROLE_USER">
        <PageHead title={cityRide ? 'Ride booked' : 'Booking Confirmed!'} sub={cityRide ? 'Matching you with the nearest driver' : 'We are matching you with a driver now'}>
          <button type="button" onClick={() => navigate('/user/bookings')} className="pc-btn"><Icon name="calendar" />View Bookings</button>
          <button type="button" onClick={() => { setSuccess(null); setForm(emptyForm(success.serviceMode)) }}
            className="pc-btn pc-btn-ghost"><Icon name="plus" />New Booking</button>
        </PageHead>
        <div className="pc-grid">
          {cityRide && (
            <Panel span={12} title="Your driver">
              <RideSearchStatus userId={user.userId} booking={success} />
            </Panel>
          )}
          <Panel span={7} title={cityRide ? 'Your ride' : 'Your trip'} meta={<StatusPill status={success.status || 'PENDING'} />}>
            <div className="pc-route">
              <i /><div style={{ overflowWrap: 'anywhere' }}>{success.fromPlace}<small>From</small></div>
              <span className="pc-route-ln" /><span />
              <i className="is-end" /><div style={{ overflowWrap: 'anywhere' }}>{success.toPlace}<small>To</small></div>
            </div>
            <div className="pc-rows" style={{ marginTop: 14 }}>
              {[['Type', cityRide ? modeLabel(success) : success.bookingType === 'HOUR_BASED' ? `Trip · Hour Based (${success.bookingHours}h)` : 'Trip · Distance Based'], ['Distance', `${success.distanceKm?.toFixed(1)} km`]].map(([k, v]) => (
                <div key={k} className="pc-row" style={{ gridTemplateColumns: 'minmax(0,1fr) auto' }}>
                  <span style={{ color: 'var(--pc-ink-2)' }}>{k}</span>
                  <b>{v}</b>
                </div>
              ))}
            </div>
          </Panel>
          <Panel span={5} title="Amount">
            <div className="pc-figure" style={{ fontSize: 40, color: 'var(--pc-brand-deep)' }}>{inr(success.totalAmount)}</div>
            <p style={{ margin: '10px 0 0', fontSize: 13.5, color: 'var(--pc-ink-2)' }}>
              {cityRide ? 'Pay your driver at the end of the ride.' : 'You can pay once a driver confirms the trip.'}
            </p>
          </Panel>
        </div>
      </DashboardLayout>
    )
  }

  // No contact number, no booking — the server refuses it too, so showing the form here would
  // only let someone fill the whole thing in before being turned away.
  if (needsMobileNumber(user)) {
    return (
      <DashboardLayout navItems={navItems} role="ROLE_USER">
        <PageHead title="Book a Trip" sub="One thing to sort out first" />
        <div className="pc-grid">
          <div className="pc-span-12" style={{ maxWidth: 680 }}>
            <MobileRequiredPanel />
          </div>
        </div>
      </DashboardLayout>
    )
  }

  const choice = (on) => ({
    display: 'inline-flex', alignItems: 'center', gap: 8, padding: '12px 14px', borderRadius: 14, cursor: 'pointer', textAlign: 'left', transition: 'all 0.15s',
    border: on ? '2px solid var(--pc-brand)' : '2px solid var(--pc-line)', background: on ? 'var(--pc-brand-soft)' : 'var(--pc-panel)',
    color: on ? 'var(--pc-brand-deep)' : 'var(--pc-ink-2)', fontWeight: 800, fontSize: 13.5,
  })
  const subHead = { fontSize: 12, fontWeight: 800, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', margin: '0 0 12px', display: 'flex', alignItems: 'center', gap: 8 }
  const lbl = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
  const picked = <div style={{ fontSize: 12, color: 'var(--pc-good)', marginTop: 4, fontWeight: 700, display: 'flex', alignItems: 'center', gap: 4 }}><Icon name="check" size={13} />Location selected</div>
  const hourTotal = form.bookingHours * pricing.pricePerHour
  const row = { gridTemplateColumns: 'minmax(0,1fr) auto' }
  const chosenFare = fareFor(form.vehicleType)

  return (
    <DashboardLayout navItems={navItems} role="ROLE_USER">
      <PageHead title={city ? 'Book a City Ride' : 'Book a Trip'} sub={city ? 'The nearest driver comes to you' : 'Fill in the details to book your journey'} />

      {error && <div className="alert-error" role="alert" style={{ marginBottom: 16, padding: '12px 16px', fontSize: 13, display: 'flex', alignItems: 'center', gap: 8 }}><Icon name="alert" size={16} />{error}</div>}

      <form onSubmit={handleSubmit} className="pc-grid">
        <Panel span={7} title={city ? 'Ride details' : 'Trip details'}>
          {/* City or Trip */}
          <div role="group" aria-label="What do you need?" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10, marginBottom: 22 }}>
            {MODES.map(m => (
              <button key={m.key} type="button" aria-pressed={form.mode === m.key} onClick={() => { set('mode', m.key); setError('') }}
                style={{ ...choice(form.mode === m.key), alignItems: 'flex-start', padding: 16 }}>
                <Icon name={m.icon} size={20} />
                <span>
                  <span style={{ display: 'block', fontSize: 15, marginBottom: 2 }}>{m.label}</span>
                  <span style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--pc-muted)' }}>{m.desc}</span>
                </span>
              </button>
            ))}
          </div>

          {/* Route Details */}
          <h3 style={subHead}><Icon name="pin" size={15} />Route</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={lbl}>From (Pickup Location) *</label>
              <PlaceAutocomplete
                value={form.fromPlace}
                onChange={handleFromChange}
                placeholder={city ? 'Where should the driver pick you up?' : 'Search your city, town or area...'}
                required
              />
              {form.fromLat && picked}
            </div>
            <div>
              <label style={lbl}>To (Destination) *</label>
              <PlaceAutocomplete
                value={form.toPlace}
                onChange={handleToChange}
                placeholder={city ? 'Where are you going?' : 'Search temple, city or destination...'}
                required
              />
              {form.toLat && picked}
              {!city && (
                <div className="pc-chips" style={{ marginTop: 10 }}>
                  {temples.slice(0, 5).map(t => (
                    <button key={t} type="button" aria-pressed={form.toPlace === t} onClick={() => handleToChange({ name: t, lat: null, lon: null })}
                      className={`pc-btn pc-btn-sm ${form.toPlace === t ? '' : 'pc-btn-ghost'}`}>
                      <Icon name="temple" size={14} />{t}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {city ? (
            <>
              {/* Passengers, then the vehicle with its fare */}
              <h3 style={{ ...subHead, marginTop: 24 }}><Icon name="users" size={15} />Passengers</h3>
              <div className="pc-chips">
                {[1, 2, 3, 4, 5, 6, 7].map(n => (
                  <button key={n} type="button" aria-pressed={Number(form.travelMembers) === n} onClick={() => set('travelMembers', n)}
                    className={`pc-btn pc-btn-sm ${Number(form.travelMembers) === n ? '' : 'pc-btn-ghost'}`} style={{ minWidth: 44 }}>
                    {n}
                  </button>
                ))}
              </div>

              <h3 style={{ ...subHead, marginTop: 24 }}><Icon name="car" size={15} />Vehicle</h3>
              <div role="radiogroup" aria-label="Vehicle" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
                {VEHICLE_ORDER.map(type => {
                  const v = VEHICLES[type]
                  const fare = fareFor(type)
                  const full = tooManyFor(type)
                  const on = form.vehicleType === type
                  return (
                    <button key={type} type="button" role="radio" aria-checked={on} disabled={full}
                      onClick={() => set('vehicleType', type)}
                      style={{ ...choice(on), flexDirection: 'column', alignItems: 'flex-start', gap: 6, padding: 16, opacity: full ? 0.5 : 1, cursor: full ? 'not-allowed' : 'pointer' }}>
                      <Icon name={v.icon} size={26} />
                      <span style={{ fontSize: 15 }}>{v.label}</span>
                      <span className="pc-figure" style={{ fontSize: 20 }}>
                        {fare ? inr(fare.fare) : estimate.loading ? '…' : '—'}
                      </span>
                      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--pc-muted)' }}>{full ? 'Too many passengers' : v.blurb}</span>
                    </button>
                  )
                })}
              </div>
              {!form.fromLat || !form.toLat ? (
                <p style={{ margin: '10px 0 0', fontSize: 12.5, color: 'var(--pc-muted)' }}>Pick both places to see the fares.</p>
              ) : estimate.failed ? (
                <p style={{ margin: '10px 0 0', fontSize: 12.5, color: 'var(--pc-muted)' }}>Fares could not be worked out right now. You will see the fare when you book.</p>
              ) : estimate.data && !estimate.data.withinCity ? (
                <p role="alert" style={{ margin: '10px 0 0', fontSize: 13, fontWeight: 700, color: 'var(--pc-warn)' }}>
                  This route is {estimate.data.distanceKm} km. City rides go up to {estimate.data.maxKm} km —{' '}
                  <button type="button" onClick={() => set('mode', 'TRIP')} style={{ background: 'none', border: 0, padding: 0, color: 'var(--pc-brand-deep)', fontWeight: 800, cursor: 'pointer', textDecoration: 'underline' }}>book it as a Trip</button>.
                </p>
              ) : null}
            </>
          ) : (
            <>
              {/* Booking Type */}
              <h3 style={{ ...subHead, marginTop: 24 }}><Icon name="clock" size={15} />Booking type</h3>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10, marginBottom: 14 }}>
                {[{ key: 'DISTANCE_BASED', icon: 'route', label: 'Distance Based', desc: 'Pay per kilometer' }, { key: 'HOUR_BASED', icon: 'clock', label: 'Hour Based', desc: 'Pay per hour' }].map(type => (
                  <button key={type.key} type="button" aria-pressed={form.bookingType === type.key} onClick={() => set('bookingType', type.key)}
                    style={{ ...choice(form.bookingType === type.key), alignItems: 'flex-start', padding: 16 }}>
                    <Icon name={type.icon} size={18} />
                    <span>
                      <span style={{ display: 'block', fontSize: 14, marginBottom: 2 }}>{type.label}</span>
                      <span style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--pc-muted)' }}>{type.desc}</span>
                    </span>
                  </button>
                ))}
              </div>
              {form.bookingType === 'HOUR_BASED' && (
                <div style={{ background: 'var(--pc-wash)', borderRadius: 14, padding: 18 }}>
                  <label style={{ ...lbl, marginBottom: 10 }}>Select Hours *</label>
                  <div className="pc-chips">
                    {[1, 2, 3, 4, 5, 6, 8, 10, 12, 24].map(h => (
                      <button key={h} type="button" aria-pressed={form.bookingHours === h} onClick={() => set('bookingHours', h)}
                        className={`pc-btn pc-btn-sm ${form.bookingHours === h ? '' : 'pc-btn-ghost'}`} style={{ minWidth: 52 }}>
                        {h}h
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Travel Details */}
              <h3 style={{ ...subHead, marginTop: 24 }}><Icon name="calendar" size={15} />Travel details</h3>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14, marginBottom: 14 }}>
                <div>
                  <label style={lbl}>From Date *</label>
                  <input type="date" className="input-field" value={form.fromDate} min={localToday()} onChange={e => set('fromDate', e.target.value)} required style={{ width: '100%', padding: '10px 14px', outline: 'none' }} />
                </div>
                <div>
                  <label style={lbl}>To Date *</label>
                  <input type="date" className="input-field" value={form.toDate} min={form.fromDate || localToday()} onChange={e => set('toDate', e.target.value)} required style={{ width: '100%', padding: '10px 14px', outline: 'none' }} />
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14 }}>
                <div>
                  <label style={lbl}>Travel Members *</label>
                  <select className="input-field" value={form.travelMembers} onChange={e => set('travelMembers', e.target.value)} style={{ width: '100%', padding: '10px 14px', outline: 'none' }}>
                    {Array.from({ length: 20 }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n} Person{n > 1 ? 's' : ''}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Car *</label>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                    {['AC', 'NON_AC'].map(type => (
                      <button key={type} type="button" aria-pressed={form.acType === type} onClick={() => set('acType', type)}
                        style={{ ...choice(form.acType === type), justifyContent: 'center', padding: 11 }}>
                        <Icon name="car" size={16} />{type === 'AC' ? 'AC' : 'Non-AC'}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </>
          )}
        </Panel>

        <div className="pc-span-5" style={{ display: 'grid', gap: 16, alignContent: 'start', minWidth: 0 }}>
          <Panel title="Route preview">
            {form.fromLat && form.toLat ? (
              <RouteMap
                fromLat={form.fromLat} fromLon={form.fromLon}
                toLat={form.toLat} toLon={form.toLon}
                fromPlace={form.fromPlace} toPlace={form.toPlace}
              />
            ) : (
              <Empty icon="map" title="No route yet">Pick both places from the suggestions to see the route.</Empty>
            )}
          </Panel>

          <Panel title="Price summary">
            <div className="pc-rows">
              {city ? (
                <>
                  <div className="pc-row" style={row}>
                    <span style={{ color: 'var(--pc-ink-2)' }}>Ride</span>
                    <b>{vehicleLabel(form.vehicleType)} · {form.travelMembers} passenger{Number(form.travelMembers) === 1 ? '' : 's'}</b>
                  </div>
                  {estimate.data && (
                    <div className="pc-row" style={row}>
                      <span style={{ color: 'var(--pc-ink-2)' }}>Road distance · time</span>
                      <b>{estimate.data.distanceKm} km · ~{estimate.data.minutes} min</b>
                    </div>
                  )}
                  <div className="pc-row" style={row}>
                    <span style={{ color: 'var(--pc-ink-2)' }}>Fare</span>
                    <span className="pc-figure" style={{ fontSize: 26, color: 'var(--pc-brand-deep)' }}>{chosenFare ? inr(chosenFare.fare) : '—'}</span>
                  </div>
                </>
              ) : (
                <>
                  <div className="pc-row" style={row}>
                    <span style={{ color: 'var(--pc-ink-2)' }}>Booking type</span>
                    <b>{form.bookingType === 'HOUR_BASED' ? 'Hour Based' : 'Distance Based'}</b>
                  </div>
                  {form.bookingType === 'HOUR_BASED' ? (
                    <>
                      <div className="pc-row" style={row}>
                        <span style={{ color: 'var(--pc-ink-2)' }}>Rate: {inr(pricing.pricePerHour)}/hour</span>
                        <b>Duration: {form.bookingHours} hour{form.bookingHours > 1 ? 's' : ''}</b>
                      </div>
                      <div className="pc-row" style={row}>
                        <span style={{ color: 'var(--pc-ink-2)' }}>Estimated total</span>
                        <span className="pc-figure" style={{ fontSize: 26, color: 'var(--pc-brand-deep)' }}>{inr(hourTotal)}</span>
                      </div>
                    </>
                  ) : (
                    <div className="pc-row" style={row}>
                      <span style={{ color: 'var(--pc-ink-2)' }}>Rate</span>
                      <b>{inr(pricing.pricePerKm)}/km</b>
                    </div>
                  )}
                  <div className="pc-row" style={row}>
                    <span style={{ color: 'var(--pc-ink-2)' }}>Travellers · car</span>
                    <b>{form.travelMembers} · {form.acType === 'AC' ? 'AC' : 'Non-AC'}</b>
                  </div>
                </>
              )}
            </div>
            {(city || form.bookingType !== 'HOUR_BASED') && (
              <p style={{ margin: '10px 0 0', fontSize: 12.5, color: 'var(--pc-muted)' }}>The final fare is worked out from the road distance when you confirm.</p>
            )}
            <button type="submit" disabled={loading || (city && tooManyFor(form.vehicleType))} className="pc-btn" style={{ width: '100%', marginTop: 16, minHeight: 48, fontSize: 15 }}>
              {loading ? <><div className="spinner" style={{ width: 18, height: 18 }} /> Calculating route & price...</>
                : <><Icon name={city ? VEHICLES[form.vehicleType]?.icon : 'check'} />{city ? `Book ${vehicleLabel(form.vehicleType).toLowerCase()} now` : 'Confirm Booking'}</>}
            </button>
          </Panel>
        </div>
      </form>
    </DashboardLayout>
  )
}
