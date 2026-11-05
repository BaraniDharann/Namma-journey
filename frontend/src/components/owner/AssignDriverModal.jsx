import React, { useEffect, useState } from 'react'
import Icon from '../dash/Icon'
import { Skeleton, Empty } from '../dash/ui'
import { getAssignOptions, assignDriver } from '../../utils/api'
import { categoryLabel } from '../../utils/carCategories'

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback

const carText = (v) => `${v.registrationNumber}${v.model ? ` · ${v.model}` : ''} · ${v.seats} seats · ${v.ac ? 'AC' : 'Non-AC'}`

/** Puts one of my free drivers, and a free car, on a Trip. The car starts as the driver's usual car when it suits. */
export default function AssignDriverModal({ booking, onClose, onAssigned }) {
  const bookingId = booking.id || booking.bookingId
  const [options, setOptions] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [driverId, setDriverId] = useState('')
  const [vehicleId, setVehicleId] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getAssignOptions(bookingId)
      .then((r) => setOptions({ drivers: r.data?.drivers || [], vehicles: r.data?.vehicles || [] }))
      .catch((e) => setLoadError(errorText(e, 'Could not load your free drivers and cars')))
  }, [bookingId])

  const pickDriver = (id) => {
    setDriverId(id)
    const usual = options.drivers.find((d) => String(d.id) === id)?.defaultVehicleId
    setVehicleId(usual && options.vehicles.some((v) => v.id === usual) ? String(usual) : '')
  }

  const submit = async () => {
    setSaving(true); setError('')
    try {
      const res = await assignDriver(bookingId, Number(driverId), vehicleId ? Number(vehicleId) : null)
      const driver = options.drivers.find((d) => String(d.id) === driverId)
      onAssigned(res.data, driver)
    } catch (e) {
      setError(errorText(e, 'Could not assign this driver'))
      setSaving(false)
    }
  }

  const empty = options && (options.drivers.length === 0 || options.vehicles.length === 0)

  return (
    <div className="modal-overlay">
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="assign-driver-title" style={{ overflow: 'hidden', padding: 0 }}>
        <div style={{ padding: '22px 24px 14px', borderBottom: '1px solid var(--pc-line)' }}>
          <h3 id="assign-driver-title" style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', marginBottom: 4 }}>Assign a driver</h3>
          <p style={{ fontSize: 13, color: 'var(--pc-ink-2)' }}>
            {booking.carCategory ? `${categoryLabel(booking.carCategory)} · ` : ''}{booking.travelMembers ? `${booking.travelMembers} travellers · ` : ''}{booking.fromPlace} → {booking.toPlace}
          </p>
        </div>
        <div style={{ padding: 24 }}>
          {loadError ? (
            <div className="pc-error" role="alert" style={{ marginBottom: 16 }}><Icon name="alert" size={16} /><span>{loadError}</span></div>
          ) : !options ? (
            <div style={{ marginBottom: 16 }}><Skeleton rows={3} height={18} /></div>
          ) : empty ? (
            <div style={{ marginBottom: 16 }}>
              <Empty icon="car" title={options.drivers.length === 0 ? 'No free drivers' : 'No free car fits this Trip'}>
                {options.drivers.length === 0
                  ? 'All your approved drivers are busy on these dates. Approve another driver or free one up.'
                  : `None of your cars in use is a free ${categoryLabel(booking.carCategory).toLowerCase()} with enough seats${booking.acType === 'AC' ? ' and AC' : ''}.`}
              </Empty>
            </div>
          ) : (
            <div style={{ display: 'grid', gap: 14, marginBottom: 20 }}>
              <div>
                <label htmlFor="assign-driver" style={labelStyle}>Driver</label>
                <select id="assign-driver" className="input-field" value={driverId} onChange={(e) => pickDriver(e.target.value)}>
                  <option value="">Choose a driver</option>
                  {options.drivers.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
              </div>
              <div>
                <label htmlFor="assign-car" style={labelStyle}>Car</label>
                <select id="assign-car" className="input-field" value={vehicleId} disabled={!driverId} onChange={(e) => setVehicleId(e.target.value)}>
                  <option value="">{driverId ? 'Choose a car' : 'Choose a driver first'}</option>
                  {options.vehicles.map((v) => {
                    const usual = options.drivers.find((d) => String(d.id) === driverId)?.defaultVehicleId === v.id
                    return <option key={v.id} value={v.id}>{carText(v)}{usual ? ' (usual car)' : ''}</option>
                  })}
                </select>
              </div>
            </div>
          )}
          {error && <div className="pc-error" role="alert" style={{ marginBottom: 16 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" onClick={onClose} disabled={saving} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
            <button type="button" onClick={submit} disabled={saving || !driverId || !vehicleId} className="pc-btn" style={{ flex: 1 }}>
              <Icon name="user" />{saving ? 'Assigning...' : 'Assign'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
