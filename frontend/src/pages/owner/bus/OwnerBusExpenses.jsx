import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../../components/DashboardLayout'
import { PageHead } from '../../../components/dash/ui'
import { ownerNavItems } from '../../../components/bus/busUi'
import BusTabs from '../../../components/bus/BusTabs'
import ExpensesPanel from '../../../components/expenses/ExpensesPanel'
import { getBuses, getBusTrips } from '../../../utils/busApi'
import { addDays, isoDate } from '../../../utils/busText'

/** What each bus costs to run, and the profit it leaves. */
export default function OwnerBusExpenses() {
  const [buses, setBuses] = useState(null)
  const [trips, setTrips] = useState([])

  useEffect(() => {
    getBuses().then((r) => setBuses((r.data || []).map((b) => ({ id: b.id, label: `${b.name} · ${b.registrationNumber}` })))).catch(() => setBuses([]))
    // Trips an expense can be tied to: the last fortnight and tomorrow's.
    getBusTrips(addDays(isoDate(), -14), addDays(isoDate(), 1))
      .then((r) => setTrips((r.data || []).map((t) => ({ id: t.id, busId: t.busId, label: `${t.travelDate} · ${t.routeName}` }))))
      .catch(() => setTrips([]))
  }, [])

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <PageHead title="Bus expenses" sub="Fuel, tolls, driver pay and repairs per bus, and the profit left after them" />
      <BusTabs />
      <ExpensesPanel kind="BUS" vehicles={buses || []} trips={trips} />
    </DashboardLayout>
  )
}
