import React, { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { OSRM_BASE_URL, TILE_URL, TILE_ATTRIBUTION } from '../config/mapServices'

// Fix default marker icons (leaflet + bundler issue)
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

const pickupIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
})

const dropIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
})

function FitBounds({ bounds }) {
  const map = useMap()
  useEffect(() => {
    if (bounds) {
      map.fitBounds(bounds, { padding: [50, 50] })
    }
  }, [bounds, map])
  return null
}

export default function RouteMap({ fromLat, fromLon, toLat, toLon, fromPlace, toPlace }) {
  const [routeCoords, setRouteCoords] = useState(null)
  const [routeInfo, setRouteInfo] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!fromLat || !fromLon || !toLat || !toLon) {
      setRouteCoords(null)
      setRouteInfo(null)
      return
    }

    const fetchRoute = async () => {
      setLoading(true)
      setError('')
      try {
        const coords = `${fromLon},${fromLat};${toLon},${toLat}`
        const res = await fetch(
          `${OSRM_BASE_URL}/route/v1/driving/${coords}?overview=full&geometries=geojson`
        )
        const data = await res.json()

        if (data.code !== 'Ok' || !data.routes || data.routes.length === 0) {
          setError('Could not find a driving route between these locations.')
          setRouteCoords(null)
          setRouteInfo(null)
          return
        }

        const route = data.routes[0]
        const geoCoords = route.geometry.coordinates.map(([lng, lat]) => [lat, lng])
        setRouteCoords(geoCoords)
        setRouteInfo({
          distanceKm: (route.distance / 1000).toFixed(1),
          durationMinutes: Math.ceil(route.duration / 60),
        })
      } catch {
        setError('Failed to fetch route. Please try again.')
        setRouteCoords(null)
        setRouteInfo(null)
      } finally {
        setLoading(false)
      }
    }

    fetchRoute()
  }, [fromLat, fromLon, toLat, toLon])

  if (!fromLat || !toLat) return null

  const center = [(fromLat + toLat) / 2, (fromLon + toLon) / 2]
  const bounds = [
    [fromLat, fromLon],
    [toLat, toLon],
  ]

  const formatDuration = (mins) => {
    if (mins < 60) return `${mins} min`
    const h = Math.floor(mins / 60)
    const m = mins % 60
    return m > 0 ? `${h}h ${m}m` : `${h}h`
  }

  return (
    <div style={{ marginTop: 16 }}>
      {loading && (
        <div style={{ textAlign: 'center', padding: 20, color: '#64748b', fontSize: 13 }}>
          <div className="spinner" style={{ width: 20, height: 20, margin: '0 auto 8px' }} />
          Loading route map...
        </div>
      )}

      {error && (
        <div style={{ padding: 12, background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 10, color: '#dc2626', fontSize: 13 }}>
          {error}
        </div>
      )}

      {routeInfo && (
        <div style={{
          display: 'flex', gap: 16, marginBottom: 12, padding: '12px 16px',
          background: 'linear-gradient(135deg, #f0fdf4, #ecfdf5)', borderRadius: 12,
          border: '1px solid #bbf7d0',
        }}>
          <div style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>Distance</div>
            <div style={{ fontSize: 20, fontWeight: 900, color: '#16a34a' }}>{routeInfo.distanceKm} km</div>
          </div>
          <div style={{ width: 1, background: '#d1fae5' }} />
          <div style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>Est. Travel Time</div>
            <div style={{ fontSize: 20, fontWeight: 900, color: '#16a34a' }}>{formatDuration(routeInfo.durationMinutes)}</div>
          </div>
        </div>
      )}

      <div style={{ borderRadius: 14, overflow: 'hidden', border: '2px solid #e2e8f0', height: 350 }}>
        <MapContainer center={center} zoom={7} style={{ height: '100%', width: '100%' }} scrollWheelZoom={true}>
          <TileLayer attribution={TILE_ATTRIBUTION} url={TILE_URL} />
          <FitBounds bounds={bounds} />

          <Marker position={[fromLat, fromLon]} icon={pickupIcon}>
            <Popup>
              <strong>Pickup</strong>
              <br />
              {fromPlace || 'Start Location'}
            </Popup>
          </Marker>

          <Marker position={[toLat, toLon]} icon={dropIcon}>
            <Popup>
              <strong>Drop</strong>
              <br />
              {toPlace || 'Destination'}
            </Popup>
          </Marker>

          {routeCoords && (
            <Polyline
              positions={routeCoords}
              pathOptions={{ color: '#f97316', weight: 5, opacity: 0.8 }}
            />
          )}
        </MapContainer>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8, fontSize: 11, color: '#94a3b8' }}>
        <span>Pickup: {fromPlace}</span>
        <span>Drop: {toPlace}</span>
      </div>
    </div>
  )
}
