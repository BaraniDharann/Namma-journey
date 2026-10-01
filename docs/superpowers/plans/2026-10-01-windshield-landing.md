# Plan: Windshield landing, loader, celebrations

Spec: `docs/superpowers/specs/2026-10-01-windshield-landing-design.md`. Build straight through, test at the end.

1. Pure modules first, test-first (Vitest): `landing/chapters.js` (chapter at progress, km,
   colour mix) and `celebrate/paymentWatch.js` (remember / pending / settle verified).
2. Celebration system: provider, popup, `useCelebrate`, styles; mount in `App.jsx`; payment
   watcher mounted in `DashboardLayout` for travellers.
3. Car-assembly loader component + styles; shown on the landing page once per session.
4. Windshield landing: lazy 3D scene, dashboard overlay with infotainment QuickBooking, chapter
   HUD, restyled content sections; reduced-motion and low-power paths.
5. Wire `celebrate()` into the success paths of the listed forms (behaviour otherwise unchanged)
   and add the "I've paid" step to the UPI modal.
6. Verify: lint, build, vitest, impeccable detector, Playwright 15 + 07/10/12/13/14 regression,
   batched visual check at 1440 and 390.
