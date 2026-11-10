import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import { getOwnerVehicles, createVehicle, updateVehicle, deactivateVehicle, activateVehicle, setVehicleMode, getOwnerDrivers } from '../../utils/api'
import { CAR_CATEGORIES, categoryLabel } from '../../utils/carCategories'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton, ErrorNote } from '../../components/dash/ui'

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const grid = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 14 }
const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback

const blank = () => ({ category: 'SEDAN', registrationNumber: '', model: '', seats: 4, ac: true })

function VehicleForm({ vehicle, onSaved, onCancel }) {
  const editing = !!vehicle
  const [form, setForm] = useState(() => (editing
    ? { category: vehicle.category, registrationNumber: vehicle.registrationMissing ? '' : vehicle.registrationNumber, model: vehicle.model || '', seats: vehicle.seats, ac: !!vehicle.ac }
    : blank()))
  const [rcPhoto, setRcPhoto] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = (patch) => setForm((f) => ({ ...f, ...patch }))
  const pick = (on) => ({
    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 10, borderRadius: 12, cursor: 'pointer',
    fontWeight: 800, fontSize: 13, fontFamily: 'inherit',
    border: on ? '2px solid var(--pc-brand)' : '2px solid var(--pc-line)', background: on ? 'var(--pc-brand-soft)' : 'var(--pc-panel)',
    color: on ? 'var(--pc-brand-deep)' : 'var(--pc-ink-2)',
  })

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      let res
      if (editing) {
        res = await updateVehicle(vehicle.id, { ...form, registrationNumber: form.registrationNumber.trim() || null, seats: Number(form.seats) })
      } else {
        const fd = new FormData()
        Object.entries(form).forEach(([k, v]) => fd.append(k, String(v ?? '')))
        if (rcPhoto) fd.append('rcPhoto', rcPhoto)
        res = await createVehicle(fd)
      }
      onSaved(res.data)
    } catch (err) {
      setError(errorText(err, 'Could not save this car'))
    } finally { setSaving(false) }
  }

  return (
    <form onSubmit={submit} style={{ display: 'grid', gap: 16 }}>
      <div>
        <span style={labelStyle}>Car type *</span>
        <div role="radiogroup" aria-label="Car type" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(120px,1fr))', gap: 8 }}>
          {CAR_CATEGORIES.map((c) => (
            <button key={c.value} type="button" role="radio" aria-checked={form.category === c.value} style={pick(form.category === c.value)}
              onClick={() => set({ category: c.value, ...(editing ? {} : { seats: c.seats }) })}>{c.label}</button>
          ))}
        </div>
      </div>
      <div style={grid}>
        <div>
          <label htmlFor="car-reg" style={labelStyle}>Registration number *</label>
          <input id="car-reg" className="input-field" placeholder="KA 01 AB 1234" maxLength={20} required
            value={form.registrationNumber} onChange={(e) => set({ registrationNumber: e.target.value })} />
        </div>
        <div>
          <label htmlFor="car-model" style={labelStyle}>Model</label>
          <input id="car-model" className="input-field" placeholder="Maruti Ertiga" maxLength={60}
            value={form.model} onChange={(e) => set({ model: e.target.value })} />
        </div>
        <div>
          <label htmlFor="car-seats" style={labelStyle}>Passenger seats *</label>
          <input id="car-seats" type="number" min={1} max={20} className="input-field" required
            value={form.seats} onChange={(e) => set({ seats: e.target.value })} />
        </div>
        <div>
          <span style={labelStyle}>Air conditioning</span>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            {[[true, 'AC'], [false, 'Non-AC']].map(([on, text]) => (
              <button key={text} type="button" aria-pressed={form.ac === on} onClick={() => set({ ac: on })} style={pick(form.ac === on)}>{text}</button>
            ))}
          </div>
        </div>
        {!editing && (
          <div>
            <label htmlFor="car-rc" style={labelStyle}>RC photo</label>
            <input id="car-rc" type="file" accept="image/*" className="input-field" onChange={(e) => setRcPhoto(e.target.files[0] || null)} />
          </div>
        )}
      </div>
      {error && <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div>}
      <div style={{ display: 'flex', gap: 10 }}>
        <button type="button" className="pc-btn pc-btn-ghost" disabled={saving} onClick={onCancel}>Cancel</button>
        <button type="submit" className="pc-btn" disabled={saving}><Icon name="check" />{saving ? 'Saving…' : editing ? 'Save car' : 'Add car'}</button>
      </div>
    </form>
  )
}

const MODES = [
  { value: 'CITY', label: 'City rides', hint: 'Nearby rides are offered to its driver when they go Online' },
  { value: 'TRAVEL', label: 'Travel', hint: 'Picked for Travel (outstation) bookings' },
  { value: 'OFF', label: 'Off', hint: 'Neither; you can still put it on a booking by hand' },
]
const modeOf = (v) => (v.active ? (v.mode || 'TRAVEL') : 'OFF')

/**
 * City / Travel / Off for one car. Choosing City opens a driver picker; the car's driver goes
 * Online in the driver app to get rides. Drivers already on another City car are not offered.
 */
function ModeSwitch({ vehicle, drivers, takenBy, busy, onChange }) {
  const current = modeOf(vehicle)
  const [picking, setPicking] = useState(false)
  const [driverId, setDriverId] = useState(vehicle.cityDriverId ? String(vehicle.cityDriverId) : '')
  const tempo = vehicle.category === 'TEMPO_TRAVELLER'
  const free = drivers.filter((d) => d.verificationStatus !== 'REJECTED'
    && (!takenBy[d.driverId] || takenBy[d.driverId] === vehicle.id))

  const seg = (on) => ({
    padding: '6px 10px', fontSize: 12, fontWeight: 800, fontFamily: 'inherit', cursor: 'pointer', border: 0,
    background: on ? 'var(--pc-brand)' : 'transparent', color: on ? '#fff' : 'var(--pc-ink-2)', borderRadius: 8,
  })

  const choose = (mode) => {
    if (mode === 'CITY') { setPicking(true); return }
    setPicking(false)
    if (mode !== current) onChange(mode, null)
  }

  return (
    <div style={{ display: 'grid', gap: 8 }}>
      <div role="radiogroup" aria-label={`What ${vehicle.registrationNumber} is used for`}
        style={{ display: 'inline-flex', gap: 2, padding: 3, borderRadius: 10, border: '1px solid var(--pc-line)', background: 'var(--pc-panel)', width: 'fit-content' }}>
        {MODES.map((m) => {
          const disabled = busy || !vehicle.active || (m.value === 'CITY' && tempo)
          return (
            <button key={m.value} type="button" role="radio" aria-checked={current === m.value} disabled={disabled}
              title={m.value === 'CITY' && tempo ? 'A Tempo Traveller does Travel trips only' : m.hint}
              style={{ ...seg(current === m.value || (m.value === 'CITY' && picking)), opacity: disabled && current !== m.value ? 0.45 : 1 }}
              onClick={() => choose(m.value)}>{m.label}</button>
          )
        })}
      </div>
      {picking && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <label htmlFor={`city-driver-${vehicle.id}`} style={{ fontSize: 12, fontWeight: 700, color: 'var(--pc-ink-2)' }}>Driver</label>
          <select id={`city-driver-${vehicle.id}`} className="input-field" style={{ width: 'auto', minWidth: 180, padding: '6px 10px' }}
            value={driverId} onChange={(e) => setDriverId(e.target.value)}>
            <option value="">Choose a driver</option>
            {free.map((d) => <option key={d.driverId} value={d.driverId}>{d.name}</option>)}
          </select>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !driverId}
            onClick={() => { onChange('CITY', Number(driverId)); setPicking(false) }}>
            <Icon name="check" size={14} />{busy ? 'Saving…' : 'Use for City rides'}
          </button>
          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setPicking(false)}>Cancel</button>
          {free.length === 0 && <span style={{ fontSize: 12, color: 'var(--pc-muted)' }}>Every driver already has a City car. Add a driver first.</span>}
        </div>
      )}
    </div>
  )
}

/** The owner's cars: each does City rides, Travel trips, or neither, as the owner decides. */
export default function OwnerVehicles() {
  const [vehicles, setVehicles] = useState(null)
  const [loadError, setLoadError] = useState(false)
  const [editing, setEditing] = useState(null) // 'new' or a vehicle
  const [busyId, setBusyId] = useState(null)
  const [rowError, setRowError] = useState(null)

  const [drivers, setDrivers] = useState([])

  const loadDrivers = () => getOwnerDrivers().then((r) => setDrivers(r.data || [])).catch(() => {})
  const load = () => {
    setLoadError(false)
    getOwnerVehicles().then((r) => setVehicles(r.data || [])).catch(() => setLoadError(true))
    loadDrivers()
  }
  useEffect(load, [])

  const changeMode = async (v, mode, driverId) => {
    setBusyId(v.id); setRowError(null)
    try {
      const res = await setVehicleMode(v.id, mode, driverId)
      saved(res.data)
      loadDrivers() // the paired driver's City switch changed
    } catch (err) {
      setRowError({ id: v.id, text: errorText(err, 'Could not change what this car is used for') })
    } finally { setBusyId(null) }
  }

  const driverById = Object.fromEntries(drivers.map((d) => [d.driverId, d]))
  const takenBy = Object.fromEntries((vehicles || []).filter((v) => v.cityDriverId).map((v) => [v.cityDriverId, v.id]))

  const saved = (v) => {
    setVehicles((list) => (list.some((x) => x.id === v.id) ? list.map((x) => (x.id === v.id ? v : x)) : [...list, v]))
    setEditing(null)
  }

  const toggle = async (v) => {
    setBusyId(v.id); setRowError(null)
    try {
      const res = await (v.active ? deactivateVehicle(v.id) : activateVehicle(v.id))
      saved(res.data)
    } catch (err) {
      setRowError({ id: v.id, text: errorText(err, v.active ? 'Could not take this car out of use' : 'Could not put this car back in use') })
    } finally { setBusyId(null) }
  }

  const missing = (vehicles || []).filter((v) => v.registrationMissing).length

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Vehicles" sub={vehicles ? `${((n) => `${n} ${n === 1 ? 'car' : 'cars'}`)(vehicles.filter((v) => v.active).length)} in use${missing ? ` · ${missing} need a registration number` : ''}` : 'City rides, Travel trips, or off: you choose per car'}>
        {editing !== 'new' && <button type="button" className="pc-btn" onClick={() => setEditing('new')}><Icon name="plus" />Add car</button>}
      </PageHead>

      <div className="pc-grid">
        {editing && (
          <Panel span={12} title={editing === 'new' ? 'Add a car' : `Edit ${editing.registrationMissing ? 'car' : editing.registrationNumber}`}>
            <VehicleForm key={editing === 'new' ? 'new' : editing.id} vehicle={editing === 'new' ? null : editing} onSaved={saved} onCancel={() => setEditing(null)} />
          </Panel>
        )}

        {loadError ? <Panel span={12} title="Your cars"><ErrorNote onRetry={load} /></Panel>
          : !vehicles ? <Panel span={12} title="Your cars"><Skeleton rows={4} height={18} /></Panel>
          : vehicles.length === 0 ? (
            <Panel span={12} title="Your cars">
              <Empty icon="car" title="No cars yet" action={<button type="button" className="pc-btn pc-btn-sm" onClick={() => setEditing('new')}><Icon name="plus" size={14} />Add car</button>}>
                Add your cars, then choose for each one: City rides near you, Travel trips, or neither.
              </Empty>
            </Panel>
          ) : MODES.map((group) => {
            const list = vehicles.filter((v) => modeOf(v) === group.value)
            return (
              <Panel key={group.value} span={12} title={group.value === 'CITY' ? 'City ride cars' : group.value === 'TRAVEL' ? 'Travel cars' : 'Off'}
                meta={`${list.length} ${list.length === 1 ? 'car' : 'cars'}`}>
                {list.length === 0 ? (
                  <div style={{ fontSize: 13, color: 'var(--pc-muted)', padding: '4px 0' }}>
                    {group.value === 'CITY' ? 'No car on City rides. Switch a car to "City rides" and choose its driver: nearby rides then go to that driver when they are Online.'
                      : group.value === 'TRAVEL' ? 'No car takes Travel bookings automatically. Switch a car to "Travel".'
                      : 'No cars switched off.'}
                  </div>
                ) : (
              <div className="pc-rows">
                {list.map((v, i) => {
                  const cityDriver = v.cityDriverId ? driverById[v.cityDriverId] : null
                  return (
                  <div key={v.id} style={{ padding: '12px 0', borderTop: i ? '1px solid var(--pc-line)' : 0, opacity: v.active ? 1 : 0.7 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                      <div style={{ minWidth: 0 }}>
                        <div className="pc-row-title" style={{ fontSize: 15 }}>
                          {v.registrationMissing ? <span style={{ color: 'var(--pc-muted)' }}>No registration number</span> : v.registrationNumber}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>
                          {categoryLabel(v.category)}{v.model ? ` · ${v.model}` : ''} · {v.seats} seats · {v.ac ? 'AC' : 'Non-AC'}
                          {v.rcPhoto && <> · <a href={v.rcPhoto} target="_blank" rel="noopener noreferrer">RC photo</a></>}
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                        {v.registrationMissing && (
                          <button type="button" className="pc-pill pc-pill-wait" style={{ border: 'none', cursor: 'pointer' }} onClick={() => setEditing(v)}
                            title="Add the number plate so drivers and travellers know which car is coming">
                            Registration missing
                          </button>
                        )}
                        <span className={`pc-pill ${v.active ? 'pc-pill-ok' : ''}`}>{v.active ? 'In use' : 'Not in use'}</span>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setEditing(v)}><Icon name="edit" size={14} />Edit</button>
                        <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" disabled={busyId === v.id} onClick={() => toggle(v)}
                          style={v.active ? { color: 'var(--pc-bad)' } : undefined}>
                          <Icon name="power" size={14} />{busyId === v.id ? 'Saving…' : v.active ? 'Take out of use' : 'Put back in use'}
                        </button>
                      </div>
                    </div>
                    <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                      <ModeSwitch vehicle={v} drivers={drivers} takenBy={takenBy} busy={busyId === v.id}
                        onChange={(mode, driverId) => changeMode(v, mode, driverId)} />
                      {v.mode === 'CITY' && (
                        <span style={{ fontSize: 12, color: 'var(--pc-ink-2)', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                          <Icon name="user" size={14} />
                          {cityDriver ? cityDriver.name : 'Driver'}
                          {cityDriver && cityDriver.status !== 'ACTIVE' && <span className="pc-pill">Offline</span>}
                          {cityDriver && cityDriver.status === 'ACTIVE' && <span className="pc-pill pc-pill-ok">Online</span>}
                        </span>
                      )}
                    </div>
                    {rowError?.id === v.id && <div className="pc-error" role="alert" style={{ marginTop: 8, fontSize: 12 }}><Icon name="alert" size={16} /><span>{rowError.text}</span></div>}
                  </div>
                  )
                })}
              </div>
                )}
              </Panel>
            )
          })}
      </div>
    </DashboardLayout>
  )
}
