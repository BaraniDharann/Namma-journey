import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, Empty, Skeleton } from '../../components/dash/ui'
import { BusPill, Tabs, mutedStyle } from '../../components/bus/busUi'
import { adminBuses } from '../../utils/busApi'
import { BUS_STATUS } from '../../utils/busText'
import { adminNavItems, errorText, when } from './adminNav'

const TABS = [
  ['PENDING', 'Pending'],
  ['APPROVED', 'Approved'],
  ['REJECTED', 'Rejected'],
  ['DRAFT', 'Drafts'],
]

/** Buses owners have sent for approval, oldest first. */
export default function AdminBusApprovals() {
  const [tab, setTab] = useState('PENDING')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async (status) => {
    setError('')
    try { setRows((await adminBuses(status)).data || []) } catch (e) { setError(errorText(e, 'Could not load buses.')); setRows([]) }
  }, [])
  useEffect(() => { setRows(null); load(tab) }, [tab, load])

  return (
    <DashboardLayout navItems={adminNavItems} role="ROLE_ADMIN">
      <PageHead title="Bus approvals" sub={tab === 'PENDING' && rows ? `${rows.length} waiting` : 'Check papers, photos and the seat layout before seats go on sale'} />
      <Tabs tabs={TABS} value={tab} onChange={setTab} label="Bus status" />

      <div className="pc-grid">
        {rows == null ? (
          <Panel span={12} title="Buses"><Skeleton rows={4} height={18} /></Panel>
        ) : error ? (
          <Panel span={12}><div className="pc-error" role="alert"><Icon name="alert" size={16} /><span>{error}</span></div></Panel>
        ) : rows.length === 0 ? (
          <Panel span={12}><Empty icon="bus" title="Nothing here">{tab === 'PENDING' ? 'Buses sent for approval appear here.' : ''}</Empty></Panel>
        ) : (
          <Panel span={12} title={TABS.find(([k]) => k === tab)[1]} meta={`${rows.length}`}>
            {rows.map((b) => (
              <Link key={b.id} to={`/admin/bus-approvals/${b.id}`} aria-label={`Open bus ${b.registrationNumber}`}
                style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', padding: '14px 0', borderTop: '1px solid var(--pc-line)', color: 'inherit', textDecoration: 'none' }}>
                <div style={{ minWidth: 0 }}>
                  <div className="pc-row-title" style={{ fontSize: 16 }}>{b.registrationNumber} · {b.name}</div>
                  <div style={mutedStyle}>
                    {b.travelsName}{b.ownerCode ? ` (${b.ownerCode})` : ''} · {b.typeLabel || (b.ac ? 'A/C' : 'Non-A/C')} · {(b.seats || []).length} seats
                    {b.submittedAt ? ` · sent ${when(b.submittedAt)}` : ''}
                  </div>
                </div>
                <BusPill table={BUS_STATUS} status={b.status} />
              </Link>
            ))}
          </Panel>
        )}
      </div>
    </DashboardLayout>
  )
}
