import React from 'react'
import { slabRows } from '../../utils/busBooking'

/** The operator's cancellation slabs with the actual clock times they end at. */
export default function CancellationTable({ slabs, note }) {
  const rows = slabRows(slabs)
  if (!rows.length) return <p style={{ fontSize: 13, color: 'var(--pc-muted)' }}>No cancellation policy published.</p>
  return (
    <>
      <table className="bus-slabs">
        <thead><tr><th scope="col">Cancel</th><th scope="col">Charge</th></tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.when}>
              <td>{r.when}<small style={{ display: 'block', color: 'var(--pc-muted)', fontWeight: 600 }}>{r.refund}</small></td>
              <td><b>{r.charge}</b></td>
            </tr>
          ))}
        </tbody>
      </table>
      <p style={{ fontSize: 12, color: 'var(--pc-muted)', margin: '8px 0 0' }}>
        {note || 'The charge is a share of the seat fare. GST on a cancelled seat is always refunded.'}
      </p>
    </>
  )
}
