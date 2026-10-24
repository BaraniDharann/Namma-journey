# Scale, Phases A and B: Cities, Cheap Wins, and Batch Matching — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** City mode keeps working at 100k+ drivers. It gets cities, a location set per city, and
cheaper hot paths (phase A). Every 2 s, each city's waiting rides and free drivers are then
paired in one batch (phase B).

**Architecture:**
- Postgres stays the source of truth.
- Redis holds the working copy a matching round needs: driver state, waiting rides, offer
  deadlines, and a lock per city.
- Each Redis-backed store has an in-memory twin, selected by `app.cache.type`. Tests use the
  twins, and a single instance without Redis still works.
- `AssignmentSolver` is pure Java: union-find, Hungarian, greedy.
- `MatchingWorker` runs the rounds for each city whose lock this instance holds.

**Tech Stack:** Spring Boot 3.4, JPA/Flyway on Postgres 15, Spring Data Redis (Lettuce), JUnit 5 +
Mockito, React 18 + Vite.

**Spec:** [docs/superpowers/specs/2026-10-23-scale-batch-matching-design.md](../specs/2026-10-23-scale-batch-matching-design.md).
Phases C (RabbitMQ, OSRM) and D (load test) get their own plans once this one has shipped.

## Global Constraints

**Offers and search**
- Offer window: **15 s**. Change `DispatchProperties.offerSeconds` default 30 → 15, and
  `application.yml` `DISPATCH_OFFER_SECONDS:15`.
- Search radius grows with waiting time, in km: `radii-km: 1,3,5` paired with
  `radius-after-seconds: 0,20,40`.
- Give up after `search-timeout-seconds: 120` → `NO_DRIVER`, then notify the owner (unchanged).

**Matching rounds**
- Round interval: `app.dispatch.round-interval-ms: 2000`.
- Candidates per ride: **K = 10**, taken from the nearest **30** found by the location search.
- Maximum pickup ETA: `app.dispatch.max-eta-seconds: 900`.
- Pair cost: `eta_seconds − 2 × waited_seconds`, with weight `app.dispatch.wait-weight: 2`.
- Hungarian for connected groups of **≤ 300 rides**. Larger groups use greedy.
- At most **2000** waiting rides read per city per round.

**Locks and liveness**
- City lock: `SET NX PX 5000`, renewed every round.
- A server holds at most `ceil(activeCities / liveServers) + 1` cities. Live servers are counted
  from heartbeat keys `match:server:{instanceId}` with a 10 s expiry.
- Location reports from one driver less than **5 s** apart are dropped.
- The "still alive" window stays at 60 s.

**Other values**
- Route cache: Redis cache `routes`, expiry **10 min**, key coordinates rounded to **3 decimals**.
- Haversine ETA (used until OSRM, phase C): average city speed `app.dispatch.city-speed-kmh: 20`.

**Rules that still apply**
- Postgres stays the source of truth, and `no_driver_overlap` stays the final guard.
- Every write that sends something to the outside world still goes through `AfterCommitDispatcher`.
- Redis key prefix is `travelplatform::`, as existing keys use.
- New code follows the house style: Javadoc that explains *why*, field injection with
  `required = false` where hand-built tests construct services, and `@PreAuthorize` on every new
  endpoint.

## Review Focus

1. **A pickup outside every active city.** The traveller gets a clear "City rides aren't
   available here — book a Trip" message, never a 500 error, and no booking is saved.
   Test: Task 1 `cityRideOutsideEveryCityIsRefused`.
2. **A driver driving from one city into the next.** They leave the old city's location set, so
   the old city's rounds never offer them a ride.
   Test: Task 2 `driverMovingCityLeavesOldSet`.
3. **Redis is emptied, or restarts, mid-search.** Waiting rides and open offers come back from
   Postgres on the next lock take-over, and no ride is stranded in `SEARCHING`.
   Test: Task 9 `rebuildsQueuesFromDatabaseAfterRedisLoss`.
4. **A driver declines, then the next round runs.** That ride never goes back to the same driver,
   even when they are the only one nearby.
   Test: Task 9 `declinedDriverNeverReOffered`.
5. **Two servers start at the same moment.** Only one runs a given city. A round never sends two
   offers for one ride, or two rides to one driver.
   Test: Task 8 `secondHolderCannotTakeLock` and Task 9 `oneDriverNeverGetsTwoRidesInARound`.

---

## Files

```
Phase A
  src/main/resources/db/migration/V15__cities_and_hot_paths.sql   (new) cities, booking.city_id, index
  entity/City.java, repository/CityRepository.java                 (new)
  service/CityService.java                                         (new) CRUD + cityFor(lat,lon)
  controller/OwnerController.java                                  (mod) /api/owner/cities
  service/UserService.java                                         (mod) City ride needs a city
  service/DriverPresenceService.java                               (mod) per-city sets, 5 s throttle
  dto/DriverOfferView.java                                         (new) the cheap offer payload
  service/DriverService.java, controller/DriverController.java     (mod) GET /api/driver/{id}/offer
  repository/DriverRepository.java                                 (mod) NOT EXISTS, LIMIT 1
  service/RoutingService.java, config/CacheConfig.java             (mod) "routes" cache
  frontend: pages/owner/OwnerCities.jsx (new), App routes + owner nav (mod),
            hooks/useDriverDispatch.js (mod), utils/api.js (mod)

Phase B   (all new unless marked)
  service/dispatch/EtaService.java, HaversineEtaService.java
  service/dispatch/AssignmentSolver.java
  service/dispatch/CityLock.java, RedisCityLock.java, LocalCityLock.java
  service/dispatch/DriverStateStore.java, RedisDriverStateStore.java, LocalDriverStateStore.java
  service/dispatch/RideQueue.java, RedisRideQueue.java, LocalRideQueue.java
  service/dispatch/DispatchStoresConfig.java                       picks Redis or local twins
  service/dispatch/MatchingWorker.java
  service/DispatchService.java                                     (mod) accept/decline/cancel on the new stores
  config/DispatchProperties.java, application.yml                  (mod) new settings
```

Paths are under `src/main/java/com/travelplatform/` unless they start with `src/` or `frontend/`.
Tests mirror them under `src/test/java/com/travelplatform/`.

---

## Phase A

### Task 1: Cities

**Files:**
- Create:
  - `V15__cities_and_hot_paths.sql`: the cities part; Task 4 adds the index to the same file
  - `entity/City.java`
  - `repository/CityRepository.java`
  - `service/CityService.java`
  - `frontend/src/pages/owner/OwnerCities.jsx`
- Modify:
  - `entity/TravelBooking.java`: `cityId`
  - `service/UserService.java`: the city branch of `createBooking`
  - `controller/OwnerController.java`
  - `frontend/src/utils/api.js`
  - `frontend/src/App.jsx`: route `/owner/cities`
  - the owner `navItems` arrays: add `{ path: '/owner/cities', label: 'Cities' }`
- Test:
  - `service/CityServiceTest.java`
  - `service/UserServiceCityTripTest.java` (extend)

**Interfaces:**
- Produces:
  - `City` (`id Long`, `name String`, `centerLat double`, `centerLon double`, `radiusKm double`,
    `active boolean`, `createdAt LocalDateTime`)
  - `CityService.cityFor(double lat, double lon) -> Optional<City>`: the active city whose radius
    contains the point; when several do, the one whose center is nearest
  - `CityService.all() -> List<City>`
  - `CityService.save(City) -> City`: validates, then refreshes the in-memory list
  - `CityService.activeIds() -> List<Long>`
  - `TravelBooking.cityId Long`, nullable (always null for Trips)

- [x] **Step 1: Write the migration.**
  - `cities` table:
    - `id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
    - `name varchar(80) NOT NULL UNIQUE`
    - `center_lat double precision NOT NULL`
    - `center_lon double precision NOT NULL`
    - `radius_km double precision NOT NULL CHECK (radius_km BETWEEN 1 AND 100)`
    - `active boolean NOT NULL DEFAULT true`
    - `created_at timestamp NOT NULL DEFAULT now()`
  - `travel_bookings`: add `city_id bigint REFERENCES cities(id)`.
  - Seed nothing.
- [x] **Step 2: Write the failing tests** in `CityServiceTest`:
  - `pointInsideRadiusFindsCity`: Bengaluru at 12.9716/77.5946, radius 30. A point 10 km north
    gives Bengaluru.
  - `pointOutsideEveryCityFindsNone`: 40 km north gives empty.
  - `overlappingCitiesPickNearestCenter`
  - `inactiveCityIgnored`
  - `saveRejectsRadiusOutside1to100` and `saveRejectsBlankName`: both throw
    `IllegalArgumentException`.
- [x] **Step 3: Run** `mvn -o -q test -Dtest=CityServiceTest`. Expected: compilation FAIL.
- [x] **Step 4: Implement `CityService`.**
  - Keep active cities in a `volatile List<City>`, loaded at startup and replaced after each save.
  - `cityFor` is a linear scan with `TripRules.distanceKm`. There are tens of cities, not
    thousands.
- [x] **Step 5: Owner endpoints**, each `@PreAuthorize("hasRole('OWNER')")`:
  - `GET /api/owner/cities`
  - `POST /api/owner/cities`
  - `PUT /api/owner/cities/{id}`

  Body: `{name, centerLat, centerLon, radiusKm, active}`.
- [x] **Step 6: Extend `UserServiceCityTripTest`.**
  - `cityRideOutsideEveryCityIsRefused`: `cityFor` returns empty. Expect `IllegalArgumentException`
    whose message contains `"Trip"`, and `saveAndFlush` never called.
  - `cityRideRecordsItsCity`: the saved booking's `cityId` equals the stubbed city's id.
- [x] **Step 7: Implement.**
  - In `createBooking`'s city branch, resolve `cityService.cityFor(fromLat, fromLon)` before
    routing.
  - Refuse when it is empty, with the message *"City rides aren't available at this pickup yet.
    Book it as a Trip instead."*
  - Carry the id into `buildBooking`.
  - Inject `cityService` with `@Autowired(required = false)`.
- [x] **Step 8: Build `OwnerCities.jsx`.**
  - A list of cities, each with Edit and an Active switch.
  - An Add/Edit form: name; center, picked with `PlaceAutocomplete` (fills lat/lon); radius in km
    (number, 1–100).
  - Use the pc dashboard components the other owner pages use.
- [x] **Step 9: Run** `mvn -o -q test -Dtest='CityServiceTest,UserServiceCityTripTest'`, then in
  `frontend/`: `npm run build`. Expected: all PASS, and the build prints `✓ built`.
- [x] **Step 10: Commit** `feat(city): cities the owner manages; City rides need one`.

### Task 2: A location set per city

**Files:**
- Modify: `service/DriverPresenceService.java`, `service/DispatchService.java` (the call site)
- Test: `service/DriverPresenceServiceTest.java`

**Interfaces:**
- Consumes: `CityService.cityFor`
- Produces:
  - `DriverPresenceService.report(long driverId, double lat, double lon) -> Optional<Long>`: the
    city the driver was placed in; empty when the report was outside every city or throttled
  - `DriverPresenceService.findWithin(long cityId, double lat, double lon, double radiusKm, int limit) -> List<Nearby>`
  - `DriverPresenceService.cityOf(long driverId) -> Optional<Long>`
  - `DriverPresenceService.remove(long driverId)`: unchanged signature; removes the driver from
    their current city's set

- [x] **Step 1: Write the failing tests** (local fallback; `CityService` mocked):
  - `driverIndexedInTheirCity`
  - `driverOutsideEveryCityNotIndexed`
  - `driverMovingCityLeavesOldSet`: report in A, then in B; `findWithin(A, …)` is empty.
  - `reportsUnderFiveSecondsApartAreDropped`: the second report at +4 s does not move the driver;
    at +6 s it does.
  - `limitCapsResults`
  - Keep the existing stale-expiry and junk-coordinate tests, now city-scoped.
- [x] **Step 2: Run** them. Expected: FAIL.
- [x] **Step 3: Implement.**
  - Redis keys:
    - `travelplatform::geo:city:{cityId}`
    - `travelplatform::driver:{id}:city`, with the presence expiry
  - On a report in a new city, `ZREM` the driver from the old city's set.
  - Searches use `GEOSEARCH … COUNT limit ASC`.
  - Throttling uses a per-driver last-accepted time: an `AtomicLong` in a `ConcurrentHashMap`
    locally, and `SET NX PX 5000` on `travelplatform::driver:{id}:throttle` in Redis.
  - The local fallback becomes a `Map<Long cityId, Map<Long driverId, Fix>>`.
- [x] **Step 4: Update `DispatchService.chooseDriver`.**
  - Call `findWithin(booking.getCityId(), …, 30)`.
  - A booking with a null `cityId` goes straight to `NO_DRIVER`. Only bookings from before V15
    can have one.
  - `DispatchServiceTest` gives its booking city 1 and stubs `CityService` to return it.
- [x] **Step 5: Run** `mvn -o -q test -Dtest='DriverPresenceServiceTest,DispatchServiceTest'`.
  Expected: PASS.
- [x] **Step 6: Commit** `feat(presence): one location set per city; drop over-frequent reports`.

### Task 3: The cheap offer check

**Files:**
- Create: `dto/DriverOfferView.java`
- Modify:
  - `service/DriverService.java`
  - `controller/DriverController.java`
  - `repository/TravelBookingRepository.java`
  - `frontend/src/hooks/useDriverDispatch.js`
  - `frontend/src/utils/api.js`
  - `frontend/src/components/driver/DriverDispatch.jsx`
- Test: `service/DriverServiceOfferTest.java`

**Interfaces:**
- Produces:
  - `DriverOfferView` record: `bookingId UUID`, `secondsLeft long`, `fromPlace`, `toPlace`,
    `fromLat`, `fromLon`, `toLat`, `toLon`, `totalAmount BigDecimal`, `vehicleType String`,
    `serviceMode String`, `offerDistanceKm Double`, `userName`, `travelMembers Integer`,
    `distanceKm Double`
  - `DriverService.currentOffer(Long driverId) -> Optional<DriverOfferView>`
  - `GET /api/driver/{driverId}/offer` (`SAME_DRIVER`): 200 with the view, or 204 when there is no
    offer
  - Frontend: `getDriverOffer(driverId)`, uncached and `_silent`

- [x] **Step 1: Write the failing tests:**
  - `noOfferGivesEmpty`
  - `liveOfferGivesSecondsLeft`: deadline in 9 s gives `secondsLeft` 9
  - `expiredOfferGivesEmpty`
- [x] **Step 2: Run** them. Expected: FAIL.
- [x] **Step 3: Implement.**
  - Add `TravelBookingRepository.findFirstByDriverIdAndDispatchStateAndStatus(Long, DispatchState, BookingStatus) -> Optional<TravelBooking>`.
    It uses the `driver_id` index.
  - Phase B (Task 10) switches the source to Redis behind the same method.
- [x] **Step 4: Frontend.**
  - `useDriverDispatch` polls `getDriverOffer` every **20 s** (`POLL_EVERY_MS = 20000`), plus once
    on each socket push.
  - It stops calling `getDriverBookingsFresh`.
  - `onTrip` comes from a STARTED trip in the existing dashboard data, through a new
    `DRIVER_TRIP_STATE_EVENT` that `DriverDashboard` and `DriverBookings` dispatch after loading.
  - The deadline Map stays.
- [x] **Step 5: Run** `mvn -o -q test -Dtest=DriverServiceOfferTest`, then `npm run build` and
  `npm test`. Expected: PASS.
- [x] **Step 6: Commit** `perf(driver): poll a one-row offer endpoint every 20 s instead of the full trip list`.

### Task 4: Faster queries

**Files:**
- Modify:
  - `V15__cities_and_hot_paths.sql`: append the index
  - `repository/DriverRepository.java`
  - `service/UserService.java`
  - `service/DriverService.java`
- Test:
  - `service/UserServiceDriverAssignmentTest.java`, `service/UserServiceCityTripTest.java`,
    `service/DriverServiceRejectionTest.java`: retarget their mocks
  - `migration/DriverOverlapConstraintTest.java`: needs Docker

**Interfaces:**
- Produces:
  - `DriverRepository.findFirstFreeTripDriver(LocalDate from, LocalDate to, boolean needAc, Collection<Long> excluded) -> Optional<Driver>`:
    a native query with `LIMIT 1`, ordered by `id`. Callers pass `List.of(-1L)` when nobody is
    excluded, because `NOT IN ()` is invalid.
  - `findDispatchableAmong`: same signature; its body becomes `NOT EXISTS`.
- Removes: `findAvailableDrivers` and `findAvailableDriversExcluding`. The `suitsAc` filter in
  Java goes with them, since the AC condition moves into SQL.

- [x] **Step 1: Write the migration index:**
  `CREATE INDEX idx_travel_bookings_driver_active ON travel_bookings (driver_id, from_date, to_date) WHERE status IN ('PENDING','CONFIRMED','STARTED') AND trip_ended_at IS NULL;`
- [x] **Step 2: Write the Docker-gated test** `freeTripDriverQueryHonoursOverlapAcAndExclusions`.
  - Seed 3 cars: busy on the date, non-AC, and free AC.
  - `findFirstFreeTripDriver(…, needAc = true, [-1])` returns the free AC car.
  - Excluding it returns empty.
  - Run it through a JDBC `PreparedStatement` with the same SQL text, held in a
    `DriverRepository.FREE_TRIP_DRIVER_SQL` constant.
- [x] **Step 3: Replace the queries.** The `NOT EXISTS` subquery:
  `NOT EXISTS (SELECT 1 FROM travel_bookings tb WHERE tb.driver_id = d.id AND tb.from_date <= :to AND tb.to_date >= :from AND tb.status IN (…) AND tb.trip_ended_at IS NULL)`.
- [x] **Step 4: Update the callers.**
  - `UserService.saveWithDriver` calls `findFirstFreeTripDriver(from, to, "AC".equalsIgnoreCase(acType), excludedOrSentinel)`.
  - `DriverService.rejectBooking` does the same, with the declined list.
  - Update the three test classes' stubs to the new method. Their assertions stay the same.
- [x] **Step 5: Run** `mvn -o -q test`. Expected: 0 failures, with only Docker tests skipped.
- [x] **Step 6: Commit** `perf(db): NOT EXISTS + LIMIT 1 for driver availability; partial index`.

### Task 5: Route cache

**Files:**
- Modify: `service/RoutingService.java`, `config/CacheConfig.java`
- Test: `service/RouteCacheKeyTest.java`

**Interfaces:**
- Produces: `RoutingService.routeKey(double fromLat, double fromLon, double toLat, double toLon) -> String`,
  a public static that rounds each value to 3 decimals with `Locale.ROOT`.

- [x] **Step 1: Write the failing tests:**
  - `nearbyPointsShareAKey`: points 30 m apart give equal keys
  - `pointsFarApartDiffer`: 300 m apart gives different keys
  - `keyIsLocaleIndependent`: under `Locale.GERMANY` there is no comma
- [x] **Step 2: Run.** Expected: FAIL.
- [x] **Step 3: Implement.**
  - Annotate the coordinate overload of `calculateRoute` with
    `@Cacheable(value = "routes", key = "T(com.travelplatform.service.RoutingService).routeKey(#fromLat,#fromLon,#toLat,#toLon)")`.
  - Add `ttls.put("routes", Duration.ofMinutes(10))` to `CacheConfig`.
  - `RouteInfo` is in `com.travelplatform.`, so the serializer's allow-list already covers it.
- [x] **Step 4: Run** `mvn -o -q test -Dtest='RouteCacheKeyTest,CacheSerializerTypeSafetyTest'`.
  Expected: PASS.
- [x] **Step 5: Commit** `perf(routing): cache coordinate routes for 10 minutes`.

---

## Phase B

### Task 6: ETA and the assignment solver

**Files:**
- Create:
  - `service/dispatch/EtaService.java`
  - `service/dispatch/HaversineEtaService.java`
  - `service/dispatch/AssignmentSolver.java`
- Test:
  - `service/dispatch/AssignmentSolverTest.java`
  - `service/dispatch/HaversineEtaServiceTest.java`

**Interfaces:**
- Produces:
  - `record GeoPoint(double lat, double lon)`
  - `EtaService.etaSeconds(GeoPoint pickup, Map<Long, GeoPoint> drivers) -> Map<Long, Integer>`.
    A driver missing from the result means there is no route.
  - `HaversineEtaService`: `ceil(km / speedKmh × 3600)`, with speed `app.dispatch.city-speed-kmh`
  - `record Edge(UUID ride, long driver, long cost)`
  - `AssignmentSolver.solve(List<Edge> edges) -> Map<UUID, Long>`: each ride gets at most one
    driver, each driver at most one ride, and the total cost of the result is minimal for groups
    of ≤ 300 rides
  - `AssignmentSolver.HUNGARIAN_MAX = 300`

- [x] **Step 1: Write the failing tests.**
  - `hungarianMatchesBruteForce`: 200 random cases, each with 2–6 rides, 2–6 drivers and random
    missing edges. The total cost equals the brute-force minimum, over all assignments that match
    the most rides possible.
  - `greedyUsedAboveLimit`: 301 rides in one group; the result is a valid matching (no shared
    drivers), and every edge in it exists.
  - `componentsSolvedIndependently`
  - `emptyInputGivesEmpty`
  - `noDriverSharedEverAcrossComponents`
- [x] **Step 2: Run.** Expected: FAIL.
- [x] **Step 3: Implement.**
  - **Grouping:** union-find over ride and driver nodes, with path compression and union by rank.
  - **Hungarian:** the O(n³) potentials/shortest-augmenting-path form (Kuhn–Munkres) on a square
    matrix, padded with `INF = Long.MAX_VALUE / 4`. Pairs that come out at INF are dropped.
  - **Greedy:** sort edges by cost, then id, and keep each one whose ride and driver are both
    still free.
- [x] **Step 4: Run** `mvn -o -q test -Dtest='AssignmentSolverTest,HaversineEtaServiceTest'`.
  Expected: PASS.
- [x] **Step 5: Commit** `feat(dispatch): ETA service and minimum-cost assignment solver`.

### Task 7: Driver state store

**Files:**
- Create:
  - `service/dispatch/DriverStateStore.java`
  - `RedisDriverStateStore.java`
  - `LocalDriverStateStore.java`
  - `DispatchStoresConfig.java`
- Modify:
  - `service/DriverService.java`: online/offline, modes, accept, start
  - `service/AdminDriverService.java`: create, vehicle update
  - `service/PaymentService.java`: trip end
  - `service/UserService.java`: cancel
- Test:
  - `service/dispatch/LocalDriverStateStoreTest.java`
  - `service/dispatch/RedisDriverStateStoreTest.java`: Testcontainers Redis, Docker-gated

**Interfaces:**
- Produces:
  - `enum DriverState { IDLE, OFFERED, BUSY }`
  - `record DriverCard(long driverId, DriverState state, VehicleType vehicle, int seats, boolean servesCity, boolean active)`
  - `DriverStateStore.cards(Collection<Long> ids) -> Map<Long, DriverCard>`: one batched read.
    A missing card is loaded from Postgres and cached.
  - `DriverStateStore.putProfile(Driver d)`: keeps the current state
  - `DriverStateStore.setState(long driverId, DriverState s)`
  - `DriverStateStore.compareAndSetState(long driverId, DriverState expected, DriverState next) -> boolean`.
    In Redis this is a small Lua script; it stops one driver receiving two rides.
  - `DriverStateStore.reconcile()`: `@Scheduled` every 60 s. It rebuilds the cards of drivers seen
    in the last 10 min from Postgres. A driver is BUSY if they have a CONFIRMED or STARTED trip
    that hasn't ended, OFFERED if they hold an OFFERED booking, IDLE otherwise.

- [x] **Step 1: Write the failing tests** (local store):
  - `missingCardLoadedFromDatabase`
  - `compareAndSetOnlyFromExpected`
  - `putProfileKeepsState`
  - `reconcileFixesDrift`: the store says IDLE, the database says CONFIRMED, so reconcile gives
    BUSY
- [x] **Step 2: Run.** Expected: FAIL.
- [x] **Step 3: Implement** both stores. `DispatchStoresConfig` chooses Redis when
  `app.cache.type=redis` and a template exists, and local otherwise. This is the same switch
  `LocationRelayConfig` uses.
- [x] **Step 4: Wire the state changes.**

  | Event | New state |
  |---|---|
  | Accept, owner assign | BUSY |
  | Trip end | IDLE |
  | Cancel by traveller | IDLE |
  | Decline or offer timeout | IDLE (Task 9) |

  Online/offline and the mode switches call `putProfile`.
- [x] **Step 5: Run** `mvn -o -q test`. Expected: 0 failures.
- [x] **Step 6: Commit** `feat(dispatch): driver state cache in Redis with a local twin`.

### Task 8: Ride queue and city lock

**Files:**
- Create:
  - `service/dispatch/RideQueue.java`, `RedisRideQueue.java`, `LocalRideQueue.java`
  - `CityLock.java`, `RedisCityLock.java`, `LocalCityLock.java`
- Test:
  - `service/dispatch/LocalRideQueueTest.java`
  - `service/dispatch/CityLockTest.java`: local, plus Redis behind Docker

**Interfaces:**
- Produces:
  - `record RideTicket(UUID bookingId, long cityId, GeoPoint pickup, VehicleType vehicle, int passengers, long searchStartedEpochMs)`
  - `RideQueue.enqueue(RideTicket t)`
  - `RideQueue.waiting(long cityId, int limit) -> List<RideTicket>`: oldest first
  - `RideQueue.remove(long cityId, UUID bookingId)`
  - `RideQueue.markOffered(long cityId, UUID bookingId, long driverId, long deadlineEpochMs)`
  - `RideQueue.expiredOffers(long cityId, long nowEpochMs) -> List<OfferRef>`, where
    `record OfferRef(UUID bookingId, long driverId)`
  - `RideQueue.clearOffer(long cityId, UUID bookingId)`
  - `RideQueue.decline(UUID bookingId, long driverId)` and `RideQueue.declined(UUID bookingId) -> Set<Long>`
  - `RideQueue.currentOffer(long driverId) -> Optional<OfferRef>`
  - `RideQueue.isEmpty(long cityId) -> boolean`: used for the rebuild check
  - `CityLock.tryAcquire(long cityId) -> boolean`, `CityLock.renew(long cityId) -> boolean`,
    `CityLock.release(long cityId)`, `CityLock.liveServers() -> int`

- [x] **Step 1: Write the failing tests:**
  - `waitingReturnsOldestFirst`
  - `markOfferedMovesFromWaitingToOffers`
  - `expiredOffersOnlyPastDeadline`
  - `declinedAccumulates`
  - `currentOfferByDriver`
  - `secondHolderCannotTakeLock`: two `CityLock` instances share one backing store; the second
    `tryAcquire` returns false until the first releases or the lock expires
  - `lockExpiresWithoutRenewal`
- [x] **Step 2: Run.** Expected: FAIL.
- [x] **Step 3: Implement.**
  - Redis keys:
    - `travelplatform::city:{id}:waiting` (sorted set, scored by search start)
    - `travelplatform::ride:{bookingId}` (hash)
    - `travelplatform::ride:{bookingId}:declined` (set)
    - `travelplatform::city:{id}:offers` (sorted set, scored by deadline; member
      `bookingId:driverId`)
    - `travelplatform::driver:{id}:offer` (string with expiry)
    - `travelplatform::lock:match:city:{id}` (`SET NX PX 5000`, value = instance id; renew and
      release go through a compare-the-value Lua script)
    - `travelplatform::match:server:{instanceId}` (heartbeat, 10 s expiry)
  - Local twins use a `ConcurrentSkipListMap` per city for the sorted sets, and a
    `ConcurrentHashMap` for the rest.
- [x] **Step 4: Run** the tests. Expected: PASS.
- [x] **Step 5: Commit** `feat(dispatch): ride queue, offer deadlines and per-city locks`.

### Task 9: Matching worker

**Files:**
- Create: `service/dispatch/MatchingWorker.java`
- Modify:
  - `service/DispatchService.java`
  - `config/DispatchProperties.java`
  - `application.yml`
  - `service/UserService.java`: enqueue after commit instead of `dispatchAfterCommit`
- Test: `service/dispatch/MatchingWorkerTest.java`, using local twins, a fixed clock and
  `HaversineEtaService`. `DispatchServiceTest` is reduced to accept/decline/cancel.

**Interfaces:**
- Consumes everything from Tasks 2 and 6–8.
- Produces:
  - `MatchingWorker.runRound(long cityId)`: package-visible for tests
  - `MatchingWorker.tick()`: `@Scheduled(fixedDelayString = "${app.dispatch.round-interval-ms:2000}")`
  - `DispatchService.enqueueAfterCommit(TravelBooking b)`
  - `DispatchService.release(TravelBooking b, Long driverId)`: same signature; now declines in the
    queue and re-enqueues
  - `DispatchService.offerExpired(TravelBooking b)`: same signature
  - `DispatchService.markMatched(TravelBooking b)`: same signature; also sets BUSY and
    `clearOffer`
- Removes: `DispatchService.dispatch`, `step`, `chooseDriver`, `sweep`, and
  `TravelBookingRepository.findDispatchWork`. The partial index from V13 stays; recovery uses it.
- Settings added to `DispatchProperties`:
  - `radiusAfterSeconds List<Integer> = [0, 20, 40]`
  - `roundIntervalMs = 2000`
  - `candidatesPerRide = 10`
  - `searchCount = 30`
  - `maxEtaSeconds = 900`
  - `waitWeight = 2`
  - `maxRidesPerRound = 2000`
  - `citySpeedKmh = 20`
  - `offerSeconds` default 15

**`runRound` algorithm.** Steps 1–3 run in this order; the rest is up to the implementer.

1. Expire offers. For each `expiredOffers` entry, in its own transaction:
   - the booking goes from OFFERED to SEARCHING, with driver null (optimistic lock)
   - record the rejection, `RideQueue.decline`, set the driver IDLE, re-enqueue, push WITHDRAWN
2. Take `waiting(cityId, maxRidesPerRound)`. Any ride older than `searchTimeoutSeconds` becomes
   `NO_DRIVER`, `notifyNoDriverNearby`, and is removed.
3. For each ride:
   - radius = the largest `radiiKm[i]` with `radiusAfterSeconds[i] ≤ waited`
   - `findWithin(cityId, pickup, radius, searchCount)`
   - drop declined drivers
   - `cards(...)`: keep IDLE, active, `servesCity`, matching vehicle, `seats ≥ passengers`
   - take the first `candidatesPerRide`
   - `etaSeconds(...)`, dropping ETAs over `maxEtaSeconds`
   - edge cost = `eta − waitWeight × waitedSeconds`
4. `solve(edges)`.
5. For each pair:
   - `compareAndSetState(driver, IDLE, OFFERED)`; if that fails, skip (the driver was taken
     meanwhile)
   - in a transaction: booking → driver, OFFERED, deadline = now + `offerSeconds`
   - if `no_driver_overlap` or the optimistic lock refuses it, set the driver back to IDLE and
     leave the ride waiting
   - otherwise `markOffered`, `notifyRideOffered`, and push OFFER after commit
6. Record the Micrometer metrics:
   - `namma.match.round.duration` (timer)
   - `namma.match.rides.waiting` and `namma.match.drivers.candidate` (gauges)
   - `namma.match.offers` (counter)

**`tick`:**
- heartbeat
- for each `activeIds()` city: renew the lock if held; otherwise try to acquire it while holding
  fewer than `ceil(active / liveServers) + 1` cities
- on a fresh acquire where `RideQueue.isEmpty(cityId)`, rebuild that city's waiting set and offers
  from Postgres, using the V13 partial index
- run `runRound` for each held city in a fixed thread pool of 4; one failing city never stops the
  others

**Tests:**
- [x] **Step 1: Write the failing tests** in `MatchingWorkerTest`:
  - `rideOfferedToNearestFreeDriver`
  - `contestedDriverGoesToLongerWaitingRide`: rides A (waited 30 s) and B (waited 0 s) are both
    1 km from driver X, and driver Y is 4 km from both; A gets X
  - `globalMinimumBeatsGreedy`: R1 is near D1 and D2, R2 is near D1 only; R1 gets D2 and R2
    gets D1
  - `radiusWidensWithWaiting`: a driver 2.5 km away; at waited 10 s, no offer; at 25 s, offered
  - `declinedDriverNeverReOffered`
  - `expiredOfferReturnsRideAndFreesDriver`: deadline + 1 ms gives the ride waiting again and the
    driver IDLE
  - `oneDriverNeverGetsTwoRidesInARound`
  - `wrongVehicleOrTooFewSeatsNeverOffered`
  - `searchTimeoutHandsToOwner`
  - `rebuildsQueuesFromDatabaseAfterRedisLoss`: empty stores, plus a SEARCHING and an OFFERED
    booking in the mocked repository; after `tick`, both are back in the queue
  - `onlyLockHolderRunsTheCity`: two workers share local stores; only one sends offers
- [x] **Step 2: Run.** Expected: FAIL.
- [x] **Step 3: Implement `MatchingWorker`, and slim `DispatchService` down** to accept, decline,
  cancel, owner override and `enqueueAfterCommit`.
- [x] **Step 4: Switch the offer source.** `DriverService.currentOffer` (Task 3) now reads
  `RideQueue.currentOffer`, then loads that one booking.
- [x] **Step 5: Update the frontend.** `DriverDispatch` shows "15s to accept" from `secondsLeft`;
  no logic change.
- [x] **Step 6: Run** the full suite, `mvn -o test`, then `npm run build`, `npm test` and lint.
  Expected: 0 failures, with only Docker tests skipped; build `✓ built`; lint has no errors.
- [x] **Step 7: Commit** `feat(dispatch): batch matching per city every 2 s (Hungarian/greedy over road ETA)`.

### Task 10: Docs and settings check

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-23-scale-batch-matching-design.md`: mark phases A and B done,
    and note anything that differed from the plan
  - `ARCHITECTURE.md`: a dispatch section of a few paragraphs
- Test: `config/ApplicationConfigDefaultsTest.java`. Add assertions that `app.dispatch.offer-seconds`
  defaults to 15 and `round-interval-ms` to 2000.

- [x] **Step 1: Add the assertions and run** `mvn -o -q test -Dtest=ApplicationConfigDefaultsTest`.
  Expected: PASS.
- [x] **Step 2: Update the docs.**
- [x] **Step 3: Commit** `docs: dispatch architecture after batch matching`.
