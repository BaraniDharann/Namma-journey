import React, { useState, useEffect, useRef } from 'react'

// Photon API by Komoot — free, fuzzy search, handles misspellings, covers all India villages/towns/cities
// India bounding box: lon_min=68.1, lat_min=6.5, lon_max=97.4, lat_max=35.7
const PHOTON_URL = 'https://photon.komoot.io/api/'
const INDIA_BBOX = '68.1,6.5,97.4,35.7'
// Bias toward center of India for better relevance
const INDIA_LAT = 20.5937
const INDIA_LON = 78.9629

function buildShortName(props) {
  const name = props.name || ''
  const city = props.city || props.county || ''
  const state = props.state || ''
  const parts = [name]
  if (city && city !== name) parts.push(city)
  if (state) parts.push(state)
  return parts.join(', ')
}

function getPlaceIcon(props) {
  const type = props.osm_value || props.type || ''
  if (['city', 'town'].includes(type)) return '🏙️'
  if (['village', 'hamlet'].includes(type)) return '🏘️'
  if (['suburb', 'neighbourhood', 'quarter'].includes(type)) return '📍'
  if (['bus_stop', 'station'].includes(type)) return '🚏'
  if (type === 'railway') return '🚉'
  return '📍'
}

async function searchPhoton(query) {
  const params = new URLSearchParams({
    q: query,
    lang: 'en',
    limit: '7',
    bbox: INDIA_BBOX,
    lat: INDIA_LAT,
    lon: INDIA_LON,
  })
  const res = await fetch(`${PHOTON_URL}?${params}`)
  if (!res.ok) throw new Error('Photon API failed')
  const data = await res.json()

  return (data.features || [])
    .filter(f => {
      // Only show results within India
      const country = (f.properties.country || '').toLowerCase()
      return country === 'india' || country === ''
    })
    .map(f => ({
      shortName: buildShortName(f.properties),
      displayName: [f.properties.name, f.properties.city, f.properties.county, f.properties.state, f.properties.country]
        .filter(Boolean)
        .join(', '),
      lat: f.geometry.coordinates[1],
      lon: f.geometry.coordinates[0],
      type: f.properties.osm_value || f.properties.type || '',
      icon: getPlaceIcon(f.properties),
    }))
}

export default function PlaceAutocomplete({ value, onChange, placeholder, required }) {
  const [query, setQuery] = useState(value || '')
  const [suggestions, setSuggestions] = useState([])
  const [showDropdown, setShowDropdown] = useState(false)
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState(!!value)
  const debounceRef = useRef(null)
  const wrapperRef = useRef(null)
  const abortRef = useRef(null)

  useEffect(() => {
    if (!value && query) {
      setQuery('')
      setSelected(false)
    }
  }, [value])

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setShowDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleInput = (text) => {
    setQuery(text)
    setSelected(false)
    onChange({ name: text, lat: null, lon: null })

    if (debounceRef.current) clearTimeout(debounceRef.current)

    if (text.trim().length < 2) {
      setSuggestions([])
      setShowDropdown(false)
      return
    }

    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const results = await searchPhoton(text.trim())
        setSuggestions(results)
        setShowDropdown(true)
      } catch {
        setSuggestions([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  const handleSelect = (place) => {
    setQuery(place.shortName)
    setSelected(true)
    setShowDropdown(false)
    setSuggestions([])
    onChange({
      name: place.shortName,
      lat: place.lat,
      lon: place.lon,
    })
  }

  return (
    <div ref={wrapperRef} style={{ position: 'relative' }}>
      <input
        className="input-field"
        placeholder={placeholder}
        value={query}
        onChange={(e) => handleInput(e.target.value)}
        onFocus={() => suggestions.length > 0 && setShowDropdown(true)}
        required={required}
        autoComplete="off"
        style={{
          borderColor: selected ? '#22c55e' : undefined,
          paddingRight: 32,
        }}
      />
      {loading && (
        <div style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)' }}>
          <div className="spinner" style={{ width: 16, height: 16 }} />
        </div>
      )}
      {selected && !loading && (
        <div style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', color: '#22c55e', fontSize: 16 }}>
          ✓
        </div>
      )}

      {showDropdown && suggestions.length > 0 && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 50,
          background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10,
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxHeight: 280, overflowY: 'auto',
          marginTop: 4,
        }}>
          {suggestions.map((place, i) => (
            <div
              key={i}
              onClick={() => handleSelect(place)}
              style={{
                padding: '10px 14px', cursor: 'pointer', fontSize: 13,
                borderBottom: i < suggestions.length - 1 ? '1px solid #f1f5f9' : 'none',
                transition: 'background 0.1s',
                display: 'flex', alignItems: 'flex-start', gap: 10,
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = '#fff7ed'}
              onMouseLeave={(e) => e.currentTarget.style.background = '#fff'}
            >
              <span style={{ fontSize: 18, lineHeight: '20px', flexShrink: 0 }}>{place.icon}</span>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: '#0f172a' }}>{place.shortName}</div>
                <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {place.displayName}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {showDropdown && suggestions.length === 0 && !loading && query.length >= 2 && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 50,
          background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10,
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)', padding: '12px 14px',
          fontSize: 13, color: '#94a3b8', marginTop: 4,
        }}>
          No places found. Try a different spelling.
        </div>
      )}
    </div>
  )
}
