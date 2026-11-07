import api from './api'

// An owner's running costs per car and bus, the profit they leave, and the bus day view.
// Writes are silent: the screens show the API's message themselves.

export const getExpenses = (from, to, kind) => api.get('/owner/expenses', { params: { from, to, kind } })
export const addExpense = (form) => api.post('/owner/expenses', form, { _silent: true })
export const deleteExpense = (id) => api.delete(`/owner/expenses/${id}`, { _silent: true })
export const getProfitSummary = (from, to, kind) => api.get('/owner/expenses/summary', { params: { from, to, kind } })
export const getBusDay = (date) => api.get('/owner/bus/day', { params: { date } })

export const EXPENSE_CATEGORIES = [
  ['FUEL', 'Fuel'],
  ['TOLL', 'Toll'],
  ['DRIVER_PAY', 'Driver pay'],
  ['MAINTENANCE', 'Maintenance'],
  ['PERMIT_TAX', 'Permit / tax'],
  ['OTHER', 'Other'],
]
export const categoryText = (c) => EXPENSE_CATEGORIES.find(([k]) => k === c)?.[1] || c
