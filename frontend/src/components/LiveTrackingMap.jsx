import React, { useEffect, useState, useRef, useCallback, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.marker.slideto'
import { createStompClient } from '../utils/websocket'
import { getDriverLocation } from '../utils/api'
import useDriverLocationSender from '../hooks/useDriverLocationSender'

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

function createCarIcon(heading = 0) {
  return L.divIcon({
    className: 'driver-car-icon',
    html: `
      <div class="car-container" style="transform:rotate(${heading}deg)">
        <div class="car-pulse-ring"></div>
        <div class="car-glow"></div>
        <div class="car-emoji">🚖</div>
      </div>
    `,
    iconSize: [48, 48],
    iconAnchor: [24, 24],
  })
}

function getBearing(from, to) {
  const toRad = (d) => (d * Math.PI) / 180
  const toDeg = (r) => (r * 180) / Math.PI
  const dLon = toRad(to[1] - from[1])
  const y = Math.sin(dLon) * Math.cos(toRad(to[0]))
  const x = Math.cos(toRad(from[0])) * Math.sin(toRad(to[0])) -
    Math.sin(toRad(from[0])) * Math.cos(toRad(to[0])) * Math.cos(dLon)
  return (toDeg(Math.atan2(y, x)) + 360) % 360
}

function findNearestRouteIndex(routeCoords, pos) {
  if (!routeCoords || !pos) return 0
  let minDist = Infinity
  let minIdx = 0
  for (let i = 0; i < routeCoords.length; i++) {
    const d = (routeCoords[i][0] - pos[0]) ** 2 + (routeCoords[i][1] - pos[1]) ** 2
    if (d < minDist) { minDist = d; minIdx = i }
  }
  return minIdx
}

function FitBounds({ bounds }) {
  const map = useMap()
  useEffect(() => {
    if (bounds) map.fitBounds(bounds, { padding: [50, 50] })
  }, [bounds, map])
  return null
}

function FollowDriver({ driverPos, active }) {
  const map = useMap()
  useEffect(() => {
    if (!active || !driverPos || !map) return
    map.panTo(driverPos, { animate: true, duration: 1 })
  }, [driverPos, map, active])
  return null
}

function DriverMarker({ position, heading, map }) {
  const markerRef = useRef(null)

  useEffect(() => {
    if (!map || !position) return
    if (!markerRef.current) {
      markerRef.current = L.marker(position, { icon: createCarIcon(heading), zIndexOffset: 1000 }).addTo(map)
    } else {
      markerRef.current.setIcon(createCarIcon(heading))
      if (markerRef.current.slideTo) {
        markerRef.current.slideTo(position, { duration: 2000 })
      } else {
        markerRef.current.setLatLng(position)
      }
    }
    return () => {}
  }, [position, heading, map])

  useEffect(() => {
    return () => {
      if (markerRef.current && map) {
        map.removeLayer(markerRef.current)
      }
    }
  }, [map])

  return null
}

// Rapido-style animated car that moves along the route from pickup to drop
function AnimatedRouteCarMarker({ routeCoords, map, speedFactor = 1 }) {
  const markerRef = useRef(null)
  const animFrameRef = useRef(null)
  const indexRef = useRef(0)
  const progressRef = useRef(0)

  useEffect(() => {
    if (!map || !routeCoords || routeCoords.length < 2) return

    const icon = createCarIcon(0)
    markerRef.current = L.marker(routeCoords[0], { icon, zIndexOffset: 1000 }).addTo(map)

    const stepsPerSegment = 60 // frames per segment for smoothness
    let currentIndex = 0
    let currentStep = 0

    const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]

    const animate = () => {
      if (!markerRef.current || !routeCoords) return

      const from = routeCoords[currentIndex]
      const to = routeCoords[currentIndex + 1]
      if (!from || !to) {
        // Loop back to start
        currentIndex = 0
        currentStep = 0
        animFrameRef.current = requestAnimationFrame(animate)
        return
      }

      const t = currentStep / stepsPerSegment
      const pos = lerp(from, to, t)
      const heading = getBearing(from, to)

      markerRef.current.setLatLng(pos)
      markerRef.current.setIcon(createCarIcon(heading))

      // Update progress for trail effect
      indexRef.current = currentIndex
      progressRef.current = t

      currentStep += speedFactor
      if (currentStep >= stepsPerSegment) {
        currentStep = 0
        currentIndex++
        if (currentIndex >= routeCoords.length - 1) {
          currentIndex = 0 // loop
        }
      }

      animFrameRef.current = requestAnimationFrame(animate)
    }

    animFrameRef.current = requestAnimationFrame(animate)

    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
      if (markerRef.current && map) map.removeLayer(markerRef.current)
    }
  }, [map, routeCoords, speedFactor])

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
  }, [map, setMap, onUserDrag])
  return null
}

export default function LiveTrackingMap({ bookingId, fromLat, fromLon, toLat, toLon, fromPlace, toPlace, isDriver, driverId }) {
  const [routeCoords, setRouteCoords] = useState(null)
  const [driverPos, setDriverPos] = useState(null)
  const [driverHeading, setDriverHeading] = useState(0)
  const [eta, setEta] = useState(null)
  const [distRemaining, setDistRemaining] = useState(null)
  const [map, setMap] = useState(null)
  const [waitingForLocation, setWaitingForLocation] = useState(true)
  const [followDriver, setFollowDriver] = useState(true)
  const etaTimerRef = useRef(null)
  const lastEtaCalcRef = useRef(0)
  const clientRef = useRef(null)
  const prevPosRef = useRef(null)

  // Driver side: send location every 3s via WebSocket/REST
  useDriverLocationSender({ bookingId, driverId, active: isDriver })

  // Fetch route on mount
  useEffect(() => {
    if (!fromLat || !toLat) return
    const coords = `${fromLon},${fromLat};${toLon},${toLat}`
    fetch(`https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`)
      .then(r => r.json())
      .then(data => {
        if (data.code === 'Ok' && data.routes?.length > 0) {
          setRouteCoords(data.routes[0].geometry.coordinates.map(([lng, lat]) => [lat, lng]))
        }
      })
      .catch(() => {})
  }, [fromLat, fromLon, toLat, toLon])

  const updateDriverPos = useCallback((lat, lon, serverHeading) => {
    const newPos = [lat, lon]
    let heading = serverHeading || 0
    if (!heading && prevPosRef.current) {
      heading = getBearing(prevPosRef.current, newPos)
    }
    prevPosRef.current = newPos
    setDriverPos(newPos)
    setDriverHeading(heading)
    setWaitingForLocation(false)
  }, [])

  // User side: WebSocket + REST polling every 5s for real driver location
  useEffect(() => {
    if (isDriver) return

    const token = localStorage.getItem('nj_token')
    const client = createStompClient(token)
    clientRef.current = client

    client.onConnect = () => {
      client.subscribe(`/topic/booking/${bookingId}/location`, (message) => {
        const loc = JSON.parse(message.body)
        updateDriverPos(loc.latitude, loc.longitude, loc.heading)
      })
    }

    client.onStompError = () => {}
    client.activate()

    const pollLocation = () => {
      getDriverLocation(bookingId).then(r => {
        if (r.data) {
          updateDriverPos(r.data.latitude, r.data.longitude, r.data.heading)
        }
      }).catch(() => {})
    }
    pollLocation()
    const pollInterval = setInterval(pollLocation, 3000)

    return () => {
      if (clientRef.current) clientRef.current.deactivate()
      if (pollInterval) clearInterval(pollInterval)
    }
  }, [bookingId, isDriver, updateDriverPos])

  // Driver side: watch own GPS and show on map
  useEffect(() => {
    if (!isDriver) return
    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        updateDriverPos(pos.coords.latitude, pos.coords.longitude, pos.coords.heading)
      },
      () => {},
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 }
    )
    return () => navigator.geolocation.clearWatch(watchId)
  }, [isDriver, updateDriverPos])

  // ETA calculation - throttled to every 10s, triggered by position changes
  useEffect(() => {
    if (!driverPos || !toLat || !toLon) return

    const now = Date.now()
    if (now - lastEtaCalcRef.current < 10000) return
    lastEtaCalcRef.current = now

    const url = `https://router.project-osrm.org/route/v1/driving/${driverPos[1]},${driverPos[0]};${toLon},${toLat}?overview=false`
    fetch(url).then(r => r.json()).then(data => {
      if (data.code === 'Ok' && data.routes?.length > 0) {
        setEta(Math.ceil(data.routes[0].duration / 60))
        setDistRemaining((data.routes[0].distance / 1000).toFixed(1))
      }
    }).catch(() => {})
  }, [driverPos, toLat, toLon])

  // Travelled route (green portion behind driver)
  const travelledRoute = useMemo(() => {
    if (!routeCoords || !driverPos) return null
    const idx = findNearestRouteIndex(routeCoords, driverPos)
    return routeCoords.slice(0, idx + 1)
  }, [routeCoords, driverPos])

  // Progress percentage
  const progress = useMemo(() => {
    if (!routeCoords || !driverPos) return 0
    const idx = findNearestRouteIndex(routeCoords, driverPos)
    return Math.round((idx / (routeCoords.length - 1)) * 100)
  }, [routeCoords, driverPos])

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
        </div>
        <div style={{ display: 'flex', gap: 16, fontSize: 13, alignItems: 'center' }}>
          {distRemaining && <span style={{ color: '#94a3b8' }}>{distRemaining} km left</span>}
          <span>ETA: <strong style={{ color: '#60a5fa' }}>{formatEta(eta)}</strong></span>
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
            background: 'linear-gradient(90deg, #22c55e, #3b82f6)',
            transition: 'width 2s ease',
          }} />
          {driverPos && (
            <div style={{
              position: 'absolute', top: -8, transition: 'left 2s ease',
              left: `calc(${progress}% - 8px)`,
              fontSize: 16, lineHeight: 1,
            }}>
              🚖
            </div>
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
          Connecting to driver's live location...
        </div>
      )}

      {/* Map */}
      <div style={{ borderRadius: 14, overflow: 'hidden', border: '2px solid #e2e8f0', height: 400, position: 'relative' }}>
        <MapContainer center={center} zoom={7} style={{ height: '100%', width: '100%' }} scrollWheelZoom={true} zoomControl={false}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
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
              <Polyline positions={routeCoords} pathOptions={{ color: '#3b82f6', weight: 5, opacity: 0.6 }} />
            </>
          )}

          {/* Travelled route - green solid */}
          {travelledRoute && travelledRoute.length > 1 && (
            <>
              <Polyline positions={travelledRoute} pathOptions={{ color: '#22c55e', weight: 8, opacity: 0.15 }} />
              <Polyline positions={travelledRoute} pathOptions={{ color: '#22c55e', weight: 5, opacity: 0.9 }} />
            </>
          )}

          {/* Remaining route - blue solid when live tracking */}
          {routeCoords && driverPos && (
            <Polyline
              positions={routeCoords.slice(findNearestRouteIndex(routeCoords, driverPos))}
              pathOptions={{ color: '#3b82f6', weight: 5, opacity: 0.8 }}
            />
          )}

          {/* Animated car along route when waiting for real GPS */}
          {routeCoords && !driverPos && map && (
            <AnimatedRouteCarMarker routeCoords={routeCoords} map={map} speedFactor={1.5} />
          )}

          {/* Real driver car - shows when we have actual driver GPS */}
          {driverPos && map && (
            <>
              <DriverMarker position={driverPos} heading={driverHeading} map={map} />
              <FollowDriver driverPos={driverPos} active={followDriver} />
            </>
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
        @keyframes glow {
          0%, 100% { box-shadow: 0 0 8px 2px rgba(59,130,246,0.5); }
          50% { box-shadow: 0 0 16px 6px rgba(59,130,246,0.8); }
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
        .driver-car-icon { background: none !important; border: none !important; }
        .car-container {
          position: relative; width: 48px; height: 48px;
          display: flex; align-items: center; justify-content: center;
          transition: transform 0.5s ease;
        }
        .car-pulse-ring {
          position: absolute; width: 48px; height: 48px; border-radius: 50%;
          border: 2px solid rgba(59,130,246,0.6);
          animation: car-ring-pulse 2s ease-out infinite;
        }
        @keyframes car-ring-pulse {
          0% { transform: scale(0.5); opacity: 1; }
          100% { transform: scale(1.8); opacity: 0; }
        }
        .car-glow {
          position: absolute; width: 36px; height: 36px; border-radius: 50%;
          background: radial-gradient(circle, rgba(59,130,246,0.4) 0%, transparent 70%);
          animation: glow 2s ease-in-out infinite;
        }
        .car-emoji {
          font-size: 32px; z-index: 1; position: relative;
          filter: drop-shadow(0 2px 6px rgba(0,0,0,0.4));
        }
      `}</style>
    </div>
  )
}
