# Plan: Postcard dashboards (all modules, all roles)

Spec: `docs/superpowers/specs/2026-10-01-postcard-dashboards-design.md`.
Execution chosen by the user: build straight through, test at the end.

1. **Foundation**
   - Fonts (Unbounded, Nunito Sans) in `index.html`.
   - `styles/postcard.css`: tokens, shell, restyled shared classes under `.pc`, motion, reduced motion.
   - `components/dash/Icon.jsx`, `ui.jsx`, `charts.jsx`, tooltip layer.
   - `dash/metrics.js` + Vitest tests (write tests first, then the functions).
   - Rebuild `DashboardLayout.jsx` (teal rail, icon nav from path, top bar, light notification
     panel, mobile drawer); keep notification behaviour identical.
2. **Dashboards** — rewrite Owner, Driver, Traveller dashboard pages on the kit.
3. **Sub-pages** — per page: Postcard page header, emoji → icons in chrome and stat tiles,
   money via `inr()`, keep every behaviour and API call as is.
   Owner: Bookings, Drivers, Payments, Reviews, Packages, PackageBookings, Revenue, Profile.
   Driver: Bookings, Profile. Traveller: Bookings, NewBooking, Payments, PackageBookings, Profile.
4. **Verify** — `npm run lint`, `vite build`, `vitest run`, impeccable detector on changed files,
   Playwright `14-postcard-modules.spec.ts` + `13-dashboard-story.spec.ts` headed; inspect
   screenshots at 1440 and 390; fix in one batch; re-run once.
