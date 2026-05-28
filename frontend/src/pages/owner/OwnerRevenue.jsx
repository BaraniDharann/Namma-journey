import React, { useEffect, useState, useRef } from 'react'
import DashboardLayout from '../../components/DashboardLayout'
import { getDailyRevenue, getMonthlyRevenue, getYearlyRevenue, setPricing, setHourlyPricing, getCurrentPricing, getOwnerBookings, getOwnerDrivers } from '../../utils/api'
import { useAuth } from '../../context/AuthContext'
// Lazy-load heavy libs on first download
const getJsPDF = () => Promise.all([import('jspdf'), import('jspdf-autotable')]).then(([m]) => m.default)
const getXLSX = () => import('xlsx')

const navItems = [
  { path: '/owner/dashboard', icon: '🏠', label: 'Dashboard' },
  { path: '/owner/bookings', icon: '📋', label: 'All Bookings' },
  { path: '/owner/drivers', icon: '🚗', label: 'Drivers' },
  { path: '/owner/payments', icon: '💳', label: 'Payments' },
  { path: '/owner/reviews', icon: '⭐', label: 'Reviews' },
  { path: '/owner/packages', icon: '📦', label: 'Packages' },
  { path: '/owner/package-bookings', icon: '🎫', label: 'Package Bookings' },
  { path: '/owner/revenue', icon: '📊', label: 'Revenue' },
  { path: '/owner/profile', icon: '👤', label: 'Profile' },
]

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
const FULL_MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December']

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

// Simple SVG line/area chart
function RevenueChart({ data, labels, height = 200 }) {
  if (!data || data.length === 0) return null
  const max = Math.max(...data, 1)
  const w = 100, h = 100
  const padTop = 10, padBot = 5
  const points = data.map((v, i) => {
    const x = data.length === 1 ? w / 2 : (i / (data.length - 1)) * w
    const y = padTop + (1 - v / max) * (h - padTop - padBot)
    return { x, y, v }
  })
  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const areaPath = linePath + ` L${points[points.length - 1].x},${h} L${points[0].x},${h} Z`

  return (
    <div style={{ position: 'relative', width: '100%', height }}>
      <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', height: '100%' }} preserveAspectRatio="none">
        <defs>
          <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#f97316" stopOpacity="0.3" />
            <stop offset="100%" stopColor="#f97316" stopOpacity="0.02" />
          </linearGradient>
          <linearGradient id="lineGrad" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#f97316" />
            <stop offset="100%" stopColor="#8b5cf6" />
          </linearGradient>
        </defs>
        {/* Grid lines */}
        {[0.25, 0.5, 0.75, 1].map(f => {
          const y = padTop + (1 - f) * (h - padTop - padBot)
          return <line key={f} x1="0" y1={y} x2={w} y2={y} stroke="#e2e8f0" strokeWidth="0.3" />
        })}
        <path d={areaPath} fill="url(#areaGrad)" />
        <path d={linePath} fill="none" stroke="url(#lineGrad)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((p, i) => (
          <circle key={i} cx={p.x} cy={p.y} r="1.8" fill="#fff" stroke="#f97316" strokeWidth="0.8" />
        ))}
      </svg>
      {/* Labels */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
        {labels.map((l, i) => (
          <span key={i} style={{ fontSize: 10, color: '#94a3b8', textAlign: 'center', flex: 1 }}>{l}</span>
        ))}
      </div>
    </div>
  )
}

// Donut chart for status breakdown
function DonutChart({ segments, size = 120 }) {
  const total = segments.reduce((s, seg) => s + seg.value, 0)
  if (total === 0) return <div style={{ width: size, height: size, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8', fontSize: 12 }}>No data</div>
  const r = 40, cx = 50, cy = 50, sw = 12
  let acc = 0
  const arcs = segments.filter(s => s.value > 0).map(seg => {
    const frac = seg.value / total
    const startAngle = acc * 2 * Math.PI - Math.PI / 2
    acc += frac
    const endAngle = acc * 2 * Math.PI - Math.PI / 2
    const large = frac > 0.5 ? 1 : 0
    const x1 = cx + r * Math.cos(startAngle), y1 = cy + r * Math.sin(startAngle)
    const x2 = cx + r * Math.cos(endAngle), y2 = cy + r * Math.sin(endAngle)
    return { ...seg, d: `M${x1},${y1} A${r},${r} 0 ${large} 1 ${x2},${y2}`, frac }
  })
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <svg viewBox="0 0 100 100" width={size} height={size}>
        {arcs.map((a, i) => <path key={i} d={a.d} fill="none" stroke={a.color} strokeWidth={sw} strokeLinecap="round" />)}
        <text x={cx} y={cy - 4} textAnchor="middle" style={{ fontSize: 14, fontWeight: 900, fill: '#0F172A' }}>{total}</text>
        <text x={cx} y={cy + 9} textAnchor="middle" style={{ fontSize: 5.5, fill: '#94a3b8' }}>Total Trips</text>
      </svg>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {segments.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: 3, background: s.color }} />
            <span style={{ fontSize: 12, color: '#475569' }}>{s.label}: <b>{s.value}</b></span>
          </div>
        ))}
      </div>
    </div>
  )
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
      // Silent: 12 parallel calls — surface any backend issue once via fetchRevenue, not 12 toasts.
      const promises = Array.from({ length: 12 }, (_, i) =>
        getMonthlyRevenue(params.year, i + 1, { silent: true }).then(r => r.data?.totalRevenue || 0).catch(() => 0)
      )
      setMonthlyBars(await Promise.all(promises))
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
  const maxBar = Math.max(...monthlyBars, 1)
  const totalYearRevenue = monthlyBars.reduce((s, v) => s + v, 0)

  // Status breakdown from all bookings
  const statusCounts = { COMPLETED: 0, CONFIRMED: 0, PENDING: 0, CANCELLED: 0 }
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

  return (
    <DashboardLayout navItems={navItems} role="ROLE_OWNER">
      <div className="animate-fadeIn">
        <div style={{ marginBottom: 24 }}>
          <h1 style={{ fontFamily: 'Poppins', fontWeight: 900, fontSize: 26, color: '#0F172A', marginBottom: 4 }}>Revenue Analytics</h1>
          <p style={{ fontSize: 14, color: '#64748b' }}>Track earnings, view reports and download data</p>
        </div>

        {/* Top Stats Row */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))', gap: 14, marginBottom: 24 }} className="stagger-children stats-grid">
          {[
            { label: 'Total Bookings', value: allBookings.length, icon: '📋', color: '#f97316' },
            { label: 'Completed Trips', value: statusCounts.COMPLETED, icon: '✅', color: '#22c55e' },
            { label: `${params.year} Revenue`, value: `₹${totalYearRevenue.toLocaleString()}`, icon: '💰', color: '#8b5cf6' },
            { label: 'Price/km', value: `₹${pricing?.pricePerKm || '—'}`, icon: '🏷️', color: '#3b82f6' },
            { label: 'Price/hour', value: `₹${pricing?.pricePerHour || '—'}`, icon: '🕐', color: '#8b5cf6' },
          ].map((s, i) => (
            <div key={i} className="stat-card-enhanced" style={{ '--accent-color': s.color, background: '#fff', borderRadius: 18, padding: '18px 16px', border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: 14, background: `${s.color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20 }}>{s.icon}</div>
                <div>
                  <div style={{ fontSize: 22, fontWeight: 900, color: '#0F172A' }}>{s.value}</div>
                  <div style={{ fontSize: 12, color: '#64748b' }}>{s.label}</div>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="dashboard-grid-main" style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 20, marginBottom: 24 }}>
          {/* Revenue Line Chart */}
          <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
              <h3 style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>Revenue Trend — {params.year}</h3>
              <select className="input-field" style={{ maxWidth: 100, padding: '6px 10px', fontSize: 13, borderRadius: 10 }} value={params.year} onChange={e => setParams(p => ({ ...p, year: Number(e.target.value) }))}>
                {yearOptions.map(y => <option key={y} value={y}>{y}</option>)}
              </select>
            </div>
            <RevenueChart data={monthlyBars} labels={MONTHS} height={220} />
            <div style={{ display: 'flex', justifyContent: 'center', gap: 24, marginTop: 16 }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 20, fontWeight: 900, color: '#f97316' }}>₹{totalYearRevenue.toLocaleString()}</div>
                <div style={{ fontSize: 11, color: '#94a3b8' }}>Total Revenue</div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 20, fontWeight: 900, color: '#0F172A' }}>₹{totalYearRevenue > 0 ? Math.round(totalYearRevenue / 12).toLocaleString() : 0}</div>
                <div style={{ fontSize: 11, color: '#94a3b8' }}>Avg/Month</div>
              </div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 20, fontWeight: 900, color: '#22c55e' }}>₹{Math.max(...monthlyBars).toLocaleString()}</div>
                <div style={{ fontSize: 11, color: '#94a3b8' }}>Best Month</div>
              </div>
            </div>
          </div>

          {/* Right Column: Donut + Pricing */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ background: '#fff', borderRadius: 18, padding: 20, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
              <h3 style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 14 }}>Booking Status</h3>
              <DonutChart segments={[
                { label: 'Completed', value: statusCounts.COMPLETED, color: '#22c55e' },
                { label: 'Confirmed', value: statusCounts.CONFIRMED, color: '#3b82f6' },
                { label: 'Pending', value: statusCounts.PENDING, color: '#F59E0B' },
                { label: 'Cancelled', value: statusCounts.CANCELLED, color: '#ef4444' },
              ]} />
            </div>
            <div style={{ background: '#fff', borderRadius: 18, padding: 20, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
              <h3 style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 12 }}>Pricing Config</h3>
              <div style={{ padding: '12px 14px', borderRadius: 12, background: '#f3e8ff', border: '1px solid #d8b4fe', marginBottom: 10 }}>
                <div style={{ fontSize: 10, color: '#8b5cf6', fontWeight: 600, marginBottom: 2 }}>Current Price/km</div>
                <div style={{ fontSize: 22, fontWeight: 900, color: '#f97316' }}>₹{pricing?.pricePerKm || '—'}</div>
              </div>
              <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
                <input className="input-field" style={{ flex: 1, padding: '8px 12px', fontSize: 13, borderRadius: 10 }} type="number" placeholder="New price/km" value={newPrice} onChange={e => setNewPrice(e.target.value)} />
                <button onClick={handleSetPrice} disabled={priceLoading || !newPrice} style={{ padding: '0 14px', borderRadius: 10, fontWeight: 700, fontSize: 13, border: 'none', cursor: 'pointer', background: priceSuccess ? '#dcfce7' : 'linear-gradient(135deg,#f97316,#8b5cf6)', color: priceSuccess ? '#15803d' : '#fff' }}>
                  {priceLoading ? '...' : priceSuccess ? '✓' : 'Set'}
                </button>
              </div>
              <div style={{ padding: '12px 14px', borderRadius: 12, background: 'linear-gradient(135deg, #f3e8ff, #ffedd5)', border: '1px solid #d8b4fe', marginBottom: 10 }}>
                <div style={{ fontSize: 10, color: '#8b5cf6', fontWeight: 600, marginBottom: 2 }}>Current Price/hour</div>
                <div style={{ fontSize: 22, fontWeight: 900, color: '#8b5cf6' }}>₹{pricing?.pricePerHour || '—'}</div>
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                <input className="input-field" style={{ flex: 1, padding: '8px 12px', fontSize: 13, borderRadius: 10 }} type="number" placeholder="New price/hour" value={newHourlyPrice} onChange={e => setNewHourlyPrice(e.target.value)} />
                <button onClick={handleSetHourlyPrice} disabled={hourlyPriceLoading || !newHourlyPrice} style={{ padding: '0 14px', borderRadius: 10, fontWeight: 700, fontSize: 13, border: 'none', cursor: 'pointer', background: hourlyPriceSuccess ? '#dcfce7' : 'linear-gradient(135deg,#8b5cf6,#f97316)', color: hourlyPriceSuccess ? '#15803d' : '#fff' }}>
                  {hourlyPriceLoading ? '...' : hourlyPriceSuccess ? '✓' : 'Set'}
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Bar Chart */}
        <div style={{ background: '#fff', borderRadius: 18, padding: 24, marginBottom: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
            <h3 style={{ fontWeight: 700, fontSize: 15, color: '#0F172A' }}>Monthly Breakdown — {params.year}</h3>
            <div style={{ display: 'flex', gap: 4, background: '#f1f5f9', borderRadius: 10, padding: 3 }}>
              {['daily','monthly','yearly'].map(t => (
                <button key={t} onClick={() => setTab(t)} style={{ padding: '6px 14px', borderRadius: 8, fontSize: 11, fontWeight: 600, cursor: 'pointer', border: 'none', background: tab === t ? 'linear-gradient(135deg,#8b5cf6,#f97316)' : 'transparent', color: tab === t ? '#fff' : '#64748b', textTransform: 'capitalize', transition: 'all 0.2s', boxShadow: tab === t ? '0 2px 8px rgba(139,92,246,0.25)' : 'none' }}>{t}</button>
              ))}
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 6, height: 180, padding: '0 4px' }}>
            {MONTHS.map((m, i) => {
              const pct = maxBar > 0 ? (monthlyBars[i] / maxBar) * 100 : 0
              const isSelected = i === params.month - 1
              return (
                <div key={m} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                  {monthlyBars[i] > 0 && (
                    <span style={{ fontSize: 8, color: '#64748b', fontWeight: 700 }}>₹{Math.round(monthlyBars[i]).toLocaleString()}</span>
                  )}
                  <div style={{
                    width: '100%', maxWidth: 40,
                    borderRadius: '8px 8px 0 0',
                    height: `${Math.max(pct, 4)}%`,
                    background: isSelected ? 'linear-gradient(to top,#f97316,#8b5cf6)' : monthlyBars[i] > 0 ? 'linear-gradient(to top,#ffedd5,#fed7aa)' : '#f1f5f9',
                    transition: 'all 0.4s cubic-bezier(0.16,1,0.3,1)',
                    cursor: 'pointer',
                    boxShadow: isSelected ? '0 4px 12px rgba(249,115,22,0.3)' : 'none',
                  }}
                    onClick={() => { setTab('monthly'); setParams(p => ({ ...p, month: i + 1 })) }}
                  />
                  <span style={{ fontSize: 10, color: isSelected ? '#f97316' : '#94a3b8', fontWeight: isSelected ? 700 : 400 }}>{m}</span>
                </div>
              )
            })}
          </div>
          {/* Period stats below bar chart */}
          {!loading && data && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(140px,1fr))', gap: 10, marginTop: 20, paddingTop: 16, borderTop: '1px solid #f1f5f9' }}>
              {displayFields.map(([key, val]) => (
                <div key={key} style={{ padding: '10px 12px', borderRadius: 12, background: '#f8fafc', border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: 10, color: '#94a3b8', marginBottom: 3, textTransform: 'capitalize' }}>{key.replace(/([A-Z])/g,' $1').trim()}</div>
                  <div style={{ fontSize: 15, fontWeight: 800, color: key.toLowerCase().includes('revenue') ? '#f97316' : '#0F172A' }}>
                    {typeof val === 'number' && key.toLowerCase().includes('revenue') ? `₹${Number(val).toLocaleString()}` : String(val)}
                  </div>
                </div>
              ))}
            </div>
          )}
          {loading && <div style={{ display: 'flex', justifyContent: 'center', padding: '20px 0' }}><div className="spinner" /></div>}
        </div>

        {/* Full Report Table + Download */}
        <div style={{ background: '#fff', borderRadius: 18, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 2px 12px rgba(0,0,0,0.04)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
            <div>
              <h3 style={{ fontWeight: 700, fontSize: 16, color: '#0F172A' }}>Complete Trip Report</h3>
              <p style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{filteredBookings.length} trips found</p>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              {/* Status filter */}
              {['all', 'COMPLETED', 'CONFIRMED', 'PENDING', 'CANCELLED'].map(f => (
                <button key={f} onClick={() => setReportFilter(f)} style={{
                  padding: '6px 14px', borderRadius: 20, fontSize: 11, fontWeight: 600, cursor: 'pointer', border: 'none', textTransform: 'capitalize',
                  background: reportFilter === f ? 'linear-gradient(135deg, #f97316, #8b5cf6)' : '#f1f5f9', color: reportFilter === f ? '#fff' : '#64748b',
                  boxShadow: reportFilter === f ? '0 2px 8px rgba(249,115,22,0.25)' : 'none', transition: 'all 0.2s',
                }}>{f === 'all' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}</button>
              ))}
              {/* Download buttons */}
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <button onClick={downloadReportPDF} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 14px', borderRadius: 10, fontSize: 11, fontWeight: 700, cursor: 'pointer', border: 'none',
                  background: 'linear-gradient(135deg,#ef4444,#dc2626)', color: '#fff',
                }}>
                  📄 Report PDF
                </button>
                <button onClick={downloadReportExcel} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 14px', borderRadius: 10, fontSize: 11, fontWeight: 700, cursor: 'pointer', border: 'none',
                  background: 'linear-gradient(135deg,#22c55e,#16a34a)', color: '#fff',
                }}>
                  📊 Report Excel
                </button>
                <button onClick={downloadTripPDF} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 14px', borderRadius: 10, fontSize: 11, fontWeight: 700, cursor: 'pointer', border: 'none',
                  background: 'linear-gradient(135deg,#8b5cf6,#f97316)', color: '#fff',
                }}>
                  📄 Trip+Driver PDF
                </button>
                <button onClick={downloadTripExcel} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 14px', borderRadius: 10, fontSize: 11, fontWeight: 700, cursor: 'pointer', border: 'none',
                  background: 'linear-gradient(135deg,#3b82f6,#2563eb)', color: '#fff',
                }}>
                  📊 Trip+Driver Excel
                </button>
              </div>
            </div>
          </div>
          {filteredBookings.length === 0 ? (
            <div className="empty-state" style={{ padding: '32px 0' }}>
              <div style={{ fontSize: 32, marginBottom: 8 }}>📋</div>
              <p style={{ color: '#64748b', fontSize: 13 }}>No bookings found for this filter</p>
            </div>
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
                    <th>Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredBookings.map((b, i) => {
                    const drv = b.driver || driverMap[b.driverId] || {}
                    return (
                      <tr key={b.bookingId || b.id || i}>
                        <td style={{ fontSize: 12, color: '#94a3b8' }}>{i + 1}</td>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{b.userName || '—'}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>{b.userPhone || ''}</div>
                        </td>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{b.fromPlace}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>→ {b.toPlace}</div>
                        </td>
                        <td>
                          <div style={{ fontSize: 12, color: '#0F172A' }}>{b.fromDate || '—'}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>to {b.toDate || '—'}</div>
                        </td>
                        <td>
                          <div style={{ fontSize: 11, color: '#475569' }}>
                            {b.distanceKm ? `${Number(b.distanceKm).toFixed(1)} km` : '—'} · {b.travelDays || 0}d · {b.travelMembers || 0}p · {b.acType || '—'}
                          </div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>
                            {b.estimatedTimeFormatted || (b.estimatedTimeMinutes ? `${Math.floor(b.estimatedTimeMinutes / 60)}h ${b.estimatedTimeMinutes % 60}m` : '')}
                          </div>
                        </td>
                        <td>
                          {drv.name ? (
                            <div>
                              <div style={{ fontWeight: 600, fontSize: 12, color: '#0F172A' }}>{drv.name}</div>
                              <div style={{ fontSize: 11, color: '#94a3b8' }}>{drv.mobile || ''}</div>
                            </div>
                          ) : <span style={{ fontSize: 12, color: '#94a3b8' }}>Not assigned</span>}
                        </td>
                        <td style={{ fontWeight: 700, color: '#f97316', fontSize: 14, whiteSpace: 'nowrap' }}>₹{b.totalAmount?.toLocaleString() || 0}</td>
                        <td>
                          <span className={`badge badge-${b.status?.toLowerCase()}`} style={{ fontSize: 10 }}>{b.status}</span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </DashboardLayout>
  )
}
