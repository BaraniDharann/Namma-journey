# Operator side, business rules, Tamil Nadu legal notes (research, Nov 2026)

Sources:
- eTravelSmart bus API docs (an Indian aggregator API in the same style as redBus SeatSeller), the best primary technical source
- QwikBus / Bitla vendor blogs
- DT Next, LiveLaw, IAMAI, news reports

redBus's operator portal needs a login and its terms are not public.
**(unconfirmed)** = inference; confirm with a real operator, a lawyer or a CA as noted.

## Contents
1. Operator onboarding, documents, commission, payout
2. Permits: Tamil Nadu legal risk (read before launch)
3. Bus and seat-layout setup, pricing
4. Routes, schedules, segment selling, quota, blocks
5. Operator dashboard
6. Cancellation, reschedule, refund, GST, insurance

---

## 1. Onboarding
redBus flow:
1. Mobile OTP signup.
2. Form: vehicle type, number of buses, **PAN, GST number**, **cancelled cheque**, payment mandate (bank account for payouts).
3. A BD team call verifies the details (1–3 business days).
4. Either an API integration with the operator's inventory system (2–6 weeks) or portal logins.
5. T&C accepted; inventory goes live.

Documents:
- operator licence, **route permits per route**, GST, PAN, bank account
- per bus: type, make, model, registration, seat layout
- per route: boarding and dropping points with address, landmark and times

Also collect, with **expiry dates**: RC, insurance, fitness, PUC, permit. Block a bus from new
trips once any of them lapses (unconfirmed as a redBus rule, but sensible).

**Commission:** about 10–20% of ticket value (third-party estimates; QwikBus example uses 12%).
GDS software takes about 2–5%.

**Payout:** not published. Industry norm is T+1 to weekly (unconfirmed). The statement should show
gross fare, commission, GST on commission, **TCS**, and net.

## 2. Permits: Tamil Nadu legal risk
- **Stage carriage** permit: may pick up and drop individual fare-paying passengers at several stops.
  This is what per-seat, multi-stop selling legally needs.
- **Contract carriage** permit: the whole vehicle is hired, point to point.
- The **All India Tourist Permit (AITP)** is a *contract carriage* permit. The Centre told the
  Kerala High Court so, and the Kerala High Court ruled AITP buses cannot run as stage carriages.
- The TN Transport Dept (June 2024) listed these as AITP violations:
  - multi-point pickup and drop
  - SMS/e-tickets and **selling via redBus/AbhiBus**
  - no passenger origin–destination record

  650+ out-of-state AITP buses were told to re-register in TN.
- **Implications for us:**
  - store `permit_type` per bus
  - keep a full passenger manifest with origin and destination per seat
  - **get legal advice before selling per-seat tickets on AITP or contract-carriage buses**
  - a likely early rule: only buses with a permit that allows per-seat sale can list seat-wise

## 3. Bus and seat-layout setup
**Reference seat schema** (eTravelSmart layout API):
- `id`: seat label; special cells include EME (emergency exit) and WSR (restroom)
- `row`, `column`, `zIndex` (0 = lower deck, 1 = upper deck)
- `length` × `width` in grid units: seater 1×1, vertical sleeper 1×2, horizontal sleeper 2×1
- `ladiesSeat`, `malesSeat`, `available`, `bookedBy` (gender)
- `ac`, `sleeper`
- `fare`, `totalFareWithTaxes`, `serviceTaxAmount`, `childFare`: **price is per seat**

**Common configurations** (unconfirmed by source, industry standard):
- Seater/semi-sleeper 2+2 with 40–49 seats; 2+1 with 30–36.
- Sleeper 2+1 with 30–36 berths: a single-berth column plus a double-berth column, lower and upper decks.
- Seater + sleeper: lower deck seats plus singles, upper deck berths (the screenshot bus).
- 1+1 premium sleeper.

**Numbering:** seaters 1..N; sleepers L1..L18 / U1..U18; operators vary. Keep labels as
free text in a data-driven layout, starting from templates per bus model.

**Pricing:**
- Per-seat fares: window, aisle, single, double, lower and upper can all differ.
- The fare can also depend on the boarding/dropping (BP/DP) pair.
- The fare may change between search and block, so re-check it at block time and snapshot it on the ticket.

**Dynamic pricing:**
- redBus revMax makes about 15k adjustments a day.
- Operator tools move the fare between an **operator-set floor and ceiling** by occupancy, booking pace and days to departure, with manual override.

**TN fare politics:**
- Omni-bus festival fares are a hot issue.
- The 2022 agreed band for Chennai–Trichy was ₹1,200–1,980.
- Madras High Court directions exist.
- Add a **per-route maximum fare guardrail**.

## 4. Routes, schedules, segments, quota
- A **service** (route template) has ordered stops. Each city has several boarding/dropping
  points, each with its own time, address and landmark. Service days (weekday pattern or date range)
  generate a **trip per date**.
- **Segment selling** (Chennai→Trichy on a Chennai→Tirunelveli bus):
  - Model the trip as consecutive legs between stops. A booking occupies a seat over legs [from, to).
  - Once A→B is sold, the seat is free again for B→C.
  - Postgres guard: an allocation row `(trip_id, seat_id, int4range(from_idx,to_idx))` with
    `EXCLUDE USING gist (trip_id WITH =, seat_id WITH =, legs WITH &&)` (needs btree_gist).
  - Many TN operators only sell full route or a few allowed origin–destination pairs with a fare
    matrix (unconfirmed). An "allowed OD pairs" setting is the norm.
- **Quota and channels:**
  - one inventory shared by all channels (OTA, counter, agents, phone)
  - optional per-channel quotas with a **release time** before departure (unconfirmed)
- **Operator blocks:** seats held for crew, VIP or counter. These are separate from payment holds.
- **Operator cancels the trip:**
  - tickets move to `SERVICE_CANCELLED`
  - **100% refund** (including the convenience fee, unconfirmed)
  - notify passengers

## 5. Operator dashboard
- Bookings and inventory reports, occupancy, revenue, and OTA reconciliation (commission, TCS, GST).
- **Passenger chart / manifest per departure:** seat, name, age, gender, BP/DP, phone. This is also a
  TN compliance record.
- Seat chart per trip showing booked, held, blocked and ladies seats.
- Phone, counter and agent bookings on the same inventory.
- Turn channels on or off; per-channel pricing.
- **Assign bus + driver/conductor per trip.** The passenger gets the bus number + crew phone before
  departure (redBus: about 30 min before; use Telegram/email here).
- Live GPS: on eTravelSmart only for GPS-enabled operators, from 2h before departure.
- Dynamic pricing controls: floor, ceiling, override.

## 6. Business rules
**Cancellation policy = per-operator slabs** (eTravelSmart format):
`[{"cutoffTime":"0-24","refundInPercentage":"5"},{"cutoffTime":"24-48",...}]`, in hours before
departure. Some operators use minutes, so normalise.

- Real examples:
  - 100% charge within 12h, 50% at 12–24h, 10% earlier
  - another operator: 100% within 24h
- Typical market pattern (unconfirmed): ~10–15% at >48h, 25% at 24–48h, 50% at 12–24h, 100% under 6–12h.
- Compute on the base fare; the convenience fee is not refunded.
- Snapshot the policy onto the ticket at booking.

**Cancel flow, two steps:**
1. Quote: charge % and refund ₹, nothing changes yet.
2. Execute, with `seatNumbersToCancel` for a partial cancel.

No refund after departure.

**Reschedule:** free until 8h before departure, once only, cannot cancel afterwards, fare difference
payable. Late changes cost 20–25%.

**Refund timing:** initiated within 24h; UPI 2–3, net banking 3–5, cards 5–7 business days; wallet instant.

**GST** (**confirm with a CA**):
- AC bus tickets: 5%, no input tax credit.
- Non-AC tickets:
  - exempt when sold offline
  - **5% when sold through an e-commerce operator under Sec 9(5)** (Notif. 17/2021, upheld by the Delhi High Court)
  - the **platform** pays it, not the operator
  - as a multi-owner marketplace we are likely that e-commerce operator
- Commission attracts 18% GST.
- TCS applies to operator payouts.

**Insurance:** optional, per passenger, opt in only at booking (old ICICI price ₹10 per trip).
