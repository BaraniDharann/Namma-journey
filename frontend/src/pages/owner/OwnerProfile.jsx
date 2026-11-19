import React, { useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { OWNER_NAV } from '../../components/owner/ownerNav'
import useOwnerMe, { rememberOwnerMe } from '../../hooks/useOwnerMe'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../components/dash/ui'
import FleetTypePanel from '../../components/bus/FleetTypePanel'
import CloseAccountPanel from '../../components/profile/CloseAccountPanel'
import {
  updateOwnerDetails, updateOwnerPayout, getOwnerClosurePreview, closeOwnerAccount,
} from '../../utils/api'

const errorText = (e, fallback) => e.response?.data?.error || e.response?.data?.message || fallback
const labelStyle = { display: 'block', fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)', marginBottom: 6 }
const inputStyle = { width: '100%', padding: '8px 12px' }

// `missing` is what a blank field says, e.g. why a bank detail matters.
function Rows({ rows, missing = 'Not added' }) {
  return (
    <div className="pc-rows">
      {rows.map(([icon, k, v, blank = missing]) => (
        <div key={k} className="pc-row" style={{ gridTemplateColumns: 'auto minmax(0, 1fr)' }}>
          <span style={{ color: 'var(--pc-muted)', display: 'inline-flex' }}><Icon name={icon} size={18} /></span>
          <div>
            <small style={{ marginTop: 0, textTransform: 'uppercase', letterSpacing: '.05em' }}>{k}</small>
            {v ? <div className="pc-row-title" style={{ overflowWrap: 'anywhere' }}>{v}</div>
              : <div style={{ fontSize: 13, color: 'var(--pc-muted)' }}>{blank}</div>}
          </div>
        </div>
      ))}
    </div>
  )
}

/** Labelled inputs plus Cancel / Save, shared by both edit forms. */
function EditForm({ fields, values, onChange, error, saving, onCancel, onSave, note }) {
  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {fields.map(([key, label, props = {}]) => (
        <div key={key}>
          <label htmlFor={`own-${key}`} style={labelStyle}>{label}</label>
          <input
            id={`own-${key}`}
            className="input-field"
            value={values[key]}
            onChange={(e) => onChange(key, e.target.value)}
            style={inputStyle}
            {...props}
          />
        </div>
      ))}
      {note}
      {error && <div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div>}
      <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
        <button type="button" className="pc-btn pc-btn-ghost" onClick={onCancel} disabled={saving}>Cancel</button>
        <button type="button" className="pc-btn" onClick={onSave} disabled={saving}>
          <Icon name="check" />{saving ? 'Saving...' : 'Save changes'}
        </button>
      </div>
    </div>
  )
}

function Saved({ text }) {
  return (
    <div className="alert-success" role="status" style={{ padding: '10px 14px', fontSize: 13.5, display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
      <Icon name="check" size={16} />{text}
    </div>
  )
}

const DETAIL_FIELDS = [
  ['travelsName', 'Travels name'],
  ['contactName', 'Contact person'],
  ['phone', 'Mobile number', { type: 'tel', inputMode: 'numeric' }],
]
const PAYOUT_FIELDS = [
  ['bankAccountHolder', 'Account holder name'],
  ['bankAccountNumber', 'Account number', { inputMode: 'numeric', autoComplete: 'off', placeholder: '9 to 18 digits' }],
  ['bankIfsc', 'IFSC', { placeholder: 'SBIN0001234', autoCapitalize: 'characters' }],
  ['bankName', 'Bank name'],
  ['upiId', 'UPI ID', { placeholder: 'name@bank' }],
  ['currentPassword', 'Current password', { type: 'password', autoComplete: 'current-password' }],
]

export default function OwnerProfile() {
  const loaded = useOwnerMe()
  const [edited, setMe] = useState(null) // the server's answer to my last save
  const me = edited || loaded

  const [details, setDetails] = useState(null) // form values while editing
  const [payout, setPayout] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState('') // 'details' | 'payout'
  const [preview, setPreview] = useState(null)

  const applyResult = (res, which) => {
    rememberOwnerMe(res.data)
    setMe(res.data)
    setSaved(which)
  }

  const editDetails = () => {
    setPayout(null); setError(''); setSaved('')
    setDetails({ travelsName: me.travelsName || '', contactName: me.contactName || '', phone: me.phone || '' })
  }
  const editPayout = () => {
    setDetails(null); setError(''); setSaved('')
    // The full account number is never sent to the browser, so it is typed again.
    setPayout({
      bankAccountHolder: me.bankAccountHolder || '', bankAccountNumber: '', bankIfsc: me.bankIfsc || '',
      bankName: me.bankName || '', upiId: me.upiId || '', currentPassword: '',
    })
  }

  const save = async (call, values, which, done) => {
    setSaving(true); setError('')
    try {
      applyResult(await call(values), which)
      done()
    } catch (e) {
      setError(errorText(e, 'Could not save these details'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <DashboardLayout navItems={OWNER_NAV} role="ROLE_OWNER">
      <PageHead title="Owner Profile" sub="Your travels, as travellers and NammaJourney see it" />

      <div className="pc-grid">
        <Panel
          span={6}
          action={me && !details && (
            <button type="button" className="pc-btn pc-btn-ghost" onClick={editDetails}><Icon name="edit" />Edit</button>
          )}
        >
          {!me ? <Skeleton rows={5} height={18} /> : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
                <span className="pc-empty-ic" style={{ width: 60, height: 60, borderRadius: 18, margin: 0 }} aria-hidden="true">
                  <Icon name="shield" size={26} />
                </span>
                <div>
                  <h2 style={{ fontSize: 18, margin: 0, color: 'var(--pc-ink)' }}>{me.travelsName}</h2>
                  <div className="pc-chips" style={{ marginTop: 8 }}>
                    <span className="pc-pill pc-pill-ok">{me.ownerCode}</span>
                    {me.platform && <span className="pc-pill pc-pill-go">Platform</span>}
                  </div>
                </div>
              </div>
              {saved === 'details' && <Saved text="Your details are saved." />}
              {details ? (
                <EditForm
                  fields={DETAIL_FIELDS}
                  values={details}
                  onChange={(k, v) => setDetails((f) => ({ ...f, [k]: v }))}
                  error={error}
                  saving={saving}
                  onCancel={() => setDetails(null)}
                  onSave={() => save(updateOwnerDetails, details, 'details', () => setDetails(null))}
                  note={<div style={{ fontSize: 12.5, color: 'var(--pc-muted)' }}>
                    Email is your login and cities are set by NammaJourney; email nammajourney463@gmail.com to change them.
                  </div>}
                />
              ) : (
                <Rows rows={[
                  ['user', 'Contact', me.contactName],
                  ['mail', 'Email', me.email],
                  ['phone', 'Phone', me.phone],
                  ['map', 'Cities', (me.cities || []).join(', ')],
                  ['wheel', 'Trip assignment', me.assignmentMode === 'MANUAL' ? 'By hand' : 'Automatic'],
                ]} />
              )}
            </>
          )}
        </Panel>
        <Panel
          span={6}
          title="Payouts"
          meta={payout ? null : 'masked for safety'}
          action={me && !payout && (
            <button type="button" className="pc-btn pc-btn-ghost" onClick={editPayout}><Icon name="edit" />Edit</button>
          )}
        >
          {!me ? <Skeleton rows={4} height={18} /> : (
            <>
              {saved === 'payout' && <Saved text="Payout details saved. We've emailed you a confirmation." />}
              {payout ? (
                <EditForm
                  fields={PAYOUT_FIELDS}
                  values={payout}
                  onChange={(k, v) => setPayout((f) => ({ ...f, [k]: v }))}
                  error={error}
                  saving={saving}
                  onCancel={() => setPayout(null)}
                  onSave={() => save(updateOwnerPayout, payout, 'payout', () => setPayout(null))}
                  note={<div style={{ fontSize: 12.5, color: 'var(--pc-muted)', display: 'flex', gap: 6 }}>
                    <Icon name="lock" size={14} style={{ flexShrink: 0, marginTop: 2 }} />
                    Your password is needed because this changes where your money goes. We&apos;ll email you when it changes.
                  </div>}
                />
              ) : (
                <Rows missing="Not added — needed to receive money" rows={[
                  ['user', 'Account holder', me.bankAccountHolder],
                  ['card', 'Account', me.bankAccountMasked],
                  ['id', 'IFSC', me.bankIfsc],
                  ['wallet', 'Bank', me.bankName],
                  ['qr', 'UPI ID', me.upiId],
                ]} />
              )}
            </>
          )}
        </Panel>
        <FleetTypePanel span={6} />

        {me && !me.platform && (
          <CloseAccountPanel
            span={6}
            onClose={(password) => closeOwnerAccount(password)}
            onOpen={() => {
              setPreview(null)
              getOwnerClosurePreview().then((r) => setPreview(r.data)).catch(() => {})
            }}
            extra={preview && (preview.drivers + preview.vehicles + preview.busRoutes + preview.packages > 0) && (
              <div style={{ padding: '12px 14px', borderRadius: 14, background: 'var(--pc-bad-soft)', color: 'var(--pc-bad)', fontSize: 13, lineHeight: 1.6, marginBottom: 14 }}>
                <b>This also switches off:</b>
                <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                  {preview.drivers > 0 && <li>{preview.drivers} driver account(s) — they can no longer sign in</li>}
                  {preview.vehicles > 0 && <li>{preview.vehicles} car(s)</li>}
                  {preview.busRoutes > 0 && <li>{preview.busRoutes} bus route(s) on sale</li>}
                  {preview.packages > 0 && <li>{preview.packages} package(s)</li>}
                </ul>
              </div>
            )}
          />
        )}
      </div>
    </DashboardLayout>
  )
}
