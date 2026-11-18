import React, { useCallback, useEffect, useState } from 'react'
import { MapContainer, TileLayer, Circle, Polygon, Tooltip } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { TILE_URL, TILE_ATTRIBUTION } from '../../config/mapServices'
import Icon from '../dash/Icon'
import { Panel, Empty, Skeleton } from '../dash/ui'
import { getZoneHeatmap } from '../../utils/api'

// Shortfall (riders waiting minus free drivers) as colour: red where riders go unserved,
// green where drivers sit idle, amber where the two are even.
const SHORT = '#dc2626'
const EVEN = '#d97706'
const SPARE = '#16a34a'
const colourFor = (c) => (c.shortfall > 0 ? SHORT : c.shortfall < 0 ? SPARE : EVEN)

/**
 * Riders waiting and free drivers per H3 hexagon in one City-ride area, refreshed every 15 s.
 * The red cells are where to send drivers; the green ones are where they wait for nothing.
 */
export default function ZoneHeatmap({ city, onClose }) {
  const [data, setData] = useState(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    getZoneHeatmap(city.id).then((r) => { setData(r.data); setFailed(false) }).catch(() => setFailed(true))
  }, [city.id])

  useEffect(() => {
    load()
    const every = setInterval(load, 15000)
    return () => clearInterval(every)
  }, [load])

  const cells = data?.cells || []
  const hot = cells.filter((c) => c.shortfall > 0).slice(0, 5)

  return (
    <Panel title={`Live demand · ${city.name}`} meta={data ? `${data.waiting} waiting · ${data.idleDrivers} free drivers` : undefined}
      style={{ marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={onClose}><Icon name="x" size={14} />Close</button>
      </div>
      {failed && !data ? (
        <Empty icon="map" title="Could not load the heatmap">Try again in a moment.</Empty>
      ) : !data ? <Skeleton rows={4} /> : !data.gridAvailable ? (
        <Empty icon="map" title="Heatmap unavailable">The hexagon grid could not start on this server.</Empty>
      ) : (
        <>
          <div style={{ height: 360, borderRadius: 14, overflow: 'hidden', border: '1px solid var(--pc-line)' }}>
            <MapContainer center={data.center} zoom={city.radiusKm > 20 ? 11 : 12} style={{ height: '100%', width: '100%' }} scrollWheelZoom={false}>
              <TileLayer attribution={TILE_ATTRIBUTION} url={TILE_URL} />
              <Circle center={data.center} radius={data.radiusKm * 1000} pathOptions={{ color: '#2563eb', weight: 1.5, fillOpacity: 0.03 }} />
              {cells.map((c) => (
                <Polygon key={c.cell} positions={c.boundary}
                  pathOptions={{ color: colourFor(c), weight: 1, fillColor: colourFor(c), fillOpacity: 0.45 }}>
                  <Tooltip>{c.waiting} waiting · {c.idleDrivers} free · {c.busyDrivers} busy</Tooltip>
                </Polygon>
              ))}
            </MapContainer>
          </div>
          <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 10, fontSize: 12.5, fontWeight: 700, color: 'var(--pc-ink-2)' }}>
            <span><i style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: SHORT, marginRight: 6 }} />Riders waiting, too few drivers</span>
            <span><i style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: EVEN, marginRight: 6 }} />Even</span>
            <span><i style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: SPARE, marginRight: 6 }} />Free drivers, no riders</span>
          </div>
          {cells.length === 0 ? (
            <p style={{ margin: '12px 0 0', fontSize: 13, color: 'var(--pc-muted)' }}>Nobody online and no rides waiting in this area right now.</p>
          ) : hot.length > 0 && (
            <div style={{ marginTop: 14 }}>
              <b style={{ fontSize: 13 }}>Where riders are short of drivers</b>
              <ol style={{ margin: '6px 0 0', paddingLeft: 18, fontSize: 13, color: 'var(--pc-ink-2)' }}>
                {hot.map((c) => (
                  <li key={c.cell}>
                    {c.waiting} waiting, {c.idleDrivers} free near {c.center[0].toFixed(4)}, {c.center[1].toFixed(4)}
                  </li>
                ))}
              </ol>
            </div>
          )}
        </>
      )}
    </Panel>
  )
}
