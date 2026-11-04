import React, { useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { Panel } from '../dash/ui'
import Icon from '../dash/Icon'
import useOwnerFleet, { setCachedFleetType } from '../../hooks/useOwnerFleet'
import { setFleetType } from '../../utils/busApi'
import { FLEET_TYPES } from '../../utils/ownerFleet'
import { errorText } from '../../utils/busText'

/** "What do you run?" on the owner's Profile: decides whether the car or bus screens show. */
export default function FleetTypePanel({ span = 6 }) {
  const { user } = useAuth()
  const current = useOwnerFleet(user?.userId ?? 'owner')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const choose = async (type) => {
    if (type === current) return
    setBusy(true); setError('')
    try {
      const res = await setFleetType(type)
      setCachedFleetType(res.data?.fleetType || type)
    } catch (e) { setError(errorText(e, 'Could not save that.')) } finally { setBusy(false) }
  }

  return (
    <Panel span={span} title="What do you run?">
      <p style={{ fontSize: 13, color: 'var(--pc-ink-2)', margin: '0 0 12px' }}>Your menu shows the screens for what you run. You can change this any time.</p>
      <div role="radiogroup" aria-label="What do you run?" style={{ display: 'grid', gap: 8 }}>
        {FLEET_TYPES.map(([type, name, hint]) => {
          const on = (current || 'CARS') === type
          return (
            <button key={type} type="button" role="radio" aria-checked={on} disabled={busy} onClick={() => choose(type)}
              style={{ display: 'flex', gap: 12, alignItems: 'center', textAlign: 'left', padding: '12px 14px', borderRadius: 12, cursor: busy ? 'wait' : 'pointer',
                border: `2px solid ${on ? 'var(--pc-brand)' : 'var(--pc-line)'}`, background: on ? 'var(--pc-brand-soft)' : 'var(--pc-panel)', color: 'var(--pc-ink)' }}>
              <Icon name={type === 'BUSES' ? 'bus' : type === 'BOTH' ? 'route' : 'car'} size={20} />
              <span style={{ flex: 1 }}><b style={{ display: 'block' }}>{name}</b><small style={{ color: 'var(--pc-muted)' }}>{hint}</small></span>
              {on && <Icon name="check" size={16} />}
            </button>
          )
        })}
      </div>
      {error && <div className="pc-error" role="alert" style={{ marginTop: 10 }}><Icon name="alert" size={16} /><span>{error}</span></div>}
    </Panel>
  )
}
