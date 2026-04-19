import { useEffect, useRef } from 'react'
import { createStompClient } from '../utils/websocket'
import { updateDriverLocation } from '../utils/api'

export default function useDriverLocationSender({ bookingId, driverId, active }) {
  const clientRef = useRef(null)
  const watchIdRef = useRef(null)
  const lastSentRef = useRef(0)

  useEffect(() => {
    if (!active || !bookingId || !driverId) return

    const token = localStorage.getItem('nj_token')
    const client = createStompClient(token)
    clientRef.current = client

    let connected = false

    const sendLocation = (lat, lon, heading) => {
      const now = Date.now()
      if (now - lastSentRef.current < 2000) return
      lastSentRef.current = now

      const payload = {
        driverId,
        bookingId,
        latitude: lat,
        longitude: lon,
        heading: heading || 0,
        timestamp: now,
      }

      if (connected && client.connected) {
        client.publish({
          destination: '/app/location.update',
          body: JSON.stringify(payload),
        })
      } else {
        updateDriverLocation(payload).catch(() => {})
      }
    }

    client.onConnect = () => {
      connected = true
    }

    client.onStompError = () => {
      connected = false
    }

    client.activate()

    if (navigator.geolocation) {
      watchIdRef.current = navigator.geolocation.watchPosition(
        (pos) => {
          sendLocation(pos.coords.latitude, pos.coords.longitude, pos.coords.heading)
        },
        (err) => {
          console.warn('Geolocation error:', err.message)
        },
        { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 }
      )
    }

    return () => {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current)
      }
      if (clientRef.current) {
        clientRef.current.deactivate()
      }
    }
  }, [active, bookingId, driverId])
}
