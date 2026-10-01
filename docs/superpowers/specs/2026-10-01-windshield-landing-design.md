# Windshield landing, car-assembly loader and celebrations

**Status:** approved 2026-10-01. Concept C ("Windshield") chosen from three storyboards; payment
popup behaviour "two steps" chosen; build-then-test chosen.

## 1. Loader — the car builds itself
`components/brand/CarAssemblyLoader.jsx` (SVG + CSS, no 3D, renders instantly). Body drops in,
wheels roll in and lock, glass and roof rack snap on, headlights blink twice, the car pulls away
right and wipes the "Namma Journey" wordmark onto the page; ~3 s, then fades out. Shown on the
landing page once per browser session (sessionStorage, guarded); reduced motion → a short fade.

## 2. Landing — C · Windshield
- A sticky full-screen three.js scene (react-three-fiber, already installed) seen from the
  driver's seat. Low-poly primitives only (no model downloads). Scroll progress drives the car
  down a road through four chapters, each with its own sky, fog, ground and roadside props:
  **City** (traffic, buildings, honk) → **Ghats** (hills, bends, trees) → **Coast** (sea, palms)
  → **Temple town at dusk** (gopuram). Roadside signboards carry chapter headlines.
- The car's dashboard is the UI overlay: speedometer dial = live fare (public pricing, ₹/hour
  as the quick booking uses), odometer = kilometres scrolled, chapter strip, and the
  infotainment screen = the existing `QuickBooking` (logic unchanged, restyled).
- After the drive the page continues with the existing content, restyled to Postcard tokens:
  numbers, fleet, packages (live), why us, how it works, reviews (live), final CTA, footer.
  Existing copy is kept; emoji icons become the kit's line icons.
- The 3D chunk is lazy-loaded; text and booking never wait for it. Low-power/phones: fewer
  props, DPR ≤ 1.5. Reduced motion: a still illustrated windshield, no scroll-driving.

## 3. Celebrations
`components/celebrate/Celebration.jsx`: `CelebrationProvider` (mounted once in `App.jsx`) and
`useCelebrate()` → `celebrate({ title, message, tone: 'party' | 'calm', action })`. Popup: confetti
burst (party only), self-drawing tick, the Namma Journey car driving across a small road; closes on
button, Escape or backdrop. Reduced motion: no confetti or driving.

Wired into: booking placed (new booking + quick booking), package booked, review sent, profile
saved (traveller); driver added, driver assigned, package created, payment verified (owner);
trip accepted (driver).

**Payment, two steps.** The UPI/Google Pay modal gets an "I've paid" button → calm popup
"Payment sent — waiting for confirmation"; the payment id is remembered locally. While a
traveller has the app open, their payments are polled every 10 s; when a remembered payment turns
VERIFIED, the party popup "Payment confirmed ₹X" shows once and the id is forgotten. "I've paid"
changes nothing on the server.

## Testing
- Vitest: chapter/scroll maths, fare dial, payment-tracking store.
- Playwright `15-windshield-landing.spec.ts`: loader plays once and reveals the page; canvas
  renders; scrolling changes chapter and odometer; fare shows; quick booking still reachable;
  390 px has no horizontal overflow; reduced-motion fallback; celebration after a real booking;
  payment two-step (I've paid → waiting popup; owner verifies via API → confirmed popup).
- Existing suites stay green.
