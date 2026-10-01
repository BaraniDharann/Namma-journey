import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import Pagination, { usePagination } from '../../components/Pagination'
import { getOwnerBookings, assignDriver, getDriverTripPhoto } from '../../utils/api'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { useCelebrate } from '../../components/celebrate/Celebration'

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

const FILTERS = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const FILTER_LABEL = { ALL: 'All', PENDING: 'Pending', CONFIRMED: 'Confirmed', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }

const muted = { fontSize: 12, color: 'var(--pc-muted)' }

function ModalHead({ id, title, sub }) {
  return (
    <div style={{ padding: '22px 24px 14px', borderBottom: '1px solid var(--pc-line)' }}>
      <h3 id={id} style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', marginBottom: 4 }}>{title}</h3>
      <p style={{ fontSize: 13, color: 'var(--pc-ink-2)' }}>{sub}</p>
    </div>
  )
}

function AssignModal({ booking, onClose, onAssign }) {
  const [driverId, setDriverId] = useState('')
  const [loading, setLoading] = useState(false)
  const handleAssign = async () => {
    if (!driverId) return
    setLoading(true)
    await onAssign(booking.id, Number(driverId))
    setLoading(false)
    onClose()
  }
  return (
    <div className="modal-overlay">
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="assign-driver-title" style={{ overflow: 'hidden', padding: 0 }}>
        <ModalHead id="assign-driver-title" title="Assign Driver" sub={`${booking.fromPlace} → ${booking.toPlace}`} />
        <div style={{ padding: 24 }}>
          <div style={{ marginBottom: 20 }}>
            <label htmlFor="assign-driver-id" style={{ display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }}>Driver ID</label>
            <input id="assign-driver-id" className="input-field" placeholder="Enter driver ID" value={driverId} onChange={e => setDriverId(e.target.value)} type="number" />
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ flex: 1 }}>Cancel</button>
            <button type="button" onClick={handleAssign} disabled={loading || !driverId} className="pc-btn" style={{ flex: 1 }}>
              <Icon name="user" />{loading ? 'Assigning...' : 'Assign Driver'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function DriverPhotoModal({ bookingId, onClose }) {
  const [photo, setPhoto] = React.useState(null)
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState('')

  React.useEffect(() => {
    getDriverTripPhoto(bookingId)
      .then(res => setPhoto(res.data))
      .catch(() => setError('No driver photo available for this trip'))
      .finally(() => setLoading(false))
  }, [bookingId])

  return (
    <div className="modal-overlay">
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="driver-photo-title" style={{ maxWidth: 420, overflow: 'hidden', padding: 0 }}>
        <ModalHead id="driver-photo-title" title="Driver Trip Photo" sub="Review driver's appearance & professionalism" />
        <div style={{ padding: 24 }}>
          {loading ? (
            <div style={{ padding: '16px 0' }}><Skeleton rows={4} height={18} /></div>
          ) : error ? (
            <div style={{ marginBottom: 16 }}>
              <Empty icon="camera" title="No photo">{error}</Empty>
            </div>
          ) : (
            <div style={{ marginBottom: 16 }}>
              <img
                src={photo.photoPath}
                alt="Driver end-trip photo"
                style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 14, border: '1px solid var(--pc-line)', marginBottom: 10 }}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, ...muted, padding: '0 4px' }}>
                <span>Driver ID: #{photo.driverId}</span>
                <span>{new Date(photo.capturedAt).toLocaleString()}</span>
              </div>
            </div>
          )}
          <button type="button" onClick={onClose} className="pc-btn pc-btn-ghost" style={{ width: '100%' }}>Close</button>
        </div>
      </div>
    </div>
  )
}

export default function OwnerBookings() {
  const celebrate = useCelebrate()
  const [bookings, setBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [assignBooking, setAssignBooking] = useState(null)
  const [search, setSearch] = useState('')
  const [photoBookingId, setPhotoBookingId] = useState(null)

  const load = async () => {
    try {
      const res = await getOwnerBookings()
      setBookings(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const filtered = bookings
    .filter(b => filter === 'ALL' || b.status === filter)
    .filter(b => !search || b.userName?.toLowerCase().includes(search.toLowerCase()) || b.fromPlace?.toLowerCase().includes(search.toLowerCase()) || b.toPlace?.toLowerCase().includes(search.toLowerCase()))

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 10)

  useEffect(() => { setCurrentPage(1) }, [search])

  const handleAssign = async (bookingId, driverId) => {
    try {
      await assignDriver(bookingId, driverId)
      load()
      celebrate({ title: 'Driver assigned', message: `Driver #${driverId} is on this trip. The traveller will see it in their bookings.` })
    } catch { /* ignore */ }
  }

  const waiting = bookings.filter(b => b.status === 'PENDING' && !b.driverId).length

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      {assignBooking && <AssignModal booking={assignBooking} onClose={() => setAssignBooking(null)} onAssign={handleAssign} />}
      {photoBookingId && <DriverPhotoModal bookingId={photoBookingId} onClose={() => setPhotoBookingId(null)} />}

      <PageHead
        title="All Bookings"
        sub={`${bookings.length} total bookings${waiting ? ` · ${waiting} waiting for a driver` : ''}`}
      />

      <div className="pc-grid">
        <Panel span={12}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
            <div style={{ flex: 1, minWidth: 200, position: 'relative' }}>
              <span aria-hidden="true" style={{ position: 'absolute', left: 13, top: '50%', transform: 'translateY(-50%)', color: 'var(--pc-muted)', display: 'flex' }}><Icon name="search" size={16} /></span>
              <input className="input-field" aria-label="Search bookings" style={{ width: '100%', paddingLeft: 38 }} placeholder="Search by customer, route..." value={search} onChange={e => setSearch(e.target.value)} />
            </div>
            <div className="pc-chips" role="group" aria-label="Filter by status">
              {FILTERS.map(f => (
                <button
                  key={f}
                  type="button"
                  aria-pressed={filter === f}
                  onClick={() => { setFilter(f); setCurrentPage(1) }}
                  className="pc-chip"
                  style={{ border: 'none', cursor: 'pointer', ...(filter === f ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}
                >{FILTER_LABEL[f]}</button>
              ))}
            </div>
          </div>
        </Panel>

        <Panel span={12} pad={false} title={filter === 'ALL' ? 'Bookings' : `${FILTER_LABEL[filter]} bookings`} meta={loading ? null : `${filtered.length} shown`}>
          {loading ? <div style={{ padding: '0 20px 20px' }}><Skeleton rows={6} /></div>
            : filtered.length === 0 ? (
              <Empty icon="calendar" title="No bookings found">
                {search || filter !== 'ALL' ? 'Try a different search or status filter.' : 'New bookings will show up here as soon as travellers make them.'}
              </Empty>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table">
                  <thead><tr><th>Customer</th><th>Route</th><th>Date</th><th>Type</th><th style={{ textAlign: 'right' }}>Amount</th><th>Driver</th><th>Photo</th><th>Status</th><th>Action</th></tr></thead>
                  <tbody>
                    {paginatedItems.map(b => (
                      // GET /owner/bookings returns the raw TravelBooking entity (id), while the
                      // assign-driver response is a TravelBookingResponse (bookingId). Use either.
                      <tr key={b.id || b.bookingId}>
                        <td><div className="pc-row-title">{b.userName}</div><div style={muted}>{b.userPhone}</div></td>
                        <td><div style={{ fontSize: 13, color: 'var(--pc-ink)' }}>{b.fromPlace}</div><div style={muted}>→ {b.toPlace}</div></td>
                        <td style={{ whiteSpace: 'nowrap' }}>{b.fromDate}</td>
                        <td>{b.bookingType === 'HOUR_BASED'
                          ? <span className="pc-chip" style={{ whiteSpace: 'nowrap' }}><Icon name="clock" size={13} />{b.bookingHours}h</span>
                          : <span className="pc-chip" style={{ whiteSpace: 'nowrap' }}><Icon name="route" size={13} />KM</span>}</td>
                        <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--pc-ink)', whiteSpace: 'nowrap' }}>{inr(b.totalAmount)}</td>
                        <td>{b.driverId ? `#${b.driverId}` : <span style={{ color: 'var(--pc-muted)' }}>Unassigned</span>}</td>
                        <td>{(b.status === 'STARTED' || b.status === 'COMPLETED') && b.driverId ? (
                          <button type="button" onClick={() => setPhotoBookingId(b.bookingId || b.id)} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="camera" size={14} />View</button>
                        ) : <span style={{ color: 'var(--pc-muted)' }}>—</span>}</td>
                        <td><StatusPill status={b.status} /></td>
                        <td>{b.status === 'PENDING' && !b.driverId && (
                          <button type="button" onClick={() => setAssignBooking(b)} className="pc-btn pc-btn-sm">Assign</button>
                        )}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Pagination currentPage={currentPage} totalPages={totalPages} onPageChange={setCurrentPage} />
              </div>
            )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
