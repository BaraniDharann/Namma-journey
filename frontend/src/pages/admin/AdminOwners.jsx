import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { adminOwners } from '../../utils/api'
import { adminNavItems, errorText, when } from './adminNav'

const TABS = [
  ['PENDING_REVIEW', 'Pending'],
  ['APPROVED', 'Approved'],
  ['REJECTED', 'Rejected'],
]

/** Travels owner applications, oldest first, with the 48-hour promise made visible. */
export default function AdminOwners() {
  const [tab, setTab] = useState('PENDING_REVIEW')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async (status) => {
    setError('')
    try {
      const res = await adminOwners(status)
      setRows(res.data || [])
    } catch (e) {
      setError(errorText(e, 'Could not load applications.'))
      setRows([])
    }
  }, [])

  useEffect(() => { setRows(null); load(tab) }, [tab, load])

  const overdue = rows?.filter((r) => r.overdue).length || 0

  return (
    <DashboardLayout navItems={adminNavItems} role="ROLE_ADMIN">
      <PageHead title="Owner applications"
        sub={tab === 'PENDING_REVIEW' && rows ? `${rows.length} waiting${overdue ? `, ${overdue} past 48 hours` : ''}` : 'Verify travels owners within 48 hours'} />

      <div className="pc-chips" role="tablist" aria-label="Application status" style={{ marginBottom: 18 }}>
        {TABS.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} className="pc-chip" onClick={() => setTab(key)}
            style={{ border: 'none', cursor: 'pointer', ...(tab === key ? { background: 'var(--pc-ink)', color: 'var(--pc-panel)' } : {}) }}>
            {label}
          </button>
        ))}
      </div>

      <div className="pc-grid">
        {rows == null ? (
          <Panel span={12} title="Applications"><Skeleton rows={4} height={18} /></Panel>
        ) : error ? (
          <Panel span={12}><div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div></Panel>
        ) : rows.length === 0 ? (
          <Panel span={12}><Empty icon="id" title="Nothing here">{tab === 'PENDING_REVIEW' ? 'New applications appear here, oldest first.' : ''}</Empty></Panel>
        ) : (
          <Panel span={12} title={TABS.find(([k]) => k === tab)[1]} meta={`${rows.length}`}>
            {rows.map((r) => (
              <Link key={r.id} to={`/admin/owners/${r.id}`} aria-label={`Open application from ${r.travelsName}`}
                style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '14px 0',
                  borderTop: '1px solid var(--pc-line)', color: 'inherit', textDecoration: 'none' }}>
                <div style={{ minWidth: 0 }}>
                  <div className="pc-row-title" style={{ fontSize: 16 }}>{r.travelsName}{r.ownerCode ? ` · ${r.ownerCode}` : ''}</div>
                  <div style={{ fontSize: 12, color: 'var(--pc-muted)' }}>{r.cities.join(', ') || '—'} · sent {when(r.submittedAt)}</div>
                </div>
                {tab === 'PENDING_REVIEW' && (
                  <span className="pc-pill" style={r.overdue ? { background: 'var(--pc-bad)', color: '#fff' } : undefined}>
                    {r.overdue ? `Overdue · ${r.hoursWaiting} h` : `${r.hoursWaiting} h waiting`}
                  </span>
                )}
              </Link>
            ))}
          </Panel>
        )}
      </div>
    </DashboardLayout>
  )
}
