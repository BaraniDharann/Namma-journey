import React, { useCallback, useEffect, useState } from 'react'
import toast from 'react-hot-toast'
import Icon from '../dash/Icon'
import { Panel } from '../dash/ui'
import {
  getUserTelegramStatus, createUserTelegramLink, getEmergencyContacts,
  addEmergencyContact, inviteEmergencyContact, removeEmergencyContact,
} from '../../utils/api'

const MAX_CONTACTS = 3

/**
 * Profile → Ride safety. Two things a passenger sets up before they need them:
 *  - Telegram: ride updates, a live map and the "are you safe?" check, all free.
 *  - Emergency contacts: alerted with a live link when the passenger presses SOS. A bot cannot
 *    message a phone number, so each contact must open their invite link once; until then the
 *    passenger shares the trip link with them by hand.
 */
export default function RideSafetyPanel({ userId }) {
  const [tg, setTg] = useState({ available: false, linked: false, loaded: false })
  const [contacts, setContacts] = useState([])
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [saving, setSaving] = useState(false)
  const [invite, setInvite] = useState(null) // { name, url } just created, for the passenger to forward

  const load = useCallback(async () => {
    try {
      const [{ data: status }, { data: list }] = await Promise.all([
        getUserTelegramStatus(userId),
        getEmergencyContacts(userId),
      ])
      setTg({ ...status, loaded: true })
      setContacts(list || [])
    } catch {
      setTg((s) => ({ ...s, loaded: true }))
    }
  }, [userId])

  useEffect(() => { load() }, [load])

  // Coming back from Telegram after tapping Start: pick up the new link status.
  useEffect(() => {
    const onFocus = () => load()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [load])

  const connect = async () => {
    try {
      const { data } = await createUserTelegramLink(userId)
      window.open(data.linkUrl, '_blank', 'noopener,noreferrer')
    } catch { /* the API layer toasted the reason */ }
  }

  const add = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const { data } = await addEmergencyContact(userId, { name, phone })
      setName('')
      setPhone('')
      if (data.inviteUrl) setInvite({ name: data.contact.name, url: data.inviteUrl })
      load()
    } catch { /* toasted */ } finally {
      setSaving(false)
    }
  }

  const reinvite = async (c) => {
    try {
      const { data } = await inviteEmergencyContact(userId, c.id)
      setInvite({ name: c.name, url: data.linkUrl })
    } catch { /* toasted */ }
  }

  const remove = async (c) => {
    if (!confirm(`Remove ${c.name} from your emergency contacts?`)) return
    try {
      await removeEmergencyContact(userId, c.id)
      load()
    } catch { /* toasted */ }
  }

  const shareInvite = async () => {
    const text = `I've added you as my emergency contact on Namma Journey. If I ever press SOS on a ride, you'll get an alert on Telegram with my live location. Tap to accept: ${invite.url}`
    if (navigator.share) {
      try { await navigator.share({ text }); return } catch { /* cancelled: fall back to copy */ }
    }
    try {
      await navigator.clipboard.writeText(text)
      toast.success('Invite copied. Paste it to them on WhatsApp or SMS.')
    } catch {
      toast.error('Could not copy. Long-press the link to copy it.')
    }
  }

  const row = { display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', borderRadius: 14, background: 'var(--pc-wash)' }
  const muted = { fontSize: 12.5, color: 'var(--pc-muted)' }

  return (
    <Panel span={8} title="Ride safety" meta={<Icon name="shield" size={18} />}>
      {/* Telegram */}
      <div style={{ ...row, flexWrap: 'wrap' }}>
        <Icon name="telegram" size={26} weight="fill" style={{ color: '#229ED9', flexShrink: 0 }} />
        <div style={{ flex: '1 1 220px', minWidth: 0 }}>
          <div style={{ fontWeight: 700, color: 'var(--pc-ink)' }}>
            {tg.linked ? 'Telegram connected' : 'Get ride alerts on Telegram'}
          </div>
          <div style={muted}>
            {tg.linked
              ? 'Driver details, a live map and safety checks arrive in Telegram during your rides.'
              : 'Free. Driver details, a live map of your car, and a quick "Are you safe?" check during the ride.'}
          </div>
        </div>
        {tg.loaded && !tg.linked && tg.available && (
          <button type="button" className="pc-btn pc-btn-sm" onClick={connect}>
            <Icon name="telegram" size={15} weight="fill" />Connect Telegram
          </button>
        )}
        {tg.linked && <span className="pc-chip"><Icon name="check" size={14} />Connected</span>}
        {tg.loaded && !tg.available && <span style={muted}>Not available right now</span>}
      </div>
      {!tg.linked && tg.available && (
        <p style={{ ...muted, margin: '8px 2px 0' }}>Telegram opens; tap <b>Start</b>, then come back here.</p>
      )}

      {/* Emergency contacts */}
      <h4 style={{ margin: '22px 0 4px', fontSize: 14, fontWeight: 800, color: 'var(--pc-ink)' }}>Emergency contacts</h4>
      <p style={{ ...muted, margin: '0 0 12px' }}>
        If you press SOS on a ride, they get an alert with your live location. Each contact must open
        their invite once on Telegram to receive alerts.
      </p>

      <div style={{ display: 'grid', gap: 8 }}>
        {contacts.map((c) => (
          <div key={c.id} style={{ ...row, flexWrap: 'wrap' }}>
            <Icon name="user" size={18} />
            <div style={{ flex: '1 1 160px', minWidth: 0 }}>
              <div style={{ fontWeight: 700, color: 'var(--pc-ink)', overflowWrap: 'anywhere' }}>{c.name}</div>
              <div style={muted}>{c.phone} · {c.linked ? 'Gets Telegram alerts' : 'Has not accepted the invite yet'}</div>
            </div>
            {!c.linked && tg.available && (
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => reinvite(c)}>
                <Icon name="send" size={14} />Invite
              </button>
            )}
            <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => remove(c)} aria-label={`Remove ${c.name}`} style={{ color: 'var(--pc-bad)' }}>
              <Icon name="trash" size={14} />
            </button>
          </div>
        ))}
      </div>

      {invite && (
        <div role="status" style={{ marginTop: 12, padding: '12px 14px', borderRadius: 14, background: 'var(--pc-brand-soft)', color: 'var(--pc-brand-deep)', fontSize: 13 }}>
          <strong>Send this invite to {invite.name}.</strong> They tap it, then Start in Telegram. It works for 7 days.
          <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
            <button type="button" className="pc-btn pc-btn-sm" onClick={shareInvite}><Icon name="send" size={14} />Share invite</button>
            <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setInvite(null)}>Done</button>
          </div>
        </div>
      )}

      {contacts.length < MAX_CONTACTS && (
        <form onSubmit={add} style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
          <input className="input-field" aria-label="Contact name" placeholder="Name" value={name}
            onChange={(e) => setName(e.target.value)} maxLength={80} required style={{ flex: '1 1 140px', padding: '8px 12px' }} />
          <input className="input-field" aria-label="Contact mobile number" placeholder="Mobile number" type="tel" value={phone}
            onChange={(e) => setPhone(e.target.value)} required style={{ flex: '1 1 140px', padding: '8px 12px' }} />
          <button type="submit" className="pc-btn pc-btn-sm" disabled={saving}><Icon name="plus" size={15} />{saving ? 'Adding…' : 'Add'}</button>
        </form>
      )}
    </Panel>
  )
}
