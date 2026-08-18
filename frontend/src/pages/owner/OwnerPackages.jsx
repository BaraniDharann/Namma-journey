import React, { useState, useEffect } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { useAuth } from '../../context/AuthContext'
import { getOwnerPackages, createPackage, updatePackage, togglePackage, deletePackage } from '../../utils/api'

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
  { path: '/owner/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '📋', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '🚗', label: 'Drivers' },
  { path: '/owner/payments', icon: '💳', label: 'Payments' },
  { path: '/owner/reviews', icon: '⭐', label: 'Reviews' },
  { path: '/owner/packages', icon: '📦', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '🎫', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '📊', label: 'Revenue' },
  { path: '/owner/profile', icon: '👤', label: 'Profile' },
]

export default function OwnerPackages() {
  const { user } = useAuth()
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
  const getCategoryColor = (cat) => {
    const colors = { TEMPLE: '#f59e0b', HONEYMOON: '#ec4899', ADVENTURE: '#10b981', HILL_STATION: '#6366f1', BEACH: '#06b6d4', HERITAGE: '#8b5cf6', WILDLIFE: '#84cc16', PILGRIMAGE: '#f97316', FAMILY: '#3b82f6', STATE_SPECIAL: '#ef4444' }
    return colors[cat] || '#6b7280'
  }

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div style={{ padding: '24px', maxWidth: 1200, margin: '0 auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <div>
            <h1 style={{ fontFamily: 'Poppins', fontSize: 26, fontWeight: 900, color: '#0F172A' }}>Travel Packages</h1>
            <p style={{ color: '#64748b', marginTop: 4, fontSize: 14 }}>Create and manage travel packages for your customers</p>
          </div>
          <button onClick={openCreate} style={{ padding: '10px 24px', background: 'linear-gradient(135deg, #8b5cf6, #f97316)', color: '#fff', border: 'none', borderRadius: 14, fontWeight: 700, cursor: 'pointer', fontSize: 14, boxShadow: '0 4px 16px rgba(139,92,246,0.3)' }}>
            + Create Package
          </button>
        </div>

        {/* Package Cards Grid */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: 60 }}><div className="spinner" /></div>
        ) : packages.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 60, background: '#f8fafc', borderRadius: 20 }}>
            <p style={{ fontSize: 48 }}>📦</p>
            <p style={{ color: '#64748b', marginTop: 8 }}>No packages yet. Create your first travel package!</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 20 }}>
            {paginatedItems.map(pkg => (
              <div key={pkg.id} style={{ background: '#fff', borderRadius: 20, overflow: 'hidden', boxShadow: '0 4px 20px rgba(0,0,0,0.05)', border: '1px solid #e2e8f0', opacity: pkg.active ? 1 : 0.6, transition: 'all 0.2s' }}>
                <div style={{ height: 160, background: pkg.imageUrl ? `url(${pkg.imageUrl}) center/cover` : `linear-gradient(135deg, ${getCategoryColor(pkg.category)}33, ${getCategoryColor(pkg.category)}11)`, display: 'flex', alignItems: 'flex-end', padding: 16, position: 'relative' }}>
                  {!pkg.imageUrl && <span style={{ fontSize: 48, position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', opacity: 0.3 }}>{pkg.category === 'TEMPLE' ? '🛕' : pkg.category === 'HONEYMOON' ? '💑' : pkg.category === 'BEACH' ? '🏖️' : pkg.category === 'ADVENTURE' ? '🏔️' : '✈️'}</span>}
                  <div style={{ display: 'flex', gap: 6 }}>
                    <span style={{ padding: '4px 12px', background: getCategoryColor(pkg.category), color: '#fff', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{getCategoryLabel(pkg.category)}</span>
                    <span style={{ padding: '4px 12px', background: 'rgba(255,255,255,0.92)', borderRadius: 20, fontSize: 11, fontWeight: 600, color: '#475569' }}>{pkg.state}</span>
                  </div>
                </div>
                <div style={{ padding: 18 }}>
                  <h3 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', marginBottom: 6 }}>{pkg.name}</h3>
                  <p style={{ fontSize: 13, color: '#64748b', marginBottom: 12, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{pkg.description}</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
                    {pkg.foodIncluded && <span style={{ padding: '3px 10px', background: '#dcfce7', color: '#166534', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>🍽️ Food</span>}
                    {pkg.accommodationIncluded && <span style={{ padding: '3px 10px', background: '#dbeafe', color: '#1e40af', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>🏨 Stay</span>}
                    {pkg.transportIncluded && <span style={{ padding: '3px 10px', background: '#FEF3C7', color: '#92400e', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>🚗 Transport</span>}
                    {pkg.tollFree && <span style={{ padding: '3px 10px', background: '#f3e8ff', color: '#8b5cf6', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>🛣️ Toll Free</span>}
                    {pkg.guideIncluded && <span style={{ padding: '3px 10px', background: '#fce7f3', color: '#be185d', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>🗣️ Guide</span>}
                    {pkg.sightseeingIncluded && <span style={{ padding: '3px 10px', background: '#ccfbf1', color: '#0f766e', borderRadius: 20, fontSize: 11, fontWeight: 500 }}>📸 Sightseeing</span>}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <div>
                      <span style={{ fontSize: 22, fontWeight: 800, color: '#8b5cf6' }}>₹{pkg.pricePerPerson?.toLocaleString()}</span>
                      <span style={{ fontSize: 12, color: '#94a3b8' }}> / person</span>
                    </div>
                    <span style={{ fontSize: 12, color: '#64748b', background: '#f1f5f9', padding: '3px 10px', borderRadius: 20 }}>{pkg.durationDays}D / {pkg.durationNights}N</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748b', marginBottom: 14 }}>
                    📍 {pkg.placesIncluded?.join(', ')} &nbsp;|&nbsp; 👥 Max {pkg.maxGroupSize} &nbsp;|&nbsp; 📋 {pkg.totalBookings || 0} bookings
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={() => openEdit(pkg)} style={{ flex: 1, padding: '9px 0', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, fontSize: 13, fontWeight: 600, color: '#475569', cursor: 'pointer' }}>Edit</button>
                    <button onClick={() => handleToggle(pkg.id)} style={{ flex: 1, padding: '9px 0', background: pkg.active ? '#FEF3C7' : '#dcfce7', border: 'none', borderRadius: 12, fontSize: 13, fontWeight: 600, color: pkg.active ? '#92400e' : '#166534', cursor: 'pointer' }}>{pkg.active ? 'Deactivate' : 'Activate'}</button>
                    <button onClick={() => handleDelete(pkg.id)} style={{ padding: '9px 14px', background: '#fee2e2', border: 'none', borderRadius: 12, fontSize: 13, fontWeight: 600, color: '#dc2626', cursor: 'pointer' }}>🗑️</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        {packages.length > 0 && <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />}

        {/* Create/Edit Modal */}
        {showForm && (
          <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={() => setShowForm(false)}>
            <div style={{ background: '#fff', borderRadius: 24, maxWidth: 800, width: '100%', maxHeight: '90vh', overflow: 'auto', padding: 0 }} onClick={e => e.stopPropagation()}>
              <div style={{ background: 'linear-gradient(135deg, #0F172A, #1e293b)', padding: '24px 32px', borderRadius: '24px 24px 0 0' }}>
                <h2 style={{ fontSize: 22, fontWeight: 800, color: '#fff' }}>{editId ? 'Edit Package' : 'Create New Package'}</h2>
              </div>
              <div style={{ padding: 32 }}>
                {error && <div style={{ padding: 14, background: '#fee2e2', color: '#dc2626', borderRadius: 12, marginBottom: 16, fontSize: 13 }}>{error}</div>}
                <form onSubmit={handleSubmit}>
                  {/* Basic Info */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
                    <div style={{ gridColumn: 'span 2' }}>
                      <label style={labelStyle}>Package Name *</label>
                      <input required style={inputStyle} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g., Kerala Backwaters Premium" />
                    </div>
                    <div style={{ gridColumn: 'span 2' }}>
                      <label style={labelStyle}>Description</label>
                      <textarea style={{ ...inputStyle, minHeight: 80 }} value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="Describe the package experience..." />
                    </div>
                    <div>
                      <label style={labelStyle}>Category *</label>
                      <select required style={inputStyle} value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))}>
                        {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                      </select>
                    </div>
                    <div>
                      <label style={labelStyle}>State *</label>
                      <select required style={inputStyle} value={form.state} onChange={e => setForm(f => ({ ...f, state: e.target.value }))}>
                        <option value="">Select State</option>
                        {INDIAN_STATES.map(s => <option key={s} value={s}>{s}</option>)}
                      </select>
                    </div>
                    <div>
                      <label style={labelStyle}>Duration Days *</label>
                      <input required type="number" min="1" style={inputStyle} value={form.durationDays} onChange={e => setForm(f => ({ ...f, durationDays: +e.target.value }))} />
                    </div>
                    <div>
                      <label style={labelStyle}>Duration Nights *</label>
                      <input required type="number" min="0" style={inputStyle} value={form.durationNights} onChange={e => setForm(f => ({ ...f, durationNights: +e.target.value }))} />
                    </div>
                    <div>
                      <label style={labelStyle}>Price Per Person (₹) *</label>
                      <input required type="number" min="1" style={inputStyle} value={form.pricePerPerson} onChange={e => setForm(f => ({ ...f, pricePerPerson: +e.target.value }))} placeholder="e.g., 5999" />
                    </div>
                    <div>
                      <label style={labelStyle}>Max Group Size *</label>
                      <input required type="number" min="1" style={inputStyle} value={form.maxGroupSize} onChange={e => setForm(f => ({ ...f, maxGroupSize: +e.target.value }))} />
                    </div>
                    <div style={{ gridColumn: 'span 2' }}>
                      <label style={labelStyle}>Image URL</label>
                      <input style={inputStyle} value={form.imageUrl} onChange={e => setForm(f => ({ ...f, imageUrl: e.target.value }))} placeholder="https://example.com/image.jpg" />
                    </div>
                  </div>

                  {/* Places Included */}
                  <div style={{ marginBottom: 24 }}>
                    <label style={labelStyle}>Places Included *</label>
                    {form.placesIncluded.map((p, i) => (
                      <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
                        <input style={{ ...inputStyle, flex: 1 }} value={p} onChange={e => updateListItem('placesIncluded', i, e.target.value)} placeholder={`Place ${i + 1}`} />
                        {form.placesIncluded.length > 1 && <button type="button" onClick={() => removeListItem('placesIncluded', i)} style={removeBtn}>×</button>}
                      </div>
                    ))}
                    <button type="button" onClick={() => addListItem('placesIncluded')} style={addBtn}>+ Add Place</button>
                  </div>

                  {/* Inclusions */}
                  <div style={{ marginBottom: 24 }}>
                    <label style={{ ...labelStyle, fontSize: 15, marginBottom: 12 }}>Inclusions</label>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                      <ToggleField label="🍽️ Food Included" checked={form.foodIncluded} onChange={v => setForm(f => ({ ...f, foodIncluded: v }))} detail={form.foodDetails} onDetailChange={v => setForm(f => ({ ...f, foodDetails: v }))} detailPlaceholder="e.g., Breakfast & Dinner included" />
                      <ToggleField label="🏨 Accommodation" checked={form.accommodationIncluded} onChange={v => setForm(f => ({ ...f, accommodationIncluded: v }))} detail={form.accommodationDetails} onDetailChange={v => setForm(f => ({ ...f, accommodationDetails: v }))} detailPlaceholder="e.g., 3-star hotel, AC rooms" />
                      <ToggleField label="🚗 Transport" checked={form.transportIncluded} onChange={v => setForm(f => ({ ...f, transportIncluded: v }))} detail={form.transportDetails} onDetailChange={v => setForm(f => ({ ...f, transportDetails: v }))} detailPlaceholder="e.g., AC car, pickup & drop" />
                      <div style={{ padding: 14, background: '#f8fafc', borderRadius: 12, border: '1px solid #e2e8f0' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                          <input type="checkbox" checked={form.tollFree} onChange={e => setForm(f => ({ ...f, tollFree: e.target.checked }))} />
                          <span style={{ fontSize: 13, fontWeight: 600 }}>🛣️ Toll Free</span>
                        </label>
                      </div>
                      <div style={{ padding: 14, background: '#f8fafc', borderRadius: 12, border: '1px solid #e2e8f0' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                          <input type="checkbox" checked={form.guideIncluded} onChange={e => setForm(f => ({ ...f, guideIncluded: e.target.checked }))} />
                          <span style={{ fontSize: 13, fontWeight: 600 }}>🗣️ Guide Included</span>
                        </label>
                      </div>
                      <div style={{ padding: 14, background: '#f8fafc', borderRadius: 12, border: '1px solid #e2e8f0' }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                          <input type="checkbox" checked={form.sightseeingIncluded} onChange={e => setForm(f => ({ ...f, sightseeingIncluded: e.target.checked }))} />
                          <span style={{ fontSize: 13, fontWeight: 600 }}>📸 Sightseeing</span>
                        </label>
                      </div>
                    </div>
                  </div>

                  {/* Highlights */}
                  <div style={{ marginBottom: 24 }}>
                    <label style={labelStyle}>Package Highlights</label>
                    {form.highlights.map((h, i) => (
                      <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
                        <input style={{ ...inputStyle, flex: 1 }} value={h} onChange={e => updateListItem('highlights', i, e.target.value)} placeholder={`Highlight ${i + 1}`} />
                        {form.highlights.length > 1 && <button type="button" onClick={() => removeListItem('highlights', i)} style={removeBtn}>×</button>}
                      </div>
                    ))}
                    <button type="button" onClick={() => addListItem('highlights')} style={addBtn}>+ Add Highlight</button>
                  </div>

                  {/* Itinerary */}
                  <div style={{ marginBottom: 28 }}>
                    <label style={{ ...labelStyle, fontSize: 15 }}>Day-wise Itinerary</label>
                    {form.itinerary.map((day, di) => (
                      <div key={di} style={{ padding: 18, background: '#f8fafc', borderRadius: 14, marginBottom: 12, border: '1px solid #e2e8f0' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                          <span style={{ fontWeight: 700, color: '#8b5cf6', fontSize: 14 }}>Day {day.day}</span>
                          {form.itinerary.length > 1 && <button type="button" onClick={() => removeItineraryDay(di)} style={{ ...removeBtn, fontSize: 12 }}>Remove Day</button>}
                        </div>
                        <input style={{ ...inputStyle, marginBottom: 8 }} value={day.title} onChange={e => updateItinerary(di, 'title', e.target.value)} placeholder="Day title (e.g., Arrival & Temple Visit)" />
                        <textarea style={{ ...inputStyle, minHeight: 50, marginBottom: 8 }} value={day.description} onChange={e => updateItinerary(di, 'description', e.target.value)} placeholder="Day description..." />
                        <label style={{ fontSize: 12, color: '#64748b', marginBottom: 4, display: 'block' }}>Activities</label>
                        {day.activities.map((a, ai) => (
                          <div key={ai} style={{ display: 'flex', gap: 6, marginBottom: 4 }}>
                            <input style={{ ...inputStyle, flex: 1, padding: '6px 10px', fontSize: 12 }} value={a} onChange={e => updateActivity(di, ai, e.target.value)} placeholder={`Activity ${ai + 1}`} />
                            {day.activities.length > 1 && <button type="button" onClick={() => removeActivity(di, ai)} style={{ ...removeBtn, padding: '4px 8px', fontSize: 11 }}>×</button>}
                          </div>
                        ))}
                        <button type="button" onClick={() => addActivity(di)} style={{ ...addBtn, fontSize: 11, padding: '4px 10px' }}>+ Activity</button>
                      </div>
                    ))}
                    <button type="button" onClick={addItineraryDay} style={addBtn}>+ Add Day</button>
                  </div>

                  {/* Actions */}
                  <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                    <button type="button" onClick={() => setShowForm(false)} style={{ padding: '11px 24px', background: '#f1f5f9', border: '1px solid #e2e8f0', borderRadius: 12, fontWeight: 600, cursor: 'pointer', color: '#475569' }}>Cancel</button>
                    <button type="submit" disabled={saving} style={{ padding: '11px 32px', background: 'linear-gradient(135deg, #8b5cf6, #f97316)', color: '#fff', border: 'none', borderRadius: 12, fontWeight: 700, cursor: 'pointer', opacity: saving ? 0.6 : 1, boxShadow: '0 4px 14px rgba(139,92,246,0.3)' }}>
                      {saving ? 'Saving...' : editId ? 'Update Package' : 'Create Package'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  )
}

function ToggleField({ label, checked, onChange, detail, onDetailChange, detailPlaceholder }) {
  return (
    <div style={{ padding: 14, background: '#f8fafc', borderRadius: 12, border: '1px solid #e2e8f0' }}>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', marginBottom: checked ? 8 : 0 }}>
        <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} />
        <span style={{ fontSize: 13, fontWeight: 600 }}>{label}</span>
      </label>
      {checked && (
        <input style={{ ...inputStyle, padding: '6px 10px', fontSize: 12 }} value={detail || ''} onChange={e => onDetailChange(e.target.value)} placeholder={detailPlaceholder} />
      )}
    </div>
  )
}

const labelStyle = { display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }
const inputStyle = { width: '100%', padding: '10px 14px', borderRadius: 12, border: '1px solid #e2e8f0', fontSize: 14, outline: 'none', boxSizing: 'border-box', background: '#fff' }
const addBtn = { padding: '6px 14px', background: '#f8fafc', border: '2px dashed #d8b4fe', borderRadius: 10, fontSize: 12, color: '#8b5cf6', cursor: 'pointer', fontWeight: 600 }
const removeBtn = { padding: '6px 12px', background: '#fee2e2', border: 'none', borderRadius: 10, fontSize: 14, color: '#dc2626', cursor: 'pointer', fontWeight: 700 }
