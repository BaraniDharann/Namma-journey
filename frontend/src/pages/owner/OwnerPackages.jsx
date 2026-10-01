import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { useAuth } from '../../context/AuthContext'
import Icon from '../../components/dash/Icon'
import { PageHead, Empty, Skeleton, StatusPill } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { getOwnerPackages, createPackage, updatePackage, togglePackage, deletePackage } from '../../utils/api'
import { useCelebrate } from '../../components/celebrate/Celebration'

const CATEGORIES = [
  { value: 'TEMPLE', label: 'Temple Visit' },
  { value: 'HONEYMOON', label: 'Honeymoon' },
  { value: 'ADVENTURE', label: 'Adventure' },
  { value: 'HILL_STATION', label: 'Hill Station' },
  { value: 'BEACH', label: 'Beach' },
  { value: 'HERITAGE', label: 'Heritage' },
  { value: 'WILDLIFE', label: 'Wildlife' },
  { value: 'PILGRIMAGE', label: 'Pilgrimage' },
  { value: 'FAMILY', label: 'Family' },
  { value: 'STATE_SPECIAL', label: 'State Special' },
]

const INDIAN_STATES = [
  'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat',
  'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh',
  'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
  'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh',
  'Uttarakhand', 'West Bengal', 'Andaman and Nicobar', 'Chandigarh', 'Delhi',
  'Jammu and Kashmir', 'Ladakh', 'Lakshadweep', 'Puducherry',
]

const emptyForm = {
  name: '', description: '', category: 'TEMPLE', state: '', durationDays: 3, durationNights: 2,
  pricePerPerson: '', maxGroupSize: 20, placesIncluded: [''], foodIncluded: false, foodDetails: '',
  accommodationIncluded: false, accommodationDetails: '', tollFree: false, transportIncluded: true,
  transportDetails: '', guideIncluded: false, sightseeingIncluded: false, imageUrl: '',
  highlights: [''], itinerary: [{ day: 1, title: '', description: '', activities: [''] }],
}

const navItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

export default function OwnerPackages() {
  const { user } = useAuth()
  const celebrate = useCelebrate()
  const [packages, setPackages] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editId, setEditId] = useState(null)
  const [form, setForm] = useState({ ...emptyForm })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { fetchPackages() }, [])

  const fetchPackages = async () => {
    setLoading(true)
    try {
      const res = await getOwnerPackages()
      setPackages(res.data || [])
    } catch { setPackages([]) }
    setLoading(false)
  }

  const openCreate = () => {
    setEditId(null)
    setForm({ ...emptyForm, placesIncluded: [''], highlights: [''], itinerary: [{ day: 1, title: '', description: '', activities: [''] }] })
    setShowForm(true)
    setError('')
  }

  const openEdit = (pkg) => {
    setEditId(pkg.id)
    setForm({
      ...pkg,
      placesIncluded: pkg.placesIncluded?.length ? pkg.placesIncluded : [''],
      highlights: pkg.highlights?.length ? pkg.highlights : [''],
      itinerary: pkg.itinerary?.length ? pkg.itinerary : [{ day: 1, title: '', description: '', activities: [''] }],
    })
    setShowForm(true)
    setError('')
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const data = {
        ...form,
        placesIncluded: form.placesIncluded.filter(p => p.trim()),
        highlights: form.highlights.filter(h => h.trim()),
        itinerary: form.itinerary.map(it => ({ ...it, activities: it.activities.filter(a => a.trim()) })),
      }
      if (editId) {
        await updatePackage(editId, data)
      } else {
        await createPackage(data, user.userId)
      }
      setShowForm(false)
      fetchPackages()
      if (!editId) {
        celebrate({ title: 'Package published', message: `${form.name || 'Your package'} is live for travellers to book.` })
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to save package')
    }
    setSaving(false)
  }

  const handleToggle = async (id) => {
    try { await togglePackage(id); fetchPackages() } catch { /* already surfaced by the api error toast */ }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this package permanently?')) return
    try { await deletePackage(id); fetchPackages() } catch { /* already surfaced by the api error toast */ }
  }

  // Dynamic list field helpers
  const addListItem = (field) => setForm(f => ({ ...f, [field]: [...f[field], ''] }))
  const updateListItem = (field, idx, val) => setForm(f => ({ ...f, [field]: f[field].map((v, i) => i === idx ? val : v) }))
  const removeListItem = (field, idx) => setForm(f => ({ ...f, [field]: f[field].filter((_, i) => i !== idx) }))

  const addItineraryDay = () => setForm(f => ({
    ...f, itinerary: [...f.itinerary, { day: f.itinerary.length + 1, title: '', description: '', activities: [''] }]
  }))
  const updateItinerary = (idx, key, val) => setForm(f => ({
    ...f, itinerary: f.itinerary.map((it, i) => i === idx ? { ...it, [key]: val } : it)
  }))
  const removeItineraryDay = (idx) => setForm(f => ({
    ...f, itinerary: f.itinerary.filter((_, i) => i !== idx).map((it, i) => ({ ...it, day: i + 1 }))
  }))
  const addActivity = (dayIdx) => setForm(f => ({
    ...f, itinerary: f.itinerary.map((it, i) => i === dayIdx ? { ...it, activities: [...it.activities, ''] } : it)
  }))
  const updateActivity = (dayIdx, actIdx, val) => setForm(f => ({
    ...f, itinerary: f.itinerary.map((it, i) => i === dayIdx ? { ...it, activities: it.activities.map((a, j) => j === actIdx ? val : a) } : it)
  }))
  const removeActivity = (dayIdx, actIdx) => setForm(f => ({
    ...f, itinerary: f.itinerary.map((it, i) => i === dayIdx ? { ...it, activities: it.activities.filter((_, j) => j !== actIdx) } : it)
  }))

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(packages, 6)

  const getCategoryLabel = (val) => CATEGORIES.find(c => c.value === val)?.label || val
  const categoryIcon = (cat) => (cat === 'TEMPLE' || cat === 'PILGRIMAGE' ? 'temple' : cat === 'HILL_STATION' || cat === 'ADVENTURE' || cat === 'WILDLIFE' ? 'map' : 'package')

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Travel Packages" sub="Create and manage travel packages for your customers">
        <button type="button" className="pc-btn" onClick={openCreate}><Icon name="plus" />Create Package</button>
      </PageHead>

      {/* Package Cards Grid */}
      {loading ? (
        <div className="pc-panel"><Skeleton rows={5} /></div>
      ) : packages.length === 0 ? (
        <div className="pc-panel">
          <Empty
            icon="package"
            title="No packages yet"
            action={<button type="button" className="pc-btn pc-btn-sm" onClick={openCreate}><Icon name="plus" size={15} />Create Package</button>}
          >
            Create your first travel package!
          </Empty>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))', gap: 16 }}>
          {paginatedItems.map(pkg => (
            <article key={pkg.id} className="pc-panel" style={{ padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column', opacity: pkg.active ? 1 : 0.75 }}>
              <div style={{ height: 160, background: pkg.imageUrl ? `url(${pkg.imageUrl}) center/cover` : 'var(--pc-wash)', display: 'flex', alignItems: 'flex-end', padding: 14, position: 'relative' }}>
                {!pkg.imageUrl && (
                  <span aria-hidden="true" style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', color: 'var(--pc-brand-deep)', opacity: 0.35, display: 'inline-flex' }}>
                    <Icon name={categoryIcon(pkg.category)} size={48} />
                  </span>
                )}
                <div className="pc-chips" style={{ gap: 6, position: 'relative' }}>
                  <span className="pc-chip" style={{ background: 'var(--pc-panel)', color: 'var(--pc-ink)' }}>{getCategoryLabel(pkg.category)}</span>
                  {pkg.state && <span className="pc-chip" style={{ background: 'var(--pc-panel)' }}><Icon name="pin" size={13} />{pkg.state}</span>}
                </div>
              </div>
              <div style={{ padding: 18, display: 'flex', flexDirection: 'column', flex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10, marginBottom: 6 }}>
                  <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--pc-ink)', margin: 0 }}>{pkg.name}</h3>
                  <StatusPill status={pkg.active ? 'ACTIVE' : 'INACTIVE'} />
                </div>
                <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', margin: '0 0 12px', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{pkg.description}</p>
                <div className="pc-chips" style={{ gap: 6, marginBottom: 14 }}>
                  {pkg.foodIncluded && <span className="pc-chip"><Icon name="check" size={13} />Food</span>}
                  {pkg.accommodationIncluded && <span className="pc-chip"><Icon name="home" size={13} />Stay</span>}
                  {pkg.transportIncluded && <span className="pc-chip"><Icon name="car" size={13} />Transport</span>}
                  {pkg.tollFree && <span className="pc-chip"><Icon name="route" size={13} />Toll Free</span>}
                  {pkg.guideIncluded && <span className="pc-chip"><Icon name="user" size={13} />Guide</span>}
                  {pkg.sightseeingIncluded && <span className="pc-chip"><Icon name="camera" size={13} />Sightseeing</span>}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 10, marginBottom: 10 }}>
                  <div>
                    <span className="pc-figure" style={{ fontSize: 22, color: 'var(--pc-ink)' }}>{inr(pkg.pricePerPerson)}</span>
                    <span style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 600 }}> / person</span>
                  </div>
                  <span className="pc-chip" style={{ padding: '4px 10px' }}><Icon name="clock" size={13} />{pkg.durationDays}D / {pkg.durationNights}N</span>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 14px', fontSize: 12.5, color: 'var(--pc-muted)', fontWeight: 600, marginBottom: 16 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, minWidth: 0 }}><Icon name="pin" size={14} />{pkg.placesIncluded?.join(', ')}</span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}><Icon name="users" size={14} />Max {pkg.maxGroupSize}</span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}><Icon name="ticket" size={14} />{pkg.totalBookings || 0} bookings</span>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 'auto' }}>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ flex: 1 }} onClick={() => openEdit(pkg)}><Icon name="edit" size={15} />Edit</button>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ flex: 1 }} onClick={() => handleToggle(pkg.id)}><Icon name="power" size={15} />{pkg.active ? 'Deactivate' : 'Activate'}</button>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }} onClick={() => handleDelete(pkg.id)} aria-label={`Delete ${pkg.name || 'package'}`} title="Delete package"><Icon name="trash" size={15} /></button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
      {packages.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}

      {/* Create/Edit Modal */}
      {showForm && (
        <div className="modal-overlay" style={{ zIndex: 1000, padding: 20 }} onClick={() => setShowForm(false)}>
          <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="pkg-form-title" style={{ maxWidth: 800, maxHeight: '90vh', overflow: 'auto', padding: 0 }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, padding: '22px 28px', borderBottom: '1px solid var(--pc-line)' }}>
              <h2 id="pkg-form-title" style={{ fontSize: 20, color: 'var(--pc-ink)', margin: 0 }}>{editId ? 'Edit Package' : 'Create New Package'}</h2>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setShowForm(false)} aria-label="Close"><Icon name="x" size={16} /></button>
            </div>
            <div style={{ padding: 28 }}>
              {error && <div className="pc-error" style={{ marginBottom: 16 }}><Icon name="alert" />{error}</div>}
              <form onSubmit={handleSubmit}>
                {/* Basic Info */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: 16, marginBottom: 24 }}>
                  <div style={{ gridColumn: '1 / -1' }}>
                    <label style={labelStyle}>Package Name *</label>
                    <input required className="input-field" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g., Kerala Backwaters Premium" />
                  </div>
                  <div style={{ gridColumn: '1 / -1' }}>
                    <label style={labelStyle}>Description</label>
                    <textarea className="input-field" style={{ minHeight: 80 }} value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Describe the package experience..." />
                  </div>
                  <div>
                    <label style={labelStyle}>Category *</label>
                    <select required className="input-field" value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))}>
                      {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={labelStyle}>State *</label>
                    <select required className="input-field" value={form.state} onChange={e => setForm(f => ({ ...f, state: e.target.value }))}>
                      <option value="">Select State</option>
                      {INDIAN_STATES.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={labelStyle}>Duration Days *</label>
                    <input required type="number" min="1" className="input-field" value={form.durationDays} onChange={e => setForm(f => ({ ...f, durationDays: +e.target.value }))} />
                  </div>
                  <div>
                    <label style={labelStyle}>Duration Nights *</label>
                    <input required type="number" min="0" className="input-field" value={form.durationNights} onChange={e => setForm(f => ({ ...f, durationNights: +e.target.value }))} />
                  </div>
                  <div>
                    <label style={labelStyle}>Price Per Person (₹) *</label>
                    <input required type="number" min="1" className="input-field" value={form.pricePerPerson} onChange={e => setForm(f => ({ ...f, pricePerPerson: +e.target.value }))} placeholder="e.g., 5999" />
                  </div>
                  <div>
                    <label style={labelStyle}>Max Group Size *</label>
                    <input required type="number" min="1" className="input-field" value={form.maxGroupSize} onChange={e => setForm(f => ({ ...f, maxGroupSize: +e.target.value }))} />
                  </div>
                  <div style={{ gridColumn: '1 / -1' }}>
                    <label style={labelStyle}>Image URL</label>
                    <input className="input-field" value={form.imageUrl} onChange={e => setForm(f => ({ ...f, imageUrl: e.target.value }))} placeholder="https://example.com/image.jpg" />
                  </div>
                </div>

                {/* Places Included */}
                <div style={{ marginBottom: 24 }}>
                  <label style={labelStyle}>Places Included *</label>
                  {form.placesIncluded.map((p, i) => (
                    <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
                      <input className="input-field" style={{ flex: 1 }} value={p} onChange={e => updateListItem('placesIncluded', i, e.target.value)} placeholder={`Place ${i + 1}`} />
                      {form.placesIncluded.length > 1 && <RemoveButton label={`Remove place ${i + 1}`} onClick={() => removeListItem('placesIncluded', i)} />}
                    </div>
                  ))}
                  <AddButton onClick={() => addListItem('placesIncluded')}>Add Place</AddButton>
                </div>

                {/* Inclusions */}
                <div style={{ marginBottom: 24 }}>
                  <label style={{ ...labelStyle, fontSize: 15, marginBottom: 12 }}>Inclusions</label>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: 12 }}>
                    <ToggleField icon="check" label="Food Included" checked={form.foodIncluded} onChange={v => setForm(f => ({ ...f, foodIncluded: v }))} detail={form.foodDetails} onDetailChange={v => setForm(f => ({ ...f, foodDetails: v }))} detailPlaceholder="e.g., Breakfast & Dinner included" />
                    <ToggleField icon="home" label="Accommodation" checked={form.accommodationIncluded} onChange={v => setForm(f => ({ ...f, accommodationIncluded: v }))} detail={form.accommodationDetails} onDetailChange={v => setForm(f => ({ ...f, accommodationDetails: v }))} detailPlaceholder="e.g., 3-star hotel, AC rooms" />
                    <ToggleField icon="car" label="Transport" checked={form.transportIncluded} onChange={v => setForm(f => ({ ...f, transportIncluded: v }))} detail={form.transportDetails} onDetailChange={v => setForm(f => ({ ...f, transportDetails: v }))} detailPlaceholder="e.g., AC car, pickup & drop" />
                    <div style={boxStyle}>
                      <label style={checkLabelStyle}>
                        <input type="checkbox" checked={form.tollFree} onChange={e => setForm(f => ({ ...f, tollFree: e.target.checked }))} />
                        <Icon name="route" size={16} />
                        <span style={{ fontSize: 13, fontWeight: 700 }}>Toll Free</span>
                      </label>
                    </div>
                    <div style={boxStyle}>
                      <label style={checkLabelStyle}>
                        <input type="checkbox" checked={form.guideIncluded} onChange={e => setForm(f => ({ ...f, guideIncluded: e.target.checked }))} />
                        <Icon name="user" size={16} />
                        <span style={{ fontSize: 13, fontWeight: 700 }}>Guide Included</span>
                      </label>
                    </div>
                    <div style={boxStyle}>
                      <label style={checkLabelStyle}>
                        <input type="checkbox" checked={form.sightseeingIncluded} onChange={e => setForm(f => ({ ...f, sightseeingIncluded: e.target.checked }))} />
                        <Icon name="camera" size={16} />
                        <span style={{ fontSize: 13, fontWeight: 700 }}>Sightseeing</span>
                      </label>
                    </div>
                  </div>
                </div>

                {/* Highlights */}
                <div style={{ marginBottom: 24 }}>
                  <label style={labelStyle}>Package Highlights</label>
                  {form.highlights.map((h, i) => (
                    <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
                      <input className="input-field" style={{ flex: 1 }} value={h} onChange={e => updateListItem('highlights', i, e.target.value)} placeholder={`Highlight ${i + 1}`} />
                      {form.highlights.length > 1 && <RemoveButton label={`Remove highlight ${i + 1}`} onClick={() => removeListItem('highlights', i)} />}
                    </div>
                  ))}
                  <AddButton onClick={() => addListItem('highlights')}>Add Highlight</AddButton>
                </div>

                {/* Itinerary */}
                <div style={{ marginBottom: 28 }}>
                  <label style={{ ...labelStyle, fontSize: 15 }}>Day-wise Itinerary</label>
                  {form.itinerary.map((day, di) => (
                    <div key={di} style={{ ...boxStyle, padding: 18, marginBottom: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                        <span style={{ fontWeight: 800, color: 'var(--pc-brand-deep)', fontSize: 14 }}>Day {day.day}</span>
                        {form.itinerary.length > 1 && (
                          <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }} onClick={() => removeItineraryDay(di)}><Icon name="trash" size={14} />Remove Day</button>
                        )}
                      </div>
                      <input className="input-field" style={{ marginBottom: 8 }} value={day.title} onChange={e => updateItinerary(di, 'title', e.target.value)} placeholder="Day title (e.g., Arrival & Temple Visit)" />
                      <textarea className="input-field" style={{ minHeight: 50, marginBottom: 8 }} value={day.description} onChange={e => updateItinerary(di, 'description', e.target.value)} placeholder="Day description..." />
                      <label style={{ fontSize: 12, color: 'var(--pc-ink-2)', fontWeight: 700, marginBottom: 4, display: 'block' }}>Activities</label>
                      {day.activities.map((a, ai) => (
                        <div key={ai} style={{ display: 'flex', gap: 6, marginBottom: 4 }}>
                          <input className="input-field" style={{ flex: 1 }} value={a} onChange={e => updateActivity(di, ai, e.target.value)} placeholder={`Activity ${ai + 1}`} />
                          {day.activities.length > 1 && <RemoveButton label={`Remove activity ${ai + 1} from day ${day.day}`} onClick={() => removeActivity(di, ai)} />}
                        </div>
                      ))}
                      <AddButton onClick={() => addActivity(di)}>Activity</AddButton>
                    </div>
                  ))}
                  <AddButton onClick={addItineraryDay}>Add Day</AddButton>
                </div>

                {/* Actions */}
                <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                  <button type="button" className="pc-btn pc-btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
                  <button type="submit" className="pc-btn" disabled={saving}>
                    <Icon name="check" />{saving ? 'Saving...' : editId ? 'Update Package' : 'Create Package'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  )
}

function ToggleField({ icon, label, checked, onChange, detail, onDetailChange, detailPlaceholder }) {
  return (
    <div style={boxStyle}>
      <label style={{ ...checkLabelStyle, marginBottom: checked ? 8 : 0 }}>
        <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} />
        {icon && <Icon name={icon} size={16} />}
        <span style={{ fontSize: 13, fontWeight: 700 }}>{label}</span>
      </label>
      {checked && (
        <input className="input-field" value={detail || ''} onChange={e => onDetailChange(e.target.value)} placeholder={detailPlaceholder} aria-label={`${label} details`} />
      )}
    </div>
  )
}

function AddButton({ onClick, children }) {
  return (
    <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ marginTop: 4 }} onClick={onClick}>
      <Icon name="plus" size={14} />{children}
    </button>
  )
}

function RemoveButton({ label, onClick }) {
  return (
    <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)', flex: 'none' }} onClick={onClick} aria-label={label} title={label}>
      <Icon name="x" size={15} />
    </button>
  )
}

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const boxStyle = { padding: 14, background: 'var(--pc-wash)', borderRadius: 14 }
const checkLabelStyle = { display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', color: 'var(--pc-ink)' }
