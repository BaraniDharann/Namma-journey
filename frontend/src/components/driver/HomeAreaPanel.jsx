import React, { useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { Panel } from '../dash/ui'
import HomeAreaPicker from './HomeAreaPicker'
import { getDriverHomeArea, setDriverHomeArea } from '../../utils/api'

/**
 * The driver's "where I drive" setting on their profile: the town riders are matched with them in.
 * Drivers who signed up before this existed have none, so the panel opens on the picker for them.
 */
export default function HomeAreaPanel({ driverId }) {
  const [area, setArea] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(false)
  const [choice, setChoice] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!driverId) return
    getDriverHomeArea(driverId)
      .then((r) => { setArea(r.data); if (!r.data?.chosen) setEditing(true) })
      .catch(() => setEditing(true))
      .finally(() => setLoading(false))
  }, [driverId])

  const save = async () => {
    if (!choice) { toast.error('Choose your state, district and town'); return }
    setSaving(true)
    try {
      const r = await setDriverHomeArea(driverId, choice)
      setArea(r.data)
      setEditing(false)
      toast.success(r.data?.zoneActive
        ? `Saved. Riders in ${r.data.zoneName} will be sent to you.`
        : `Saved. City rides start in ${r.data?.zoneName} once you are approved.`)
    } catch { /* the api toast shows the reason */ } finally {
      setSaving(false)
    }
  }

  const place = area?.place
  return (
    <Panel span={12} title="Where you drive" meta={area?.chosen ? (area.zoneActive ? 'City rides on' : 'waiting for approval') : 'not set'}>
      {loading ? null : !editing && area?.chosen ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
            <Icon name="pin" size={20} />
            <div style={{ minWidth: 0 }}>
              <b style={{ display: 'block', color: 'var(--pc-ink)', overflowWrap: 'anywhere' }}>{place?.name}</b>
              <small style={{ color: 'var(--pc-muted)', fontWeight: 600 }}>
                {place?.district} district, {place?.state} · rides within {area.zoneRadiusKm} km of town
              </small>
            </div>
          </div>
          <button type="button" className="pc-btn pc-btn-sm pc-btn-ghost" onClick={() => setEditing(true)}>Change</button>
        </div>
      ) : (
        <>
          {!area?.chosen && (
            <p role="status" style={{ margin: '0 0 14px', fontSize: 13.5, fontWeight: 700, color: 'var(--pc-warn)' }}>
              Choose the town you drive in. Until you do, riders there may not be sent to you.
            </p>
          )}
          <HomeAreaPicker
            idPrefix="profile-area"
            initial={place ? { state: place.state, district: place.district, placeId: place.id } : null}
            onChange={setChoice}
          />
          <div style={{ display: 'flex', gap: 10, marginTop: 14 }}>
            <button type="button" className="pc-btn" disabled={saving || !choice} onClick={save}>
              <Icon name="check" />{saving ? 'Saving…' : 'Save'}
            </button>
            {area?.chosen && (
              <button type="button" className="pc-btn pc-btn-ghost" onClick={() => setEditing(false)}>Cancel</button>
            )}
          </div>
        </>
      )}
    </Panel>
  )
}
