import React, { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../components/dash/ui'
import { SeatDecks, SeatLegend } from '../../components/bus/SeatDeck'
import { BusPill, Notice, dangerBtn } from '../../components/bus/busUi'
import { adminBus, adminApproveBus, adminRejectBus } from '../../utils/busApi'
import { seatClassName, seatClassesOf, seatClassOf } from '../../utils/busLayouts'
import { BUS_STATUS, DOC_TYPES, PERMIT_TYPES, daysUntil, uploadUrl } from '../../utils/busText'
import { rejectReasonError, REJECT_MAX } from '../../utils/rejectReason'
import { adminNavItems, errorText, when } from './adminNav'

const label = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }

function Row({ k, v }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '6px 0', borderTop: '1px solid var(--pc-line)' }}>
      <span style={{ color: 'var(--pc-muted)', fontSize: 13 }}>{k}</span>
      <span style={{ fontSize: 13, fontWeight: 600, textAlign: 'right', overflowWrap: 'anywhere' }}>{v || '—'}</span>
    </div>
  )
}

/** One bus: papers, photos and seat layout, then approve or reject with a reason. */
export default function AdminBusDetail() {
  const { id } = useParams()
  const [bus, setBus] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const load = useCallback(async () => {
    try { setBus((await adminBus(id)).data); setError('') } catch (e) { setError(errorText(e, 'Could not load this bus.')) }
  }, [id])
  useEffect(() => { load() }, [load])

  const act = async (fn, ok) => {
    setBusy(true); setNotice(null)
    try { setBus((await fn()).data); setNotice({ tone: 'ok', text: ok }); setRejecting(false) }
    catch (e) { setNotice({ tone: 'error', text: errorText(e, 'Could not save that decision.') }) }
    finally { setBusy(false) }
  }

  const approve = () => act(() => adminApproveBus(id), 'Approved. The owner can now put this bus on a route.')
  const reject = () => act(() => adminRejectBus(id, reason.trim()), 'Rejected. The owner sees the reason and can send the bus again.')
  const reasonProblem = rejectReasonError(reason)

  return (
    <DashboardLayout navItems={adminNavItems} role="ROLE_ADMIN">
      <p style={{ marginBottom: 8 }}><Link to="/admin/bus-approvals">← All buses</Link></p>
      {!bus ? (
        error ? <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div>
          : <Panel span={12}><Skeleton rows={6} height={18} /></Panel>
      ) : (
        <>
          <PageHead title={`${bus.registrationNumber} · ${bus.name}`}
            sub={`${bus.travelsName || 'Owner'}${bus.ownerCode ? ` · ${bus.ownerCode}` : ''}${bus.submittedAt ? ` · sent ${when(bus.submittedAt)}` : ''}`}>
            <BusPill table={BUS_STATUS} status={bus.status} />
          </PageHead>
          <Notice notice={notice} onClose={() => setNotice(null)} />

          <div className="pc-grid">
            <Panel span={5} title="Bus">
              <Row k="Type" v={bus.typeLabel || (bus.ac ? 'A/C' : 'Non-A/C')} />
              <Row k="Permit" v={PERMIT_TYPES.find(([v]) => v === bus.permitType)?.[1] || bus.permitType} />
              <Row k="Seats" v={`${(bus.seats || []).length}`} />
              {seatClassesOf(bus.seats).map((c) => <Row key={c} k={seatClassName(c)} v={`${bus.seats.filter((s) => seatClassOf(s) === c).length}`} />)}
              <Row k="Women-only seats" v={`${(bus.seats || []).filter((s) => s.ladiesOnly).length}`} />
              <Row k="Amenities" v={(bus.amenities || []).join(', ')} />
              {bus.rejectionReason && <Row k="Rejected because" v={bus.rejectionReason} />}
            </Panel>

            <Panel span={7} title="Documents" meta="Number and dates must match the bus">
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 12 }}>
                {DOC_TYPES.map(([type, name]) => {
                  const doc = (bus.documents || []).find((d) => d.type === type)
                  const days = doc?.expiresOn ? daysUntil(doc.expiresOn) : null
                  return (
                    <figure key={type} style={{ margin: 0 }}>
                      <figcaption style={label}>{name}</figcaption>
                      {doc?.path ? (
                        <>
                          <a href={uploadUrl(doc.path)} target="_blank" rel="noopener noreferrer">
                            <img src={uploadUrl(doc.path)} alt={name} style={{ width: '100%', maxHeight: 200, objectFit: 'contain', borderRadius: 8, background: 'var(--pc-wash)' }} />
                          </a>
                          <div style={{ fontSize: 12.5, marginTop: 4, color: days != null && days < 0 ? 'var(--pc-bad)' : 'var(--pc-ink-2)' }}>
                            Valid till {doc.expiresOn || '—'}{days != null && days < 0 ? ' (expired)' : ''}
                          </div>
                        </>
                      ) : <p style={{ fontSize: 13 }}>Not uploaded</p>}
                    </figure>
                  )
                })}
              </div>
            </Panel>

            <Panel span={12} title="Photos" meta={`${(bus.photos || []).length}`}>
              {!(bus.photos || []).length ? <p style={{ fontSize: 13 }}>No photos.</p> : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(180px,1fr))', gap: 12 }}>
                  {[...bus.photos].sort((a, b) => a.position - b.position).map((p) => (
                    <a key={p.id} href={uploadUrl(p.path)} target="_blank" rel="noopener noreferrer">
                      <img src={uploadUrl(p.path)} alt="Bus" style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 10, background: 'var(--pc-wash)' }} />
                    </a>
                  ))}
                </div>
              )}
            </Panel>

            <Panel span={12} title="Seat layout">
              {(bus.seats || []).length
                ? <><SeatDecks seats={bus.seats} stateOf={(s) => (s.ladiesOnly ? 'FEMALE_ONLY' : 'AVAILABLE')} /><SeatLegend states={['AVAILABLE', 'FEMALE_ONLY']} /></>
                : <p style={{ fontSize: 13 }}>No seats yet.</p>}
            </Panel>

            {bus.status === 'PENDING' && (
              <Panel span={12} title="Decision">
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <button type="button" className="pc-btn" disabled={busy} onClick={approve}>{busy ? 'Saving…' : 'Approve bus'}</button>
                  <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={() => setRejecting(true)}>Reject</button>
                </div>
                {rejecting && (
                  <div style={{ marginTop: 14 }}>
                    <label htmlFor="bus-reject-reason" style={label}>Reason the owner will see</label>
                    <textarea id="bus-reject-reason" className="input-field" rows={3} value={reason} maxLength={REJECT_MAX}
                      onChange={(e) => setReason(e.target.value)} style={{ width: '100%', resize: 'vertical', marginTop: 6 }} />
                    <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                      <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={() => setRejecting(false)}>Cancel</button>
                      <button type="button" className="pc-btn" style={dangerBtn} disabled={busy || !!reasonProblem} onClick={reject}>Reject bus</button>
                    </div>
                  </div>
                )}
              </Panel>
            )}
          </div>
        </>
      )}
    </DashboardLayout>
  )
}
