# Scaling to 100k+: batch matching

**Status:** draft for review, 2026-10-23.
**Builds on:** [Ride Now dispatch](2026-10-23-ride-now-dispatch-design.md) and
[City and Trip modes](2026-10-23-city-trip-modes-design.md).

## Goal

City mode has to stay fast and correct with **100k+ registered drivers and users**. Within that:

- tens of thousands of drivers online at the same time
- about 2,000 City bookings a minute at peak

City dispatch moves from "offer each booking to its nearest driver, one at a time" to
**batch matching**:

- every 2 seconds, per city
- every waiting ride and every free driver are matched together
- the matching minimises total road travel time to pickup

Trips (booked for dates) keep date-based assignment, with the faster queries from phase A.

## Decisions

| Topic | Decision |
|---|---|
| Hosting | Cloud VMs:<br>- several app servers behind a load balancer<br>- managed Postgres with a read replica<br>- Redis, RabbitMQ, and one OSRM routing server |
| Travel time (ETA) | Self-hosted OSRM, using its `/table` endpoint (a travel-time matrix). Until it exists, straight-line distance divided by an average city speed. |
| Cities | The owner adds and edits them. Nothing is seeded. City mode works only inside an active city. |
| Offer window | **15 seconds** (was 30) |
| Search rings | Still 1 → 3 → 5 km, now widening with **waiting time**: 1 km at first, 3 km after 20 s, 5 km after 40 s. All four values are settings. |
| Giving up | After 2 minutes with no match, the ride goes to the owner as `NO_DRIVER` (unchanged). |

## Targets

These have to be proven by the phase D load test before we claim them.

| Measure | Target |
|---|---|
| Online drivers reporting location every 15 s | 100,000 (about 6,700 reports/s across all servers) |
| City bookings | 2,000/min at peak |
| Booking → offer sent, when a driver is free nearby | p95 ≤ 3 s |
| Location report | p99 ≤ 50 ms |
| One matching round for 500 rides × 2,000 drivers | ≤ 500 ms |

## Architecture

```
 phones ──HTTPS/WSS──▶ load balancer ──▶ app servers (stateless, N copies)
                                          │   ├─ REST API
                                          │   ├─ matching worker (runs only the cities it holds a lock for)
                                          │   └─ STOMP over RabbitMQ (broker relay)
                                          ▼
                    Redis: locations, driver state, waiting rides, offer deadlines, per-city locks
                    Postgres: source of truth (bookings, drivers); double-booking constraint as the safety net
                    OSRM: road routes and ETA matrices
```

## Phase A: cities and the cheap wins (no new infrastructure)

1. **Cities table.**
   - Columns: `id`, `name`, `center_lat`, `center_lon`, `radius_km`, `active`.
   - The owner adds, edits and switches cities on and off on a new screen.
   - **A City booking's city** is the nearest active city whose radius contains the pickup.
     Outside every city, a City booking is refused, with a pointer to Trips.
   - **A driver's city** is worked out from each location report; drivers have no fixed city.
     Moving into another city moves them between per-city location sets.
2. **One location set per city:** Redis key `geo:city:{id}`, plus `driver:{id}:city` to remember
   the current city. The 60-second "still alive" expiry and the in-memory fallback stay.
3. **Cheap offer check.**
   - `GET /api/driver/{id}/offer` returns the current offer from Redis: booking id, seconds left,
     pickup, drop, fare, vehicle and distance.
   - The driver app polls it every **20 s** as a fallback, instead of downloading its full trip
     history every 8 s.
4. **Faster queries.**
   - The busy-driver check becomes `NOT EXISTS (… WHERE tb.driver_id = d.id …)`, which uses the
     `driver_id` index.
   - Trip assignment becomes one SQL query that includes the AC condition, with `LIMIT 1`, instead
     of loading every free driver.
   - Add an index on `travel_bookings (driver_id, from_date, to_date)` for active bookings.
5. **Route cache.**
   - `RoutingService.calculateRoute(lat, lon, lat, lon)` is cached in Redis for 10 minutes.
   - The cache key rounds coordinates to 3 decimal places (about 110 m).
   - The fare estimate and the booking then share one route lookup.
6. **Ignore over-frequent location reports:** a report arriving less than 5 s after that driver's
   previous one is dropped.

## Phase B: the batch matching engine

### State in Redis

Postgres stays the record of truth. Redis holds the working copy that one round needs, so a round
makes no database reads.

| Key | Type | Contents |
|---|---|---|
| `driver:{id}` | hash | `state` (IDLE/OFFERED/BUSY), `vehicleType`, `seats`, `servesCity`, `active`, `cityId` |
| `city:{id}:waiting` | sorted set | rides waiting, scored by search start time |
| `ride:{bookingId}` | hash | pickup coordinates, vehicle type, passengers, search start, fare |
| `ride:{bookingId}:declined` | set | drivers who declined or timed out on this ride |
| `city:{id}:offers` | sorted set | open offers, scored by deadline (the timeout queue) |
| `lock:match:city:{id}` | string | which server runs this city; expires after 5 s and is renewed every round |

**How the copy stays correct**

- The driver hash is written whenever the profile changes: vehicle, switches, online/offline.
- It is also written at each change of state: offer, accept, start, end, cancel, decline.
- Every minute a reconcile job rebuilds driver states from Postgres, so any drift corrects itself.
- If Redis is emptied, the server that takes a city's lock rebuilds `waiting` and `offers` from
  bookings in `SEARCHING` or `OFFERED` state.

### One matching round (every 2 s, per city, on the server holding that city's lock)

1. **Expire offers.**
   - Read from `city:{id}:offers` every offer whose deadline has passed.
   - Record each as a decline, set the driver back to IDLE, and return the ride to `waiting`.
2. **Gather rides.** Read up to 2,000 waiting rides. Any ride older than 2 minutes becomes
   `NO_DRIVER` and the owner is notified.
3. **Find candidates.** For each ride:
   - Choose the search radius from how long it has waited (1, 3 or 5 km).
   - Search that city's location set for the nearest 30 drivers.
   - Keep up to **K = 10** drivers who are IDLE, active, taking City rides, driving the requested
     vehicle with enough seats, and not on the ride's declined list. Driver hashes are read in one
     batched Redis request.
4. **Score.**
   - ETA from each candidate to the pickup comes from one OSRM `/table` request per ride (one
     destination, up to K sources).
   - `cost = eta_seconds − 2 × waited_seconds`. A ride that has waited longer wins a contested
     driver, so nobody starves.
   - Pairs with an ETA over 15 minutes are dropped.
5. **Solve the assignment.**
   - Split the ride–driver graph into connected groups using **union-find**. Rides far apart never
     compete, so a city usually breaks into many small independent problems.
   - Groups of up to 300 rides are solved by the **Hungarian algorithm**. It finds the minimum
     total cost in O(n³), about 30 ms at n = 300. Missing pairs get infinite cost.
   - Larger groups use **greedy**: sort the pairs by cost and take each one whose ride and driver
     are both still free. That is O(E log E), where E is the number of candidate pairs.
6. **Send the offers.** For each matched pair:
   - One short database transaction: set the booking to `driverId`, `OFFERED`, deadline = now + 15 s
     (optimistic locking still applies).
   - If `no_driver_overlap` refuses it, skip this pair; that ride goes into the next round.
   - Otherwise:
     - set the driver to OFFERED
     - remove the ride from `waiting` and add it to `offers`
     - push the offer to the driver
7. **Metrics** per round:
   - rides waiting
   - drivers available
   - pairs considered
   - time to solve
   - match rate
   - average ETA

### Driver answers

- **Accept** (database first, then Redis):
  - the booking becomes `CONFIRMED` / `MATCHED`
  - the driver becomes BUSY
  - the ride leaves `offers`
- **Decline:**
  - record the rejection and add the driver to the ride's declined set
  - set the driver to IDLE
  - put the ride back in `waiting`; the next round, about 2 s later, re-matches it
- **Trip end:** the driver becomes IDLE (as today, the ended trip frees them).
- **Traveller cancels:** the ride leaves `waiting` or `offers`; a driver holding the offer becomes
  IDLE and their offer is withdrawn.

### Spreading cities across servers

- Every server tries to take each city's lock with `SET NX PX 5000`, and renews the locks it holds
  each round.
- A server keeps at most `ceil(active cities / live servers) + 1` cities, so the work spreads out.
- When a server dies, its locks expire within 5 s and another server takes those cities.

### Components (one job each)

| Unit | Job |
|---|---|
| `CityService` | city CRUD (create, read, update, delete); find the city for a point (cached) |
| `DriverPresenceService` | per-city location sets, freshness |
| `DriverStateStore` | the Redis driver hash and the reconcile job |
| `RideQueue` | the waiting set, ride hashes, declined sets, the offer deadline queue |
| `EtaService` | interface: `HaversineEtaService` now, `OsrmEtaService` in phase C |
| `AssignmentSolver` | union-find split, Hungarian, greedy: plain Java, no I/O, fully unit-tested |
| `MatchingWorker` | city locks, the round loop, writing offers |
| `DispatchService` | accept, decline, cancel, owner override; replaces the old one-at-a-time step and sweep |

## Phase C: infrastructure

1. **RabbitMQ as the STOMP broker.**
   - Uses Spring's `enableStompBrokerRelay`, with RabbitMQ's STOMP plugin.
   - Every server then reaches every socket.
   - The Redis relays for offers and locations are removed.
   - Destinations and permission checks are unchanged.
2. **Self-hosted OSRM.**
   - A Docker `osrm-backend` server using Geofabrik's India map data (or one region), prepared with
     the MLD algorithm.
   - `OsrmEtaService` calls `/table/v1/driving`; `RoutingService` uses it for routes.
   - Production refuses the public OSRM demo server.
3. **Deployment kit.**
   - A `docker-compose.prod.yml` containing the app, RabbitMQ and OSRM.
   - An nginx load-balancer config with WebSocket stickiness.
   - A runbook for managed Postgres and Redis, and the environment variables.
   - Provisioning the cloud account itself needs your credentials; I write the files and the steps.

## Phase D: load test at 100k

New k6 scenarios in `loadtest/`:

- `presence.js`: 100k virtual drivers reporting every 15 s
- `city-rides.js`: 2,000 bookings/min, with a driver-side responder that accepts or declines
- matching-round timings collected from metrics

The pass/fail limits are the targets above. Results go into `loadtest/README.md`. The targets are
not claimed until this passes.

## Testing

- **`AssignmentSolver`:**
  - Hungarian results checked against brute force on small random cases
  - greedy fallback
  - union-find grouping
  - empty and infinite-cost inputs
- **`MatchingWorker` against an in-memory Redis stand-in:**
  - timeout re-queues the ride
  - decline excludes the driver
  - a long wait wins the contested driver
  - the radius widens with time
  - two workers never run the same city
  - recovery after Redis is emptied
- **Database (needs Docker):** the `NOT EXISTS` query and the `LIMIT 1` trip query match the old
  results.
- **Existing suites:** dispatch, city/trip and fares are updated for the new flow.

## Out of scope

- Surge pricing.
- Live traffic in ETAs (OSRM uses typical speeds).
- Pooling or shared rides.
- Geofences drawn as polygons (cities are circles for now).
- Razorpay comes after phase B unless you reprioritise.
