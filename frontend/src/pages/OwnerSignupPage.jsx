import React, { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ownerSignup, ownerResubmit, ownerResubmitPrefill, publicCities } from '../utils/api'
import { ownerFormError } from '../utils/ownerForm'
import AuthShell, { Field, Alert, Submit } from '../components/auth/AuthShell'
import DocTile from '../components/driver/DocTile'

const EMPTY = {
  travelsName: '', contactName: '', email: '', phone: '', cityIds: [],
  panNumber: '', aadhaarNumber: '',
  bankAccountHolder: '', bankAccountNumber: '', bankIfsc: '', bankName: '', upiId: '',
  aadhaarConsent: false,
}

const DOCUMENTS = [
  { key: 'registrationDoc', label: 'Business registration', icon: 'id' },
  { key: 'panDoc', label: 'PAN card', icon: 'id' },
  { key: 'aadhaarDoc', label: 'Aadhaar card (QR side)', icon: 'id' },
]

const CONSENT = 'I consent to NammaJourney verifying my Aadhaar offline (UIDAI Secure QR) for owner registration.'

const sectionHead = { fontSize: 15, margin: '18px 0 10px', fontWeight: 700 }

/**
 * A travels owner applying to list on NammaJourney: business, KYC and bank details plus three
 * document photos. With ?resubmit=<token> (from the rejection email) it pre-fills a rejected
 * application, keeps the documents already sent unless replaced, and sends it back for review.
 */
export default function OwnerSignupPage() {
  const [params] = useSearchParams()
  const resubmitToken = params.get('resubmit')
  const [form, setForm] = useState(EMPTY)
  const [docs, setDocs] = useState({ registrationDoc: null, panDoc: null, aadhaarDoc: null })
  const [cities, setCities] = useState([])
  const [rejection, setRejection] = useState(null)
  const [panHint, setPanHint] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(null)
  const top = useRef(null)

  useEffect(() => {
    publicCities().then((r) => setCities(r.data || [])).catch(() => setError('Could not load the city list. Refresh to try again.'))
  }, [])

  useEffect(() => {
    if (!resubmitToken) return
    ownerResubmitPrefill(resubmitToken)
      .then(({ data }) => {
        setForm((f) => ({
          ...f, travelsName: data.travelsName || '', contactName: data.contactName || '', email: data.email || '',
          phone: data.phone || '', cityIds: data.cityIds || [], bankAccountHolder: data.bankAccountHolder || '',
          bankIfsc: data.bankIfsc || '', bankName: data.bankName || '',
        }))
        setRejection(data.rejectionReason || '')
        setPanHint(data.panMasked || '')
      })
      .catch((e) => setError(e.response?.data?.error || 'This link has expired. Please contact support.'))
  }, [resubmitToken])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const toggleCity = (id) => setForm((f) => ({
    ...f, cityIds: f.cityIds.includes(id) ? f.cityIds.filter((c) => c !== id) : [...f.cityIds, id],
  }))

  const submit = async (e) => {
    e.preventDefault()
    const problem = ownerFormError(form, docs, !!resubmitToken)
    setError(problem)
    if (problem) { top.current?.scrollIntoView({ behavior: 'smooth' }); return }
    setLoading(true)
    try {
      const fd = new FormData()
      Object.entries(form).forEach(([k, v]) => {
        if (k === 'cityIds') v.forEach((id) => fd.append('cityIds', String(id)))
        else fd.append(k, String(v ?? ''))
      })
      DOCUMENTS.forEach((d) => { if (docs[d.key]) fd.append(d.key, docs[d.key]) })
      const res = resubmitToken ? await ownerResubmit(resubmitToken, fd) : await ownerSignup(fd)
      setDone(res.data)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Could not send the application. Please check your details and try again.')
      top.current?.scrollIntoView({ behavior: 'smooth' })
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    const by = done.reviewBy ? new Date(done.reviewBy).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : null
    return (
      <AuthShell role="owner" title="Application received" sub="Thank you for applying to Namma Journey.">
        <Alert tone="ok">We will verify your documents within 48 hours{by ? ` (by ${by})` : ''}.</Alert>
        <p className="au-sub">Once approved, your Owner ID and password are emailed to {form.email}.</p>
        <p className="au-sub" style={{ marginTop: 16 }}><Link to="/owner/login">Go to owner sign in</Link></p>
      </AuthShell>
    )
  }

  return (
    <AuthShell role="owner" title={resubmitToken ? 'Fix your application' : 'List your travels'}
      sub={resubmitToken ? 'Correct the details below and send it again.' : 'Apply once. We verify your documents within 48 hours.'}
      footer={<p className="au-sub" style={{ marginTop: 16 }}>Already approved? <Link to="/owner/login">Sign in</Link></p>}>
      <div ref={top} />
      {rejection && <Alert>{`Why it was not approved: ${rejection}`}</Alert>}
      <Alert>{error}</Alert>
      <form onSubmit={submit} className="au-form" noValidate>
        <h2 style={sectionHead}>Your travels</h2>
        <Field label="Travels name" icon="user">
          <input type="text" value={form.travelsName} onChange={set('travelsName')} maxLength={255} required />
        </Field>
        <Field label="Contact person" icon="user">
          <input type="text" value={form.contactName} autoComplete="name" onChange={set('contactName')} maxLength={255} required />
        </Field>
        <Field label="Email" icon="mail">
          <input type="email" value={form.email} autoComplete="email" onChange={set('email')} required />
        </Field>
        <Field label="Mobile number" icon="phone">
          <input type="tel" inputMode="numeric" maxLength={10} value={form.phone} autoComplete="tel"
            onChange={(e) => setForm({ ...form, phone: e.target.value.replace(/\D/g, '') })} required />
        </Field>

        <fieldset style={{ border: 0, padding: 0, margin: '0 0 12px' }}>
          <legend style={{ fontSize: 13, fontWeight: 700, marginBottom: 8 }}>Cities you serve</legend>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {cities.map((c) => (
              <label key={c.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 999,
                border: '1px solid var(--au-line)', cursor: 'pointer', fontSize: 13 }}>
                <input type="checkbox" checked={form.cityIds.includes(c.id)} onChange={() => toggleCity(c.id)} />
                {c.name}
              </label>
            ))}
            {!cities.length && <span className="au-sub">Loading cities…</span>}
          </div>
        </fieldset>

        <h2 style={sectionHead}>Identity</h2>
        <Field label={panHint ? `PAN (on file: ${panHint})` : 'PAN'} icon="id">
          <input type="text" placeholder="ABCDE1234F" maxLength={10} value={form.panNumber}
            onChange={(e) => setForm({ ...form, panNumber: e.target.value.toUpperCase() })} required />
        </Field>
        <Field label="Aadhaar number" icon="id">
          <input type="text" inputMode="numeric" placeholder="12 digits - only the last 4 are kept" maxLength={12} value={form.aadhaarNumber}
            onChange={(e) => setForm({ ...form, aadhaarNumber: e.target.value.replace(/\D/g, '') })} required />
        </Field>

        <h2 style={sectionHead}>Bank and UPI</h2>
        <Field label="Account holder name" icon="user">
          <input type="text" value={form.bankAccountHolder} onChange={set('bankAccountHolder')} required />
        </Field>
        <Field label="Account number" icon="id">
          <input type="text" inputMode="numeric" maxLength={18} value={form.bankAccountNumber}
            onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value.replace(/\D/g, '') })} required />
        </Field>
        <Field label="IFSC" icon="id">
          <input type="text" placeholder="SBIN0001234" maxLength={11} value={form.bankIfsc}
            onChange={(e) => setForm({ ...form, bankIfsc: e.target.value.toUpperCase() })} required />
        </Field>
        <Field label="Bank name" icon="id">
          <input type="text" value={form.bankName} onChange={set('bankName')} required />
        </Field>
        <Field label="UPI ID (customers pay you here)" icon="id">
          <input type="text" placeholder="name@bank" value={form.upiId} onChange={set('upiId')} required />
        </Field>

        <h2 style={sectionHead}>Documents</h2>
        <p className="au-sub" style={{ marginBottom: 10 }}>
          Clear photos (JPEG or PNG, 5 MB max). For Aadhaar, photograph the side with the large QR code.
          {resubmitToken && ' Leave a document empty to keep the one you sent before.'}
        </p>
        <div className="pc" style={{ background: 'none', marginBottom: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 12 }}>
            {DOCUMENTS.map((d) => (
              <DocTile key={d.key} label={d.label} icon={d.icon} file={docs[d.key]}
                missing={!!error && !resubmitToken && !docs[d.key]}
                onChange={(f) => setDocs((p) => ({ ...p, [d.key]: f }))} />
            ))}
          </div>
        </div>

        <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 13, margin: '4px 0 16px' }}>
          <input type="checkbox" checked={form.aadhaarConsent} onChange={(e) => setForm({ ...form, aadhaarConsent: e.target.checked })} />
          <span>{CONSENT}</span>
        </label>

        <Submit loading={loading} loadingText="Sending...">{resubmitToken ? 'Send for review again' : 'Apply'}</Submit>
      </form>
    </AuthShell>
  )
}
