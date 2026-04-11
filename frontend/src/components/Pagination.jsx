import React from 'react'

export default function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null

  const getPages = () => {
    const pages = []
    const maxVisible = 5
    let start = Math.max(1, currentPage - Math.floor(maxVisible / 2))
    let end = Math.min(totalPages, start + maxVisible - 1)
    if (end - start + 1 < maxVisible) start = Math.max(1, end - maxVisible + 1)
    if (start > 1) { pages.push(1); if (start > 2) pages.push('...') }
    for (let i = start; i <= end; i++) pages.push(i)
    if (end < totalPages) { if (end < totalPages - 1) pages.push('...'); pages.push(totalPages) }
    return pages
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 24, flexWrap: 'wrap' }}>
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
        style={{
          padding: '8px 14px', borderRadius: 8, border: '1px solid #e2e8f0',
          background: currentPage === 1 ? '#f8fafc' : '#fff', color: currentPage === 1 ? '#cbd5e1' : '#475569',
          cursor: currentPage === 1 ? 'not-allowed' : 'pointer', fontSize: 13, fontWeight: 600,
          transition: 'all 0.2s',
        }}
      >
        ← Prev
      </button>
      {getPages().map((p, i) => (
        <button
          key={i}
          onClick={() => typeof p === 'number' && onPageChange(p)}
          disabled={p === '...'}
          style={{
            padding: '8px 12px', borderRadius: 8, fontSize: 13, fontWeight: 600,
            border: p === currentPage ? '1.5px solid #f97316' : '1px solid #e2e8f0',
            background: p === currentPage ? 'linear-gradient(135deg, #f97316, #ea580c)' : '#fff',
            color: p === currentPage ? '#fff' : p === '...' ? '#94a3b8' : '#475569',
            cursor: p === '...' ? 'default' : 'pointer',
            minWidth: 38, transition: 'all 0.2s',
          }}
        >
          {p}
        </button>
      ))}
      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
        style={{
          padding: '8px 14px', borderRadius: 8, border: '1px solid #e2e8f0',
          background: currentPage === totalPages ? '#f8fafc' : '#fff', color: currentPage === totalPages ? '#cbd5e1' : '#475569',
          cursor: currentPage === totalPages ? 'not-allowed' : 'pointer', fontSize: 13, fontWeight: 600,
          transition: 'all 0.2s',
        }}
      >
        Next →
      </button>
    </div>
  )
}

export function usePagination(items, itemsPerPage = 6) {
  const [currentPage, setCurrentPage] = React.useState(1)
  const totalPages = Math.max(1, Math.ceil(items.length / itemsPerPage))
  const safeCurrentPage = Math.min(currentPage, totalPages)
  const paginatedItems = items.slice((safeCurrentPage - 1) * itemsPerPage, safeCurrentPage * itemsPerPage)

  React.useEffect(() => {
    if (currentPage > totalPages) setCurrentPage(totalPages)
  }, [items.length, totalPages, currentPage])

  return { currentPage: safeCurrentPage, totalPages, paginatedItems, setCurrentPage }
}
