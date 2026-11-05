---
name: bus-booking
description: Researched domain knowledge (redBus-style) for adding intercity BUS ticket booking to Namma Journeys - owners listing buses, seat layouts (lower/upper deck, seater, single/double sleeper, 2+1, 2+2), routes with boarding/dropping points, search and price comparison across owners, a seat map with sold / female-only / available states, seat hold during payment, tickets, per-operator cancellation slabs, reschedule, and the TN permit and GST rules. Use this whenever the user talks about buses, bus travels owners, bus tickets, seat selection, seat maps, sleeper or seater layouts, redBus or AbhiBus, "book my show style seats", boarding points, or plans, specs, designs or builds any part of the bus module, even if they only mention one small piece such as "show available seats" or "add bus details for owner".
---

# Bus booking (redBus-style) for Namma Journeys

Namma Journeys today books **cars** (City rides and Trips) through owners and drivers. The user
wants owners who run **buses** to list them, and customers to compare buses across owners and
book **individual seats** on a seat map, the way redBus does (they shared a redBus seat screen
as the reference). This skill holds the research behind that, so plans and code follow how
Indian bus ticketing actually works instead of guessing.

Seat-level booking has a different shape from car booking. A car is booked whole by one customer,
priced per km or hour. A bus trip is a fixed scheduled departure whose seats are sold one by one to
many strangers, at per-seat prices, with concurrency, gender-seat and refund rules that cars never
needed. Expect to add new tables and flows, not stretch `Vehicle` / `TravelBooking`.

## Reference files: read the one you need
- `references/customer-flow.md`: every customer screen and feature: search, results card,
  filters, **seat screen legend and layout**, boarding/dropping, passenger info, payment,
  ticket, cancel/reschedule, reviews, women-safety features. Read it for any UI or customer-flow work.
- `references/operator-and-rules.md`: owner onboarding and documents, **TN permit legal risk**,
  layout and pricing setup, routes, segment selling, operator dashboard, cancellation slabs,
  refunds, GST. Read it for owner-side work, pricing, policies, or anything compliance-related.
- `references/data-model-and-concurrency.md`: proposed tables, the **seat-hold → pay → ticket**
  sequence, the Postgres exclusion constraint that makes double booking impossible, the
  server-side gender rule, cancel quote, and abuse limits, all mapped onto existing classes in this
  repo. Read it before designing tables or writing any booking or payment code.

The research marks unverified details as **(unconfirmed)**. Treat those as sensible defaults to
confirm with the user, not as facts about redBus.

## The rules that matter most
These are where a naive build goes wrong:

1. **Never double-sell a seat.** The database must enforce it (unique or exclusion constraint on
   active allocations), because UI checks and in-memory locks race. A seat hold lasts 10 minutes, and
   the payment window is shorter than the hold.
2. **Payment can succeed after the hold expired.** Re-hold if the seats are still free, otherwise
   refund in full automatically. A sweeper reconciles captured payments that have no ticket.
3. **Release holds the moment a payment fails or is cancelled**, and stop anyone from holding a seat
   forever by re-posting (a real redBus bug-bounty finding).
4. **Gender seats are enforced on the server.** A female-booked seat makes its pair seat female-only.
   Ladies-only seats need a female passenger. The female-only state must look clearly different from
   available, because redBus's pale-pink outline caused mis-bookings.
5. **Price is per seat**, not per bus: lower vs upper, single vs double berth. Snapshot fare, GST
   and cancellation policy onto the ticket at booking.
6. **Cancellation is per operator, per seat, partial allowed**, quoted in ₹ before confirming. When
   the operator cancels the trip, everyone gets a full refund.
7. **Permits are a legal issue in Tamil Nadu.** Per-seat, multi-stop sale needs a stage-carriage
   style permit. AITP (contract carriage) buses selling seats online were flagged by the TN Transport
   Department in 2024. Store the permit type and keep an origin–destination passenger manifest. Raise
   this with the user before launch.
8. **Non-AC tickets sold through a platform attract 5% GST payable by the platform** (Sec 9(5)).
   Flag it for a CA; don't silently ignore it.

## How to plan the bus module
Follow this repo's usual flow: brainstorm, then spec in `docs/superpowers/specs`, then a plan,
then build in phases. Each phase should ship on its own.

1. **Settle the open decisions with the user first** (see below). They change the data model.
2. **Phase 1: owner adds a bus.** Vehicle kind CAR/BUS at onboarding (a bus-only owner never
   sees car screens). Bus details, documents with expiry (compressed through `FileStorageService`),
   a seat layout picked from templates (2+2 seater 40, 2+1 sleeper 30, seater + sleeper) and editable.
   Row-level security like the other owner tables.
3. **Phase 2: routes and schedules.** Service, ordered stops with boarding/dropping points
   (time, address, landmark), run days, per-price-group fares, cancellation slabs. Generate trips
   per date. Owner seat chart and blocks.
4. **Phase 3: customer search and compare.** From/To/date gives a list of bus cards across owners
   (bus type string, times, duration, rating, from-price, seats left, single seats left).
   Filters: AC, sleeper/seater, single seats, departure time, BP/DP, operator, price. Sort by
   price, departure, rating.
5. **Phase 4: seat map and booking.** Lower/upper deck panels, legend, per-seat price,
   tabs Select seats → Board/Drop point → Passenger info, sticky footer total. Hold → Razorpay →
   confirm. Ticket by email + Telegram.
6. **Phase 5: after booking.** My Bookings, cancel quote and partial cancel, operator trip cancel,
   passenger manifest, bus number + crew phone sent before departure, reviews.
7. Later: reschedule, segment selling, dynamic pricing within floor/ceiling, offers, live tracking,
   women-booking mode.

## Open decisions to put to the user
- Full-route tickets only in v1, or selling part of a route (e.g. Chennai→Trichy on a
  Chennai→Tirunelveli bus)? This decides whether allocations need leg ranges.
- Do owners also sell the same bus on redBus or at a counter? Our app has no inventory sync, so
  either we are the only channel or the owner blocks those seats manually.
- Seat types in v1: seaters only, or sleepers and two decks too?
- Commission % and payout cycle for bus owners; who pays the 5% non-AC GST.
- Which permit types may list seat-wise (legal advice).
- Default cancellation slabs, offered as a template the owner can edit.

## Existing code to reuse (verify the names still exist)
`Owner` + row-level security (V28), `FileStorageService` / `ImageCompressor`, `PaymentService` /
`RazorpayGateway`, `AdvanceHoldSweeper` (hold-expiry pattern), idempotency keys (V7), the SQS
outbox, Telegram and email notifications (no SMS), Flyway migrations, React 18 + Vite +
Tailwind 4 frontend in `frontend/`.
