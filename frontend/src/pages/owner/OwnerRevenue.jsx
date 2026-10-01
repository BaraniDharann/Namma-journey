import React, { useEffect, useState } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { getDailyRevenue, getMonthlyRevenue, getMonthlyRevenueSeries, getYearlyRevenue, setPricing, setHourlyPricing, getCurrentPricing, getOwnerBookings, getOwnerDrivers } from '../../utils/api'
import { useAuth } from '../../context/AuthContext'
import Icon from '../../components/dash/Icon'
import { Panel, PageHead, SummaryStrip, StatusPill, Empty, Skeleton } from '../../components/dash/ui'
import { AreaChart, BarChart, Donut } from '../../components/dash/charts'
import { inr, shortPlace, STATUS_META } from '../../dash/metrics'
// Lazy-load heavy libs on first download
const getJsPDF = () => Promise.all([import('jspdf'), import('jspdf-autotable')]).then(([m]) => m.default)
const getXLSX = () => import('xlsx')

const navItems = [
  { path: '/owner/dashboard', icon: '', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '', label: 'Drivers' },
  { path: '/owner/payments', icon: '', label: 'Payments' },
  { path: '/owner/reviews', icon: '', label: 'Reviews' },
  { path: '/owner/packages', icon: '', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '', label: 'Revenue' },
  { path: '/owner/profile', icon: '', label: 'Profile' },
]

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']

// Excel download helper
async function downloadExcel(headers, rows, filename, sheetName = 'Report') {
  const XLSX = await getXLSX()
  const data = [headers, ...rows]
  const ws = XLSX.utils.aoa_to_sheet(data)
  ws['!cols'] = headers.map((h, i) => {
    const maxLen = Math.max(h.length, ...rows.map(r => String(r[i] ?? '').length))
    return { wch: Math.min(maxLen + 2, 35) }
  })
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName)
  XLSX.writeFile(wb, filename)
}

// PDF download helper with styled table
async function downloadPDF(title, subtitle, headers, rows, filename, summaryInfo = []) {
  const jsPDFClass = await getJsPDF()
  const { default: autoTable } = await import('jspdf-autotable')
  const doc = new jsPDFClass({ orientation: headers.length > 10 ? 'landscape' : 'portrait', unit: 'mm', format: 'a4' })

  // Header bar
  doc.setFillColor(249, 115, 22)
  doc.rect(0, 0, doc.internal.pageSize.getWidth(), 28, 'F')
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(18)
  doc.setTextColor(255, 255, 255)
  doc.text('Namma Journey', 14, 12)
  doc.setFontSize(10)
  doc.setFont('helvetica', 'normal')
  doc.text(title, 14, 20)
  doc.setFontSize(8)
  doc.text(`Generated: ${new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })}`, doc.internal.pageSize.getWidth() - 14, 20, { align: 'right' })

  let startY = 34

  // Summary info boxes
  if (summaryInfo.length > 0) {
    doc.setFontSize(8)
    doc.setTextColor(100, 116, 139)
    doc.text(subtitle, 14, startY)
    startY += 6
    const boxW = (doc.internal.pageSize.getWidth() - 28) / Math.min(summaryInfo.length, 4)
    summaryInfo.forEach((info, i) => {
      const x = 14 + (i % 4) * boxW
      const y = startY + Math.floor(i / 4) * 14
      doc.setFillColor(248, 250, 252)
      doc.roundedRect(x, y, boxW - 4, 12, 2, 2, 'F')
      doc.setFontSize(7)
      doc.setTextColor(148, 163, 184)
      doc.text(info.label, x + 3, y + 4.5)
      doc.setFontSize(10)
      doc.setTextColor(15, 23, 42)
      doc.setFont('helvetica', 'bold')
      doc.text(String(info.value), x + 3, y + 9.5)
      doc.setFont('helvetica', 'normal')
    })
    startY += Math.ceil(summaryInfo.length / 4) * 14 + 4
  }

  // Table
  autoTable(doc, {
    startY,
    head: [headers],
    body: rows,
    styles: { fontSize: 7, cellPadding: 2.5, lineColor: [226, 232, 240], lineWidth: 0.2 },
    headStyles: { fillColor: [15, 23, 42], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 7 },
    alternateRowStyles: { fillColor: [248, 250, 252] },
    columnStyles: headers.reduce((acc, _, i) => {
      if (i === 0) acc[i] = { cellWidth: 8 }
      return acc
    }, {}),
    didParseCell: (data) => {
      // Highlight amount column in indigo
      const hdr = headers[data.column.index]?.toLowerCase() || ''
      if (data.section === 'body' && (hdr.includes('amount') || hdr.includes('₹'))) {
        data.cell.styles.textColor = [249, 115, 22]
        data.cell.styles.fontStyle = 'bold'
      }
      // Color status badges
      if (data.section === 'body' && hdr === 'status') {
        const val = String(data.cell.raw).toUpperCase()
        if (val === 'COMPLETED') data.cell.styles.textColor = [22, 163, 74]
        else if (val === 'CONFIRMED') data.cell.styles.textColor = [37, 99, 235]
        else if (val === 'PENDING') data.cell.styles.textColor = [202, 138, 4]
        else if (val === 'CANCELLED') data.cell.styles.textColor = [220, 38, 38]
      }
    },
    margin: { left: 14, right: 14 },
  })

  // Footer on each page
  const pageCount = doc.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(7)
    doc.setTextColor(148, 163, 184)
    doc.text(`Page ${i} of ${pageCount}`, doc.internal.pageSize.getWidth() / 2, doc.internal.pageSize.getHeight() - 8, { align: 'center' })
    doc.text('Namma Journey - Confidential', 14, doc.internal.pageSize.getHeight() - 8)
  }

  doc.save(filename)
}

export default function OwnerRevenue() {
  const { user } = useAuth()
  const [tab, setTab] = useState('monthly')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [pricing, setPricingData] = useState(null)
  const [newPrice, setNewPrice] = useState('')
  const [newHourlyPrice, setNewHourlyPrice] = useState('')
  const [priceLoading, setPriceLoading] = useState(false)
  const [priceSuccess, setPriceSuccess] = useState(false)
  const [hourlyPriceLoading, setHourlyPriceLoading] = useState(false)
  const [hourlyPriceSuccess, setHourlyPriceSuccess] = useState(false)
  const [monthlyBars, setMonthlyBars] = useState(Array(12).fill(0))
  const [allBookings, setAllBookings] = useState([])
  const [drivers, setDrivers] = useState([])
  const [reportFilter, setReportFilter] = useState('all')

  const now = new Date()
  const currentYear = now.getFullYear()
  const [params, setParams] = useState({
    date: now.toISOString().split('T')[0],
    year: currentYear,
    month: now.getMonth() + 1,
  })

  useEffect(() => {
    getCurrentPricing().then(r => setPricingData(r.data)).catch(() => {})
    getOwnerBookings().then(r => setAllBookings(r.data || [])).catch(() => {})
    getOwnerDrivers().then(r => setDrivers(r.data || [])).catch(() => {})
  }, [])

  const driverMap = {}
  drivers.forEach(d => { driverMap[d.driverId] = d })

  const fetchRevenue = async () => {
    setLoading(true)
    try {
      let res
      if (tab === 'daily') res = await getDailyRevenue(params.date)
      else if (tab === 'monthly') res = await getMonthlyRevenue(params.year, params.month)
      else res = await getYearlyRevenue(params.year)
      setData(res.data)
    } catch { setData(null) }
    setLoading(false)
  }

  useEffect(() => { fetchRevenue() }, [tab, params])

  useEffect(() => {
    const fetchBars = async () => {
      // One request for the whole year. This used to be 12 parallel calls, which on its own
      // was most of a signed-in owner's per-minute request budget.
      // Silent: surface any backend issue once via fetchRevenue rather than twice.
      try {
        const res = await getMonthlyRevenueSeries(params.year, { silent: true })
        const months = res.data?.months || []
        setMonthlyBars(Array.from({ length: 12 }, (_, i) => months[i]?.totalRevenue || 0))
      } catch {
        setMonthlyBars(Array.from({ length: 12 }, () => 0))
      }
    }
    fetchBars()
  }, [params.year])

  const handleSetPrice = async () => {
    if (!newPrice) return
    setPriceLoading(true)
    try {
      await setPricing(Number(newPrice), user.userId)
      setPriceSuccess(true)
      getCurrentPricing().then(r => setPricingData(r.data)).catch(() => {})
      setTimeout(() => setPriceSuccess(false), 3000)
    } catch { /* ignore */ }
    setPriceLoading(false)
  }

  const handleSetHourlyPrice = async () => {
    if (!newHourlyPrice) return
    setHourlyPriceLoading(true)
    try {
      await setHourlyPricing(Number(newHourlyPrice), user.userId)
      setHourlyPriceSuccess(true)
      getCurrentPricing().then(r => setPricingData(r.data)).catch(() => {})
      setTimeout(() => setHourlyPriceSuccess(false), 3000)
    } catch { /* ignore */ }
    setHourlyPriceLoading(false)
  }

  const yearOptions = []
  for (let y = 2023; y <= currentYear; y++) yearOptions.push(y)

  const displayFields = data ? Object.entries(data).filter(([, val]) => !Array.isArray(val)) : []
  const totalYearRevenue = monthlyBars.reduce((s, v) => s + v, 0)

  // Status breakdown from all bookings
  const statusCounts = { COMPLETED: 0, STARTED: 0, CONFIRMED: 0, PENDING: 0, CANCELLED: 0 }
  allBookings.forEach(b => { if (statusCounts[b.status] !== undefined) statusCounts[b.status]++ })

  // Filtered bookings for the report table
  const filteredBookings = reportFilter === 'all' ? allBookings : allBookings.filter(b => b.status === reportFilter)

  // Build report data rows
  const buildReportRows = () => {
    return filteredBookings.map((b, i) => {
      const drv = b.driver || driverMap[b.driverId] || {}
      return [
        i + 1,
        String(b.bookingId || b.id || '').slice(0, 8),
        b.userName || '',
        b.userPhone || '',
        b.fromPlace || '',
        b.toPlace || '',
        b.fromDate || '',
        b.toDate || '',
        b.travelDays ?? '',
        b.travelMembers ?? '',
        b.acType || '',
        b.distanceKm ? Number(b.distanceKm).toFixed(1) : '',
        b.estimatedTimeFormatted || (b.estimatedTimeMinutes ? `${Math.floor(b.estimatedTimeMinutes / 60)}h ${b.estimatedTimeMinutes % 60}m` : ''),
        b.totalAmount ? `${Number(b.totalAmount).toLocaleString()}` : '0',
        b.status || '',
        drv.name || '—',
        drv.mobile || '—',
        drv.licenseNumber || '—',
        b.paymentMethod || 'N/A',
        b.bookingDate ? String(b.bookingDate).split('T')[0] : '',
      ]
    })
  }
  const reportHeaders = ['#', 'Booking ID', 'Customer', 'Phone', 'From', 'To', 'Start Date', 'End Date', 'Days', 'Members', 'AC', 'Distance', 'Duration', 'Amount', 'Status', 'Driver', 'Driver Phone', 'License', 'Payment', 'Booked On']

  const buildTripRows = () => {
    return filteredBookings.map((b, i) => {
      const drv = b.driver || driverMap[b.driverId] || {}
      return [
        i + 1,
        String(b.bookingId || b.id || '').slice(0, 8),
        b.userName || '',
        b.userPhone || '',
        b.fromPlace || '',
        b.toPlace || '',
        b.fromDate || '',
        b.toDate || '',
        b.travelDays ?? '',
        b.travelMembers ?? '',
        b.acType || '',
        b.distanceKm ? Number(b.distanceKm).toFixed(1) : '',
        b.estimatedTimeFormatted || '',
        b.totalAmount ? `${Number(b.totalAmount).toLocaleString()}` : '0',
        b.status || '',
        drv.name || '—',
        drv.mobile || '—',
        drv.email || '—',
        drv.licenseNumber || '—',
      ]
    })
  }
  const tripHeaders = ['#', 'Trip ID', 'Customer', 'Phone', 'From', 'To', 'Start Date', 'End Date', 'Days', 'Members', 'AC', 'Distance', 'Duration', 'Amount', 'Status', 'Driver', 'Driver Phone', 'Driver Email', 'License No']

  const filterLabel = reportFilter === 'all' ? 'All' : reportFilter
  const totalAmount = filteredBookings.reduce((s, b) => s + (b.totalAmount || 0), 0)
  const summaryInfo = [
    { label: 'Total Trips', value: filteredBookings.length },
    { label: 'Filter', value: filterLabel },
    { label: 'Total Revenue', value: `Rs.${totalAmount.toLocaleString()}` },
    { label: 'Year', value: params.year },
  ]

  // PDF Downloads
  const downloadReportPDF = () => {
    downloadPDF(
      `Booking Report — ${filterLabel}`,
      `${filteredBookings.length} bookings | Total: Rs.${totalAmount.toLocaleString()}`,
      reportHeaders,
      buildReportRows(),
      `NammaJourney_${filterLabel}_Report_${params.year}.pdf`,
      summaryInfo
    )
  }

  const downloadTripPDF = () => {
    downloadPDF(
      `Trip + Driver Details — ${filterLabel}`,
      `${filteredBookings.length} trips with driver allocation details`,
      tripHeaders,
      buildTripRows(),
      `NammaJourney_TripDetails_${params.year}.pdf`,
      summaryInfo
    )
  }

  // Excel Downloads
  const downloadReportExcel = () => {
    downloadExcel(reportHeaders, buildReportRows(), `NammaJourney_${filterLabel}_Report_${params.year}.xlsx`, 'Booking Report')
  }

  const downloadTripExcel = () => {
    downloadExcel(tripHeaders, buildTripRows(), `NammaJourney_TripDetails_${params.year}.xlsx`, 'Trip Details')
  }

  // Clicking (or pressing Enter on) a month bar selects that month, as before.
  const pickMonth = (i) => {
    setTab('monthly')
    setParams(p => ({ ...p, month: i + 1 }))
  }

  const rate = (v) => (v ? `₹${Number(v).toLocaleString('en-IN')}` : '—')
  const statusSlices = ['COMPLETED', 'STARTED', 'CONFIRMED', 'PENDING', 'CANCELLED']
    .filter(k => statusCounts[k] > 0)
    .map(k => [STATUS_META[k].label, statusCounts[k], STATUS_META[k].slot])
  const toggleClass = (on) => (on ? 'pc-btn pc-btn-sm' : 'pc-btn pc-btn-ghost pc-btn-sm')

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <PageHead title="Revenue Analytics" sub="Track earnings, view reports and download data" />

      <div className="pc-grid">
        {/* Top Stats */}
        <SummaryStrip
          items={[
            { icon: 'calendar', label: 'Total Bookings', value: allBookings.length.toLocaleString('en-IN'), sub: `${statusCounts.PENDING} pending · ${statusCounts.CONFIRMED} confirmed` },
            { icon: 'check', label: 'Completed Trips', value: statusCounts.COMPLETED.toLocaleString('en-IN'), sub: `${statusCounts.CANCELLED} cancelled` },
            { icon: 'wallet', label: `${params.year} Revenue`, value: inr(totalYearRevenue), sub: 'all months this year' },
            { icon: 'route', label: 'Price/km', value: rate(pricing?.pricePerKm), sub: 'current rate' },
            { icon: 'clock', label: 'Price/hour', value: rate(pricing?.pricePerHour), sub: 'current rate' },
          ]}
        />

        {/* Revenue Trend */}
        <Panel
          span={8}
          title={`Revenue Trend — ${params.year}`}
          action={(
            <select aria-label="Year" className="input-field" style={{ width: 'auto', minHeight: 34, padding: '4px 10px', fontSize: 13 }} value={params.year} onChange={e => setParams(p => ({ ...p, year: Number(e.target.value) }))}>
              {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          )}
        >
          <AreaChart data={MONTHS.map((m, i) => ({ label: m, value: monthlyBars[i] }))} format={inr} height={220} caption={`Revenue by month, ${params.year}`} />
          <div className="pc-minis" style={{ marginTop: 14 }}>
            <div><b>{inr(totalYearRevenue)}</b>Total Revenue</div>
            <div><b>{inr(totalYearRevenue > 0 ? totalYearRevenue / 12 : 0)}</b>Avg/Month</div>
            <div><b>{inr(Math.max(...monthlyBars))}</b>Best Month</div>
          </div>
        </Panel>

        {/* Booking Status */}
        <Panel span={4} title="Booking Status" meta="all bookings">
          <Donut segments={statusSlices} label="total trips" caption="Booking status" />
        </Panel>

        {/* Monthly Breakdown */}
        <Panel
          span={8}
          title={`Monthly Breakdown — ${params.year}`}
          action={(
            <div className="pc-chips" role="group" aria-label="Revenue period" style={{ gap: 4 }}>
              {['daily', 'monthly', 'yearly'].map(t => (
                <button key={t} type="button" aria-pressed={tab === t} className={toggleClass(tab === t)} style={{ textTransform: 'capitalize', minHeight: 30 }} onClick={() => setTab(t)}>{t}</button>
              ))}
            </div>
          )}
        >
          <BarChart data={MONTHS.map((m, i) => ({ label: m, value: monthlyBars[i] }))} format={inr} height={180} highlight={params.month - 1} caption={`Monthly revenue, ${params.year}`} onSelect={pickMonth} />
          {/* Period stats below bar chart */}
          {!loading && data && (
            <div className="pc-minis" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', marginTop: 18, paddingTop: 16, borderTop: '1px solid var(--pc-line)' }}>
              {displayFields.map(([key, val]) => (
                <div key={key} style={{ textTransform: 'capitalize', overflowWrap: 'anywhere' }}>
                  <b style={{ color: key.toLowerCase().includes('revenue') ? 'var(--pc-brand-deep)' : 'var(--pc-ink)', textTransform: 'none' }}>
                    {typeof val === 'number' && key.toLowerCase().includes('revenue') ? inr(val) : String(val)}
                  </b>
                  {key.replace(/([A-Z])/g, ' $1').trim()}
                </div>
              ))}
            </div>
          )}
          {loading && <div style={{ marginTop: 16 }}><Skeleton rows={2} /></div>}
        </Panel>

        {/* Pricing Config */}
        <Panel span={4} title="Pricing Config">
          <div className="pc-rows">
            <div style={{ padding: '4px 0 14px' }}>
              <div style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 700, marginBottom: 4 }}>Current Price/km</div>
              <div className="pc-figure" style={{ fontSize: 24, color: 'var(--pc-ink)', marginBottom: 10 }}>{rate(pricing?.pricePerKm)}</div>
              <div style={{ display: 'flex', gap: 6 }}>
                <input aria-label="New price per km" className="input-field" style={{ flex: 1, minWidth: 0 }} type="number" placeholder="New price/km" value={newPrice} onChange={e => setNewPrice(e.target.value)} />
                <button type="button" className={priceSuccess ? 'pc-btn pc-btn-teal' : 'pc-btn'} onClick={handleSetPrice} disabled={priceLoading || !newPrice}>
                  {priceLoading ? '...' : priceSuccess ? <><Icon name="check" size={16} />Saved</> : 'Set'}
                </button>
              </div>
            </div>
            <div style={{ padding: '14px 0 2px', borderTop: '1px solid var(--pc-line)' }}>
              <div style={{ fontSize: 12, color: 'var(--pc-muted)', fontWeight: 700, marginBottom: 4 }}>Current Price/hour</div>
              <div className="pc-figure" style={{ fontSize: 24, color: 'var(--pc-ink)', marginBottom: 10 }}>{rate(pricing?.pricePerHour)}</div>
              <div style={{ display: 'flex', gap: 6 }}>
                <input aria-label="New price per hour" className="input-field" style={{ flex: 1, minWidth: 0 }} type="number" placeholder="New price/hour" value={newHourlyPrice} onChange={e => setNewHourlyPrice(e.target.value)} />
                <button type="button" className={hourlyPriceSuccess ? 'pc-btn pc-btn-teal' : 'pc-btn'} onClick={handleSetHourlyPrice} disabled={hourlyPriceLoading || !newHourlyPrice}>
                  {hourlyPriceLoading ? '...' : hourlyPriceSuccess ? <><Icon name="check" size={16} />Saved</> : 'Set'}
                </button>
              </div>
            </div>
          </div>
        </Panel>

        {/* Full Report Table + Download */}
        <Panel span={12} pad={false} title="Complete Trip Report" meta={`${filteredBookings.length} trips found`}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', padding: '0 20px 14px' }}>
            {/* Status filter */}
            <div className="pc-chips" role="group" aria-label="Filter by status" style={{ gap: 6 }}>
              {['all', 'COMPLETED', 'CONFIRMED', 'PENDING', 'CANCELLED'].map(f => (
                <button key={f} type="button" aria-pressed={reportFilter === f} className={toggleClass(reportFilter === f)} onClick={() => setReportFilter(f)}>
                  {f === 'all' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
            {/* Download buttons */}
            <div className="pc-chips" style={{ gap: 6 }}>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={downloadReportPDF}><Icon name="download" size={15} />Report PDF</button>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={downloadReportExcel}><Icon name="download" size={15} />Report Excel</button>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={downloadTripPDF}><Icon name="download" size={15} />Trip+Driver PDF</button>
              <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={downloadTripExcel}><Icon name="download" size={15} />Trip+Driver Excel</button>
            </div>
          </div>
          {filteredBookings.length === 0 ? (
            <Empty icon="calendar" title="No bookings found for this filter">Try another status, or check back once trips are booked.</Empty>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table animated-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Customer</th>
                    <th>Route</th>
                    <th>Dates</th>
                    <th>Details</th>
                    <th>Driver</th>
                    <th style={{ textAlign: 'right' }}>Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredBookings.map((b, i) => {
                    const drv = b.driver || driverMap[b.driverId] || {}
                    return (
                      <tr key={b.bookingId || b.id || i}>
                        <td style={{ color: 'var(--pc-muted)' }}>{i + 1}</td>
                        <td>
                          <div className="pc-row-title">{b.userName || '—'}</div>
                          <small style={{ color: 'var(--pc-muted)' }}>{b.userPhone || ''}</small>
                        </td>
                        <td title={`${b.fromPlace || ''} → ${b.toPlace || ''}`}>
                          <div className="pc-row-title">{shortPlace(b.fromPlace)}</div>
                          <small style={{ color: 'var(--pc-muted)' }}>→ {shortPlace(b.toPlace)}</small>
                        </td>
                        <td>
                          <div style={{ color: 'var(--pc-ink)' }}>{b.fromDate || '—'}</div>
                          <small style={{ color: 'var(--pc-muted)' }}>to {b.toDate || '—'}</small>
                        </td>
                        <td>
                          <div style={{ fontSize: 12.5 }}>
                            {b.distanceKm ? `${Number(b.distanceKm).toFixed(1)} km` : '—'} · {b.travelDays || 0}d · {b.travelMembers || 0}p · {b.acType || '—'}
                          </div>
                          <small style={{ color: 'var(--pc-muted)' }}>
                            {b.estimatedTimeFormatted || (b.estimatedTimeMinutes ? `${Math.floor(b.estimatedTimeMinutes / 60)}h ${b.estimatedTimeMinutes % 60}m` : '')}
                          </small>
                        </td>
                        <td>
                          {drv.name ? (
                            <div>
                              <div className="pc-row-title">{drv.name}</div>
                              <small style={{ color: 'var(--pc-muted)' }}>{drv.mobile || ''}</small>
                            </div>
                          ) : <span style={{ color: 'var(--pc-muted)' }}>Not assigned</span>}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 800, color: 'var(--pc-ink)', whiteSpace: 'nowrap' }}>{inr(b.totalAmount)}</td>
                        <td><StatusPill status={b.status} /></td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      </div>
    </DashboardLayout>
  )
}
