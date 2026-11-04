import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import BusShell from '../../components/bus/BusShell'
import BusSearchForm, { busSearchPath } from '../../components/bus/BusSearchForm'
import Icon from '../../components/dash/Icon'
import { Empty, Skeleton } from '../../components/dash/ui'
import { searchBusTrips } from '../../utils/busApi'
import { addDays, errorText, inr, isoDate } from '../../utils/busText'
import { whenText } from '../../utils/busBooking'
import {
  SORTS, DEPARTURE_BUCKETS, sortTrips, filterTrips, emptyFilters, filterOptions, activeFilterCount, toggleIn,
  clockText, dayShift, durationText, ratingText,
} from '../../utils/busSearch'

/** Reads the search out of the URL: ids, date and the town names shown in the heading. */
function useQuery() {
  const [params] = useSearchParams()
  const from = params.get('from')
  const to = params.get('to')
  return {
    from: from ? { id: Number(from), name: params.get('fromName') || 'From' } : null,
    to: to ? { id: Number(to), name: params.get('toName') || 'To' } : null,
    date: params.get('date') || isoDate(),
  }
}

function Check({ checked, onChange, children }) {
  return (
    <label className="bus-check">
      <input type="checkbox" checked={checked} onChange={onChange} />
      <span>{children}</span>
    </label>
  )
}

function Filters({ filters, setFilters, options, open, onClose, shown, total }) {
  const set = (k, v) => setFilters((f) => ({ ...f, [k]: v }))
  const flip = (k, v) => setFilters((f) => ({ ...f, [k]: toggleIn(f[k], v) }))
  const price = (k) => (e) => set(k, e.target.value === '' ? null : Number(e.target.value))
  const group = (title, key, values) => values.length > 1 && (
    <>
      <h3>{title}</h3>
      {values.map((v) => <Check key={v} checked={filters[key].includes(v)} onChange={() => flip(key, v)}>{v}</Check>)}
    </>
  )
  return (
    <aside className={`pc-panel bus-filters${open ? ' is-open' : ''}`} aria-label="Filters">
      <div className="bus-filter-head">
        <b style={{ fontFamily: 'var(--pc-fd)', fontSize: 15 }}>Filters</b>
        <span style={{ display: 'flex', gap: 6 }}>
          {activeFilterCount(filters) > 0 && <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setFilters(emptyFilters())}>Clear all</button>}
          <button type="button" className="pc-iconbtn bus-filter-close" onClick={onClose} aria-label="Close filters"><Icon name="x" size={16} /></button>
        </span>
      </div>

      <h3>Bus type</h3>
      <Check checked={filters.acTypes.includes('AC')} onChange={() => flip('acTypes', 'AC')}>A/C</Check>
      <Check checked={filters.acTypes.includes('NONAC')} onChange={() => flip('acTypes', 'NONAC')}>Non-A/C</Check>
      <Check checked={filters.kinds.includes('SLEEPER')} onChange={() => flip('kinds', 'SLEEPER')}>Sleeper</Check>
      <Check checked={filters.kinds.includes('SEATER')} onChange={() => flip('kinds', 'SEATER')}>Seater</Check>
      <Check checked={filters.singleOnly} onChange={() => set('singleOnly', !filters.singleOnly)}>Single seats available</Check>

      <h3>Departure</h3>
      {DEPARTURE_BUCKETS.map(([k, label]) => <Check key={k} checked={filters.buckets.includes(k)} onChange={() => flip('buckets', k)}>{label}</Check>)}

      <h3>Price</h3>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <input className="input-field" type="number" inputMode="numeric" min={0} aria-label="Lowest price" placeholder={`Min ${options.minFare || ''}`} value={filters.minPrice ?? ''} onChange={price('minPrice')} />
        <input className="input-field" type="number" inputMode="numeric" min={0} aria-label="Highest price" placeholder={`Max ${options.maxFare || ''}`} value={filters.maxPrice ?? ''} onChange={price('maxPrice')} />
      </div>

      {group('Boarding point', 'boarding', options.boarding)}
      {group('Dropping point', 'dropping', options.dropping)}
      {group('Travels', 'travels', options.travels)}

      <button type="button" className="pc-btn bus-filter-close" style={{ width: '100%', marginTop: 18 }} onClick={onClose}>
        Show {shown} of {total} bus{total === 1 ? '' : 'es'}
      </button>
    </aside>
  )
}

function TripCard({ t, seatsPath }) {
  const shift = dayShift(t.departureAt, t.arrivalAt)
  const low = Number(t.seatsLeft) > 0 && Number(t.seatsLeft) <= 5
  return (
    <article className="pc-panel bus-card">
      <div style={{ minWidth: 0 }}>
        <h2>{t.travelsName}</h2>
        <div className="bus-card-type">{t.busName} · {t.typeLabel}</div>
        <div style={{ marginTop: 8 }}>
          <span className={`bus-rate${Number(t.ratingCount) > 0 ? '' : ' is-new'}`}>{ratingText(t)}</span>
          {Number(t.ratingCount) > 0 && <span style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 700, marginLeft: 6 }}>{t.ratingCount} rating{t.ratingCount === 1 ? '' : 's'}</span>}
        </div>
      </div>
      <div className="bus-times">
        <div>
          <b>{clockText(t.departureAt)}</b>
          <small title={t.boardingPoints?.[0]?.name}>{t.boardingPoints?.[0]?.name}</small>
        </div>
        <span className="bus-dur">{durationText(t.durationMinutes)}</span>
        <div>
          <b>{clockText(t.arrivalAt)}{shift > 0 && <sup title={whenText(t.arrivalAt)}>+{shift} day{shift === 1 ? '' : 's'}</sup>}</b>
          <small title={t.droppingPoints?.[t.droppingPoints.length - 1]?.name}>{t.droppingPoints?.[t.droppingPoints.length - 1]?.name}</small>
        </div>
      </div>
      <div className="bus-buy">
        <span className="bus-from">From</span>
        <span className="bus-fare">{inr(t.minFare)}</span>
        <span className={`bus-left${low ? ' is-low' : ''}`}>
          {t.seatsLeft} seat{t.seatsLeft === 1 ? '' : 's'} left{Number(t.singleSeatsLeft) > 0 ? ` · ${t.singleSeatsLeft} single` : ''}
        </span>
        <Link to={seatsPath} className="pc-btn">View seats</Link>
      </div>
      {(t.amenities || []).length > 0 && (
        <div className="bus-amen" aria-label="Amenities">
          {t.amenities.slice(0, 6).map((a) => <span key={a}>{a}</span>)}
          {t.amenities.length > 6 && <span>+{t.amenities.length - 6} more</span>}
        </div>
      )}
    </article>
  )
}

/** Public bus search results: every operator's buses for a pair and date, sorted and filtered here. */
export default function BusSearchPage() {
  const q = useQuery()
  const navigate = useNavigate()
  const [trips, setTrips] = useState(null)
  const [error, setError] = useState('')
  const [sort, setSort] = useState('CHEAPEST')
  const [filters, setFilters] = useState(emptyFilters)
  const [drawer, setDrawer] = useState(false)
  const [editing, setEditing] = useState(false)
  const today = isoDate()
  const fromId = q.from?.id
  const toId = q.to?.id

  const load = useCallback(async () => {
    if (!fromId || !toId) { setTrips([]); return }
    setTrips(null); setError('')
    try { setTrips((await searchBusTrips(fromId, toId, q.date)).data || []) } catch (e) { setError(errorText(e, 'Could not load buses. Try again.')); setTrips([]) }
  }, [fromId, toId, q.date])
  useEffect(() => { load() }, [load])
  // A new search starts with no filters.
  useEffect(() => { setFilters(emptyFilters()) }, [fromId, toId])

  const options = useMemo(() => filterOptions(trips), [trips])
  const shown = useMemo(() => sortTrips(filterTrips(trips, filters), sort), [trips, filters, sort])
  const active = activeFilterCount(filters)

  const go = (query) => { setEditing(false); navigate(busSearchPath(query)) }
  const moveDay = (n) => go({ from: q.from, to: q.to, date: addDays(q.date, n) })
  const seatsPath = (t) => {
    const p = new URLSearchParams({ from: String(fromId), to: String(toId), date: q.date, fromName: q.from.name, toName: q.to.name })
    return `/bus/trip/${t.tripId}?${p}`
  }

  if (!q.from || !q.to) {
    return (
      <BusShell>
        <h1 style={{ fontFamily: "var(--pc-fd)", fontSize: 24, margin: "0 0 16px" }}>Book a bus</h1>
        <BusSearchForm />
      </BusShell>
    )
  }

  return (
    <BusShell>
      <div className="bus-summary">
        <div>
          <h1>{q.from.name} <Icon name="arrow" size={18} /> {q.to.name}</h1>
          <p>{whenText(q.date)} · {trips == null ? 'Searching…' : `${trips.length} bus${trips.length === 1 ? '' : 'es'}`}</p>
        </div>
        <div className="bus-datestrip" role="group" aria-label="Change date">
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => moveDay(-1)} disabled={q.date <= today} aria-label="Previous day"><Icon name="back" size={15} />{whenText(addDays(q.date, -1))}</button>
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => moveDay(1)} aria-label="Next day">{whenText(addDays(q.date, 1))}<Icon name="arrow" size={15} /></button>
          <button type="button" className="pc-btn pc-btn-teal pc-btn-sm" onClick={() => setEditing((v) => !v)} aria-expanded={editing}><Icon name="edit" size={15} />Modify</button>
        </div>
      </div>
      {editing && <div style={{ marginBottom: 16 }}><BusSearchForm initial={q} onSearch={go} idPrefix="bus-edit" /></div>}

      <div className="bus-results">
        {drawer && <div className="pc-scrim" onClick={() => setDrawer(false)} aria-hidden="true" />}
        <Filters filters={filters} setFilters={setFilters} options={options} open={drawer} onClose={() => setDrawer(false)} shown={shown.length} total={trips?.length || 0} />

        <section aria-label="Buses">
          <div className="bus-sorts">
            <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm bus-filter-open" onClick={() => setDrawer(true)}>
              <Icon name="filter" size={15} />Filters{active ? ` (${active})` : ''}
            </button>
            <span style={{ fontSize: 12.5, fontWeight: 800, color: 'var(--pc-muted)' }}>Sort by</span>
            <div className="pc-chips" role="group" aria-label="Sort by">
              {SORTS.map(([k, label]) => <button key={k} type="button" className="pc-chip" aria-pressed={sort === k} onClick={() => setSort(k)}>{label}</button>)}
            </div>
          </div>

          {trips == null ? (
            <div className="pc-panel"><Skeleton rows={6} height={18} /></div>
          ) : error ? (
            <div className="pc-panel">
              <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span style={{ flex: 1 }}>{error}</span><button type="button" className="pc-btn pc-btn-sm" onClick={load}>Retry</button></div>
            </div>
          ) : trips.length === 0 ? (
            <div className="pc-panel">
              <Empty icon="bus" title="No buses on this date"
                action={<button type="button" className="pc-btn pc-btn-sm" onClick={() => moveDay(1)}>Try {whenText(addDays(q.date, 1))}</button>}>
                No operator runs {q.from.name} to {q.to.name} on {whenText(q.date)}, or every bus has already left.
              </Empty>
            </div>
          ) : shown.length === 0 ? (
            <div className="pc-panel">
              <Empty icon="filter" title="No bus matches these filters" action={<button type="button" className="pc-btn pc-btn-sm" onClick={() => setFilters(emptyFilters())}>Clear filters</button>}>
                {trips.length} bus{trips.length === 1 ? '' : 'es'} run on this date.
              </Empty>
            </div>
          ) : (
            shown.map((t) => <TripCard key={t.tripId} t={t} seatsPath={seatsPath(t)} />)
          )}
        </section>
      </div>
    </BusShell>
  )
}
