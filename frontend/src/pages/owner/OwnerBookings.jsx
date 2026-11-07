import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import Pagination, { usePagination } from '../../components/Pagination'
import { getOwnerBookings, getCityRides, getDriverTripPhoto } from '../../utils/api'
import useOwnerMe from '../../hooks/useOwnerMe'
import { categoryLabel } from '../../utils/carCategories'
import AssignDriverModal from '../../components/owner/AssignDriverModal'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { inr } from '../../dash/metrics'
import { useCelebrate } from '../../components/celebrate/Celebration'

const FILTERS = ['ALL', 'PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED']
const FILTER_LABEL = { ALL: 'All', PENDING: 'Pending', CONFIRMED: 'Confirmed', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }

const muted = { fontSize: 12, color: 'var(--pc-muted)' }
// A MANUAL owner's new Trip, or one AUTO found no free driver and car for.
const needsDriver = (b) => b.status === 'PENDING' && !b.driverId

function ModalHead({ id, title, sub }) {
  return (
    <div style={{ padding: '22px 24px 14px', borderBottom: '1px solid var(--pc-line)' }}>
      <h3 id={id} style={{ fontWeight: 700, fontSize: 18, color: 'var(--pc-ink)', marginBottom: 4 }}>{title}</h3>
      <p style={{ fontSize: 13, color: 'var(--pc-ink-2)' }}>{sub}</p>
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
  const [needsDriverOnly, setNeedsDriverOnly] = useState(false)
  const [assignBooking, setAssignBooking] = useState(null)
  const [search, setSearch] = useState('')
  const [photoBookingId, setPhotoBookingId] = useState(null)

  // The platform owner can also look at City rides (read-only here: independent drivers take them).
  const me = useOwnerMe()
  const [scope, setScope] = useState('trips')

  const load = async () => {
    try {
      const res = scope === 'city' ? await getCityRides() : await getOwnerBookings()
      setBookings(res.data || [])
    } catch { /* ignore */ }
    setLoading(false)
  }

  useEffect(() => { setLoading(true); load() }, [scope]) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = bookings
    .filter(b => filter === 'ALL' || b.status === filter)
    .filter(b => !needsDriverOnly || needsDriver(b))
    .filter(b => !search || b.userName?.toLowerCase().includes(search.toLowerCase()) || b.fromPlace?.toLowerCase().includes(search.toLowerCase()) || b.toPlace?.toLowerCase().includes(search.toLowerCase()))

  const { currentPage, totalPages, paginatedItems, setCurrentPage } = usePagination(filtered, 10)

  useEffect(() => { setCurrentPage(1) }, [search])

  const handleAssigned = (_res, driver) => {
    setAssignBooking(null)
    load()
    celebrate({ title: 'Driver assigned', message: `${driver?.name || 'Your driver'} is on this trip. The traveller will see it in their bookings.` })
  }

  const waiting = bookings.filter(needsDriver).length

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      {assignBooking && <AssignDriverModal booking={assignBooking} onClose={() => setAssignBooking(null)} onAssigned={handleAssigned} />}
      {photoBookingId && <DriverPhotoModal bookingId={photoBookingId} onClose={() => setPhotoBookingId(null)} />}

      <PageHead
        title="All Bookings"
        sub={`${bookings.length} total bookings${waiting ? ` · ${waiting} waiting for a driver` : ''}`}
      />
      {me?.platform && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <button type="button" aria-pressed={scope === 'trips'} className={scope === 'trips' ? 'pc-btn pc-btn-sm' : 'pc-btn pc-btn-ghost pc-btn-sm'} onClick={() => setScope('trips')}>My Trips</button>
          <button type="button" aria-pressed={scope === 'city'} className={scope === 'city' ? 'pc-btn pc-btn-sm' : 'pc-btn pc-btn-ghost pc-btn-sm'} onClick={() => setScope('city')}>City rides</button>
        </div>
      )}

      <div className="pc-grid">
        <Panel span={12}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
            {/* A 320px basis puts the chips on their own line on phones, so the search gets the full width. */}
            <div style={{ flex: '1 1 320px', minWidth: 0, position: 'relative' }}>
              <span aria-hidden="true" style={{ position: 'absolute', left: 13, top: '50%', transform: 'translateY(-50%)', color: 'var(--pc-muted)', display: 'flex' }}><Icon name="search" size={16} /></span>
              <input className="input-field" aria-label="Search bookings" style={{ width: '100%', paddingLeft: 38 }} placeholder="Search by customer, route..." value={search} onChange={e => setSearch(e.target.value)} />
            </div>
            <button
              type="button"
              aria-pressed={needsDriverOnly}
              onClick={() => { setNeedsDriverOnly((on) => !on); setCurrentPage(1) }}
              className="pc-chip"
              style={{ border: 'none', cursor: 'pointer', ...(needsDriverOnly ? { background: 'var(--pc-brand-deep)', color: '#fff' } : {}) }}
            >Needs a driver{waiting ? ` (${waiting})` : ''}</button>
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
                {search || filter !== 'ALL' || needsDriverOnly ? 'Try a different search or status filter.' : scope === 'city' ? 'City rides will show up here as soon as travellers book them.' : (
                  <>New bookings will show up here as soon as travellers make them. Travellers can only book car types you&apos;ve priced. <Link to="/owner/pricing">Set your prices</Link></>
                )}
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
                        <td>
                          {b.bookingType === 'HOUR_BASED'
                            ? <span className="pc-chip" style={{ whiteSpace: 'nowrap' }}><Icon name="clock" size={13} />{b.bookingHours}h</span>
                            : <span className="pc-chip" style={{ whiteSpace: 'nowrap' }}><Icon name="route" size={13} />KM</span>}
                          {b.carCategory && <div style={{ ...muted, marginTop: 4, whiteSpace: 'nowrap' }}>{categoryLabel(b.carCategory)}</div>}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--pc-ink)', whiteSpace: 'nowrap' }}>{inr(b.totalAmount)}</td>
                        <td>{b.driverId ? `#${b.driverId}`
                          : needsDriver(b) ? <span className="pc-pill pc-pill-wait" style={{ whiteSpace: 'nowrap' }}>Needs a driver</span>
                          : <span style={{ color: 'var(--pc-muted)' }}>Unassigned</span>}</td>
                        <td>{(b.status === 'STARTED' || b.status === 'COMPLETED') && b.driverId ? (
                          <button type="button" onClick={() => setPhotoBookingId(b.bookingId || b.id)} className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="camera" size={14} />View</button>
                        ) : <span style={{ color: 'var(--pc-muted)' }}>—</span>}</td>
                        <td><StatusPill status={b.status} /></td>
                        <td>{b.status === 'PENDING' && b.advanceDue ? (
                          <span style={{ fontSize: 12, color: 'var(--pc-muted)', whiteSpace: 'nowrap' }}>Awaiting advance</span>
                        ) : scope === 'trips' && needsDriver(b) && (
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
