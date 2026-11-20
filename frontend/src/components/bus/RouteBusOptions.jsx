import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '../dash/Icon'
import { Panel } from '../dash/ui'
import { searchBusPlaces, searchBusTrips } from '../../utils/busApi'
import { findBusTown } from '../../utils/busTownMatch'
import { busSearchPath } from './BusSearchForm'
import { inr, isoDate } from '../../utils/busText'

const SHOW = 4
const clock = (iso) => (iso ? new Date(iso).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }) : '')
const hours = (m) => (m ? `${Math.floor(m / 60)}h${m % 60 ? ` ${m % 60}m` : ''}` : '')

/**
 * "Buses on this route" for the booking page: when the From / To the traveller typed are both bus
 * towns with a bus between them on the date, the cheapest few are shown next to the car price,
 * each opening its seat map. Renders nothing when there is no bus, so the page looks as before.
 */
export default function RouteBusOptions({ fromPlace, toPlace, date }) {
  const day = date && date >= isoDate() ? date : isoDate()
  const key = `${fromPlace}|${toPlace}|${day}`
  const [result, setFound] = useState(null) // { key, from, to, trips }
  // A result from an earlier From / To is never shown for the new one.
  const found = result && result.key === key ? result : null

  useEffect(() => {
    if (!fromPlace || !toPlace) return undefined
    let live = true
    const timer = setTimeout(async () => {
      const [from, to] = await Promise.all([
        findBusTown(fromPlace, searchBusPlaces),
        findBusTown(toPlace, searchBusPlaces),
      ])
      if (!live) return
      if (!from || !to || from.id === to.id) { setFound(null); return }
      let trips = []
      try { trips = (await searchBusTrips(from.id, to.id, day)).data || [] } catch { trips = [] }
      if (live) setFound(trips.length ? { key, from, to, trips } : null)
    }, 400)
    return () => { live = false; clearTimeout(timer) }
  }, [fromPlace, toPlace, day, key])

  if (!found) return null
  const { from, to, trips } = found
  const sorted = [...trips].sort((a, b) => Number(a.minFare) - Number(b.minFare))
  const query = { from, to, date: day }
  const seatsPath = (t) => {
    const p = new URLSearchParams({ from: String(from.id), to: String(to.id), date: day, fromName: from.name, toName: to.name })
    return `/bus/trip/${t.tripId}?${p}`
  }

  return (
    <Panel title="Buses on this route" meta={`${from.name} → ${to.name}`}>
      <div style={{ display: 'grid', gap: 8 }}>
        {sorted.slice(0, SHOW).map((t) => (
          <Link key={t.tripId} to={seatsPath(t)} style={{
            display: 'grid', gridTemplateColumns: 'minmax(0,1fr) auto', gap: 8, alignItems: 'center',
            padding: '10px 12px', borderRadius: 12, border: '1px solid var(--pc-line)',
            textDecoration: 'none', color: 'inherit', background: 'var(--pc-surface, #fff)',
          }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontWeight: 800, fontSize: 14, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                <Icon name="bus" size={14} /> {t.travelsName || t.busName}
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 2 }}>
                {clock(t.departureAt)} → {clock(t.arrivalAt)}{t.durationMinutes ? ` · ${hours(t.durationMinutes)}` : ''}
                {t.typeLabel ? ` · ${t.typeLabel}` : ''}{t.ac ? ' · AC' : ''}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontWeight: 800, color: 'var(--pc-brand-deep)' }}>from {inr(t.minFare)}</div>
              <div style={{ fontSize: 12, color: t.seatsLeft <= 5 ? '#c2410c' : 'var(--pc-muted)' }}>
                {t.seatsLeft} seat{t.seatsLeft === 1 ? '' : 's'} left
              </div>
            </div>
          </Link>
        ))}
      </div>
      <Link to={busSearchPath(query)} className="pc-link" style={{ display: 'inline-flex', marginTop: 10 }}>
        {trips.length > SHOW ? `See all ${trips.length} buses` : 'Open bus search'}<Icon name="arrow" size={14} />
      </Link>
    </Panel>
  )
}
