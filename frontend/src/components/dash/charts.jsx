import React, { useEffect, useId, useState } from 'react'

/**
 * Hand-built charts for the Postcard dashboards. Colours come from the --pc-s1..s5 series
 * tokens (fixed order, CVD-validated); every chart carries a legend or direct labels, hover
 * values via data-tip (see TipLayer), and a visually hidden table so no value is chart-only.
 */

const fmtDefault = (v) => Math.round(v).toLocaleString('en-IN')

/**
 * Width of the chart's box. A callback ref, so measuring starts whenever the box mounts —
 * the chart often first renders an empty state while its data loads.
 */
function useWidth(fallback = 600) {
  const [el, setEl] = useState(null)
  const [w, setW] = useState(fallback)
  useEffect(() => {
    if (!el || typeof ResizeObserver === 'undefined') return undefined
    const ro = new ResizeObserver(([e]) => setW(Math.max(160, Math.round(e.contentRect.width))))
    ro.observe(el)
    return () => ro.disconnect()
  }, [el])
  return [setEl, w]
}

function SrTable({ caption, head, rows }) {
  return (
    <table className="pc-sr">
      <caption>{caption}</caption>
      <thead><tr>{head.map((h) => <th key={h} scope="col">{h}</th>)}</tr></thead>
      <tbody>{rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody>
    </table>
  )
}

const niceMax = (v) => {
  if (v <= 0) return 1
  const p = 10 ** Math.floor(Math.log10(v))
  const n = v / p
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p
}
const short = (v, max = v) => (!v ? '0' : max >= 100000 ? `${+(v / 100000).toFixed(2)}L` : max >= 1000 ? `${+(v / 1000).toFixed(1)}k` : `${Math.round(v)}`)

export function NoData({ children = 'Nothing to chart yet.' }) {
  return <div className="pc-nodata">{children}</div>
}

/** Change over time: one line + soft area, crosshair on hover, the peak labelled. */
/**
 * `variant="line"` draws the stock-ticker form: no fill, a dot per point, and the latest value
 * with its change since the first point.
 */
export function AreaChart({ data, format = fmtDefault, height = 200, caption = 'Trend', variant = 'area' }) {
  const [ref, W] = useWidth()
  const [hover, setHover] = useState(null)
  const gid = useId().replace(/:/g, '')
  if (!data?.length || data.every((d) => !d.value)) return <NoData />
  const H = height, pl = 40, pr = 12, pt = 18, pb = 24
  const max = niceMax(Math.max(...data.map((d) => d.value)))
  const x = (i) => pl + (data.length === 1 ? 0.5 : i / (data.length - 1)) * (W - pl - pr)
  const y = (v) => pt + (1 - v / max) * (H - pt - pb)
  const line = data.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(d.value)}`).join(' ')
  const peak = data.reduce((best, d, i) => (d.value > data[best].value ? i : best), 0)
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * max)
  const every = Math.max(1, Math.ceil(data.length / Math.max(2, Math.floor(W / 70))))
  return (
    <div ref={ref} className="pc-chart">
      <svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} role="img" aria-label={caption} onMouseLeave={() => setHover(null)}>
        <defs>
          <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="var(--pc-brand)" stopOpacity=".26" />
            <stop offset="1" stopColor="var(--pc-brand)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {ticks.map((t) => (
          <g key={t}>
            <line className="pc-gridline" x1={pl} x2={W - pr} y1={y(t)} y2={y(t)} />
            <text className="pc-ax" x={pl - 8} y={y(t) + 3.5} textAnchor="end">{short(t, max)}</text>
          </g>
        ))}
        {data.map((d, i) => (i % every === 0 || i === data.length - 1) && (
          <text key={d.label} className="pc-ax" x={x(i)} y={H - 6} textAnchor="middle">{d.label}</text>
        ))}
        {variant === 'area' && <path className="pc-area-fill" d={`${line} L${x(data.length - 1)},${y(0)} L${x(0)},${y(0)} Z`} fill={`url(#${gid})`} />}
        <path className="pc-area-line" d={line} pathLength="1" fill="none" stroke={variant === 'line' ? 'var(--pc-s1)' : 'var(--pc-brand)'} strokeWidth={variant === 'line' ? 2 : 2.2} strokeLinejoin="round" strokeLinecap="round" />
        {variant === 'line' && data.map((d, i) => (
          <circle key={i} className="pc-line-dot" cx={x(i)} cy={y(d.value)} r="3.2" fill="var(--pc-panel)" stroke="var(--pc-s1)" strokeWidth="2" style={{ animationDelay: `${0.4 + i * 0.04}s` }} />
        ))}
        {hover != null && <line x1={x(hover)} x2={x(hover)} y1={pt} y2={H - pb} stroke="var(--pc-ink-2)" strokeOpacity=".35" />}
        <circle cx={x(hover ?? peak)} cy={y(data[hover ?? peak].value)} r="4.5" fill={variant === 'line' ? 'var(--pc-s1)' : 'var(--pc-brand)'} stroke="var(--pc-panel)" strokeWidth="2" />
        {variant === 'line' && hover == null && (() => {
          const first = data[0].value, last = data[data.length - 1].value
          const ch = first ? ((last - first) / first) * 100 : null
          const lx = x(data.length - 1), ly = y(last)
          return (
            <g transform={`translate(${Math.min(lx, W - pr - 4)} ${Math.max(ly - 14, pt + 2)})`}>
              <text className="pc-ax pc-ax-strong" textAnchor="end">{format(last)}{ch == null ? '' : `  ${ch >= 0 ? '▲' : '▼'} ${Math.abs(ch).toFixed(1)}%`}</text>
            </g>
          )
        })()}
        {variant === 'area' && hover == null && (
          <text className="pc-ax pc-ax-strong" x={Math.min(Math.max(x(peak), pl + 40), W - pr - 40)} y={y(data[peak].value) - 10} textAnchor="middle">
            {format(data[peak].value)} · {data[peak].label}
          </text>
        )}
        {data.map((d, i) => (
          <rect
            key={i}
            x={x(i) - (W - pl - pr) / data.length / 2}
            y={0}
            width={(W - pl - pr) / data.length}
            height={H - pb}
            fill="transparent"
            data-tip={`${format(d.value)}|${d.label}${d.note ? ` · ${d.note}` : ''}`}
            onMouseEnter={() => setHover(i)}
          />
        ))}
      </svg>
      <SrTable caption={caption} head={['Period', 'Value']} rows={data.map((d) => [d.label, format(d.value)])} />
    </div>
  )
}

/**
 * Magnitude per category over time. One colour; the highlighted bar (default: today/last) in brand.
 * Pass onSelect(index) to make each bar a button (click, Enter or Space).
 */
export function BarChart({ data, format = fmtDefault, height = 160, highlight = data?.length - 1, caption = 'Bars', onSelect }) {
  if (!data?.length || data.every((d) => !d.value)) return <NoData />
  const max = Math.max(...data.map((d) => d.value)) || 1
  return (
    <div className="pc-chart">
      <div className="pc-bars" style={{ height }} role="img" aria-label={caption}>
        {data.map((d, i) => (
          <div
            key={d.label}
            className={onSelect ? "pc-bar-col is-pick" : "pc-bar-col"}
            data-tip={`${format(d.value)}|${d.label}`}
            tabIndex={0}
            role={onSelect ? "button" : undefined}
            aria-label={onSelect ? `${d.label}: ${format(d.value)}` : undefined}
            aria-pressed={onSelect ? i === highlight : undefined}
            onClick={onSelect ? () => onSelect(i) : undefined}
            onKeyDown={onSelect ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onSelect(i) } } : undefined}
          >
            <span className="pc-bar-v">{i === highlight && d.value ? format(d.value) : ''}</span>
            <i className={i === highlight ? 'pc-bar is-hi' : 'pc-bar'} style={{ height: `${Math.max((d.value / max) * 100, d.value ? 3 : 1)}%`, animationDelay: `${i * 45}ms` }} />
          </div>
        ))}
      </div>
      <div className="pc-bars-x">{data.map((d) => <span key={d.label}>{d.label}</span>)}</div>
      <SrTable caption={caption} head={['Label', 'Value']} rows={data.map((d) => [d.label, format(d.value)])} />
    </div>
  )
}

/** Ranked list with inline bars; values always printed beside the bar. */
export function HBarList({ rows, slot = 's1', format = (v) => v, caption = 'Ranking' }) {
  if (!rows?.length) return <NoData />
  const max = Math.max(...rows.map((r) => r[1])) || 1
  return (
    <div className="pc-hbars">
      {rows.map(([label, v], i) => (
        <div key={label} className="pc-hbar" data-tip={`${format(v)}|${label}`}>
          <span className="pc-hbar-l" title={label}>{label}</span>
          <span className="pc-hbar-t"><i style={{ width: `${(v / max) * 100}%`, background: `var(--pc-${slot})`, animationDelay: `${i * 60}ms` }} /></span>
          <b>{format(v)}</b>
        </div>
      ))}
      <SrTable caption={caption} head={['Item', 'Value']} rows={rows.map(([l, v]) => [l, format(v)])} />
    </div>
  )
}

/** Part-to-whole at a glance (≤ 6 segments), total in the middle, legend with values beside it. */
/** `solid` turns the ring into a full pie (no centre total). */
export function Donut({ segments, label = 'total', size = 136, caption = 'Breakdown', solid = false }) {
  const total = segments?.reduce((s, x) => s + x[1], 0) || 0
  if (!total) return <NoData />
  const r = solid ? size / 4 : size / 2 - 12
  const sw = solid ? size / 2 - 2 : 16
  const lens = segments.map((x) => (x[1] / total) * 100)
  const starts = lens.map((_, i) => lens.slice(0, i).reduce((a, b) => a + b, 0))
  return (
    <div className="pc-donut">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={caption}>
        <circle r={r} cx={size / 2} cy={size / 2} fill="none" stroke="var(--pc-wash)" strokeWidth={sw} />
        {segments.map(([name, v, slot], i) => {
          const len = lens[i]
          // A ring separates slices with a dash gap; a solid pie uses hairline spokes instead,
          // since a dash gap there widens into a wedge.
          const gap = segments.length > 1 && !solid ? 1.2 : 0
          return (
            <circle
              key={name}
              className="pc-donut-seg"
              r={r}
              cx={size / 2}
              cy={size / 2}
              fill="none"
              stroke={`var(--pc-${slot})`}
              strokeWidth={sw}
              pathLength="100"
              strokeDasharray={`${Math.max(len - gap, 0.4)} 100`}
              strokeDashoffset={-starts[i]}
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
              style={{ animationDelay: `${i * 90}ms` }}
              data-tip={`${name}: ${v}|${Math.round((v / total) * 100)}% of ${label}`}
              tabIndex={0}
            />
          )
        })}
        {solid && segments.length > 1 && starts.map((st, i) => {
          const t = (st / 100) * Math.PI * 2 - Math.PI / 2
          return <line key={i} x1={size / 2} y1={size / 2} x2={size / 2 + Math.cos(t) * (size / 2)} y2={size / 2 + Math.sin(t) * (size / 2)} stroke="#fff" strokeWidth="2" pointerEvents="none" />
        })}
        {!solid && <text x="50%" y="48%" textAnchor="middle" className="pc-donut-total">{total.toLocaleString('en-IN')}</text>}
        {!solid && <text x="50%" y="63%" textAnchor="middle" className="pc-ax">{label}</text>}
      </svg>
      <ul className="pc-legend pc-legend-col">
        {segments.map(([name, v, slot]) => (
          <li key={name}><i style={{ background: `var(--pc-${slot})` }} />{name}<b>{v.toLocaleString('en-IN')}</b></li>
        ))}
      </ul>
      <SrTable caption={caption} head={['Segment', 'Count']} rows={segments.map(([n, v]) => [n, v])} />
    </div>
  )
}

/** A single share against its whole (fleet busy, completion rate). */
export function Gauge({ pct, value, sub, caption = 'Gauge' }) {
  const S = 176, r = 68, cx = S / 2, cy = S / 2 + 4
  const p = Math.max(0, Math.min(1, pct || 0))
  const pt = (a) => [cx + r * Math.cos((a * Math.PI) / 180), cy + r * Math.sin((a * Math.PI) / 180)]
  const [x1, y1] = pt(135)
  const [x2, y2] = pt(405)
  const arc = `M${x1} ${y1} A${r} ${r} 0 1 1 ${x2} ${y2}`
  return (
    <div className="pc-gauge">
      <svg width={S} height={S - 22} viewBox={`0 0 ${S} ${S - 22}`} role="img" aria-label={`${caption}: ${Math.round(p * 100)}%`}>
        <path d={arc} fill="none" stroke="var(--pc-wash)" strokeWidth="14" strokeLinecap="round" />
        <path
          className="pc-gauge-arc"
          d={arc}
          pathLength="100"
          fill="none"
          stroke="var(--pc-brand)"
          strokeWidth="14"
          strokeLinecap="round"
          strokeDasharray={`${p * 100} 100`}
          data-tip={`${Math.round(p * 100)}%|${sub}`}
        />
      </svg>
      <div className="pc-gauge-mid"><b>{value}</b><span>{sub}</span></div>
    </div>
  )
}

/** Weekday × time-band intensity, one hue light→dark, with a scale legend. */
export function Heatmap({ matrix, rows, cols, note, caption = 'Heatmap' }) {
  const max = Math.max(1, ...matrix.flat())
  const total = matrix.flat().reduce((a, b) => a + b, 0)
  if (!total) return <NoData>No bookings yet to show a pattern.</NoData>
  const tone = (v) => `color-mix(in oklab, var(--pc-seq) ${v ? 14 + (v / max) * 76 : 4}%, var(--pc-panel))`
  return (
    <div className="pc-chart">
      <div className="pc-heat" style={{ gridTemplateColumns: `38px repeat(${cols.length}, 1fr)` }} role="img" aria-label={caption}>
        <span />
        {cols.map((c) => <span key={c} className="pc-heat-c">{c}</span>)}
        {matrix.map((row, r) => (
          <React.Fragment key={rows[r]}>
            <span className="pc-heat-r">{rows[r]}</span>
            {row.map((v, c) => (
              <i key={c} style={{ background: tone(v), animationDelay: `${(r + c) * 18}ms` }} data-tip={`${v} booking${v === 1 ? '' : 's'}|${rows[r]} · ${cols[c]}`} />
            ))}
          </React.Fragment>
        ))}
      </div>
      <div className="pc-heat-leg">
        Fewer {[0.1, 0.3, 0.55, 0.8, 1].map((f) => <i key={f} style={{ background: tone(f * max) }} />)} More
        {note && <span>· {note}</span>}
      </div>
      <SrTable caption={caption} head={['Day', ...cols]} rows={matrix.map((row, r) => [rows[r], ...row])} />
    </div>
  )
}

export function Legend({ items }) {
  return (
    <ul className="pc-legend">
      {items.map(([name, slot]) => <li key={name}><i style={{ background: `var(--pc-${slot})` }} />{name}</li>)}
    </ul>
  )
}
