# Bus Booking (sub-project 3): Design

Travels owners who run buses list them on Namma Journeys. Customers compare buses across owners
and book individual seats on a seat map, redBus-style. Seats are sold **only on Namma Journeys**;
redBus is a feature/UI reference, not an integration. Research: `.claude/skills/bus-booking/`.

## Decisions

| Topic | Decision |
|---|---|
| Module shape | Separate bus module (own tables, services, screens). Car code untouched except where noted. |
| Seat kinds | Lower and upper deck; seater, single sleeper, double sleeper; ladies-only seats. |
| Part of a route | Allowed: any stop to any later stop. A seat is taken only for the legs it covers. |
| Double booking | Postgres exclusion constraint on (trip, seat, leg range) for active allocations. |
| Seat hold | 10 minutes. No extension by re-posting. 1 active hold per trip and 3 overall per user. |
| Commission | 8% of fares (without GST), per owner, admin can change it (`owners.bus_commission_percent`). |
| GST | Owner enters fares without GST; 5% (`bus.gst-percent`) is added at checkout. Platform keeps GST. |
| Payout | Per completed trip, created the next morning; admin pays by UPI/bank and records the UTR. |
| Cancellation | Owner-edited slabs from a default template, snapshotted onto each ticket. Partial cancel per seat. |
| Messages | Email + Telegram (no SMS) via the outbox. |
| Payments | Razorpay, same account and webhook. Bus payments get their own tables (`bus_payments`, `bus_refunds`), because `payments` requires a car booking and holds only one refund. The webhook passes orders it does not know to the bus module. |

Out of scope for now: reschedule, coupons/offers, live GPS, "booking for women" mode, demand-based
pricing, RazorpayX automatic payouts, counter/agent bookings.

## Data model: `V30__bus_booking.sql`

Every bus table except `bus_places` has `owner_id` and gets the V28 row-level security policies
(`app_unscoped` + `owner_scoped`), so an owner's request sees only their rows. Customer, admin and
job connections are unscoped and work as before.

```
owners            + fleet_type varchar(6) NOT NULL DEFAULT 'CARS' CHECK IN ('CARS','BUSES','BOTH')
                  + bus_commission_percent numeric(5,2) NOT NULL DEFAULT 8 CHECK 0..50

bus_places        id, name, district, lat, lng, created_by_owner_id, created_at
                  UNIQUE (lower(name)). Seeded with Tamil Nadu district HQs and major towns.

buses             id, owner_id, registration_number (unique key like vehicles), name, ac,
                  permit_type STAGE_CARRIAGE|CONTRACT_CARRIAGE|ALL_INDIA_TOURIST,
                  amenities varchar(500) (comma list), status DRAFT|PENDING|APPROVED|REJECTED,
                  rejection_reason, submitted_at, reviewed_at, reviewed_by_admin_id, created_at
bus_documents     bus_id, owner_id, type RC|PERMIT|INSURANCE|FITNESS, path, expires_on date; PK (bus_id, type)
bus_photos        id, bus_id, owner_id, path, position
bus_seats         id, bus_id, owner_id, label, deck 0|1 (lower|upper), row_no, col_no,
                  kind SEATER|SLEEPER, berth SINGLE|DOUBLE (null for seater), pair_label, ladies_only
                  UNIQUE (bus_id, label), UNIQUE (bus_id, deck, row_no, col_no)

bus_routes        id, owner_id, bus_id, name, run_days smallint (bit 0 = Mon … bit 6 = Sun),
                  active_from date, active_to date null, max_seats_per_booking int default 6,
                  cancellation_policy jsonb, status DRAFT|PUBLISHED|PAUSED, created_at
bus_route_stops   id, route_id, owner_id, seq, place_id; UNIQUE (route_id, seq), UNIQUE (route_id, place_id)
bus_route_points  id, stop_id, owner_id, kind BOARDING|DROPPING|BOTH, name, address, landmark,
                  lat, lng, day_offset int, time time
bus_route_fares   route_id, owner_id, from_seq, to_seq, seat_class, fare numeric(10,2) > 0
                  PK (route_id, from_seq, to_seq, seat_class); CHECK from_seq < to_seq

bus_trips         id, route_id, bus_id, owner_id, travel_date, departure_at, arrival_at,
                  status SCHEDULED|DEPARTED|COMPLETED|CANCELLED, cancel_reason,
                  driver_name, driver_phone, conductor_name, conductor_phone, reminder_sent_at,
                  UNIQUE (route_id, travel_date)
bus_seat_allocations id, trip_id, owner_id, seat_label, legs int4range, status
                  HELD|CONFIRMED|BLOCKED|CANCELLED|EXPIRED, gender M|F (null for BLOCKED),
                  ticket_id uuid null, expires_at, created_at
                  EXCLUDE USING gist (trip_id WITH =, seat_label WITH =, legs WITH &&)
                    WHERE (status IN ('HELD','CONFIRMED','BLOCKED'))
bus_tickets       id uuid, ticket_no 'NJB-' + 8 chars unique, user_id, owner_id, trip_id,
                  from_seq, to_seq, boarding_point_id, dropping_point_id, contact_phone,
                  contact_email, extra_phone, fare_total, gst_percent, gst_total, total_amount,
                  commission_percent, cancellation_policy jsonb (snapshot), status
                  PENDING|CONFIRMED|PARTIALLY_CANCELLED|CANCELLED|SERVICE_CANCELLED|EXPIRED|COMPLETED,
                  hold_expires_at, idempotency_key (unique per user), created_at, confirmed_at, version
bus_ticket_passengers id, ticket_id, owner_id, seat_label, seat_class, name, age, gender,
                  fare, gst, status CONFIRMED|CANCELLED, cancel_charge, refund_amount, cancelled_at
bus_payments      id uuid, ticket_id, user_id, owner_id, amount, status CREATED|CAPTURED|FAILED,
                  razorpay_order_id unique, razorpay_payment_id unique, created_at, captured_at
bus_refunds       id uuid, ticket_id, payment_id, owner_id, amount, reason, status PENDING|PROCESSED|FAILED,
                  razorpay_refund_id, created_at, processed_at
bus_owner_payouts id, owner_id, trip_id unique, fares_travelled, charges_kept, gross,
                  commission_percent, commission, net, status DUE|PAID, utr, paid_at,
                  paid_by_admin_id, created_at
bus_reviews       id, ticket_id unique, bus_id, owner_id, user_id, rating 1..5, comment, created_at
```

**Seat class** is derived, never entered: `SEATER` for any seater; otherwise
`LOWER_SINGLE`, `LOWER_DOUBLE`, `UPPER_SINGLE`, `UPPER_DOUBLE` from deck and berth.

**Cancellation policy JSON:** slabs ordered by `minHours` descending, e.g.
`[{"minHours":48,"percent":10},{"minHours":24,"percent":25},{"minHours":12,"percent":50},{"minHours":0,"percent":100}]`.
The charge is the first slab with `hoursBeforeBoarding >= minHours`. This one is the default template.
Hours are measured to the passenger's **boarding point time**.

**Times:** a point's actual time = `travel_date + day_offset days + time`. A trip's `departure_at`
is the earliest point time at stop 0, and `arrival_at` is the latest point time at the last stop.

## Owner side

- **Fleet type:** `PUT /api/owner/fleet-type {fleetType}`; it is also returned with the owner profile.
  The owner menu shows car items for CARS/BOTH and bus items for BUSES/BOTH. Owners without a
  fleet type are treated as CARS.
- **Buses:** create (DRAFT) → edit → seat layout → documents and photos → submit (PENDING) →
  admin approves or rejects. A rejected bus is edited and resubmitted. Submitting needs ≥1 seat,
  all 4 documents with future expiry dates, and ≥1 photo.
- **Seat layout:** the owner replaces the whole seat list. This is refused once any ticket exists for
  the bus. Templates (`2+1 Sleeper 30`, `2+2 Seater 40`, `Seater + Sleeper`) live in the frontend.
  `pair_label` is computed by the frontend (same deck, same row, adjacent column with no aisle gap),
  and the owner can edit it. The server checks that pairs are symmetric and refer to existing seats.
- **Routes:** stops (≥2 distinct places, in order), points per stop (stop 0 needs ≥1 boarding
  point, the last stop needs ≥1 dropping point), run days, dates, fares, policy, max seats.
  Publishing needs an APPROVED bus and at least one fare. Stops are locked after publish. Fares,
  points, policy and run days can be edited and affect new bookings only. Pause stops new trips
  from being created and hides future trips from search.
- **Trip creation:** on publish and daily at 01:00 for the next 60 days. A trip is created for each date
  where the run-day bit is set, the date is within `active_from..active_to`, the bus is APPROVED, and
  every document expires on or after that date. Idempotent through `UNIQUE (route_id, travel_date)`.
  A document expiring within 15 days triggers one owner notification.
- **Trips screen:** a seat chart with passengers; block/unblock seats (BLOCKED allocations
  over the full route); set crew; CSV manifest; cancel trip with a reason.

## Customer side

- `GET /api/bus/places?q=`: autocomplete, public.
- `GET /api/bus/search?from=&to=&date=`: public. A trip qualifies when its route has stops
  `from` then `to` in that order, at least one fare for that pair, the trip is SCHEDULED, and the
  boarding time at the `from` stop is still in the future. Each card has: trip id, owner travels
  name, bus name, type label (e.g. `A/C Sleeper (2+1)`, `Non-A/C Seater / Sleeper`), departure and
  arrival at the chosen stops, duration, rating avg/count, min available fare, seats left, single
  seats left, amenities, boarding and dropping point names. Sorting and filtering happen in the client.
- `GET /api/bus/trips/{id}?from=&to=`: public seat map. Each seat has label, deck, row, col, kind,
  berth, class, fare, and state `AVAILABLE | SOLD | BOOKED_F | BOOKED_M | FEMALE_ONLY | MALE_ONLY`
  (a HELD seat shows as SOLD). The response also has boarding points at `from`, dropping points at
  `to`, photos, amenities, the stop list, and the cancellation slabs as ₹ amounts with clock times.
- **Gender rule** (on the server, during the hold): a `ladies_only` seat needs F. If the seat's pair
  is HELD or CONFIRMED by another ticket with overlapping legs, the passenger's gender must equal
  that occupant's. Both seats of a pair in the same hold may be any gender. The map shows
  `FEMALE_ONLY` / `MALE_ONLY` from the same rule.

## Hold, pay, ticket

1. `POST /api/bus/holds` (ROLE_USER, `Idempotency-Key`). In one transaction:
   1. expire stale HELD rows on the trip
   2. check the limits, the gender rule, the max seats, and that boarding is in the future
   3. price the seats on the server
   4. insert the allocations HELD with `expires_at = now + 10 min`, plus a PENDING ticket and its passengers

   An exclusion violation returns 409 `"Seat U3 was just booked by someone else"`.
2. `POST /api/bus/tickets/{id}/pay` creates a Razorpay order and a `bus_payments` row (CREATED).
   The checkout timeout is set to the remaining hold time, at most 8 minutes.
3. `POST /api/bus/tickets/{id}/verify` (signature check), or the webhook `payment.captured` /
   `order.paid` for a bus order. Both are idempotent per Razorpay payment id. The ticket is confirmed
   if its allocations are still HELD and not expired. Otherwise:
   - if the same seats are still free, they are re-held and confirmed
   - if they are taken, a full refund is made and the ticket is EXPIRED, with a message to the customer
4. `POST /api/bus/tickets/{id}/release`: the customer closed checkout or the payment failed. The
   allocations go to EXPIRED straight away.
5. The **sweeper** runs every minute: it expires HELD rows and PENDING tickets past their hold time,
   and finds CAPTURED payments whose ticket is not CONFIRMED and applies step 3 to them.
6. On confirmation, the outbox sends an email (ticket details + cancellation table) and a Telegram message.
   The **reminder** goes about 2 hours before each passenger's boarding time: bus registration,
   crew phones, boarding point. It is sent to the contact and the extra phone (email/Telegram where linked).
7. `GET /api/bus/tickets`, `GET /api/bus/tickets/{id}`: the customer's own tickets.

## After booking

- `GET /api/bus/tickets/{id}/cancel-quote?seats=U3,U4` returns, per seat: fare, charge %, charge,
  refund (fare − charge + full seat GST). Refused at or after the boarding time.
- `POST /api/bus/tickets/{id}/cancel {seats}` cancels the passengers and their allocations
  (the seats become sellable again), queues a partial refund (`bus_refunds`), and sets the ticket to
  PARTIALLY_CANCELLED or CANCELLED.
- **Owner cancels a trip:** the trip becomes CANCELLED, every live ticket becomes SERVICE_CANCELLED, all
  allocations are CANCELLED, a full refund (fare + GST) is made per paid ticket, and everyone is
  notified. No payout is made for the trip.
- **Status job** (every 5 min): SCHEDULED with `departure_at <= now` becomes DEPARTED; DEPARTED with
  `arrival_at + 2h <= now` becomes COMPLETED, and its CONFIRMED/PARTIALLY_CANCELLED tickets become COMPLETED.
- **Payout job** (daily 06:00, idempotent through `trip_id` unique):
  - `fares_travelled` = sum of the fares of CONFIRMED passengers
  - `charges_kept` = sum of `cancel_charge`
  - `gross` = fares_travelled + charges_kept
  - `commission` = gross × owner percent / 100
  - `net` = gross − commission

  All amounts are rounded half-up to paise.
- **Admin:** bus approvals; payouts due (owner UPI / bank shown) with "mark paid" (UTR required);
  per-owner bus commission.
- **Reviews:** `POST /api/bus/tickets/{id}/review {rating, comment}`, once, only on a COMPLETED ticket.
  The averages per bus feed the search cards.

## API summary

Owner (`hasRole('OWNER')`, prefix `/api/owner/bus`):
```
GET  /buses                       POST /buses (JSON details → DRAFT)
GET  /buses/{id}                  PUT  /buses/{id}
PUT  /buses/{id}/seats            POST /buses/{id}/documents/{type} (multipart file, expiresOn)
POST /buses/{id}/photos (multipart) DELETE /buses/{id}/photos/{photoId}
POST /buses/{id}/submit
POST /places {name, district}
GET  /routes  POST /routes  GET /routes/{id}  PUT /routes/{id}
POST /routes/{id}/publish  POST /routes/{id}/pause
GET  /trips?from=&to=             GET /trips/{id}
PUT  /trips/{id}/crew             POST /trips/{id}/blocks {seats}   POST /trips/{id}/unblock {seats}
GET  /trips/{id}/manifest.csv     POST /trips/{id}/cancel {reason}
GET  /payouts
```
Admin (`hasRole('ADMIN')`, prefix `/api/admin/bus`):
```
GET /buses?status=   GET /buses/{id}   POST /buses/{id}/approve   POST /buses/{id}/reject {reason}
GET /payouts?status= POST /payouts/{id}/paid {utr}   PUT /owners/{id}/commission {percent}
```
Customer (prefix `/api/bus`): public `places`, `search`, `trips/{id}`. ROLE_USER: `holds`,
`tickets`, `tickets/{id}`, `pay`, `verify`, `release`, `cancel-quote`, `cancel`, `review`.

## Frontend

React 18 + Vite + Tailwind 4, following existing pages and `DashboardLayout`.

- **Owner:** Buses (list, 4-step add/edit wizard, layout editor with lower/upper deck panels and a
  preview), Routes (wizard: stops, points, days, fare grid with "Fill middle stops", policy),
  Trips (list + trip detail: seat chart, blocks, crew, manifest CSV, cancel), Bus earnings.
  Fleet type in Profile.
- **Admin:** Bus approvals, Bus payouts, commission field on the owner detail page.
- **Customer:** a Bus tab on the landing page and dashboard; `/bus/search` results (cards, sort,
  filters); `/bus/trip/:id` (Select seats → Boarding/Dropping → Passenger details → Razorpay checkout
  with countdown); `/user/bus-tickets` (Upcoming/Completed/Cancelled, cancel with quote, review).
- **Seat legend:** Available (white, green outline), Selected (green fill), Sold (grey), Booked by
  woman (pink fill), **Women only (pink dashed border + "Women" label)**, Booked by man (blue fill),
  Men only (blue dashed border).

## Testing

- **Migration and constraints** (Testcontainers): the overlap rule, adjacent legs allowed, expired
  rows don't block, and RLS isolation between owners.
- **Concurrency:** 20 threads holding the same seat → exactly one wins.
- **Services:**
  - pricing + GST
  - gender rule cases
  - hold limits and idempotency
  - payment on time / late-free / late-taken / failed / duplicate
  - sweeper
  - slab boundaries
  - partial cancel
  - trip cancel
  - payout maths and idempotency
  - trip generation
- **Frontend unit tests:** templates and pair calculation, seat state → style, fare fill, quote display.

## Build order

1. Owner side (migration, buses, layout, admin approval, routes, trips, owner trips screen).
2. Customer booking (search, seat map, hold, payment, ticket, sweeper, reminder).
3. After booking (cancel, trip cancel, status job, payouts, reviews).
