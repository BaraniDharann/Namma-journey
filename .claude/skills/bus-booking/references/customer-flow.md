# redBus customer side: features and UI (research, Nov 2026)

Sources: press articles, consumer complaints with redBus replies, App Store listing, a
security write-up, search excerpts of redbus.in pages, plus the user's own screenshot of the
seat screen. redbus.in itself and the redBus Medium blog did not load during research.
**(unconfirmed)** = recollection or inference; verify in the live app before copying exactly.

## Contents
1. Home / search
2. Search results list (bus card, sort, filters)
3. Seat selection screen (states, gender rules, layouts)
4. Boarding & dropping points
5. Passenger info
6. Payment and seat hold
7. Ticket / confirmation
8. Post-booking (cancel, reschedule, review, help)
9. Account
10. Other features (Primo, women safety, insurance, deals)

---

## 1. Home / search
- From, To, Date of Journey + **"Booking for women"** toggle (confirmed). It surfaces women
  deals, the count of women on each bus, priority helpline, buses preferred by women. A
  preference, not a hard filter (unconfirmed).
- Today / Tomorrow chips, calendar with past dates greyed (unconfirmed).
- Swap ⇄ button between From and To (unconfirmed, standard).
- City autocomplete with sub-areas: "Koyambedu" resolves to Chennai (unconfirmed).
- Recent searches as chips (unconfirmed).
- Offer carousels with codes and bank offers; min-fare and validity conditions.
- Tamil UI launched April 2024; redBus reported ~3x better conversion than English.

## 2. Search results list
**Bus card** (matches screenshot; exact layout unconfirmed):
- operator name
- bus type string: "A/C Sleeper (2+1)", "NON A/C Seater / Sleeper (2+1)", "A/C Seater (2+2)"
- departure → arrival time (+ next-day date), duration "07h 30m"
- rating badge (e.g. ★4.5) + rating count
- "Starts from ₹X", struck-through original when a deal applies
- "N Seats left", "N Single seats left"
- amenity icons; badges: live tracking (confirmed), Primo (confirmed),
  on-time tag for ≥80% on-time performance (confirmed), deal label

**Sort:** default ML "Bus Score" ranking; Ratings, Departure time, Price (confirmed);
"Fastest" (unconfirmed).

**Filters:**
- Bus type: AC / Non-AC / Sleeper / Seater (confirmed)
- Single seats: at least one seat with no neighbour (confirmed; >5% of sessions use it)
- Departure time buckets: before 6am, 6–12, 12–6pm, after 6pm (labels unconfirmed)
- Arrival time buckets (unconfirmed)
- Boarding points and dropping points: searchable checklists (confirmed)
- Operators: searchable checklist (confirmed)
- Amenities: WiFi, water bottle, blanket, charging point, reading light, CCTV, live tracking (list unconfirmed)
- Deals (confirmed); Primo, live tracking, rating ≥4, price range (unconfirmed)
- Quick chips above the list on the app: AC, Sleeper, Single seats, Primo (unconfirmed)

## 3. Seat selection screen
**Layout** (from the user's screenshot of PRM Roadways, A/C Seater / Sleeper 2+1):
- Two panels: **Lower deck** with the steering-wheel icon at the front, **Upper deck** without it.
- Seaters are drawn as small chair icons, sleepers as tall rectangles (berths).
- A mixed bus has a lower deck with seaters plus a single-sleeper column, and an upper deck of all sleepers.
- **Every seat shows its own price** under it: ₹1460 seater, ₹1810 lower single sleeper,
  ₹1760 upper single sleeper, ₹1660 upper double sleeper, ₹1360 for a discounted seat.
- Step tabs at the top: **Select seats → Board/Drop point → Passenger Info**.
- Sticky footer: "1 seat  ₹1,760  [⊞ fare breakup]" and the CTA **"Select boarding & dropping points"**.
- Right panel: operator name, rating (★4.5, 194 ratings), "20:00 - 07:15 · Thu 05 Nov", bus type,
  photo carousel, then tabs: Highlights, Cancellation policy, Reschedule policy, Bus route,
  Boarding points, Amenities. "Women's choice: Highly rated by women" highlight card.
- "Women deal ₹100 OFF" badge in the header.

**Seat states (legend):**

| State | Look | Source |
|---|---|---|
| Available | white with green/grey outline, price shown | screenshot |
| Sold | grey fill, "Sold" label, not clickable | screenshot |
| Booked by female | pink, person icon | confirmed |
| Available only for female | pink outline/icon on white, still shows price | screenshot + complaint |
| Booked by male | blue/lavender, person icon | screenshot |
| Available only for male | blue outline (newer legend) | unconfirmed |
| Selected | filled brand/green accent | unconfirmed colour |

A known UX flaw: the "female only" state was too close to "available", so men picked those seats.
Make it unmistakable.

Price-filter chips above the layout ("All", "₹1460", "₹1760") highlight seats at that price (unconfirmed).

**Gender adjacency rule:** confirmed by redBus replies (2016 CEO statement, 2017 support):
*when a female books a seat, the adjoining seat becomes reserved for females only* and is shown
in a different colour. The passenger page then says the seat "will be confirmed for a female
passenger". A mirror rule for men may exist on newer layouts (unconfirmed). There was at least
one reported failure, so **enforce it on the server**, not only in the UI.

**Single vs double:** in a 2+1 sleeper the "1" side has single berths and the "2" side has
double berths. "Single seat" means no neighbour. A double berth is usually sold as two seats
with the adjacency rule applied (unconfirmed).

**Max seats per booking:** commonly 6 (unconfirmed); operators may set it lower.

## 4. Boarding & dropping points
- Two radio lists or tabs. Each row has a **time** (21:30), a **point name**
  ("Koyambedu Omni Bus Stand"), an **address or landmark**, and sometimes a contact number.
- Both points are required before passenger info. Long lists get a search box.
- After booking, Track My Bus suggests the nearest boarding point and its ETA.

## 5. Passenger info
- **Per seat:** seat number, Name, Age, Gender (Male/Female radio). Female-only seats force
  Female.
- **Contact:** mobile (+ country code) and email; the ticket goes to both.
- State of residence for GST place of supply (unconfirmed). A GST invoice can be downloaded later (confirmed).
- **Add-ons:**
  - Free Cancellation (a paid add-on giving a full refund up to a cut-off)
  - insurance (ACKO), which can only be added at booking time
- WhatsApp updates opt-in (unconfirmed as a checkbox); tickets are sent on WhatsApp.
- An extra contact number that also receives the bus number and tracking link (confirmed).
- Passenger name, age, gender and phone are shared with the operator for boarding checks.

## 6. Payment and seat hold
- Coupons, bank offers, **redDeals**:
  - operator-funded discounts, typically 5–25%, applied automatically with no code
  - stack with other offers
  - types: Return trip, Early bird, Last minute, Trial, Festive
- Wallet ("Offer Cash"): refunds and cashback land here; cashback expires (6 months seen); cannot be withdrawn as cash.
- Methods: UPI, cards, net banking, wallets, pay-later.
- **Seat hold: 10 minutes** after moving to payment (confirmed via bug-bounty write-up).
  - The bug: a researcher re-sent the lock request every 10 min and held a seat forever.
  - So we need to rate-limit holds, not extend a hold by re-locking, and cap active holds per user or session.
- **Payment failure:** debited-but-no-ticket gets refunded in 5–7 bank working days.
  One complaint: the seat stayed "booked" for 15+ min after a failed payment, so **release the hold
  immediately on failure**.

## 7. Ticket / confirmation
- Ticket ID is alphanumeric starting with "T" (e.g. TN9M48486363), plus the operator **PNR**.
- Sent as an SMS m-ticket, an emailed PDF, and on WhatsApp.
- **Contents:**
  - operator and bus type, date, seat numbers
  - passenger name, age, gender
  - boarding point name, address, landmark, time, reporting time, contact
  - dropping point
  - fare breakup: base, GST, discount
  - **cancellation slab table with exact date-time cut-offs and ₹ amounts**
  - date-change rules, T&C
- About **30 min before departure:** bus registration number + tracking link (also to the extra
  contact). Driver or conductor phone depends on the operator.
- Track My Bus: live map, ETA to boarding point, delay alerts, share link, "time to get off"
  alert. Only for GPS-enabled operators.

## 8. Post-booking
- **Cancel:** My Bookings → ticket → Cancel → choose seats (**partial, per seat**) → see
  exact refund → confirm. Confirmation goes by SMS and email.
- **Charges are set by each operator**, per seat, on the fare excluding GST. Examples:
  - 0–6h before departure: 100%
  - then 50% / 25% / 15% / 10% further out
  - one operator: 50% at ≥72h and 100% inside 72h
  - convenience fee not refunded
- **Free Cancellation add-on:** full refund up to 6h or 12h before departure. Cannot be combined with vouchers.
- **Operator cancels the trip:** automatic full refund plus SMS/email.
- **Refund times:** wallet instant, UPI 2–3 days, net banking 3–5, cards 5–7 business days.
- **Reschedule** (select operators only):
  - free until 8h before departure
  - **once only**
  - **the ticket cannot be cancelled afterwards**
  - the passenger pays any fare difference
- **Reviews after the trip:** staff behaviour, bus quality, punctuality + text. They feed Bus Score,
  Primo and the on-time tag; bad operators can be delisted.
- **Help:** in-app issue flows per booking, WhatsApp bot, call centre, email.

## 9. Account
- Login with mobile + OTP (Google/email unconfirmed). Many offers need a verified mobile.
- Wallet; referral gives ₹100 after the friend's first trip.
- Saved co-travellers (unconfirmed).
- My Bookings: Upcoming / Completed / Cancelled; download ticket and GST invoice; cancel; change date; track.

## 10. Other features
- **Primo:** curated top ~10% operators ("4+ rated, friendly staff, on-time"), ~95% on-time.
- **Women safety:**
  - "Booking for women" mode, women-rated buses, count of women on the bus
  - adjacency rule
  - priority helpline
  - share live tracking with family
- **Return trip discount:** a redDeal type funded by the operator.
- **Group booking:** no dedicated product; bus hire is a separate flow.
- **Insurance:** "redBus Assurance" (ACKO) covers accident, some cancellations, and **bus-type
  mismatch** (booked AC, got non-AC). Old ICICI version cost ₹10 per trip.
- Rest-stop info (location, duration).
- rPool (carpool, 2019) is a side product; ignore.
