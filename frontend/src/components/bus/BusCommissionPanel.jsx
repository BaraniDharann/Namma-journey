import React, { useState } from 'react'
import { Panel } from '../dash/ui'
import Icon from '../dash/Icon'
import { adminSetBusCommission } from '../../utils/busApi'
import { errorText } from '../../utils/busText'

/** Admin: the share of an owner's bus fares Namma Journeys keeps (default 8%, 0 to 50). */
export default function BusCommissionPanel({ ownerId, percent }) {
  const [value, setValue] = useState(percent ?? 8)
  const [saved, setSaved] = useState(percent ?? 8)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState(null)

  const n = Number(value)
  const valid = value !== '' && Number.isFinite(n) && n >= 0 && n <= 50

  const save = async (e) => {
    e.preventDefault()
    setBusy(true); setMsg(null)
    try {
      const res = await adminSetBusCommission(ownerId, n)
      const p = res.data?.busCommissionPercent ?? n
      setSaved(p); setValue(p)
      setMsg({ ok: true, text: `Saved. New bus tickets use ${p}%.` })
    } catch (err) { setMsg({ ok: false, text: errorText(err, 'Could not save the commission.') }) } finally { setBusy(false) }
  }

  return (
    <Panel span={6} title="Bus commission %" meta="Applies to tickets booked from now on">
      <form onSubmit={save} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <input type="number" className="input-field" min={0} max={50} step={0.5} value={value} aria-label="Bus commission percent"
          onChange={(e) => setValue(e.target.value)} style={{ width: 120 }} />
        <span style={{ fontSize: 13, color: 'var(--pc-muted)' }}>% of fares, without GST</span>
        <button type="submit" className="pc-btn pc-btn-sm" disabled={busy || !valid || Number(saved) === n}><Icon name="check" size={14} />Save</button>
      </form>
      {!valid && <p style={{ fontSize: 12.5, color: 'var(--pc-bad)', margin: '8px 0 0' }}>Enter 0 to 50.</p>}
      {msg && <p role={msg.ok ? 'status' : 'alert'} style={{ fontSize: 12.5, margin: '8px 0 0', color: msg.ok ? 'var(--pc-good)' : 'var(--pc-bad)' }}>{msg.text}</p>}
    </Panel>
  )
}
