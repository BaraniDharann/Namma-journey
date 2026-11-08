import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DashboardLayout from '../../../components/DashboardLayout'
import Icon from '../../../components/dash/Icon'
import { Panel, PageHead, Skeleton } from '../../../components/dash/ui'
import PlacePicker from '../../../components/bus/PlacePicker'
import {
  BusPill, Notice, Steps, ownerNavItems, labelStyle, capsStyle, wellStyle, fieldGrid, mutedStyle,
} from '../../../components/bus/busUi'
import {
  getBuses, getBusRoute, createBusRoute, updateBusRoute, publishBusRoute, pauseBusRoute,
} from '../../../utils/busApi'
import { seatClassesOf, seatClassName } from '../../../utils/busLayouts'
import {
  DEFAULT_POLICY, WEEKDAYS, fareKey, faresToGrid, fillMiddleFares, gridToFares, normalisePolicy,
  policyProblems, remapFareGrid, routeProblems, slabText, stopPairs,
} from '../../../utils/busFares'
import { ROUTE_STATUS, errorText, isoDate } from '../../../utils/busText'

const STEPS = ['Bus and days', 'Stops and points', 'Fares', 'Cancellation']
const POINT_KINDS = [['BOARDING', 'Boarding'], ['DROPPING', 'Dropping'], ['BOTH', 'Both']]

const blankPoint = (kind) => ({ kind, name: '', address: '', landmark: '', lat: null, lng: null, dayOffset: 0, time: '' })

const emptyRoute = () => ({
  busId: '', name: '', runDays: [1, 2, 3, 4, 5, 6, 7], activeFrom: isoDate(), activeTo: '',
  maxSeatsPerBooking: 6, cancellationPolicy: DEFAULT_POLICY.map((s) => ({ ...s })), stops: [], status: 'DRAFT',
})

function fromServer(r) {
  return {
    busId: r.busId, name: r.name || '', runDays: r.runDays || [], activeFrom: r.activeFrom || '', activeTo: r.activeTo || '',
    maxSeatsPerBooking: r.maxSeatsPerBooking ?? 6,
    cancellationPolicy: (r.cancellationPolicy?.length ? r.cancellationPolicy : DEFAULT_POLICY).map((s) => ({ ...s })),
    stops: [...(r.stops || [])].sort((a, b) => a.seq - b.seq).map((s) => ({
      placeId: s.placeId, placeName: s.placeName, points: (s.points || []).map((p) => ({ ...p, time: (p.time || '').slice(0, 5) })),
    })),
    status: r.status,
  }
}

function PointRow({ point, onChange, onRemove, idp }) {
  const set = (k, v) => onChange({ ...point, [k]: v })
  return (
    <div style={{ ...wellStyle, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: 10, alignItems: 'end' }}>
      <div>
        <label htmlFor={`${idp}-kind`} style={labelStyle}>Type</label>
        <select id={`${idp}-kind`} className="input-field" value={point.kind} onChange={(e) => set('kind', e.target.value)}>
          {POINT_KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>
      <div style={{ gridColumn: 'span 2' }}>
        <label htmlFor={`${idp}-name`} style={labelStyle}>Point name *</label>
        <input id={`${idp}-name`} className="input-field" placeholder="Koyambedu bus stand" maxLength={100} value={point.name} onChange={(e) => set('name', e.target.value)} />
      </div>
      <div>
        <label htmlFor={`${idp}-time`} style={labelStyle}>Time *</label>
        <input id={`${idp}-time`} type="time" className="input-field" value={point.time} onChange={(e) => set('time', e.target.value)} />
      </div>
      <div>
        <label htmlFor={`${idp}-day`} style={labelStyle}>Day</label>
        <select id={`${idp}-day`} className="input-field" value={point.dayOffset} onChange={(e) => set('dayOffset', Number(e.target.value))}>
          <option value={0}>Same day</option>
          <option value={1}>Next day (+1)</option>
          <option value={2}>Day after (+2)</option>
        </select>
      </div>
      <div style={{ gridColumn: 'span 2' }}>
        <label htmlFor={`${idp}-addr`} style={labelStyle}>Address</label>
        <input id={`${idp}-addr`} className="input-field" maxLength={200} value={point.address || ''} onChange={(e) => set('address', e.target.value)} />
      </div>
      <div>
        <label htmlFor={`${idp}-lm`} style={labelStyle}>Landmark</label>
        <input id={`${idp}-lm`} className="input-field" maxLength={100} value={point.landmark || ''} onChange={(e) => set('landmark', e.target.value)} />
      </div>
      <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ color: 'var(--pc-bad)' }} onClick={onRemove}>
        <Icon name="trash" size={14} />Remove
      </button>
    </div>
  )
}

/** Create or edit a bus route: bus and days, stops with their points, fares and the refund rules. */
export default function OwnerBusRouteWizard() {
  const { id } = useParams()
  const isNew = !id || id === 'new'
  const navigate = useNavigate()
  const [route, setRoute] = useState(emptyRoute)
  const [routeId, setRouteId] = useState(isNew ? null : id)
  const [grid, setGrid] = useState({})
  const [buses, setBuses] = useState([])
  const [loading, setLoading] = useState(true)
  const [step, setStep] = useState(0)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)

  // The id of the route already on screen: the first save of a new route moves the URL to its
  // id, and that must not reload (a reload could land after a publish and show stale state).
  const shown = useRef(null)
  const take = useCallback((r) => { setRoute(fromServer(r)); setGrid(faresToGrid(r.fares)); setRouteId(r.id); shown.current = String(r.id) }, [])

  useEffect(() => {
    if (!isNew && shown.current === String(id)) return undefined
    let live = true
    setLoading(true)
    Promise.all([getBuses(), isNew ? null : getBusRoute(id)])
      .then(([b, r]) => { if (!live) return; setBuses(b.data || []); if (r) take(r.data) })
      .catch((e) => live && setNotice({ tone: 'error', text: errorText(e, 'Could not load this route.') }))
      .finally(() => live && setLoading(false))
    return () => { live = false }
  }, [id, isNew, take])

  const published = route.status !== 'DRAFT'
  const bus = buses.find((b) => String(b.id) === String(route.busId))
  const classes = useMemo(() => seatClassesOf(bus?.seats || []), [bus])
  const approved = buses.filter((b) => b.status === 'APPROVED' || String(b.id) === String(route.busId))
  const set = (k, v) => setRoute((r) => ({ ...r, [k]: v }))

  // Stops: changing their order keeps fares attached to the same towns.
  const setStops = (stops) => {
    setGrid((g) => remapFareGrid(g, route.stops.map((s) => s.placeId), stops.map((s) => s.placeId)))
    set('stops', stops)
  }
  const addStop = (place) => {
    const first = route.stops.length === 0
    setStops([...route.stops, { placeId: place.id, placeName: place.name, points: [blankPoint(first ? 'BOARDING' : 'DROPPING')] }])
  }
  const moveStop = (i, d) => {
    const next = [...route.stops]
    const [s] = next.splice(i, 1)
    next.splice(i + d, 0, s)
    setStops(next)
  }
  const setPoints = (i, points) => set('stops', route.stops.map((s, k) => (k === i ? { ...s, points } : s)))

  const body = () => ({
    busId: Number(route.busId),
    name: route.name.trim() || (route.stops.length > 1 ? `${route.stops[0].placeName} to ${route.stops[route.stops.length - 1].placeName}` : 'New route'),
    runDays: [...route.runDays].sort(),
    activeFrom: route.activeFrom,
    activeTo: route.activeTo || null,
    maxSeatsPerBooking: Number(route.maxSeatsPerBooking),
    cancellationPolicy: normalisePolicy(route.cancellationPolicy),
    stops: route.stops.map((s, seq) => ({
      seq, placeId: s.placeId,
      points: s.points.map((p) => ({ ...p, name: p.name.trim(), dayOffset: Number(p.dayOffset) || 0 })),
    })),
    fares: gridToFares(grid, route.stops.length, classes),
  })

  const save = async (quiet) => {
    setBusy(true); setNotice(null)
    try {
      const res = routeId ? await updateBusRoute(routeId, body()) : await createBusRoute(body())
      take(res.data)
      if (!routeId || isNew) navigate(`/owner/bus-routes/${res.data.id}`, { replace: true })
      if (!quiet) setNotice({ tone: 'ok', text: 'Route saved.' })
      return res.data
    } catch (e) {
      setNotice({ tone: 'error', text: errorText(e, 'Could not save this route.') })
      return null
    } finally { setBusy(false) }
  }

  const act = async (fn, ok) => {
    const saved = await save(true)
    if (!saved) return
    setBusy(true)
    try { take((await fn(saved.id)).data); setNotice({ tone: 'ok', text: ok }) }
    catch (e) { setNotice({ tone: 'error', text: errorText(e, 'Could not do that right now.') }) }
    finally { setBusy(false) }
  }

  const publish = () => act(publishBusRoute, 'Published. Trips are being created for the next 60 days.')
  const pause = () => {
    if (window.confirm('Pause this route? No new trips are created and future trips are hidden from search. Tickets already sold stay valid.')) {
      act(pauseBusRoute, 'Paused.')
    }
  }

  if (loading) {
    return (
      <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
        <Panel span={12}><Skeleton rows={6} height={18} /></Panel>
      </DashboardLayout>
    )
  }

  const problems = [...routeProblems(route), ...policyProblems(route.cancellationPolicy)]
  const fares = gridToFares(grid, route.stops.length, classes)
  const publishProblems = [...problems, ...(fares.length ? [] : ['Enter at least one fare.']), ...(bus?.status === 'APPROVED' ? [] : ['The bus must be approved.'])]
  const pairs = stopPairs(route.stops.length)
  const placeName = (seq) => route.stops[seq]?.placeName

  return (
    <DashboardLayout navItems={ownerNavItems} role="ROLE_OWNER">
      <p style={{ marginBottom: 8 }}><Link to="/owner/bus-routes">← All routes</Link></p>
      <PageHead title={isNew && !routeId ? 'New bus route' : (route.name || 'Bus route')}
        sub={bus ? `${bus.name} · ${bus.registrationNumber}` : 'Choose a bus to start'}>
        {routeId && <BusPill table={ROUTE_STATUS} status={route.status} />}
        <button type="button" className="pc-btn pc-btn-ghost" disabled={busy || !route.busId} onClick={() => save(false)}>
          <Icon name="check" />{route.status === 'DRAFT' ? 'Save draft' : 'Save changes'}
        </button>
        {route.status !== 'PUBLISHED' && (
          <button type="button" className="pc-btn" disabled={busy || publishProblems.length > 0} onClick={publish}
            title={publishProblems.length ? publishProblems.join(' ') : undefined}>
            <Icon name="send" />{route.status === 'PAUSED' ? 'Publish again' : 'Publish'}
          </button>
        )}
        {route.status === 'PUBLISHED' && (
          <button type="button" className="pc-btn pc-btn-ghost" disabled={busy} onClick={pause}><Icon name="power" />Pause</button>
        )}
      </PageHead>

      <Notice notice={notice} onClose={() => setNotice(null)} />
      {published && (
        <div style={{ ...wellStyle, marginBottom: 16, display: 'flex', gap: 10, alignItems: 'center', fontSize: 13 }}>
          <Icon name="lock" size={16} /><span>The bus and stops are fixed once a route is published. Points, fares, days and the refund rules can still change; changes apply to new bookings only.</span>
        </div>
      )}

      <Steps steps={STEPS} step={step} onStep={setStep} enabled={(i) => i === 0 || !!route.busId} />

      <div className="pc-grid">
        {step === 0 && (
          <Panel span={8} title="Bus and days">
            <div style={fieldGrid}>
              <div>
                <label htmlFor="rt-bus" style={labelStyle}>Bus *</label>
                <select id="rt-bus" className="input-field" value={route.busId} disabled={published}
                  onChange={(e) => set('busId', e.target.value)}>
                  <option value="">Choose a bus</option>
                  {approved.map((b) => <option key={b.id} value={b.id}>{b.name} · {b.registrationNumber}{b.typeLabel ? ` · ${b.typeLabel}` : ''}</option>)}
                </select>
                {!approved.length && <p style={{ ...mutedStyle, marginTop: 6 }}>No bus in service yet. <Link to="/owner/buses">Add one</Link> and put it in service first.</p>}
              </div>
              <div>
                <label htmlFor="rt-name" style={labelStyle}>Route name</label>
                <input id="rt-name" className="input-field" placeholder="Chennai to Madurai night" maxLength={100} value={route.name} onChange={(e) => set('name', e.target.value)} />
              </div>
              <div>
                <label htmlFor="rt-from" style={labelStyle}>Runs from *</label>
                <input id="rt-from" type="date" className="input-field" value={route.activeFrom} onChange={(e) => set('activeFrom', e.target.value)} />
              </div>
              <div>
                <label htmlFor="rt-to" style={labelStyle}>Runs until</label>
                <input id="rt-to" type="date" className="input-field" min={route.activeFrom} value={route.activeTo} onChange={(e) => set('activeTo', e.target.value)} />
                <p style={{ ...mutedStyle, marginTop: 4 }}>Leave empty to keep running.</p>
              </div>
              <div>
                <label htmlFor="rt-max" style={labelStyle}>Most seats in one booking</label>
                <input id="rt-max" type="number" min={1} max={10} className="input-field" value={route.maxSeatsPerBooking} onChange={(e) => set('maxSeatsPerBooking', e.target.value)} />
              </div>
            </div>
            <fieldset style={{ border: 'none', padding: 0, margin: '18px 0 0' }}>
              <legend style={labelStyle}>Runs on *</legend>
              <div style={{ display: 'flex', columnGap: 14, flexWrap: 'wrap' }}>
                {WEEKDAYS.map(([n, l]) => (
                  <label key={n} style={{ display: 'inline-flex', gap: 6, alignItems: 'center', minHeight: 40, fontSize: 13, fontWeight: 700, cursor: 'pointer' }}>
                    <input type="checkbox" checked={route.runDays.includes(n)}
                      onChange={(e) => set('runDays', e.target.checked ? [...route.runDays, n] : route.runDays.filter((d) => d !== n))} />
                    {l}
                  </label>
                ))}
              </div>
            </fieldset>
          </Panel>
        )}

        {step === 1 && (
          <Panel span={12} title="Stops and points" meta="In travel order">
            <div style={{ display: 'grid', gap: 18 }}>
              {route.stops.map((s, i) => (
                <div key={s.placeId} style={{ borderTop: i ? '1px solid var(--pc-line)' : 'none', paddingTop: i ? 16 : 0 }}>
                  <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginBottom: 10 }}>
                    <span className="pc-chip" style={{ fontWeight: 800 }}>{i + 1}</span>
                    <b style={{ fontSize: 16 }}>{s.placeName}</b>
                    <span style={mutedStyle}>{i === 0 ? 'First stop: needs a boarding point' : i === route.stops.length - 1 ? 'Last stop: needs a dropping point' : ''}</span>
                    <span style={{ flex: 1 }} />
                    {!published && (
                      <>
                        <button type="button" className="pc-iconbtn" aria-label={`Move ${s.placeName} up`} disabled={i === 0} onClick={() => moveStop(i, -1)}><Icon name="arrow" size={14} style={{ transform: 'rotate(-90deg)' }} /></button>
                        <button type="button" className="pc-iconbtn" aria-label={`Move ${s.placeName} down`} disabled={i === route.stops.length - 1} onClick={() => moveStop(i, 1)}><Icon name="arrow" size={14} style={{ transform: 'rotate(90deg)' }} /></button>
                        <button type="button" className="pc-iconbtn" aria-label={`Remove ${s.placeName}`} onClick={() => setStops(route.stops.filter((_, k) => k !== i))}><Icon name="trash" size={14} /></button>
                      </>
                    )}
                  </div>
                  <div style={{ display: 'grid', gap: 10 }}>
                    {s.points.map((p, k) => (
                      <PointRow key={p.id ?? `n${k}`} idp={`pt-${i}-${k}`} point={p}
                        onChange={(np) => setPoints(i, s.points.map((x, j) => (j === k ? np : x)))}
                        onRemove={() => setPoints(i, s.points.filter((_, j) => j !== k))} />
                    ))}
                    <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" style={{ width: 'fit-content' }}
                      onClick={() => setPoints(i, [...s.points, blankPoint(i === 0 ? 'BOARDING' : i === route.stops.length - 1 ? 'DROPPING' : 'BOTH')])}>
                      <Icon name="plus" size={14} />Add a point at {s.placeName}
                    </button>
                  </div>
                </div>
              ))}
              {!published && (
                <div style={{ maxWidth: 420 }}>
                  <label htmlFor="rt-add-stop" style={labelStyle}>{route.stops.length ? 'Add the next stop' : 'Starting town'}</label>
                  <PlacePicker id="rt-add-stop" onPick={addStop} exclude={route.stops.map((s) => s.placeId)} />
                </div>
              )}
            </div>
          </Panel>
        )}

        {step === 2 && (
          <Panel span={12} pad={false} title="Fares" meta="Without GST; 5% GST is added at checkout">
            {route.stops.length < 2 || !classes.length ? (
              <p style={{ padding: '0 20px 20px', fontSize: 13 }}>Add at least two stops and pick a bus with a seat layout first.</p>
            ) : (
              <>
                <div style={{ padding: '0 20px 12px', display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                  <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => setGrid((g) => fillMiddleFares(g, route.stops.length, classes))}>
                    <Icon name="sparkle" size={14} />Fill middle stops
                  </button>
                  <span style={mutedStyle}>Enter the full-route fare ({placeName(0)} to {placeName(route.stops.length - 1)}), then fill the shorter trips by their share of the stops. Fares you typed are kept.</span>
                </div>
                <div style={{ overflowX: 'auto' }}>
                  <table className="data-table">
                    <thead>
                      <tr><th>From → to</th>{classes.map((c) => <th key={c} style={{ textAlign: 'right' }}>{seatClassName(c)}</th>)}</tr>
                    </thead>
                    <tbody>
                      {pairs.map(({ fromSeq, toSeq }) => (
                        <tr key={`${fromSeq}-${toSeq}`}>
                          <td style={{ whiteSpace: 'nowrap', fontWeight: fromSeq === 0 && toSeq === route.stops.length - 1 ? 800 : 600 }}>
                            {placeName(fromSeq)} → {placeName(toSeq)}
                          </td>
                          {classes.map((c) => {
                            const k = fareKey(fromSeq, toSeq, c)
                            return (
                              <td key={c} style={{ textAlign: 'right' }}>
                                <input type="number" min={0} step={10} className="input-field" inputMode="numeric"
                                  aria-label={`Fare ${placeName(fromSeq)} to ${placeName(toSeq)}, ${seatClassName(c)}`}
                                  style={{ width: 110, textAlign: 'right' }} value={grid[k] ?? ''} placeholder="₹"
                                  onChange={(e) => setGrid((g) => ({ ...g, [k]: e.target.value }))} />
                              </td>
                            )
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p style={{ ...mutedStyle, padding: '10px 20px 18px' }}>Leave a cell empty to not sell that trip in that class.</p>
              </>
            )}
          </Panel>
        )}

        {step === 3 && (
          <Panel span={8} title="Cancellation charges" meta="Counted to the passenger's boarding time">
            <div style={{ display: 'grid', gap: 10 }}>
              {route.cancellationPolicy.map((s, i) => (
                <div key={i} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                  <div>
                    <label htmlFor={`slab-h-${i}`} style={labelStyle}>Hours before boarding (at least)</label>
                    <input id={`slab-h-${i}`} type="number" min={0} className="input-field" style={{ width: 140 }} value={s.minHours}
                      onChange={(e) => set('cancellationPolicy', route.cancellationPolicy.map((x, k) => (k === i ? { ...x, minHours: e.target.value } : x)))} />
                  </div>
                  <div>
                    <label htmlFor={`slab-p-${i}`} style={labelStyle}>Charge %</label>
                    <input id={`slab-p-${i}`} type="number" min={0} max={100} className="input-field" style={{ width: 110 }} value={s.percent}
                      onChange={(e) => set('cancellationPolicy', route.cancellationPolicy.map((x, k) => (k === i ? { ...x, percent: e.target.value } : x)))} />
                  </div>
                  <button type="button" className="pc-iconbtn" aria-label="Remove slab" onClick={() => set('cancellationPolicy', route.cancellationPolicy.filter((_, k) => k !== i))}>
                    <Icon name="trash" size={14} />
                  </button>
                </div>
              ))}
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => set('cancellationPolicy', [...route.cancellationPolicy, { minHours: '', percent: '' }])}>
                  <Icon name="plus" size={14} />Add slab
                </button>
                <button type="button" className="pc-btn pc-btn-ghost pc-btn-sm" onClick={() => set('cancellationPolicy', DEFAULT_POLICY.map((x) => ({ ...x })))}>
                  <Icon name="refresh" size={14} />Use the standard slabs
                </button>
              </div>
            </div>
            {!policyProblems(route.cancellationPolicy).length && (
              <div style={{ ...wellStyle, marginTop: 16 }}>
                <span style={capsStyle}>Travellers will see</span>
                <ul style={{ margin: '8px 0 0', paddingLeft: 18, fontSize: 13 }}>
                  {normalisePolicy(route.cancellationPolicy).map((s, i, all) => <li key={i}>{slabText(s, i, all)}</li>)}
                </ul>
              </div>
            )}
          </Panel>
        )}

        {problems.length > 0 && (
          <Panel span={step === 1 || step === 2 ? 12 : 4} title="Still to do">
            <ul style={{ fontSize: 13, paddingLeft: 18, margin: 0 }}>{problems.map((p) => <li key={p}>{p}</li>)}</ul>
          </Panel>
        )}
      </div>

      {step < STEPS.length - 1 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <button type="button" className="pc-btn pc-btn-ghost" disabled={!route.busId} onClick={() => setStep(step + 1)}>
            Next: {STEPS[step + 1]}<Icon name="arrow" size={14} />
          </button>
        </div>
      )}
    </DashboardLayout>
  )
}
