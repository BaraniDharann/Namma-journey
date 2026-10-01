# Postcard redesign: owner, driver and traveller dashboards

**Status:** approved 2026-10-01. Direction C ("Postcard") chosen from three sampled directions;
scope widened by the user to every module for all three roles; build-then-test chosen over
staged spec/plan review.

## Goal

Replace the current dashboard UI — emoji icons, four identical stat tiles per role, decorative
photo banners, broken money formatting (`₹6,000.223`, `₹1,38,005.134` wrapping) and nothing
that says what to do next — with one coherent, task-first design across the owner, driver and
traveller apps, including charts that answer each role's real questions.

## Scope

In: the shared dashboard shell (sidebar, top bar, notifications, mobile drawer) and every page
rendered inside it:

- Owner: dashboard, bookings, drivers, payments, reviews, packages, package bookings, revenue, profile
- Driver: dashboard, trips, profile
- Traveller: dashboard (road-trip animation kept), bookings, new booking, payments, packages, profile

Out: landing page, login/signup pages, package detail page, backend changes, new APIs.

## Visual system (Postcard)

| Token | Value | Use |
|---|---|---|
| Rail | `#0f3d3e` teal, ink `#fff` / `#a7c4c2` | sidebar |
| Page | `#fff8f0` cream | dashboard background |
| Panel | `#ffffff`, radius 22px, soft shadow, no border | cards |
| Ink | `#0f2a2b` / `#3e5a5b` / muted `#56706f` (AA on white) | text |
| Brand | `#f97316` action, `#c2410c` behind white text and for links | buttons, today block |
| Series | `#2a78d6 #eb6834 #1baf7a #eda100 #e87ba4` (fixed order, validated CVD-safe) | charts |
| Sequential | teal `#0f766e` mixed into panel | heatmap |
| Fonts | Unbounded (headings, figures), Nunito Sans (body) | via Google Fonts |

Icons: one authored line-icon set (24px grid, 1.8 stroke) replaces every emoji in navigation and
page chrome. Motion: panels rise in once (staggered), charts draw in (line trace, donut/gauge
sweep, bars grow), hover lift on cards/buttons; all off under `prefers-reduced-motion`.

Shared classes used by the sub-pages (`btn-*`, `input-field`, `card`, `glass-card`, `stat-card*`,
`badge-*`, `data-table`, `page-title`, `empty-state`, `modal-*`, `chip`, notifications) are restyled
**inside the dashboard shell only** (`.pc` scope) so the landing and login pages are untouched.

## Cards per role (data source in brackets)

**Owner dashboard**
- Today block: bookings waiting for a driver [bookings PENDING without driverId], payments to
  verify [pending payments, count + amount], drivers not set up [first login pending / Telegram
  not linked]. Each links to the page that resolves it.
- Summary strip: revenue this month + change vs last month [monthly revenue], bookings
  [bookings], fleet busy [drivers on a STARTED trip ÷ drivers], rating [reviews; "No reviews yet"].
- Charts: revenue by month this year, area [monthly series]; bookings by status, donut; fleet
  right now, gauge; top routes, horizontal bars; when people book, day × time heatmap
  [bookingDate]; trips per month, bars [monthly series].
- Recent bookings table.

**Driver dashboard**
- Online/offline switch [availability ACTIVE/INACTIVE].
- Next trip card with Navigate (Google Maps directions) and Call (tel:) [earliest CONFIRMED/STARTED].
- Live map when a trip is started (existing LiveTrackingMap).
- Today: earnings, trips, km [COMPLETED bookings by date].
- Charts: earnings this week, bars; completion rate, gauge; trip mix by booking type, donut.
- New requests with Accept / Decline [driver booking action].

**Traveller dashboard**
- Road-trip animation (unchanged).
- Upcoming trip with countdown and status [earliest future booking].
- Charts: spend by month, bars [payments/bookings]; trip length, donut [travelDays];
  top destinations, horizontal bars.
- Kilometres travelled, total figure [distanceKm].
- Recent trips with status and "Rate trip" for completed ones.

Removed from the sampled mockups because no data exists: licence expiry, payment-method mix,
hours online, acceptance rate, driver star breakdown, trip category, distance goal, driver
name/car on traveller booking.

## Architecture

- `frontend/src/components/dash/Icon.jsx` — icon set, `navIcon(path)` mapping for sidebars.
- `frontend/src/components/dash/charts.jsx` — AreaChart, BarChart, HBarList, Donut, Gauge,
  Heatmap, Legend; hand-built SVG/HTML, no chart library. Hover values via `data-tip` and one
  shared tooltip layer mounted by the layout.
- `frontend/src/components/dash/ui.jsx` — Panel, PageHead, SummaryStrip, TaskList, StatusPill,
  Empty, Skeleton.
- `frontend/src/dash/metrics.js` — pure derivations (formatting, grouping, series); unit-tested.
- `frontend/src/styles/postcard.css` — tokens, shell, restyled shared classes, motion.
- `DashboardLayout.jsx` rebuilt; existing notification logic preserved.

Each dashboard loads its calls in parallel; every panel owns loading, empty and error states so
one failed call never blanks the page.

## Testing

- Vitest unit tests for `dash/metrics.js` (new dev dependency).
- Playwright `14-postcard-modules.spec.ts`: signs in as owner, driver and traveller through the
  real forms, opens every module, asserts the Postcard shell, page heading, no emoji in the
  navigation, no JS errors or failed API calls, and no horizontal overflow at 390px; dashboard
  charts render with legends. Screenshots attached per page.
- Existing `13-dashboard-story.spec.ts` stays green.
