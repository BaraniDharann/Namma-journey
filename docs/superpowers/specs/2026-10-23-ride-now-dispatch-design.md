# Ride Now: nearest-driver dispatch

## Goal

A traveller can book a ride for **right now**. The system finds the nearest online driver
around the pickup point and offers the trip to them. The driver has a short window to accept.
If they decline or do not answer, the next nearest driver gets the offer. This is how Rapido
dispatches. Scheduled (future-date) bookings keep today's date-based assignment unchanged.

## Decisions (agreed 2026-10-23)

- **Option A.** Add "Ride Now" alongside scheduled bookings. Do not replace scheduled bookings.
- **Search rings:** 1 km → 3 km → 5 km around the pickup point. Each ring tries its drivers
  nearest first. When every driver within 5 km has been tried, the booking goes to the owner.
- **Offer window:** 30 seconds. No answer counts as a decline.
- **Settings:** the ring sizes, offer window and search time limit are settings
  (`app.dispatch.*`), not values fixed in the code.

## Driver presence

- "Online" is the existing `Driver.status = ACTIVE` switch on the driver dashboard.
- While online, the driver's phone reports its position every 15 s
  (`PUT /api/driver/{id}/presence`).
- `DriverPresenceService` keeps positions in a Redis GEO set. A per-driver "last seen" key
  expires after 60 s. A driver with no fix in the last 60 s is not offered rides.
  - If Redis is unavailable, the same data falls back to an in-memory map with a haversine
    search. This follows the degraded-not-broken pattern of `LocationTrackingService`.
- Going offline removes the driver from the set.
- Every live-trip location update also refreshes presence.
- **Ending a trip** puts the driver at the drop-off point. They are offerable straight away, and
  their next GPS report replaces that position with the real one.

## Data model (V13)

New columns on `travel_bookings`:

| Column | Type | Meaning |
|---|---|---|
| `dispatch_mode` | `SCHEDULED` \| `RIDE_NOW` | Default `SCHEDULED`. |
| `dispatch_state` | `SEARCHING` \| `OFFERED` \| `MATCHED` \| `NO_DRIVER` | Null for scheduled bookings. |
| `dispatch_started_at` | timestamp | When the search began. Bounds the search time. |
| `offer_expires_at` | timestamp | Deadline of the current offer. |
| `offer_distance_km` | double | Distance from the offered driver to the pickup point. |
| `trip_ended_at` | timestamp | Set when the driver ends the trip (see below). |

**Driver freed at trip end.** A trip stays `STARTED` after the driver ends it, until it is paid.
The overlap constraint `no_driver_overlap` treats `STARTED` as busy, so a driver waiting on a
UPI verification could never take the next ride.

- V13 rebuilds the constraint with `AND trip_ended_at IS NULL`.
- The availability queries change the same way.
- This applies to scheduled trips too: a driver who has ended a trip is free.

## Dispatch flow (`DispatchService`)

1. **Creating a booking.** A request with `rideNow: true` is a booking for today, in IST. It is
   saved `PENDING` with no driver and with `dispatch_state = SEARCHING`. After commit, dispatch
   runs.
2. **Choosing a driver.** For each ring in turn, candidates are the online drivers within that
   radius of the pickup point, nearest first. Drivers are skipped if they:
   - have declined or timed out on this booking (`booking_driver_rejections`),
   - are not `ACTIVE`, or
   - have an active, unended booking today. That includes a pending offer, so a driver only
     ever holds one offer at a time.
3. **Making the offer.** The first candidate gets the offer. The booking's `driverId` is set to
   them, with `dispatch_state = OFFERED` and `offer_expires_at = now + 30 s`.
   - The DB constraint settles any race. If it refuses the write, the next candidate is tried.
   - The driver is notified in the app, over the WebSocket topic `/topic/driver/{id}/offers`,
     and on Telegram.
4. **Accepting.** The booking goes to `CONFIRMED` with `MATCHED`. Accepting after the deadline
   is refused with "This ride offer has expired".
5. **Declining or timing out.** The driver is recorded in `booking_driver_rejections` and step 2
   runs again. A scheduled job checks for expired offers every 5 s.
6. **Nobody available.** No candidate within 5 km leaves the booking `SEARCHING` with no
   driver. The scheduled job retries every 5 s, so drivers who come online are picked up. After
   `search-timeout` (2 min) the state becomes `NO_DRIVER`.
   - The owner is notified once, and the booking shows in the owner's existing "needs a driver"
     list.
   - The traveller sees "No driver nearby yet — we're assigning one".
7. **Owner assigns by hand.** The booking becomes `MATCHED` and its offer fields are cleared.
8. **Traveller cancels.** The booking leaves `PENDING`, so dispatch ignores it from then on.

**Concurrency.**
- Accept and timeout race on `@Version`. The loser's optimistic-lock failure is caught and
  skipped.
- Two instances processing the same expiry race the same way.

**Offer push across instances.** Offers are relayed through Redis pub/sub
(`travelplatform::driver-offer-relay`), the same way location pushes are. The driver dashboard
also polls every 5 s as a fallback.

## Frontend

- **NewBooking:** a "Ride now / Schedule" switch. Ride now hides the dates. After booking, the
  traveller sees a live "Finding a driver near you…" status that polls the booking until it is
  matched, or until no driver is found.
- **DriverDashboard:**
  - While online, `useDriverPresence` sends GPS every 15 s. If location permission is denied,
    a warning explains that nearby rides need location.
  - It subscribes to the offer topic and shows an offer card with pickup, drop-off, distance to
    pickup, fare and a 30 s countdown, with Accept and Decline.
- **Location sharing at trip start:** this already happens. The live map, and the sender with
  it, mounts when the trip is `STARTED`.

## Out of scope

- Vehicle types and matching on AC/non-AC.
- Surge pricing.
- Showing the driver's approach to the passenger before the trip starts.
- Razorpay. It comes next, as its own piece of work.

## Testing

**Unit tests (Mockito)** for `DispatchService`:
- ring order and nearest first
- skipping declined drivers
- expiry leading to the next driver
- giving up after the search time
- refusing an accept after expiry
- an owner override clearing the offer

**Presence fallback:** a radius search over the in-memory store.

**Migration test:** extend `DriverOverlapConstraintTest` so an ended trip does not block. It
runs only where Docker is available.
