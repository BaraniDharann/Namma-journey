import React from 'react'
import Icon from '../dash/Icon'
import { VEHICLES, VEHICLE_ORDER } from '../../dash/vehicles'

export const DEFAULT_VEHICLE = {
  vehicleType: 'CAR', vehicleNumber: '', vehicleModel: '', vehicleAc: true, vehicleSeats: 4, servesCity: true, servesTrip: true,
}

/** Starting values for a kind of vehicle: bikes and autos take City rides only, with fixed seats and no AC. */
const forType = (type) => (type === 'CAR'
  ? { vehicleType: 'CAR', vehicleAc: true, vehicleSeats: 4, servesTrip: true }
  : { vehicleType: type, vehicleAc: false, vehicleSeats: VEHICLES[type].maxPassengers, servesTrip: false })

const label = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const grid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 14 }

/**
 * A driver's vehicle and the work they take (City rides, Trips), for the owner's create and edit
 * forms. Controlled: `value` is the vehicle, `onChange` receives the fields that changed.
 */
export default function VehicleFields({ value, onChange, idPrefix = 'veh' }) {
  const car = value.vehicleType === 'CAR'
  const pick = (on) => ({
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 12, borderRadius: 14, cursor: 'pointer',
    fontWeight: 800, fontSize: 13.5, fontFamily: 'inherit',
    border: on ? '2px solid var(--pc-brand)' : '2px solid var(--pc-line)', background: on ? 'var(--pc-brand-soft)' : 'var(--pc-panel)',
    color: on ? 'var(--pc-brand-deep)' : 'var(--pc-ink-2)',
  })

  return (
    <div style={{ display: 'grid', gap: 16 }}>
      <div>
        <span style={label}>Vehicle type *</span>
        <div role="radiogroup" aria-label="Vehicle type" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 8 }}>
          {VEHICLE_ORDER.map((t) => (
            <button key={t} type="button" role="radio" aria-checked={value.vehicleType === t}
              onClick={() => value.vehicleType !== t && onChange(forType(t))} style={pick(value.vehicleType === t)}>
              <Icon name={VEHICLES[t].icon} size={18} />{VEHICLES[t].label}
            </button>
          ))}
        </div>
      </div>
      <div style={grid}>
        <div>
          <label htmlFor={`${idPrefix}-number`} style={label}>Number plate</label>
          <input id={`${idPrefix}-number`} className="input-field" placeholder="KA 01 AB 1234" maxLength={20}
            value={value.vehicleNumber || ''} onChange={(e) => onChange({ vehicleNumber: e.target.value })} />
        </div>
        <div>
          <label htmlFor={`${idPrefix}-model`} style={label}>Model</label>
          <input id={`${idPrefix}-model`} className="input-field" placeholder={car ? 'Maruti Ertiga' : value.vehicleType === 'AUTO' ? 'Bajaj RE' : 'Honda Activa'} maxLength={60}
            value={value.vehicleModel || ''} onChange={(e) => onChange({ vehicleModel: e.target.value })} />
        </div>
        {car && (
          <>
            <div>
              <label htmlFor={`${idPrefix}-seats`} style={label}>Passenger seats</label>
              <select id={`${idPrefix}-seats`} className="input-field" value={value.vehicleSeats} onChange={(e) => onChange({ vehicleSeats: Number(e.target.value) })}>
                {[4, 5, 6, 7, 8, 10, 12].map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </div>
            <div>
              <span style={label}>Air conditioning</span>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {[[true, 'AC'], [false, 'Non-AC']].map(([on, text]) => (
                  <button key={text} type="button" aria-pressed={value.vehicleAc === on} onClick={() => onChange({ vehicleAc: on })} style={pick(value.vehicleAc === on)}>{text}</button>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
      <div>
        <span style={label}>Takes</span>
        <div className="pc-chips">
          <button type="button" className="pc-switch" role="switch" aria-checked={!!value.servesCity} onClick={() => onChange({ servesCity: !value.servesCity })}>
            <span className="pc-switch-track" />City rides
          </button>
          {car ? (
            <button type="button" className="pc-switch" role="switch" aria-checked={!!value.servesTrip} onClick={() => onChange({ servesTrip: !value.servesTrip })}>
              <span className="pc-switch-track" />Trips
            </button>
          ) : (
            <span style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--pc-muted)', alignSelf: 'center' }}>Trips are by car only.</span>
          )}
        </div>
      </div>
    </div>
  )
}
