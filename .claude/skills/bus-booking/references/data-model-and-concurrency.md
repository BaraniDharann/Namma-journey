# Proposed data model and seat-hold design for this codebase

A starting proposal synthesised from the research (Indian bus aggregator APIs use block → confirm)
and this repo's conventions. It is not final; the plan or spec decides. Check the current
code before relying on any name here.

## Contents
1. What exists today to reuse
2. Tables
3. Seat hold → payment → ticket
4. Availability query
5. Gender rule check (server-side)
6. Cancellation and refund
7. Abuse limits

---

## 1. What exists today (verify; true as of Nov 2026)
- `Owner` (multi-owner, code like NJO-0001) with **Postgres row-level security** per owner
  (V28). Bus tables owned by an owner should get the same RLS policy.
- `Vehicle` (owner_id, `CarCategory` category, registration_number, model, seats, ac, rc_photo,
  active) and `OwnerTripRate` (per owner + category rates). These are car-only.
  Buses need a vehicle kind (CAR / BUS) or a separate `buses` table. Prefer a separate table:
  bus pricing is per seat per trip, not per km/hour, so little is shared.
- `Payment`, `PaymentService`, `RazorpayGateway`, `AdvanceHoldSweeper` (an existing
  hold-expiry sweeper pattern to copy), `V7__idempotency_keys.sql` (reuse idempotency keys).
- SQS outbox for side effects (refunds, notifications); Telegram + email for passenger messages,
  no SMS.
- `FileStorageService` + `ImageCompressor` for permit, RC and bus photos.
- Flyway migrations in `src/main/resources/db/migration` (next number after the latest V*).

## 2. Tables (sketch)
| Table | Key columns |
|---|---|
| `buses` | id, owner_id, registration_number, name/model, ac, bus_kind (SEATER/SLEEPER/SEATER_SLEEPER), layout_id, permit_type (STAGE/CONTRACT/AITP), permit/RC/insurance/fitness docs + expiry dates, amenities, photos, active |
| `seat_layouts` | id, owner_id (null for shared templates), name ("2+1 Sleeper 30"), decks |
| `layout_seats` | layout_id, label (L1/U1/14), deck (0 lower / 1 upper), row, col, length, width, seat_type (SEATER/SLEEPER), berth (SINGLE/DOUBLE), pair_label (the adjacent seat for the gender rule), price_group, ladies_only |
| `bus_services` | id, owner_id, bus_id, name, run_days / date range, cancellation_policy (jsonb slabs), reschedule_allowed |
| `service_stops` | service_id, seq, city_id, point name, address, landmark, lat/lng, arrive/depart offset, is_boarding, is_dropping |
| `service_fares` | service_id, price_group, (from_seq, to_seq if OD pairs), fare |
| `bus_trips` | id, service_id, bus_id, travel_date, departure_at, status (SCHEDULED/CANCELLED/DEPARTED/COMPLETED), driver/conductor, vehicle reg sent |
| `seat_allocations` | id, trip_id, seat_label, legs int4range, status (HELD/CONFIRMED/BLOCKED/CANCELLED), gender, hold_id, ticket_id, expires_at |
| `bus_tickets` | id, ticket_no, user_id, trip_id, boarding/dropping stop, contact, fare snapshot, GST, policy snapshot, payment_id, status (PENDING/CONFIRMED/CANCELLED/PARTIALLY_CANCELLED/SERVICE_CANCELLED/TRAVELLED) |
| `bus_ticket_passengers` | ticket_id, seat_label, name, age, gender, fare, status |

The **double-booking guard** is the database itself:
```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE seat_allocations ADD CONSTRAINT no_overlap
  EXCLUDE USING gist (trip_id WITH =, seat_label WITH =, legs WITH &&)
  WHERE (status IN ('HELD','CONFIRMED','BLOCKED'));
```
The partial predicate means expired holds must be flipped to a non-active status (or deleted)
before the seat can be re-held. Do that in the same transaction as the new hold:
`UPDATE ... SET status='EXPIRED' WHERE trip_id=? AND status='HELD' AND expires_at < now()`.
If v1 sells the full route only, legs = [0,1) for everything and a plain unique index
`(trip_id, seat_label) WHERE status IN (...)` is enough.

## 3. Hold → pay → ticket
1. **Hold** `POST /api/bus/trips/{id}/holds` with seats, BP/DP, passengers and an idempotency key.
   In one transaction:
   - expire stale holds
   - validate the gender rule and the max seats per booking (6)
   - re-price from `service_fares`
   - insert `seat_allocations` (HELD, `expires_at = now() + 10 min`)
   - create a PENDING ticket

   A constraint violation becomes 409 "seat just taken".
2. **Pay:** create a Razorpay order for the ticket. Keep the gateway checkout window shorter than
   the hold (e.g. 8 min against a 10 min hold).
3. **Confirm** (webhook or client callback, idempotent on payment id):
   `UPDATE seat_allocations SET status='CONFIRMED' WHERE hold_id=? AND status='HELD' AND expires_at > now()`.
   - Updated rows equal the seat count: ticket CONFIRMED, notify through the outbox.
   - Zero rows (the hold expired): try to re-hold the same seats. If they are taken, **auto-refund
     in full** through the outbox and tell the user.
4. **Payment failed or cancelled:** release the hold immediately (redBus left seats stuck for 15+ min).
5. A **sweeper** (like `AdvanceHoldSweeper`) expires holds and reconciles captured payments that
   have no confirmed ticket every few minutes.

## 4. Availability for a seat map
A seat is unavailable for a request covering legs R if any allocation with
`status='CONFIRMED' OR status='BLOCKED' OR (status='HELD' AND expires_at > now())` has
`legs && R`. Return per seat: label, deck, position, type, price, and one state:
`AVAILABLE | SOLD | BOOKED_F | BOOKED_M | FEMALE_ONLY | MALE_ONLY | HELD`.
Show a HELD seat as sold to everyone except the holder.

## 5. Gender rule (server-side, in the hold transaction)
- Seat `ladies_only` → the passenger must be F.
- For each requested seat with a `pair_label`: if the pair is CONFIRMED/HELD by someone else whose
  legs overlap, then the requested gender must equal that occupant's gender. If both pair seats are
  in the **same booking**, allow any mix (a family travelling together).
- Mirror this in the UI (FEMALE_ONLY / MALE_ONLY states), but the server is the authority.

## 6. Cancel / refund
- `GET /api/bus/tickets/{id}/cancel-quote?seats=...` returns charge % and ₹ refund per seat, from the
  **snapshotted** policy and the hours left before departure.
- `POST .../cancel` sets the allocations to CANCELLED (the seat is free for resale) and queues the refund in the outbox.
- An operator trip cancel sets every ticket to SERVICE_CANCELLED, gives a 100% refund, and notifies everyone.

## 7. Abuse limits (lesson from the redBus seat-lock bug bounty)
- A hold cannot be extended by re-posting. A new hold on the same seats by the same user only
  succeeds after the old one expires, and counts toward a limit.
- Cap active holds per user (e.g. 1 per trip, 3 overall) and rate-limit the hold endpoint.
- Require a logged-in, OTP-verified user to hold seats.
