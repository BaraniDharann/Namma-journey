import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import Icon from '../dash/Icon'
import { useAuth } from '../../context/AuthContext'
import { rememberReturn } from '../../utils/returnTo'
import '../../styles/bus.css'

const HOME = { ROLE_USER: '/user/dashboard', ROLE_DRIVER: '/driver/dashboard', ROLE_OWNER: '/owner/dashboard', ROLE_ADMIN: '/admin/owners' }

/** The public bus pages (search and seat map): Postcard look, a slim top bar, no rail. */
export default function BusShell({ children }) {
  const { user } = useAuth()
  const location = useLocation()
  return (
    <div className="pc bus-shell">
      <header className="bus-top">
        <Link to="/?mode=bus" className="bus-brand">
          <span className="pc-logo-mark"><Icon name="bus" size={19} /></span>
          <span>Namma Journey</span>
        </Link>
        <span className="bus-top-sp" />
        {user?.role === 'ROLE_USER' && <Link to="/user/bus-tickets" className="pc-btn pc-btn-ghost pc-btn-sm"><Icon name="ticket" size={15} />My bus tickets</Link>}
        {user
          ? <Link to={HOME[user.role] || '/'} className="pc-btn pc-btn-sm">Dashboard</Link>
          : <Link to="/login" className="pc-btn pc-btn-sm" onClick={() => rememberReturn(location.pathname + location.search)}>Login</Link>}
      </header>
      <main className="bus-main">{children}</main>
    </div>
  )
}
