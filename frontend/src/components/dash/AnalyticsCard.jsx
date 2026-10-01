import React, { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import Icon from './Icon'
import { AreaChart, BarChart, Donut, HBarList, NoData } from './charts'

/**
 * One analytics card per dashboard: pick what to look at (the tabs), then how to look at it (the
 * chart-type icons). A time series can be a stock-style line, an area or bars; a breakdown can be
 * a pie or bars. Types that would misread the data (a pie of months) are disabled, not hidden.
 *
 * datasets: [{ id, label, kind: 'series' | 'parts', data: [{ label, value, note? }], format? }]
 */
const TYPES = [
  { id: 'line', icon: 'line', label: 'Line', kinds: ['series'] },
  { id: 'area', icon: 'area', label: 'Area', kinds: ['series'] },
  { id: 'bar', icon: 'bar', label: 'Bars', kinds: ['series', 'parts'] },
  { id: 'pie', icon: 'pie', label: 'Pie', kinds: ['parts'] },
]
const SLOTS = ['s1', 's2', 's3', 's4', 's5']
const DEFAULT = { series: 'area', parts: 'pie' }

/** Breakdowns keep five colour slots; anything past that folds into "Other". */
function toSegments(data) {
  const sorted = [...data].sort((a, b) => b.value - a.value)
  const head = sorted.slice(0, 4)
  const rest = sorted.slice(4).reduce((s, d) => s + d.value, 0)
  const rows = rest ? [...head, { label: 'Other', value: rest }] : sorted.slice(0, 5)
  return rows.filter((d) => d.value > 0).map((d, i) => [d.label, d.value, SLOTS[i]])
}

export default function AnalyticsCard({ title = 'Analytics', datasets, span = 8, loading }) {
  const [dsId, setDsId] = useState(datasets[0]?.id)
  const ds = datasets.find((d) => d.id === dsId) || datasets[0]
  const [types, setTypes] = useState({})
  const type = types[ds?.id] || DEFAULT[ds?.kind] || 'bar'
  const fmt = ds?.format || ((v) => Math.round(v).toLocaleString('en-IN'))
  const pick = (t) => setTypes((m) => ({ ...m, [ds.id]: t }))

  let chart = <NoData />
  if (ds && !loading) {
    if (ds.kind === 'series') {
      if (type === 'bar') chart = <BarChart data={ds.data} format={fmt} height={220} caption={ds.label} />
      else chart = <AreaChart data={ds.data} format={fmt} height={240} caption={ds.label} variant={type === 'line' ? 'line' : 'area'} />
    } else if (type === 'pie') {
      chart = <Donut segments={toSegments(ds.data)} solid size={196} label={ds.label.toLowerCase()} caption={ds.label} />
    } else {
      chart = <HBarList rows={ds.data.map((d) => [d.label, d.value])} format={fmt} caption={ds.label} />
    }
  }

  return (
    <section className={`pc-panel pc-span-${span} pc-analytics`} aria-label={title}>
      <header className="pc-analytics-hd">
        <h2>{title}</h2>
        <div className="pc-analytics-types" role="group" aria-label="Chart type">
          {TYPES.map((t) => {
            const ok = ds && t.kinds.includes(ds.kind)
            return (
              <button
                key={t.id}
                type="button"
                className="pc-typebtn"
                aria-pressed={type === t.id}
                aria-label={`Show as ${t.label.toLowerCase()} chart`}
                title={ok ? `${t.label} chart` : `${t.label} does not suit this data`}
                disabled={!ok}
                onClick={() => pick(t.id)}
              >
                <Icon name={t.icon} size={20} />
              </button>
            )
          })}
        </div>
      </header>
      <div className="pc-analytics-tabs" role="tablist" aria-label="Data">
        {datasets.map((d) => (
          <button key={d.id} type="button" role="tab" aria-selected={d.id === ds.id} className="pc-tab" onClick={() => setDsId(d.id)}>
            {d.label}
          </button>
        ))}
      </div>
      <div className="pc-analytics-body">
        {loading ? (
          <div className="pc-skel-stack"><span className="pc-skel" style={{ height: 180, width: '100%' }} /></div>
        ) : (
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={`${ds.id}-${type}`}
              initial={{ opacity: 0, y: 10, filter: 'blur(4px)' }}
              animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            >
              {chart}
            </motion.div>
          </AnimatePresence>
        )}
      </div>
    </section>
  )
}
