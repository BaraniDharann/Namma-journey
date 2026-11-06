import React, { useId, useMemo, useState } from 'react'
import Icon from '../dash/Icon'
import { STATES, POPULAR_STATES } from '../../data/indiaPlaces'
import { INDIA, stateCode, cityCode, label, toggle, covered, search, validPlaceName } from '../../utils/serviceAreas'

/**
 * "Where do you run trips?" for a travels owner: a search over India's states and cities, quick
 * picks (whole India, all of Tamil Nadu, Kerala, ...), and a state-by-state list. Whole India
 * covers everything; a whole state covers its cities. A town that is not listed can be typed in.
 * `value` is a list of ServiceAreas codes; the search input is named `name` so a form can focus it.
 */
export default function ServiceAreaPicker({ value, onChange, name = 'serviceAreas', error }) {
  const [q, setQ] = useState('')
  const listId = useId()
  const results = useMemo(() => search(q), [q])
  const typed = q.trim().replace(/\s+/g, ' ')
  const canAddTyped = typed.length >= 2 && validPlaceName(typed)
    // not while it is just the start of a listed place ("madu" is Madurai on its way)
    && !results.some((r) => r.title.toLowerCase().startsWith(typed.toLowerCase()))
  const india = value.includes(INDIA)

  const pick = (code) => { onChange(toggle(value, code)); setQ('') }
  const addTyped = () => pick(cityCode('', typed.replace(/^./, (c) => c.toUpperCase())))

  const onKey = (e) => {
    if (e.key !== 'Enter') return
    e.preventDefault() // Enter here picks a place; it must not move the form to the next step
    if (results[0]) pick(results[0].code)
    else if (canAddTyped) addTyped()
  }

  return (
    <div className={`sa${error ? ' is-bad' : ''}`}>
      <span className="au-label">Where do you run trips?</span>

      {value.length > 0 && (
        <ul className="sa-picked" aria-label="Chosen places">
          {value.map((c) => (
            <li key={c}>
              <span>{label(c)}</span>
              <button type="button" onClick={() => onChange(value.filter((x) => x !== c))} aria-label={`Remove ${label(c)}`}>
                <Icon name="x" size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {india ? (
        <p className="sa-note"><Icon name="check" size={16} />You run trips anywhere in India. Remove it above to choose states or cities instead.</p>
      ) : (<>
        <div className="sa-search">
          <span className="au-input">
            <Icon name="search" size={18} className="au-input-ic" />
            <input type="search" name={name} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKey}
              placeholder="Search a city, town or state" autoComplete="off" aria-invalid={error ? true : undefined}
              role="combobox" aria-expanded={q.trim().length >= 2} aria-controls={listId} aria-autocomplete="list" />
          </span>
          {q.trim().length >= 2 && (
            <ul className="sa-results" id={listId} role="listbox" aria-label="Matching places">
              {results.map((r) => {
                const on = value.includes(r.code) || covered(value, r.code)
                return (
                  <li key={r.code} role="option" aria-selected={on}>
                    <button type="button" onClick={() => pick(r.code)} disabled={covered(value, r.code)}>
                      <Icon name={r.code === INDIA ? 'map' : r.code.startsWith('STATE:') ? 'map' : 'pin'} size={18} />
                      <span><b>{r.title}</b><small>{r.sub}</small></span>
                      {on ? <Icon name="check" size={16} className="sa-on" /> : <Icon name="plus" size={16} />}
                    </button>
                  </li>
                )
              })}
              {canAddTyped && (
                <li role="option" aria-selected={false}>
                  <button type="button" onClick={addTyped}>
                    <Icon name="plus" size={18} />
                    <span><b>Add “{typed}”</b><small>A town not in our list</small></span>
                  </button>
                </li>
              )}
              {!results.length && !canAddTyped && <li className="sa-empty">No place matches “{typed}”.</li>}
            </ul>
          )}
        </div>

        <div className="sa-quick" aria-label="Quick picks">
          <QuickChip on={false} onClick={() => pick(INDIA)} icon="map">Whole India</QuickChip>
          {POPULAR_STATES.map((s) => (
            <QuickChip key={s} on={value.includes(stateCode(s))} onClick={() => pick(stateCode(s))}>All of {s}</QuickChip>
          ))}
        </div>

        <details className="sa-browse">
          <summary>Browse by state <small>All 28 states and 8 union territories</small></summary>
          <div className="sa-states">
            {STATES.map((s) => <StateGroup key={s.name} state={s} value={value} onPick={pick} />)}
          </div>
        </details>
      </>)}
    </div>
  )
}

function QuickChip({ on, onClick, icon, children }) {
  return (
    <button type="button" className={`sa-chip${on ? ' is-on' : ''}`} aria-pressed={on} onClick={onClick}>
      <Icon name={on ? 'check' : icon || 'plus'} size={15} />{children}
    </button>
  )
}

/** One state: "All of <state>" and its cities, folded until opened. */
function StateGroup({ state, value, onPick }) {
  const whole = value.includes(stateCode(state.name))
  const count = value.filter((c) => c.startsWith(`CITY:${state.name}:`)).length
  return (
    <details className="sa-state">
      <summary>
        <span>{state.name}</span>
        {whole ? <em>All</em> : count > 0 ? <em>{count}</em> : null}
      </summary>
      <div className="sa-state-body">
        <QuickChip on={whole} onClick={() => onPick(stateCode(state.name))}>All of {state.name}</QuickChip>
        {state.cities.map((c) => {
          const code = cityCode(state.name, c)
          const on = whole || value.includes(code)
          return (
            <button key={c} type="button" className={`sa-chip sa-city${on ? ' is-on' : ''}`} aria-pressed={on}
              disabled={whole} onClick={() => onPick(code)}>
              {on && <Icon name="check" size={14} />}{c}
            </button>
          )
        })}
      </div>
    </details>
  )
}
