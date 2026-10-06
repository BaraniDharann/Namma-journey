import React from 'react'
import {
  House, CalendarBlank, Wallet, Star, Package, Ticket, ChartBar, User, Users, Bell, Plus,
  ArrowRight, ArrowLeft, WarningCircle, Check, X, Path, MapPin, MagnifyingGlass, Phone,
  NavigationArrow, Clock, SignOut, List, CurrencyInr, TrendUp, ShieldCheck, PencilSimple, Trash,
  Eye, DownloadSimple, Funnel, MapTrifold, Power, EnvelopeSimple, Camera, Link as LinkIcon,
  PaperPlaneTilt, ArrowsClockwise, Sparkle, HandsPraying, CreditCard, QrCode, ChartPie,
  ChartLine, ChartLineUp, SteeringWheel, Lock, Eye as EyeOpen, EyeSlash, GoogleLogo, IdentificationCard,
  Crown, Suitcase, Gauge, Sun, Moon,
} from '@phosphor-icons/react'
import ErtigaIcon from '../brand/ErtigaIcon'

/**
 * The app's one icon set, backed by Phosphor so every glyph is drawn to the same grid and stroke.
 * Pages keep calling <Icon name="…" />; small sizes use the bold weight for legibility, larger
 * ones the friendlier duotone.
 */
const SET = {
  home: House, calendar: CalendarBlank, car: ErtigaIcon, wallet: Wallet, star: Star, package: Package,
  ticket: Ticket, chart: ChartBar, user: User, users: Users, bell: Bell, plus: Plus, arrow: ArrowRight,
  back: ArrowLeft, alert: WarningCircle, check: Check, x: X, route: Path, pin: MapPin,
  search: MagnifyingGlass, phone: Phone, nav: NavigationArrow, clock: Clock, logout: SignOut,
  menu: List, rupee: CurrencyInr, trend: TrendUp, shield: ShieldCheck, edit: PencilSimple,
  trash: Trash, eye: Eye, download: DownloadSimple, filter: Funnel, map: MapTrifold, power: Power,
  mail: EnvelopeSimple, camera: Camera, link: LinkIcon, send: PaperPlaneTilt, refresh: ArrowsClockwise,
  sparkle: Sparkle, temple: HandsPraying, card: CreditCard, qr: QrCode,
  pie: ChartPie, bar: ChartBar, line: ChartLine, area: ChartLineUp,
  wheel: SteeringWheel, lock: Lock, 'eye-open': EyeOpen, 'eye-off': EyeSlash, google: GoogleLogo,
  id: IdentificationCard, crown: Crown, suitcase: Suitcase, gauge: Gauge, sun: Sun, moon: Moon,
}

export default function Icon({ name, size = 18, className = '', style, title, weight }) {
  const Glyph = SET[name] || Sparkle
  // Callers that asked for a filled star (by fill style or an `is-on` class) get the fill weight.
  const filled = style?.fill === 'currentColor' || /\bis-on\b/.test(className)
  const w = weight || (filled ? 'fill' : size >= 20 ? 'duotone' : 'bold')
  const { fill, ...rest } = style || {}
  void fill
  return (
    <Glyph
      className={`pc-ic ${className}`}
      size={size}
      weight={w}
      style={rest}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      role={title ? 'img' : undefined}
    />
  )
}

/** Sidebar icon for a route, so pages can keep passing their nav items unchanged. */
const NAV = [
  [/\/dashboard$/, 'home'],
  [/\/bookings\/new$/, 'plus'],
  [/package-bookings$/, 'ticket'],
  [/\/packages$/, 'package'],
  [/\/bookings$/, 'calendar'],
  [/\/drivers$/, 'car'],
  [/\/payments$/, 'wallet'],
  [/\/reviews$/, 'star'],
  [/\/revenue$/, 'chart'],
  [/\/profile$/, 'user'],
]
export const navIcon = (path) => NAV.find(([re]) => re.test(path))?.[1] || 'sparkle'

/** Each nav destination keeps one tint, used for its icon chip across the app. */
const TINT = { home: 'brand', plus: 'brand', calendar: 'blue', ticket: 'pink', package: 'violet', car: 'teal', wallet: 'green', star: 'amber', chart: 'blue', user: 'slate' }
export const navTint = (path) => TINT[navIcon(path)] || 'brand'
