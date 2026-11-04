import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import { getOwnerVehicles, createVehicle, updateVehicle, deactivateVehicle, activateVehicle } from '../../utils/api'
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

/** The owner's cars: every Trip goes out in one of these. */
export default function OwnerVehicles() {
  const [vehicles, setVehicles] = useState(null)
  const [loadError, setLoadError] = useState(false)
  const [editing, setEditing] = useState(null) // 'new' or a vehicle
  const [busyId, setBusyId] = useState(null)
  const [rowError, setRowError] = useState(null)

  const load = () => {
    setLoadError(false)
    getOwnerVehicles().then((r) => setVehicles(r.data || [])).catch(() => setLoadError(true))
  }
  useEffect(load, [])

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
      <PageHead title="Vehicles" sub={vehicles ? `${vehicles.filter((v) => v.active).length} cars in use${missing ? ` · ${missing} need a registration number` : ''}` : 'Your cars for Trips'}>
        {editing !== 'new' && <button type="button" className="pc-btn" onClick={() => setEditing('new')}><Icon name="plus" />Add car</button>}
      </PageHead>

      <div className="pc-grid">
        {editing && (
          <Panel span={12} title={editing === 'new' ? 'Add a car' : `Edit ${editing.registrationMissing ? 'car' : editing.registrationNumber}`}>
            <VehicleForm key={editing === 'new' ? 'new' : editing.id} vehicle={editing === 'new' ? null : editing} onSaved={saved} onCancel={() => setEditing(null)} />
          </Panel>
        )}

        <Panel span={12} title="Your cars" meta={vehicles ? `${vehicles.length} total` : null}>
          {loadError ? <ErrorNote onRetry={load} />
            : !vehicles ? <Skeleton rows={4} height={18} />
            : vehicles.length === 0 ? (
              <Empty icon="car" title="No cars yet" action={<button type="button" className="pc-btn pc-btn-sm" onClick={() => setEditing('new')}><Icon name="plus" size={14} />Add car</button>}>
                Add the cars your drivers take on Trips. Travellers book by car type, so add at least one of each type you price.
              </Empty>
            ) : (
              <div className="pc-rows">
                {vehicles.map((v, i) => (
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
                    {rowError?.id === v.id && <div className="pc-error" role="alert" style={{ marginTop: 8, fontSize: 12 }}><Icon name="alert" size={16} /><span>{rowError.text}</span></div>}
                  </div>
                ))}
              </div>
            )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
