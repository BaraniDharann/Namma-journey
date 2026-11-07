import React from 'react'
import { Link, useLocation } from 'react-router-dom'

// The one "Bus" sidebar item opens these screens; this bar moves between them.
export const BUS_TABS = [
  ['/owner/bus', 'Today'],
  ['/owner/buses', 'Buses'],
  ['/owner/bus-routes', 'Routes'],
  ['/owner/bus-trips', 'Trips'],
  ['/owner/bus-expenses', 'Expenses'],
  ['/owner/bus-earnings', 'Earnings'],
]

/** Tab links across the bus screens; a sub-page (a trip, a bus) keeps its section's tab lit. */
export default function BusTabs() {
  const { pathname } = useLocation()
  const active = BUS_TABS.slice().reverse().find(([to]) => pathname === to || pathname.startsWith(`${to}/`))?.[0]
  return (
    <nav className="pc-chips pc-bus-tabs" aria-label="Bus sections" style={{ marginBottom: 18 }}>
      {BUS_TABS.map(([to, label]) => (
        <Link key={to} to={to} className="pc-chip" aria-current={active === to ? 'page' : undefined}>
          {label}
        </Link>
      ))}
    </nav>
  )
}
