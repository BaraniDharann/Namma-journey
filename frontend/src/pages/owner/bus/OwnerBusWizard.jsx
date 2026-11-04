import React, { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../../components/dash/ui'
import SeatLayoutEditor from '../../../components/bus/SeatLayoutEditor'
import {
  BusPill, Notice, Steps, ownerNavItems, labelStyle, wellStyle, fieldGrid, mutedStyle,
} from '../../../components/bus/busUi'
import {
  getBus, createBus, updateBus, saveBusSeats, uploadBusDocument, uploadBusPhoto, deleteBusPhoto, submitBus,
} from '../../../utils/busApi'
import { cleanSeats, layoutProblems } from '../../../utils/busLayouts'
import {
  AMENITIES, BUS_STATUS, DOC_TYPES, PERMIT_TYPES, daysUntil, errorText, isoDate, uploadUrl,
} from '../../../utils/busText'

const STEPS = ['Details', 'Seat layout', 'Documents', 'Photos']
const EMPTY = { registrationNumber: '', name: '', ac: true, permitType: 'STAGE_CARRIAGE', amenities: [] }

const pickDetails = (b) => ({
  registrationNumber: b.registrationNumber || '', name: b.name || '', ac: !!b.ac,
  permitType: b.permitType || 'STAGE_CARRIAGE', amenities: b.amenities || [],
})

/** What still stops this bus from being sent for approval (the server checks the same). */
function submitBlockers(bus) {
  const out = []
  if (!(bus.seats || []).length) out.push('Save a seat layout.')
  const today = isoDate()
  for (const [type, name] of DOC_TYPES) {
    const doc = (bus.documents || []).find((d) => d.type === type)
    if (!doc) out.push(`Upload the ${name.toLowerCase()}.`)
    else if (!doc.expiresOn || doc.expiresOn <= today) out.push(`The ${name.toLowerCase()} has expired; upload the renewed one.`)
  }
  if (!(bus.photos || []).length) out.push('Add at least one photo.')
  return out
}

function DocumentRow({ type, name, doc, disabled, onUpload }) {
  const [file, setFile] = useState(null)
  const [expiresOn, setExpiresOn] = useState(doc?.expiresOn || '')
  const [busy, setBusy] = useState(false)
  const days = doc?.expiresOn ? daysUntil(doc.expiresOn) : null

  const send = async () => {
    setBusy(true)
    try { if (await onUpload(type, file, expiresOn)) setFile(null) } finally { setBusy(false) }
  }

  return (
    <div style={{ padding: '14px 0', borderTop: '1px solid var(--pc-line)', display: 'grid', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        <strong>{name}</strong>
        {doc ? (
          <span className={`pc-pill ${days < 0 ? 'pc-pill-stop' : days <= 15 ? 'pc-pill-wait' : 'pc-pill-ok'}`}>
            {days < 0 ? 'Expired' : `Valid till ${doc.expiresOn}`}
          </span>
        ) : <span className="pc-pill pc-pill-wait">Not uploaded</span>}
      </div>
      {doc?.path && (
        <a href={uploadUrl(doc.path)} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-block', width: 'fit-content' }}>
          <img src={uploadUrl(doc.path)} alt={name} style={{ height: 90, borderRadius: 8, background: 'var(--pc-wash)', objectFit: 'contain' }} />
        </a>
      )}
      {!disabled && (
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div style={{ flex: '1 1 200px' }}>
            <label htmlFor={`doc-${type}`} style={labelStyle}>{doc ? 'Replace with' : 'Photo or scan'}</label>
            <input id={`doc-${type}`} type="file" accept="image/*" className="input-field" onChange={(e) => setFile(e.target.files[0] || null)} />
          </div>
          <div style={{ flex: '0 1 180px' }}>
            <label htmlFor={`exp-${type}`} style={labelStyle}>Valid till</label>
            <input id={`exp-${type}`} type="date" className="input-field" min={isoDate()} value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />
          </div>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !file || !expiresOn} onClick={send}>
            <Icon name="check" size={14} />{busy ? 'Uploading…' : 'Upload'}
          </button>
        </div>
      )}
    </div>
  )
}

/** Add or edit a bus in four steps, then send it to Namma Journeys for approval. */
export default function OwnerBusWizard() {
  const { id } = useParams()
  const isNew = !id || id === 'new'
  const navigate = useNavigate()
  const [bus, setBus] = useState(null)
  const [loading, setLoading] = useState(!isNew)
  const [step, setStep] = useState(0)
  const [form, setForm] = useState(EMPTY)
  const [seats, setSeats] = useState([])
  const [seatsDirty, setSeatsDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)

  const take = useCallback((b) => {
    setBus(b)
    setForm(pickDetails(b))
    setSeats(b.seats || [])
    setSeatsDirty(false)
  }, [])

  useEffect(() => {
    if (isNew) { setBus(null); setForm(EMPTY); setSeats([]); setLoading(false); return }
    setLoading(true)
    getBus(id).then((res) => take(res.data))
      .catch((e) => setNotice({ tone: 'error', text: errorText(e, 'Could not load this bus.') }))
      .finally(() => setLoading(false))
  }, [id, isNew, take])

  const status = bus?.status || 'DRAFT'
  const pending = status === 'PENDING'
  const detailsLocked = !!bus && !['DRAFT', 'REJECTED'].includes(status)
  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }))

  const run = async (fn, okText) => {
    setBusy(true); setNotice(null)
    try {
      const res = await fn()
      if (res?.data) take(res.data)
      if (okText) setNotice({ tone: 'ok', text: okText })
      return res
    } catch (e) {
      setNotice({ tone: 'error', text: errorText(e, 'Could not save that. Please try again.') })
      return null
    } finally { setBusy(false) }
  }

  const saveDetails = async (e) => {
    e.preventDefault()
    const body = { ...form, registrationNumber: form.registrationNumber.trim().toUpperCase().replace(/\s+/g, ''), name: form.name.trim() }
    if (isNew) {
      const res = await run(() => createBus(body), 'Bus saved as a draft. Now draw its seats.')
      if (res?.data?.id) { navigate(`/owner/buses/${res.data.id}`, { replace: true }); setStep(1) }
    } else {
      const res = await run(() => updateBus(bus.id, body), 'Details saved.')
      if (res) setStep(1)
    }
  }

  const saveSeats = async () => {
    const res = await run(() => saveBusSeats(bus.id, cleanSeats(seats)), 'Seat layout saved.')
    if (res) setStep(2)
  }

  const uploadDoc = async (type, file, expiresOn) => !!(await run(() => uploadBusDocument(bus.id, type, file, expiresOn), 'Document uploaded.'))

  const addPhotos = async (files) => {
    for (const file of files) {
      if (!(await run(() => uploadBusPhoto(bus.id, file), `${files.length > 1 ? 'Photos' : 'Photo'} added.`))) break
    }
  }

  const removePhoto = (photoId) => {
    if (window.confirm('Remove this photo?')) run(() => deleteBusPhoto(bus.id, photoId), 'Photo removed.')
  }

  const submit = () => run(() => submitBus(bus.id), 'Sent for approval. We will let you know once it is checked.')

  if (loading) {
    return (
      <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
        <Panel span={12}><Skeleton rows={6} height={18} /></Panel>
      </DashboardLayout>
    )
  }

  const blockers = bus ? submitBlockers(bus) : []
  const canSubmit = bus && ['DRAFT', 'REJECTED'].includes(status) && !blockers.length && !seatsDirty
  const seatProblems = layoutProblems(seats)

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <p style={{ marginBottom: 8 }}><Link to="/owner/buses">← All buses</Link></p>
      <PageHead title={isNew ? 'Add a bus' : (bus?.name || bus?.registrationNumber || 'Bus')}
        sub={bus ? `${bus.registrationNumber}${bus.typeLabel ? ` · ${bus.typeLabel}` : ''}` : 'Details first; you can come back to finish the rest'}>
        {bus && <BusPill table={BUS_STATUS} status={status} />}
      </PageHead>

      {status === 'REJECTED' && bus?.rejectionReason && (
        <div className="pc-error" role="alert" style={{ marginBottom: 16 }}>
          <Icon name="alert" size={16} /><span>Not approved: {bus.rejectionReason}. Fix this and send the bus again.</span>
        </div>
      )}
      {pending && (
        <div style={{ ...wellStyle, marginBottom: 16, display: 'flex', gap: 10, alignItems: 'center' }}>
          <Icon name="clock" size={16} /><span style={{ fontSize: 13 }}>Waiting for approval. You can change this bus again once it has been checked.</span>
        </div>
      )}
      <Notice notice={notice} onClose={() => setNotice(null)} />

      <Steps steps={STEPS} step={step} onStep={setStep} enabled={(i) => i === 0 || !!bus} />

      <div className="pc-grid">
        {step === 0 && (
          <Panel span={8} title="Bus details">
            <form onSubmit={saveDetails}>
              <fieldset disabled={detailsLocked || busy} style={{ border: 'none', padding: 0, margin: 0 }}>
                <div style={fieldGrid}>
                  <div>
                    <label htmlFor="bus-reg" style={labelStyle}>Registration number *</label>
                    <input id="bus-reg" className="input-field" placeholder="TN01AB1234" required maxLength={13}
                      value={form.registrationNumber} onChange={(e) => set('registrationNumber', e.target.value.toUpperCase())} />
                  </div>
                  <div>
                    <label htmlFor="bus-name" style={labelStyle}>Bus name *</label>
                    <input id="bus-name" className="input-field" placeholder="Night Rider" required maxLength={80}
                      value={form.name} onChange={(e) => set('name', e.target.value)} />
                  </div>
                  <div>
                    <span style={labelStyle}>Air conditioning</span>
                    <div className="pc-chips">
                      {[[true, 'A/C'], [false, 'Non-A/C']].map(([v, l]) => (
                        <button key={l} type="button" className="pc-chip" aria-pressed={form.ac === v} onClick={() => set('ac', v)}
                          style={{ border: 'none', cursor: 'pointer', ...(form.ac === v ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}>{l}</button>
                      ))}
                    </div>
                  </div>
                  <div>
                    <label htmlFor="bus-permit" style={labelStyle}>Permit type *</label>
                    <select id="bus-permit" className="input-field" value={form.permitType} onChange={(e) => set('permitType', e.target.value)}>
                      {PERMIT_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                    </select>
                  </div>
                </div>
                <div style={{ marginTop: 16 }}>
                  <span style={labelStyle}>Amenities</span>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 16px' }}>
                    {AMENITIES.map((a) => (
                      <label key={a} style={{ display: 'inline-flex', gap: 6, alignItems: 'center', fontSize: 13 }}>
                        <input type="checkbox" checked={form.amenities.includes(a)}
                          onChange={(e) => set('amenities', e.target.checked ? [...form.amenities, a] : form.amenities.filter((x) => x !== a))} />
                        {a}
                      </label>
                    ))}
                  </div>
                </div>
                {!detailsLocked && (
                  <button type="submit" className="pc-btn" style={{ marginTop: 18 }} disabled={busy}>
                    <Icon name="check" />{busy ? 'Saving…' : isNew ? 'Save and continue' : 'Save details'}
                  </button>
                )}
              </fieldset>
            </form>
            {detailsLocked && <p style={{ ...mutedStyle, marginTop: 12 }}>Details are fixed once a bus is sent for approval.</p>}
          </Panel>
        )}

        {step === 1 && bus && (
          <Panel span={12} title="Seat layout" meta={bus.layoutLocked ? 'Locked' : seatsDirty ? 'Not saved yet' : undefined}>
            <SeatLayoutEditor seats={seats} locked={bus.layoutLocked || pending}
              onChange={(next) => { setSeats(next); setSeatsDirty(true) }} />
            {!bus.layoutLocked && !pending && (
              <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
                <button type="button" className="pc-btn" disabled={busy || !seatsDirty || seatProblems.length > 0} onClick={saveSeats}>
                  <Icon name="check" />{busy ? 'Saving…' : 'Save layout'}
                </button>
                {seatsDirty && <button type="button" className="pc-btn pc-btn-ghost" onClick={() => { setSeats(bus.seats || []); setSeatsDirty(false) }}>Undo changes</button>}
              </div>
            )}
          </Panel>
        )}

        {step === 2 && bus && (
          <Panel span={8} title="Documents" meta="All four, valid on the day of travel">
            <p style={{ ...mutedStyle, margin: '0 0 6px' }}>Trips are only created for dates on or before every expiry date, so renew papers early.</p>
            {DOC_TYPES.map(([type, name]) => (
              <DocumentRow key={`${type}-${(bus.documents || []).find((d) => d.type === type)?.expiresOn || ''}`}
                type={type} name={name} disabled={pending || busy}
                doc={(bus.documents || []).find((d) => d.type === type)} onUpload={uploadDoc} />
            ))}
          </Panel>
        )}

        {step === 3 && bus && (
          <>
            <Panel span={8} title="Photos" meta={`${(bus.photos || []).length}`}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(150px,1fr))', gap: 12 }}>
                {[...(bus.photos || [])].sort((a, b) => a.position - b.position).map((p) => (
                  <figure key={p.id} style={{ margin: 0, position: 'relative' }}>
                    <img src={uploadUrl(p.path)} alt="Bus" style={{ width: '100%', height: 110, objectFit: 'cover', borderRadius: 10, background: 'var(--pc-wash)' }} />
                    {!pending && (
                      <button type="button" className="pc-iconbtn" aria-label="Remove photo" onClick={() => removePhoto(p.id)} disabled={busy}
                        style={{ position: 'absolute', top: 6, right: 6, background: 'var(--pc-panel)' }}>
                        <Icon name="trash" size={14} />
                      </button>
                    )}
                  </figure>
                ))}
                {!pending && (
                  <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, height: 110, borderRadius: 10, cursor: 'pointer', border: '2px dashed var(--pc-line)', background: 'var(--pc-wash)', color: 'var(--pc-muted)' }}>
                    <Icon name="camera" size={22} />
                    <span style={{ fontSize: 12, fontWeight: 700 }}>{busy ? 'Uploading…' : 'Add photos'}</span>
                    <input type="file" accept="image/*" multiple aria-label="Add bus photos" style={{ display: 'none' }} disabled={busy}
                      onChange={(e) => { const files = [...e.target.files]; e.target.value = ''; if (files.length) addPhotos(files) }} />
                  </label>
                )}
              </div>
              <p style={{ ...mutedStyle, marginTop: 10 }}>Outside, inside and the berths: travellers see these before they book.</p>
            </Panel>

            <Panel span={4} title="Send for approval">
              {status === 'APPROVED' ? (
                <p style={{ fontSize: 13, margin: 0 }}>This bus is approved. Add it to a route to start selling seats.</p>
              ) : pending ? (
                <p style={{ fontSize: 13, margin: 0 }}>Sent{bus.submittedAt ? ` on ${new Date(bus.submittedAt).toLocaleDateString('en-IN')}` : ''}. Waiting for approval.</p>
              ) : (
                <>
                  {blockers.length > 0 ? (
                    <ul style={{ fontSize: 13, paddingLeft: 18, margin: '0 0 12px' }}>{blockers.map((b) => <li key={b}>{b}</li>)}</ul>
                  ) : (
                    <p style={{ fontSize: 13, margin: '0 0 12px' }}>Everything is in place. Namma Journeys will check the papers and photos.</p>
                  )}
                  {seatsDirty && <p style={{ ...mutedStyle, margin: '0 0 12px' }}>Save the seat layout first.</p>}
                  <button type="button" className="pc-btn" style={{ width: '100%' }} disabled={busy || !canSubmit} onClick={submit}>
                    <Icon name="send" />{status === 'REJECTED' ? 'Send again' : 'Submit for approval'}
                  </button>
                </>
              )}
            </Panel>
          </>
        )}
      </div>

      {bus && step < 3 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <button type="button" className="pc-btn pc-btn-ghost" onClick={() => setStep(step + 1)}>
            Next: {STEPS[step + 1]}<Icon name="arrow" size={14} />
          </button>
        </div>
      )}
    </DashboardLayout>
  )
}
