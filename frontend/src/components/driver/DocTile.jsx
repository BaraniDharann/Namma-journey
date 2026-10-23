import React, { useEffect, useMemo } from 'react'
import Icon from '../dash/Icon'

/** An image upload tile that previews the chosen picture, so a driver can see it is the right one. */
export default function DocTile({ label, icon, file, onChange, missing }) {
  const preview = useMemo(() => (file ? URL.createObjectURL(file) : ''), [file])
  // Release the blob when the file changes or the tile goes away.
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  return (
    <label style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, minHeight: 130, padding: 8,
      position: 'relative', borderRadius: 14, cursor: 'pointer', textAlign: 'center', overflow: 'hidden',
      border: `2px dashed ${file ? 'var(--pc-brand)' : missing ? 'var(--pc-bad)' : 'var(--pc-line)'}`,
      background: file ? 'var(--pc-brand-soft)' : 'var(--pc-wash)',
    }}>
      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--pc-ink-2)' }}>{label}</span>
      {preview
        ? <img src={preview} alt={`Preview of ${label}`} style={{ maxWidth: '100%', maxHeight: 84, borderRadius: 8, objectFit: 'cover' }} />
        : <Icon name={icon} size={24} />}
      <span style={{ fontSize: 11.5, fontWeight: 600, color: 'var(--pc-muted)' }}>{file ? 'Tap to change' : 'Tap to upload'}</span>
      <input type="file" accept="image/*" aria-label={label} style={{ position: 'absolute', opacity: 0, width: 1, height: 1 }}
        onChange={(e) => onChange(e.target.files?.[0] || null)} />
    </label>
  )
}
