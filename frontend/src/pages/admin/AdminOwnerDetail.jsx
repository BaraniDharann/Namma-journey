import React, { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../components/dash/ui'
import { adminOwner, adminOwnerCheck, adminApproveOwner, adminRejectOwner, adminResendCredentials } from '../../utils/api'
import { rejectReasonError, REJECT_MAX } from '../../utils/rejectReason'
import { adminNavItems, errorText, when } from './adminNav'

// Each check is made by hand on a free official source; the button opens it in a new tab.
const CHECKS = [
  { type: 'PAN', label: 'PAN', how: 'Income Tax "Verify Your PAN": active, and the name matches.',
    link: 'https://eportal.incometax.gov.in/iec/foservices/#/pre-login/verifyYourPAN' },
  { type: 'AADHAAR', label: 'Aadhaar', how: 'UIDAI signature valid below, and the QR name and photo match the person. If the QR could not be read, scan the card with UIDAI\'s "Aadhaar QR Scanner" app.',
    link: 'https://myaadhaar.uidai.gov.in/verifyAadhaar' },
  { type: 'REGISTRATION_DOC', label: 'Business registration', how: 'Name and number match the travels name. Look up a GST or Udyam number on its portal.',
    link: 'https://services.gst.gov.in/services/searchtp' },
  { type: 'BANK', label: 'Bank', how: 'Account holder matches the owner or travels name, and the IFSC is a real branch.',
    link: 'https://www.rbi.org.in/Scripts/IFSCMICRDetails.aspx' },
]

const QR_STATUS = {
  VALID: ['UIDAI signature valid', 'var(--pc-good, #15803d)'],
  INVALID_SIGNATURE: ['Signature does NOT match - card edited or forged', 'var(--pc-bad)'],
  NOT_CHECKED: ['QR read, but no UIDAI certificate is configured - not verified', 'var(--pc-muted)'],
  NOT_SECURE_QR: ['No Secure QR on this image (old card or other QR)', 'var(--pc-muted)'],
  UNREADABLE: ['QR could not be read from the photo', 'var(--pc-muted)'],
}

const label = { fontSize: 12, fontWeight: 700, color: 'var(--pc-muted)', textTransform: 'uppercase', letterSpacing: '.03em' }

function Row({ k, v }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '6px 0', borderTop: '1px solid var(--pc-line)' }}>
      <span style={{ color: 'var(--pc-muted)', fontSize: 13 }}>{k}</span>
      <span style={{ fontSize: 13, fontWeight: 600, textAlign: 'right', overflowWrap: 'anywhere' }}>{v || '—'}</span>
    </div>
  )
}

function CheckRow({ check, value, disabled, onSave }) {
  const [note, setNote] = useState(value?.note || '')
  const [busy, setBusy] = useState(false)
  const save = async (result) => { setBusy(true); try { await onSave(check.type, result, note) } finally { setBusy(false) } }
  const state = value?.result
  return (
    <div style={{ padding: '12px 0', borderTop: '1px solid var(--pc-line)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        <strong>{check.label}</strong>
        <span className="pc-pill" style={state === 'VERIFIED' ? { background: 'var(--pc-brand-soft)' } : state === 'FAILED' ? { background: 'var(--pc-bad)', color: '#fff' } : undefined}>
          {state === 'VERIFIED' ? '✔ Verified' : state === 'FAILED' ? '✘ Failed' : 'Not checked'}
        </span>
      </div>
      <p style={{ fontSize: 12.5, color: 'var(--pc-ink-2)', margin: '6px 0' }}>{check.how}</p>
      {value?.checkedAt && <p style={{ fontSize: 11.5, color: 'var(--pc-muted)', margin: '0 0 6px' }}>Checked {when(value.checkedAt)} by admin #{value.checkedByAdminId}</p>}
      {!disabled && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <a className="pc-btn pc-btn-ghost pc-btn-sm" href={check.link} target="_blank" rel="noopener noreferrer">Open official site</a>
          <input className="input-field" style={{ flex: '1 1 180px', minWidth: 0 }} placeholder="Note (required if failed)" value={note}
            maxLength={500} aria-label={`${check.label} note`} onChange={(e) => setNote(e.target.value)} />
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy} onClick={() => save('VERIFIED')}>✔ Verified</button>
          <button type="button" className="pc-btn pc-btn-sm" disabled={busy || !note.trim()} style={{ background: 'var(--pc-bad)', boxShadow: 'none' }}
            onClick={() => save('FAILED')}>✘ Failed</button>
        </div>
      )}
    </div>
  )
}

/** One owner application: details, documents, the Aadhaar QR result, the checklist and the decision. */
export default function AdminOwnerDetail() {
  const { id } = useParams()
  const [o, setO] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  const load = useCallback(async () => {
    try { setO((await adminOwner(id)).data); setError('') } catch (e) { setError(errorText(e, 'Could not load this application.')) }
  }, [id])
  useEffect(() => { load() }, [load])

  const saveCheck = async (type, result, note) => {
    try {
      const res = await adminOwnerCheck(id, type, { result, note: note.trim() || null })
      setO(res.data)
      setNotice(null)
    } catch (e) { setNotice({ tone: 'error', text: errorText(e, 'Could not save that check.') }) }
  }

  const act = async (fn, ok) => {
    setBusy(true); setNotice(null)
    try { setNotice({ tone: 'ok', text: ok(await fn()) }) }
    catch (e) { setNotice({ tone: 'error', text: errorText(e, 'Could not save that decision.') }) }
    finally { setBusy(false); setRejecting(false); load() }
  }

  const approve = () => act(() => adminApproveOwner(id), (res) => res.data.credentialsEmailed
    ? `Approved as ${res.data.ownerCode}. The Owner ID and password were emailed.`
    : `Approved as ${res.data.ownerCode}, but the email did not go out. Use "Resend credentials".`)
  const resend = () => act(() => adminResendCredentials(id), (res) => res.data.credentialsEmailed
    ? 'A new password was emailed.' : 'The email did not go out again. Check the mail settings.')
  const reject = () => act(() => adminRejectOwner(id, reason.trim()), () => 'Rejected. The owner was emailed the reason and a link to fix it.')

  const pending = o?.status === 'PENDING_REVIEW'
  const qr = o?.aadhaarQr
  const reasonProblem = rejectReasonError(reason)
  const failedNotes = o ? Object.values(o.checks || {}).filter((c) => c.result === 'FAILED').map((c) => c.note) : []

  return (
    <DashboardLayout navItems={adminNavItems} role="ROLE_ADMIN">
      <p style={{ marginBottom: 8 }}><Link to="/admin/owners">← All applications</Link></p>
      {!o ? (
        error ? <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div>
          : <Panel span={12}><Skeleton rows={6} height={18} /></Panel>
      ) : (
        <>
          <PageHead title={o.travelsName || 'Owner application'}
            sub={`${o.status.replace('_', ' ').toLowerCase()}${o.ownerCode ? ` · ${o.ownerCode}` : ''}${pending ? ` · decide by ${when(o.reviewBy)}${o.overdue ? ' (overdue)' : ''}` : ''}`} />

          {notice && (
            <div className={notice.tone === 'ok' ? 'alert-success' : 'pc-error'} role={notice.tone === 'ok' ? 'status' : 'alert'}
              style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
              <Icon name={notice.tone === 'ok' ? 'check' : 'alert'} size={16} /><span>{notice.text}</span>
            </div>
          )}

          <div className="pc-grid">
            <Panel span={6} title="Applicant">
              <Row k="Contact" v={o.contactName} />
              <Row k="Email" v={o.email} />
              <Row k="Mobile" v={o.phone} />
              <Row k="Cities" v={o.cities.join(', ')} />
              <Row k="PAN" v={o.panMasked} />
              <Row k="Aadhaar (last 4)" v={o.aadhaarLast4} />
              <Row k="Aadhaar consent" v={when(o.aadhaarConsentAt)} />
              <Row k="Bank holder" v={o.bankAccountHolder} />
              <Row k="Account" v={o.bankAccountMasked} />
              <Row k="IFSC" v={o.bankIfsc} />
              <Row k="Bank" v={o.bankName} />
              <Row k="UPI" v={o.upiId} />
              <Row k="Sent" v={when(o.submittedAt)} />
              {o.rejectionReason && <Row k="Rejected because" v={o.rejectionReason} />}
            </Panel>

            <Panel span={6} title="Aadhaar Secure QR (UIDAI offline check)">
              {!qr ? <p style={{ fontSize: 13 }}>No QR result recorded.</p> : (
                <>
                  <p style={{ fontWeight: 700, color: (QR_STATUS[qr.status] || [])[1] }}>{(QR_STATUS[qr.status] || [qr.status])[0]}</p>
                  {qr.name && (
                    <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start', marginTop: 8 }}>
                      {qr.photoDataUrl && <img src={qr.photoDataUrl} alt="Photo from the Aadhaar QR" style={{ width: 96, borderRadius: 8 }} />}
                      <div style={{ flex: 1 }}>
                        <Row k="Name (QR)" v={qr.name} />
                        <Row k="Contact name" v={o.contactName} />
                        <Row k="DOB / gender" v={`${qr.dob || '—'} / ${qr.gender || '—'}`} />
                        <Row k="State, PIN" v={`${qr.state || '—'}, ${qr.pincode || '—'}`} />
                        <Row k="Last 4 digits" v={`${qr.last4 || '—'} ${qr.last4MatchesTyped ? '✔ matches typed' : '✘ differs from typed'}`} />
                      </div>
                    </div>
                  )}
                </>
              )}
            </Panel>

            <Panel span={12} title="Documents">
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 12 }}>
                {[['Business registration', o.documents.registration], ['PAN card', o.documents.pan], ['Aadhaar card', o.documents.aadhaar]].map(([name, src]) => (
                  <figure key={name} style={{ margin: 0 }}>
                    <figcaption style={label}>{name}</figcaption>
                    {src
                      ? <a href={src} target="_blank" rel="noopener noreferrer"><img src={src} alt={name} style={{ width: '100%', maxHeight: 220, objectFit: 'contain', borderRadius: 8, background: 'var(--pc-wash)' }} /></a>
                      : <p style={{ fontSize: 13 }}>Not uploaded</p>}
                  </figure>
                ))}
              </div>
            </Panel>

            <Panel span={12} title="Verification checklist" meta={pending ? 'All four must be verified to approve' : undefined}>
              {CHECKS.map((c) => <CheckRow key={c.type} check={c} value={o.checks?.[c.type]} disabled={!pending} onSave={saveCheck} />)}
            </Panel>

            {pending && (
              <Panel span={12} title="Decision">
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <button type="button" className="pc-btn" disabled={busy || !o.canApprove} onClick={approve}>
                    {busy ? 'Saving…' : 'Approve and email Owner ID'}
                  </button>
                  <button type="button" className="pc-btn pc-btn-ghost" disabled={busy}
                    onClick={() => { setRejecting(true); if (!reason && failedNotes.length) setReason(failedNotes.join('; ')) }}>Reject</button>
                </div>
                {!o.canApprove && <p style={{ fontSize: 12.5, color: 'var(--pc-muted)', marginTop: 8 }}>Approve unlocks once all four checks are verified.</p>}
                {rejecting && (
                  <div style={{ marginTop: 14 }}>
                    <label htmlFor="reject-reason" style={label}>Reason the owner will see</label>
                    <textarea id="reject-reason" className="input-field" rows={3} value={reason} maxLength={REJECT_MAX}
                      onChange={(e) => setReason(e.target.value)} style={{ width: '100%', resize: 'vertical', marginTop: 6 }} />
                    <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                      <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={() => setRejecting(false)}>Cancel</button>
                      <button type="button" className="pc-btn" style={{ background: 'var(--pc-bad)', boxShadow: 'none' }} disabled={busy || !!reasonProblem} onClick={reject}>
                        Reject and email the owner
                      </button>
                    </div>
                  </div>
                )}
              </Panel>
            )}

            {o.status === 'APPROVED' && o.mustChangePassword && (
              <Panel span={12} title="Owner has not signed in yet">
                <p style={{ fontSize: 13, marginBottom: 10 }}>If the credentials email did not arrive, send a new password.</p>
                <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={resend}>Resend credentials</button>
              </Panel>
            )}
          </div>
        </>
      )}
    </DashboardLayout>
  )
}
