import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import useDriverLocationSender from '../hooks/useDriverLocationSender'
import useLiveDriverLocation from '../hooks/useLiveDriverLocation'
import { OSRM_BASE_URL, TILE_URL, TILE_ATTRIBUTION } from '../config/mapServices'
import { SmoothVehicleMarker, RouteCruiseMarker } from './map/VehicleMarkers'
import { pickVehicle, vehicleSvg, VEHICLE_MARKER_CSS } from './map/vehicleIcons'
import {
  bearing, buildStraightRoute, cumulativeDistances, haversineKm, nearestIndex,
} from './map/geo'

const pickupIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-green.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34],
})

const dropIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34],
})

function FitBounds({ bounds }) {
  const map = useMap()
  useEffect(() => {
    if (bounds) map.fitBounds(bounds, { padding: [50, 50] })
  }, [bounds, map])
  return null
}

function MapInstance({ setMap, onUserDrag }) {
  const map = useMap()
  useEffect(() => {
    setMap(map)
    if (onUserDrag) {
      map.on('dragstart', onUserDrag)
      return () => map.off('dragstart', onUserDrag)
    }
    return undefined
  }, [map, setMap, onUserDrag])
  return null
}

export default function LiveTrackingMap({
  bookingId, fromLat, fromLon, toLat, toLon, fromPlace, toPlace,
  isDriver, driverId, travelMembers,
}) {
  const [routeCoords, setRouteCoords] = useState(null)
  const [driverPos, setDriverPos] = useState(null)
  const [driverHeading, setDriverHeading] = useState(0)
  const [eta, setEta] = useState(null)
  const [distRemaining, setDistRemaining] = useState(null)
  // True when the figures came from the straight-line fallback rather than a real route,
  // so the UI can label them instead of passing a guess off as a routed ETA.
  const [etaIsEstimate, setEtaIsEstimate] = useState(false)
  const [map, setMap] = useState(null)
  const [waitingForLocation, setWaitingForLocation] = useState(true)
  const [followDriver, setFollowDriver] = useState(true)
  const [secondsAgo, setSecondsAgo] = useState(null)
  const lastEtaCalcRef = useRef(0)
  const prevPosRef = useRef(null)

  // Each trip gets its own vehicle, so two bookings open side by side are
  // told apart at a glance and the marker hints at the car that was sent.
  const vehicle = useMemo(() => pickVehicle(bookingId, travelMembers), [bookingId, travelMembers])

  // Fetch route on mount, falling back to a straight line if OSRM is unavailable
  useEffect(() => {
    if (!fromLat || !toLat) return undefined
    let cancelled = false
    const fallback = () => { if (!cancelled) setRouteCoords(buildStraightRoute(fromLat, fromLon, toLat, toLon)) }
    const coords = `${fromLon},${fromLat};${toLon},${toLat}`
    fetch(`${OSRM_BASE_URL}/route/v1/driving/${coords}?overview=full&geometries=geojson`)
      .then(r => r.json())
      .then(data => {
        if (cancelled) return
        if (data.code === 'Ok' && data.routes?.length > 0) {
          setRouteCoords(data.routes[0].geometry.coordinates.map(([lng, lat]) => [lat, lng]))
        } else {
          fallback()
        }
      })
      .catch(fallback)
    return () => { cancelled = true }
  }, [fromLat, fromLon, toLat, toLon])

  const updateDriverPos = useCallback((lat, lon, serverHeading) => {
    const newPos = [lat, lon]
    let heading = serverHeading || 0
    if (!heading && prevPosRef.current) {
      heading = bearing(prevPosRef.current, newPos)
    }
    prevPosRef.current = newPos
    setDriverPos(newPos)
    setDriverHeading(heading)
    setWaitingForLocation(false)
  }, [])

  // Driver side: drive the car along the route (or real GPS) and publish it.
  // onPosition keeps the driver's own marker in sync with what we send.
  useDriverLocationSender({ bookingId, driverId, active: isDriver, routeCoords, onPosition: updateDriverPos })

  // User side: live driver location via WebSocket push + 5s REST poll fallback.
  // The hook only updates React state, so the marker auto-refreshes and moves
  // without any page reload (Rapido/Zomato style).
  const { location: liveLoc, lastUpdate, socketLive } = useLiveDriverLocation({
    bookingId,
    active: !isDriver,
  })

  // Feed each live update into the shared marker sink.
  useEffect(() => {
    if (isDriver || !liveLoc) return
    updateDriverPos(liveLoc.latitude, liveLoc.longitude, liveLoc.heading)
  }, [liveLoc, isDriver, updateDriverPos])

  // Tick a "last updated Ns ago" freshness counter once per second.
  useEffect(() => {
    if (isDriver || !lastUpdate) return undefined
    const tick = () => setSecondsAgo(Math.floor((Date.now() - lastUpdate) / 1000))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [isDriver, lastUpdate])

  // ETA calculation - throttled to every 10s, triggered by position changes
  useEffect(() => {
    if (!driverPos || !toLat || !toLon) return

    const now = Date.now()
    if (now - lastEtaCalcRef.current < 10000) return
    lastEtaCalcRef.current = now

    // OSRM defaults to a shared community demo server that is rate-limited and regularly
    // unreachable. Swallowing that failure leaves the passenger staring at a moving car with
    // no distance and no ETA at all, which reads as the tracking being broken. Fall back to a
    // straight-line estimate at 60 km/h — the same assumption RoutingService makes server-side.
    const straightLineEstimate = () => {
      const km = haversineKm(driverPos, [toLat, toLon])
      setDistRemaining(km.toFixed(1))
      setEta(Math.max(1, Math.ceil(km)))
      setEtaIsEstimate(true)
    }

    const url = `${OSRM_BASE_URL}/route/v1/driving/${driverPos[1]},${driverPos[0]};${toLon},${toLat}?overview=false`
    fetch(url).then(r => r.json()).then(data => {
      if (data.code === 'Ok' && data.routes?.length > 0) {
        setEta(Math.ceil(data.routes[0].duration / 60))
        setDistRemaining((data.routes[0].distance / 1000).toFixed(1))
        setEtaIsEstimate(false)
      } else {
        straightLineEstimate()
      }
    }).catch(straightLineEstimate)
  }, [driverPos, toLat, toLon])

  // Running length of the route, so progress is measured in metres covered
  // rather than waypoints passed — OSRM packs waypoints tightly around
  // junctions, which would make the bar lurch at every roundabout.
  const routeMetrics = useMemo(
    () => (routeCoords ? cumulativeDistances(routeCoords) : null),
    [routeCoords],
  )

  // One nearest-point lookup per position feeds both the travelled line and the
  // progress bar; it used to run three times per render.
  const travelled = useMemo(() => {
    if (!routeCoords || !routeMetrics || !driverPos) return null
    const idx = nearestIndex(routeCoords, driverPos)
    const percent = routeMetrics.total > 0
      ? Math.round((routeMetrics.cum[idx] / routeMetrics.total) * 100)
      : 0
    return { coords: routeCoords.slice(0, idx + 1), percent }
  }, [routeCoords, routeMetrics, driverPos])

  const progress = travelled?.percent ?? 0

  if (!fromLat || !toLat) return null

  const center = [(fromLat + toLat) / 2, (fromLon + toLon) / 2]
  const bounds = [[fromLat, fromLon], [toLat, toLon]]

  const formatEta = (mins) => {
    if (!mins) return '--'
    if (mins < 60) return `${mins} min`
    const h = Math.floor(mins / 60)
    const m = mins % 60
    return m > 0 ? `${h}h ${m}m` : `${h}h`
  }

  return (
    <div style={{ position: 'relative' }}>
      {/* Live info bar */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '10px 16px', marginBottom: 8, borderRadius: 12,
        background: 'linear-gradient(135deg, #0f172a, #1e293b)', color: '#fff',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className="live-dot" />
          <span style={{ fontSize: 13, fontWeight: 700, letterSpacing: 1 }}>LIVE TRACKING</span>
          {!isDriver && (
            <span style={{ fontSize: 11, color: '#94a3b8', fontWeight: 500 }}>
              {secondsAgo == null
                ? 'connecting…'
                : `${socketLive ? '● ' : ''}updated ${secondsAgo}s ago`}
            </span>
          )}
        </div>
        <div style={{ display: 'flex', gap: 16, fontSize: 13, alignItems: 'center' }}>
          {distRemaining && <span style={{ color: '#94a3b8' }}>{distRemaining} km left</span>}
          <span>
            ETA: <strong style={{ color: '#60a5fa' }}>{formatEta(eta)}</strong>
            {etaIsEstimate && (
              <span
                title="Routing service unavailable — showing a straight-line estimate"
                style={{ marginLeft: 4, color: '#94a3b8', fontSize: 11 }}
              >
                ~est
              </span>
            )}
          </span>
        </div>
      </div>

      {/* Route info with progress */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, fontSize: 12, color: '#334155',
        padding: '10px 14px', background: '#f8fafc', borderRadius: 10, border: '1px solid #e2e8f0',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0 }}>
          <span style={{
            width: 10, height: 10, borderRadius: '50%', background: '#22c55e', flexShrink: 0,
            border: '2px solid #bbf7d0',
          }} />
          <span style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {fromPlace || 'Pickup'}
          </span>
        </div>

        {/* Mini progress bar */}
        <div style={{
          flex: '0 0 80px', height: 4, background: '#e2e8f0', borderRadius: 2,
          position: 'relative', overflow: 'visible',
        }}>
          <div style={{
            position: 'absolute', left: 0, top: 0, height: '100%', borderRadius: 2,
            width: `${progress}%`,
            background: `linear-gradient(90deg, #22c55e, ${vehicle.body})`,
            transition: 'width 2s linear',
          }} />
          {driverPos && (
            <div
              // Same vehicle as the map marker, turned to face along the bar.
              dangerouslySetInnerHTML={{ __html: vehicleSvg(vehicle, 20) }}
              style={{
                position: 'absolute', top: -10, transition: 'left 2s linear',
                left: `calc(${progress}% - 10px)`,
                lineHeight: 0, transform: 'rotate(90deg)',
              }}
            />
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: 0, justifyContent: 'flex-end' }}>
          <span style={{ fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {toPlace || 'Drop'}
          </span>
          <span style={{
            width: 10, height: 10, borderRadius: '50%', background: '#ef4444', flexShrink: 0,
            border: '2px solid #fecaca',
          }} />
        </div>
      </div>

      {/* Waiting for driver location message */}
      {waitingForLocation && (
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          padding: '8px 12px', marginBottom: 8, borderRadius: 8,
          background: 'linear-gradient(135deg, #eff6ff, #f0fdf4)', border: '1px solid #bfdbfe', fontSize: 12, color: '#1e40af',
        }}>
          <span className="waiting-spinner" />
          Connecting to driver&apos;s live location...
        </div>
      )}

      {/* Map */}
      <div style={{ borderRadius: 14, overflow: 'hidden', border: '2px solid #e2e8f0', height: 400, position: 'relative' }}>
        <MapContainer center={center} zoom={7} style={{ height: '100%', width: '100%' }} scrollWheelZoom={true} zoomControl={false}>
          <TileLayer attribution={TILE_ATTRIBUTION} url={TILE_URL} />
          <FitBounds bounds={bounds} />
          <MapInstance setMap={setMap} onUserDrag={() => setFollowDriver(false)} />

          <Marker position={[fromLat, fromLon]} icon={pickupIcon}>
            <Popup><strong>Pickup</strong><br />{fromPlace || 'Start'}</Popup>
          </Marker>

          <Marker position={[toLat, toLon]} icon={dropIcon}>
            <Popup><strong>Drop</strong><br />{toPlace || 'Destination'}</Popup>
          </Marker>

          {/* Route line - blue with glow effect */}
          {routeCoords && (
            <>
              {/* Route glow (wider, semi-transparent) */}
              <Polyline positions={routeCoords} pathOptions={{ color: '#3b82f6', weight: 10, opacity: 0.15 }} />
              {/* Main route line */}
              <Polyline positions={routeCoords} pathOptions={{ color: '#3b82f6', weight: 5, opacity: 0.75 }} />
            </>
          )}

          {/* Travelled route - green solid */}
          {travelled && travelled.coords.length > 1 && (
            <>
              <Polyline positions={travelled.coords} pathOptions={{ color: '#22c55e', weight: 8, opacity: 0.15 }} />
              <Polyline positions={travelled.coords} pathOptions={{ color: '#22c55e', weight: 5, opacity: 0.9 }} />
            </>
          )}

          {/* Route preview while the driver's first fix is still on its way */}
          {routeCoords && !driverPos && (
            <RouteCruiseMarker
              routeCoords={routeCoords}
              vehicle={vehicle}
              tooltip={`${vehicle.label} · route preview`}
            />
          )}

          {/* Real driver car - shows when we have actual driver GPS */}
          {driverPos && (
            <SmoothVehicleMarker
              position={driverPos}
              heading={driverHeading}
              routeCoords={routeCoords}
              vehicle={vehicle}
              follow={followDriver}
              tooltip={isDriver ? `Your ${vehicle.label}` : `Your ride · ${vehicle.label}`}
            />
          )}
        </MapContainer>
        {/* Recenter button */}
        {driverPos && (
          <button onClick={() => {
            setFollowDriver(true)
            if (map) map.panTo(driverPos, { animate: true, duration: 0.5 })
          }} style={{
            position: 'absolute', bottom: 12, right: 12, zIndex: 1000,
            width: 38, height: 38, borderRadius: '50%', border: '2px solid #e2e8f0',
            background: followDriver ? '#3b82f6' : '#fff', color: followDriver ? '#fff' : '#3b82f6',
            cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)', transition: 'all 0.2s',
          }} title="Re-center on driver">
            📍
          </button>
        )}
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(1.2); }
        }
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
        .live-dot {
          width: 10px; height: 10px; border-radius: 50%;
          background: #22c55e; box-shadow: 0 0 8px #22c55e;
          animation: pulse 1.5s ease-in-out infinite;
        }
        .waiting-spinner {
          width: 14px; height: 14px; border-radius: 50%;
          border: 2px solid #bfdbfe; border-top-color: #3b82f6;
          animation: spin 1s linear infinite;
        }
        ${VEHICLE_MARKER_CSS}
      `}</style>
    </div>
  )
}
