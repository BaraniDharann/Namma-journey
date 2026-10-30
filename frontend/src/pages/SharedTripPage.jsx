import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { MapContainer, TileLayer, Marker, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import Icon from '../components/dash/Icon'
import { TILE_URL, TILE_ATTRIBUTION } from '../config/mapServices'
import { getSharedTrip } from '../utils/api'

const POLL_MS = 10000

const carIcon = L.divIcon({
  className: '',
  html: '<div style="width:22px;height:22px;border-radius:50%;background:#0f766e;border:3px solid #fff;box-shadow:0 1px 6px rgba(0,0,0,.35)"></div>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
})

const dropIcon = L.divIcon({
  className: '',
  html: '<div style="width:16px;height:16px;border-radius:4px;background:#dc2626;border:3px solid #fff;box-shadow:0 1px 6px rgba(0,0,0,.35)"></div>',
  iconSize: [16, 16],
  iconAnchor: [8, 8],
})

function Follow({ position }) {
  const map = useMap()
  useEffect(() => {
    if (position) map.setView(position, Math.max(map.getZoom(), 14), { animate: true })
  }, [position, map])
  return null
}

const ago = (ms) => {
  if (!ms) return ''
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000))
  if (s < 60) return `${s}s ago`
  return `${Math.round(s / 60)} min ago`
}

/**
 * The page family opens from a shared trip link or an SOS alert. No login: the token in the
 * URL is the permission. Shows where the car is, refreshed every few seconds - never the
 * passenger's phone number.
 */
export default function SharedTripPage() {
  const { token } = useParams()
  const [trip, setTrip] = useState(null)
  const [state, setState] = useState('loading') // loading | ok | gone

  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const { data } = await getSharedTrip(token)
        if (!alive) return
        setTrip(data)
        setState('ok')
      } catch {
        if (alive) setState((s) => (s === 'ok' ? s : 'gone'))
      }
    }
    load()
    const t = setInterval(load, POLL_MS)
    return () => { alive = false; clearInterval(t) }
  }, [token])

  const page = { minHeight: '100vh', background: 'var(--pc-page)', padding: '16px', display: 'flex', justifyContent: 'center' }
  const card = { width: '100%', maxWidth: 720, background: 'var(--pc-panel)', borderRadius: 18, overflow: 'hidden', boxShadow: 'var(--pc-shadow)' }

  if (state === 'loading') {
    return <div style={page}><div style={{ ...card, padding: 24 }} aria-busy="true">Loading trip…</div></div>
  }
  if (state === 'gone') {
    return (
      <div style={page}>
        <div style={{ ...card, padding: 24 }}>
          <h1 style={{ fontSize: 20, margin: 0, color: 'var(--pc-ink)' }}>This trip link is no longer active</h1>
          <p style={{ color: 'var(--pc-muted)' }}>The trip has ended, or the link is not valid.</p>
        </div>
      </div>
    )
  }

  const position = trip.latitude != null ? [trip.latitude, trip.longitude] : null
  const drop = trip.toLat != null ? [trip.toLat, trip.toLon] : null
  const center = position || drop || [12.9716, 77.5946]

  return (
    <div style={page}>
      <div style={card}>
        {trip.sos && !trip.ended && (
          <div role="alert" style={{ padding: '12px 16px', background: 'var(--pc-bad-soft)', color: 'var(--pc-ink)', fontSize: 14 }}>
            <strong style={{ color: 'var(--pc-bad)' }}>{trip.passengerFirstName} pressed SOS.</strong> Please call
            them now. If you cannot reach them and believe they are in danger, call <a href="tel:112"><strong>112</strong></a>.
          </div>
        )}
        <div style={{ padding: '16px 18px' }}>
          <h1 style={{ fontSize: 19, margin: 0, color: 'var(--pc-ink)' }}>
            {trip.ended ? `${trip.passengerFirstName}'s trip has ended` : `${trip.passengerFirstName}'s trip, live`}
          </h1>
          <div style={{ fontSize: 13.5, color: 'var(--pc-muted)', marginTop: 6, overflowWrap: 'anywhere' }}>
            {trip.fromPlace} → {trip.toPlace}
          </div>
          {trip.driverName && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 10, fontSize: 14, color: 'var(--pc-ink)' }}>
              <Icon name="car" size={18} />
              <span><strong>{trip.driverName}</strong>{trip.vehicle ? ` · ${trip.vehicle}` : ''}</span>
            </div>
          )}
        </div>
        <div style={{ height: 'min(60vh, 460px)' }}>
          <MapContainer center={center} zoom={14} style={{ height: '100%', width: '100%' }} scrollWheelZoom={false}>
            <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
            {position && <Marker position={position} icon={carIcon} />}
            {drop && <Marker position={drop} icon={dropIcon} />}
            {position && <Follow position={position} />}
          </MapContainer>
        </div>
        <div style={{ padding: '12px 18px', fontSize: 12.5, color: 'var(--pc-muted)', display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
          <span>{position ? `Car position updated ${ago(trip.positionAt)}` : 'Waiting for the car\'s position…'}</span>
          {position && (
            <a href={`https://maps.google.com/?q=${trip.latitude},${trip.longitude}`} target="_blank" rel="noopener noreferrer">
              Open in Google Maps
            </a>
          )}
        </div>
      </div>
    </div>
  )
}
