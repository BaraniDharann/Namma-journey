# Owner Fleet (sub-project 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every approved owner runs their own Trip fleet (vehicles, drivers, per-category prices, assignment mode) on a dashboard scoped to their own data; City rides become platform-only and independent-driver-only.

**Architecture:** V27 adds owner columns, vehicles, rates and checks and moves existing data to NJO-0001. V28 adds Postgres RLS enforced through `SET ROLE nj_owner_scope` on connections used by owner requests. Owner services filter by `OwnerScope.ownerId()` explicitly, and RLS is the backstop. Platform screens check `OwnerScope.requirePlatform()`.

**Tech Stack:** Spring Boot 3.4 / Java 17, JPA (ddl validate), Flyway, PostgreSQL 15 Testcontainers, Redis cache, React 18 + Vite + vitest.

**Spec:** `docs/superpowers/specs/2026-11-03-owner-fleet-design.md`

## Global Constraints
- Local only, no deploy, no push.
- Categories exactly `HATCHBACK`, `SEDAN`, `SUV`, `TEMPO_TRAVELLER`. Assignment modes `AUTO`, `MANUAL`.
- Seats → category in the migration: ≤5 `SEDAN`, 6–8 `SUV`, ≥9 `TEMPO_TRAVELLER`. Missing plate → `TEMP-<driver id>`.
- NJO-0001 = `app.owners.platform-owner-code` and gets `assignment_mode = AUTO`. The default for everyone else is `MANUAL`.
- Status codes:
  - another owner's record → 404
  - platform screen for a non-platform owner → 403
  - validation → 400
  - busy driver or car, or approving without checks → 409
- Owner-fleet drivers can never take City rides. Enforced by the `drivers_fleet_no_city` constraint and the dispatch filters.
- Notifications are not under RLS. The owner feed is `recipient_id = <owner id>`, role `ROLE_OWNER`.
- Use non-quiet `mvn` in task-done (empty `-q` output trips pipefail).

## Review Focus
1. A pooled connection must never keep `nj_owner_scope` or `nj.owner_id` after an owner request; the next customer request must see everything.
2. Async or after-commit work started by an owner request runs on another thread with no scope, so a write there is unscoped (acceptable). Check that nothing relies on the scope there.
3. Caches keyed without the owner id would leak one owner's list to another; check every `@Cacheable` an owner reaches.
4. The migration must handle zero owners (fresh DB), drivers without plates, and duplicate plates across drivers (the vehicles unique index).
5. MANUAL owners: a Trip with no driver must not break the customer's booking view, cancellation or advance-hold sweeper (driver_id null paths).

---

### Task 1: V27 schema, data move, entities

**Files:**
- Create: `src/main/resources/db/migration/V27__owner_fleet.sql`
- Create: entities `entity/Vehicle.java`, `entity/CarCategory.java`, `entity/OwnerTripRate.java` (`@IdClass Key`), `entity/DriverVerificationCheck.java` (`@IdClass Key`; enums `Type {LICENCE, RC}`, `Result {VERIFIED, FAILED}`)
- Create: repos `VehicleRepository`, `OwnerTripRateRepository`, `DriverVerificationCheckRepository`
- Modify:
  - `Owner` (`assignmentMode` enum `AssignmentMode {AUTO, MANUAL}`, default MANUAL)
  - `Driver` (`ownerId`, `defaultVehicleId`; `servesCity` default **false**)
  - `TravelBooking` (`ownerId`, `carCategory`, `vehicleId`)
  - `TravelPackage` (`ownerId`)
  - `Payment`, `Review`, `TripDriverPhoto`, `PackageBooking`: `ownerId` with `insertable=false, updatable=false`
- Test: `src/test/java/com/travelplatform/migration/OwnerFleetMigrationTest.java`

**Interfaces:**
- Produces:
  - `enum CarCategory { HATCHBACK, SEDAN, SUV, TEMPO_TRAVELLER }`
  - `Vehicle {Long id; Long ownerId; CarCategory category; String registrationNumber; String model; int seats; boolean ac; String rcPhoto; boolean active; LocalDateTime createdAt}`
  - `OwnerTripRate {Long ownerId; CarCategory category; BigDecimal perKm; BigDecimal perHour; LocalDateTime updatedAt}`
  - `DriverVerificationCheck {Long driverId; Type type; Result result; String note; Long checkedByOwnerId; LocalDateTime checkedAt}`
  - `Owner.AssignmentMode`
  - SQL-side: `travel_bookings.owner_id/car_category/vehicle_id`, the `no_vehicle_overlap` constraint, the `drivers_fleet_no_city` and `drivers_owner_fleet_has_owner` checks, and owner-fill triggers on payments/reviews/trip_driver_photos/package_bookings.

- [ ] **Step 1: Write failing migration tests.** Same harness as `MultiOwnerMigrationTest`: migrate to 26, insert fixtures, then migrate.
  - `existingFleetMovesToNJO0001`:
    - Fixtures: an owner (NJO-0001), 2 OWNER_FLEET drivers (one with plate `KA01AB1234` and 7 seats, one with no plate and 4 seats) both serves_city=true, 1 INDEPENDENT driver, 1 TRIP booking for driver 1, 1 CITY booking, 1 package, 1 notification with recipient 'owner', and a pricing_config row (12.50, 200.00).
    - Assertions:
      - fleet drivers have owner_id = NJO-0001 id and serves_city=false; the independent driver keeps owner_id null and serves_city=true;
      - vehicles: `KA01AB1234` SUV, `TEMP-<id2>` SEDAN, each the driver's default_vehicle_id;
      - the TRIP has owner_id, car_category SUV and vehicle_id; the CITY booking has owner_id null;
      - the package has owner_id; the notification recipient_id = owner id text;
      - NJO-0001 has 4 owner_trip_rates rows at 12.50/200.00 and assignment_mode AUTO.
  - `freshDatabaseMigrates`: no rows, no errors.
  - `ownerFleetCannotServeCity`: inserting an OWNER_FLEET driver with serves_city=true fails with SQLState 23514.
  - `vehicleCannotDoubleBook`: two active trips with the same vehicle_id on overlapping dates fail with 23P01.
  - `paymentInheritsBookingOwner`: inserting a payment for an owned booking gets that owner_id.
- [ ] **Step 2: Run.** `mvn test -Dtest=OwnerFleetMigrationTest`. Expected: FAIL (column/table does not exist).
- [ ] **Step 3: Write V27.** Order of statements:
  1. Tables and columns.
  2. The data move:
     - vehicles from OWNER_FLEET drivers;
     - when two drivers share a plate (normalised), the second gets `TEMP-<id>`;
     - rates from the latest pricing_config, else 10.00/150.00;
     - set owner_id where the owner exists (`WHERE EXISTS` on the NJO-0001 owner, so a fresh DB is a no-op).
  3. `serves_city=false` for the fleet.
  4. Constraints:
     - `drivers_owner_fleet_has_owner`: `CHECK ((fleet = 'OWNER_FLEET') = (owner_id IS NOT NULL))`. A database with fleet drivers but no owner row fails here, loudly, rather than guessing an owner.
     - `no_vehicle_overlap`: the same shape as V13 `no_driver_overlap`, on `vehicle_id`.
  5. Triggers (plpgsql `BEFORE INSERT` setting `NEW.owner_id` from the parent).
- [ ] **Step 4: Add entities and repos, and modify existing entities.**
- [ ] **Step 5: Run.** `mvn test -Dtest=OwnerFleetMigrationTest,RevenueQueriesTest,MultiOwnerMigrationTest`. Expected: PASS. `RevenueQueriesTest` proves entity validation against the schema.
- [ ] **Step 6: Commit.** `feat(fleet): V27 vehicles, per-category rates, owner columns; existing fleet moves to NJO-0001`

### Task 2: Row-level security and the owner scope

**Files:**
- Create:
  - `src/main/resources/db/migration/V28__owner_row_level_security.sql`
  - `config/OwnerScope.java` (request-thread holder plus bean)
  - `config/OwnerScopedDataSource.java`
  - `config/OwnerScopeDataSourcePostProcessor.java`
  - `config/OwnerScopeFilter.java`
- Modify: `SecurityConfig` (register the filter after `JwtAuthenticationFilter`)
- Test: `src/test/java/com/travelplatform/config/OwnerRowLevelSecurityTest.java` (`@DataJpaTest` + Testcontainers, imports `OwnerScopedDataSource` via the post-processor), `config/OwnerScopeFilterTest.java` (unit)

**Interfaces:**
- Produces:
  - `OwnerScope` (`@Component`):
    - `static void bind(long ownerId, boolean platform)`, `static void clear()`, `static Optional<Bound> current()`
    - `record Bound(long ownerId, boolean platform)`
    - instance methods `Long ownerId()` (throws `AccessDeniedException` when unbound), `boolean isPlatform()`, `void requirePlatform()` (throws `AccessDeniedException`)
  - `OwnerScopeFilter`: binds for ROLE_OWNER after JWT auth. `platform` = `ownerRepository.findById(id).ownerCode equals platformOwnerCode` (cached in a `ConcurrentHashMap<Long,Boolean>`).
- V28:
  - role `nj_owner_scope` (DO block `IF NOT EXISTS`), grants, default privileges, `GRANT nj_owner_scope TO CURRENT_USER`;
  - function `nj_owner_visible(bigint)`;
  - `ENABLE`/`FORCE` RLS plus policies `app_unscoped` and `owner_scoped` on: drivers, vehicles, owner_trip_rates, travel_bookings, payments, reviews, trip_driver_photos, travel_packages, package_bookings;
  - driver_verification_checks: `owner_scoped` uses `EXISTS (SELECT 1 FROM drivers d WHERE d.id = driver_id)`, since drivers is already filtered.

- [ ] **Step 1: Write failing tests** (`OwnerRowLevelSecurityTest`):
  - Fixtures: owners A and B (A has the platform code), each with a driver and a TRIP; one CITY booking with owner null.
  - `ownerSeesOnlyOwnRows`: bind B → `bookings.findAll()` gives only B's trip; `findById(A trip)` is empty; `revenueTotals` counts only B.
  - `platformOwnerAlsoSeesUnownedRows`: bind A platform → A's trip plus the CITY row, not B's.
  - `scopeWithoutSettingSeesNothing`: a raw connection runs `SET ROLE nj_owner_scope` with no setting → `count(*)` from travel_bookings = 0.
  - `connectionIsResetAfterScopedUse`: bind B, query, clear → next query sees all 3 bookings, and `SELECT current_user` is not `nj_owner_scope`.
  - `ownerCannotInsertForAnotherOwner`: bind B, save a vehicle with ownerId A → exception.
  - `OwnerScopeFilterTest`: ROLE_OWNER binds and clears after the chain (also when the chain throws); ROLE_USER does not bind.
- [ ] **Step 2: Run.** `mvn test -Dtest=OwnerRowLevelSecurityTest,OwnerScopeFilterTest`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement.**
  - The DataSource wraps `getConnection()`. When `OwnerScope.current()` is present: `SET ROLE nj_owner_scope` + `SELECT set_config('nj.owner_id', ?, false), set_config('nj.platform', ?, false)`.
  - It returns a JDK `Proxy` connection whose `close()` runs `RESET ROLE; RESET nj.owner_id; RESET nj.platform` before delegating. On `SQLException`, evict via `HikariDataSource.evictConnection` (the target unwrapped) and close.
  - The post-processor wraps any `DataSource` bean.
- [ ] **Step 4: Run** the same tests, plus `SecurityEndpointsTest`. Expected: PASS.
- [ ] **Step 5: Commit.** `feat(fleet): Postgres row-level security for owner data, scoped per owner request`

### Task 3: City rides are independent-only

**Files:**
- Modify:
  - `service/dispatch/DriverCard.java` (add `Driver.Fleet fleet`), `DriverCardLoader`, `RedisDriverStateStore`, `LocalDriverStateStore`
  - `MatchingWorker.eligible()`, `DriverPresenceService.report`, `DriverRepository.findDispatchableAmong`
  - `DriverVehicles.setModes`/`apply` (owner fleet: servesCity=true → `IllegalArgumentException("City rides are for independent drivers only; travels owners' drivers take Trips.")`)
- Test: extend `MatchingWorkerTest` (`ownerFleetDriverIsNeverACityCandidate`), `DriverApprovalGateTest` (findDispatchableAmong excludes owner fleet), a new `DriverVehiclesTest` case (`ownerFleetCannotTurnOnCity`), and a `DriverPresenceService` test (`ownerFleetNotAddedToCityGeo`).

**Interfaces:**
- Produces: `DriverCard(Long driverId, State state, VehicleType vehicle, int seats, boolean servesCity, boolean active, Driver.Fleet fleet)`. Older Redis cards without fleet deserialize as `OWNER_FLEET`, which is safe and excluded.

- [ ] Step 1: write the failing tests.
- [ ] Step 2: run them. Expected: FAIL.
- [ ] Step 3: implement.
- [ ] Step 4: run `mvn test -Dtest='MatchingWorkerTest,DriverApprovalGateTest,DriverVehiclesTest,DriverPresence*Test,DispatchServiceTest'`. Expected: PASS.
- [ ] Step 5: commit `fix(dispatch): City rides go to independent drivers only`.

### Task 4: Per-owner, per-category Trip rates and booking price

**Files:**
- Create: `service/TripRateService.java`, `dto/TripRateRequest.java`, `dto/TripRateView.java`
- Modify:
  - `UserService.buildBooking`/`updateBooking`
  - booking request DTO (add `carCategory`, default `SEDAN` when null for old clients)
  - `PublicController` (`GET /api/public/trip-rates`; remove `/public/pricing`)
  - `OwnerController` (`GET /api/owner/trip-rates`, `PUT /api/owner/trip-rates/{category}`, `PUT /api/owner/assignment-mode`; remove `/pricing/set`, `/pricing/set-hourly`, `/pricing/current`)
  - `OwnerService` (drop the PricingConfig methods)
  - `SecurityConfig` (drop the `/api/owner/pricing/current` rule)
  - `PricingHistoryTest` → replaced by `TripRateServiceTest`
- Test: `TripRateServiceTest`, extend `UserServiceCityTripTest`

**Interfaces:**
- Produces:
  - `TripRateService.ratesFor(Long ownerId): List<TripRateView>` (`@Cacheable("tripRates", key="#ownerId")`)
  - `TripRateService.rate(Long ownerId, CarCategory c): OwnerTripRate`, which throws `IllegalArgumentException("This car type is not available")`
  - `TripRateService.set(Long ownerId, CarCategory c, BigDecimal perKm, BigDecimal perHour)`, `@CacheEvict("tripRates", key="#ownerId")`; uses `Money.positivePrice`
  - `TripRateService.platformOwnerId(): Long` (looks up by platform code)
  - `record TripRateView(CarCategory category, BigDecimal perKm, BigDecimal perHour)`

- [ ] Step 1: write the failing tests:
  - `priceUsesOwnersCategoryRate`: SUV at 18/km × 100 km → 1800.00;
  - `hourlyUsesCategoryPerHour`;
  - `unpricedCategoryRefused`;
  - `bookingCarriesOwnerAndCategory`: a TRIP booking has ownerId = the platform owner and carCategory;
  - `setRejectsNonPositive`.
- [ ] Step 2: run. Expected: FAIL.
- [ ] Step 3: implement. The `tripRates` cache TTL is the same as the old `pricing` TTL in `CacheConfig`.
- [ ] Step 4: run `mvn test -Dtest='TripRateServiceTest,UserService*Test,SecurityEndpointsTest'`. Expected: PASS.
- [ ] Step 5: commit `feat(fleet): Trip prices per owner and car category`.

### Task 5: Assignment (AUTO, MANUAL, assign-options, rejection)

**Files:**
- Modify:
  - `DriverRepository`: replace `FREE_TRIP_DRIVER_SQL`/`findFirstFreeTripDriver` with `FREE_TRIP_PAIR_SQL`/`findFirstFreeTripPair`, returning an interface projection `TripPair {Long getDriverId(); Long getVehicleId();}`
  - `UserService` create path; `DriverService.rejectBooking`; `OwnerService.assignDriver`
  - `NotificationService`: `notifyTripNeedsDriver(TravelBooking)` sends an in-app note to the owner feed plus email via the outbox to the owner's email
  - `dto/AssignDriverRequest` (add `Long vehicleId`)
- Create: `dto/AssignOptions.java`
- Test: `TripAssignmentTest` (Testcontainers `@DataJpaTest` for the pair SQL), plus updates to `UserServiceDriverAssignmentTest`, `DriverServiceRejectionTest`, `OwnerServiceAssignNotifyTest`

**Interfaces:**
- Consumes: `Vehicle`, `Owner.AssignmentMode` (Task 1); `OwnerScope` (Task 2).
- Produces:
  - `findFirstFreeTripPair(Long ownerId, String category, int seats, boolean needAc, LocalDate from, LocalDate to, Collection<Long> excluded): Optional<TripPair>`
  - `OwnerService.assignDriver(UUID bookingId, Long driverId, Long vehicleId)` → `TravelBookingResponse`
  - `OwnerService.assignOptions(UUID bookingId)` → `AssignOptions(List<DriverOption> drivers, List<VehicleOption> vehicles)`
    - `DriverOption(Long id, String name, Long defaultVehicleId)`
    - `VehicleOption(Long id, String registrationNumber, String model, int seats, boolean ac)`

- [ ] Step 1: write the failing tests:
  - `autoPrefersUsualCar`;
  - `autoRespectsCategorySeatsAc`;
  - `autoSkipsBusyVehicle`;
  - `autoNeverPicksAnotherOwnersDriver`;
  - `manualLeavesTripUnassignedAndNotifies`;
  - `rejectionReoffersForAuto`;
  - `rejectionHandsBackForManual`: driverId and vehicleId become null, PENDING, owner notified;
  - `assignRejectsWrongCategoryVehicle` (400);
  - `assignRejectsOtherOwnersDriver` (404, `ResourceNotFoundException`);
  - `assignOptionsListsOnlyFreeOwnSuitable`;
  - `assignWithoutVehicleUsesUsualCar`.
- [ ] Step 2: run. Expected: FAIL.
- [ ] Step 3: implement.
  - A busy vehicle is an overlapping active booking on `vehicle_id` (same predicate as busy driver).
  - ORDER BY `(v.id = d.default_vehicle_id) DESC, d.id, v.id`.
  - A lost race catches the `no_vehicle_overlap`/`no_driver_overlap` violation and retries with that driver excluded, as today.
  - A conflict in assignDriver → `ConflictException` (409).
- [ ] Step 4: run `mvn test -Dtest='TripAssignmentTest,UserService*Test,DriverServiceRejectionTest,OwnerService*Test'`. Expected: PASS.
- [ ] Step 5: commit `feat(fleet): automatic or manual Trip assignment per owner, with car per trip`.

### Task 6: Vehicles and owner drivers (pending, checklist, approval)

**Files:**
- Create:
  - `service/VehicleService.java`
  - `controller/OwnerFleetController.java` (`/api/owner/vehicles` GET/POST(multipart with rcPhoto)/PUT `{id}`/POST `{id}/deactivate`)
  - `service/OwnerDriverReviewService.java` (checks, approve, reject)
- Modify:
  - `AdminDriverService.createDriver`:
    - takes `Long defaultVehicleId`;
    - sets ownerId from OwnerScope, UNDER_REVIEW, servesCity=false;
    - does not email.
  - The approve/reject routes in `OwnerController` dispatch by fleet: an independent driver → `driverVerificationService` + `requirePlatform()`; an owner-fleet driver → `OwnerDriverReviewService`.
  - `PUT /api/owner/drivers/{id}/checks/{type}`.
  - `PUT /api/owner/drivers/{id}/default-vehicle`.
  - `getAllDrivers`/`getDriverById` scoped (`fleet=OWNER_FLEET AND ownerId=me`; cache key includes owner).
  - `deleteDriver` and `telegram-link`: ownership check.
  - `FileStorageService`/`StorageConfig`: RC photos stored under `uploads/drivers/` as today.
  - `DriverDetailsResponse`: `defaultVehicle`, `checks`.
- Test: `VehicleServiceTest`, `OwnerDriverReviewServiceTest`; update the `AdminDriverService` tests.

**Interfaces:**
- Produces:
  - `VehicleService.list(): List<Vehicle>`, `create(VehicleForm, MultipartFile rc)`, `update(Long id, VehicleForm)`, `deactivate(Long id)`.
    - `record VehicleForm(String category, String registrationNumber, String model, Integer seats, Boolean ac)`.
    - Plate normalised upper-case without spaces, unique → 400 "Registration number already added".
    - `deactivate` refuses (409) while the vehicle is on an active trip.
  - `OwnerDriverReviewService.recordCheck(Long driverId, Type, Result, String note)`, `approve(Long driverId)` (409 unless both are VERIFIED; on success a new password is set and `emailService.sendDriverCredentials` is called), `reject(Long driverId, String reason)`.

- [ ] Step 1: write the failing tests:
  - `createVehicleNormalisesPlateAndRejectsDuplicate`;
  - `otherOwnersVehicleIs404`;
  - `deactivateRefusedWhileOnTrip`;
  - `ownerDriverStartsPendingWithoutEmail`;
  - `approveNeedsBothChecks`;
  - `approveEmailsCredentials`;
  - `failedCheckNeedsNote`;
  - `independentApprovalIsPlatformOnly` (403 for a non-platform owner);
  - `defaultVehicleMustBeOwnActive`.
- [ ] Step 2: run. Expected: FAIL.
- [ ] Step 3: implement.
- [ ] Step 4: run `mvn test -Dtest='VehicleServiceTest,OwnerDriverReviewServiceTest,AdminDriver*Test,DriverVerificationServiceTest'`. Expected: PASS.
- [ ] Step 5: commit `feat(fleet): owners add vehicles and approve their own drivers`.

### Task 7: Scoped owner screens and platform screens

**Files:**
- Modify:
  - `OwnerService`: bookings (TRIP and own), revenue (own; cache keys `#root.target.scopeKey() + ...`), trip revenue, driver photo (own)
  - `TravelBookingRepository` revenue queries: add an `ownerId` param and an optional `serviceMode`
  - `PaymentService.getPendingPayments(Long ownerId)` + `verifyPayment` ownership
  - `ReviewService` (own reviews)
  - `TravelPackageService`/`PackageBookingService` (ownerId from scope; ownership checks; public listing unchanged)
  - `TravelPackageController` (drop the `ownerId` param)
  - `NotificationService`: every owner-bucket write takes the booking's owner id. Platform-only notices (driver applications, dues, auto-approval, SOS, no-driver-nearby) go to the platform owner's id.
  - `NotificationController.assertOwnRecipient`: an owner gets only `recipientId == own id`.
  - `BookingAccessGuard`: as in the spec.
  - Platform gate (`requirePlatform`) on: driver-applications, city-fares, cities, payouts, dues, platform endpoints.
  - New `GET /api/owner/platform/city-rides`, `/platform/payments/pending`, `/platform/revenue/monthly-series`, `/platform/drivers` (independent).
  - `SecurityConfig` `/actuator/**`: OWNER + platform.
  - `GET /api/owner/me` (`OwnerProfileView`: ownerCode, travelsName, cities, masked bank, upi, assignmentMode, platform).
  - `PaymentService.generateUpiDeepLink` uses the booking owner's upiId, falling back to the config.
- Test: `OwnerScreensScopeTest` (Testcontainers `@SpringBootTest`-light or `@DataJpaTest` + services), plus updates to `NotificationRecipientRoleTest`, `BookingAccessGuardLocationTest`, `WebSocketAuthInterceptorTest`, `RevenueQueriesTest`, `PaymentServiceSecurityTest`, `PackageBookingSecurityTest`, `ReviewServiceModeTest`, `SecurityEndpointsTest`.

**Interfaces:**
- Consumes: `OwnerScope.ownerId()/isPlatform()/requirePlatform()`.
- Produces: `TravelBookingRepository.revenueTotals(LocalDateTime start, LocalDateTime end, Long ownerId, ServiceMode mode)`, and the same extra params on `monthlyRevenue` and `revenueTrips`.

- [ ] Step 1: write the failing tests:
  - `bookingsListOnlyOwnTrips`: NJO-0001 doesn't see CITY;
  - `revenueOnlyOwn`;
  - `revenueCacheIsPerOwner`;
  - `pendingPaymentsOnlyOwn`;
  - `verifyOtherOwnersPayment404`;
  - `reviewsOnlyOwn`;
  - `packagesOwnedByCaller` (body ownerId ignored);
  - `otherOwnersPackage404`;
  - `ownerReadsOnlyOwnNotificationFeed`;
  - `guardAllowsOwnTripOnly`;
  - `platformEndpoints403ForOtherOwner`;
  - `actuatorPlatformOnly`;
  - `meReturnsProfileMasked`;
  - `upiLinkUsesBookingOwnersUpi`.
- [ ] Step 2: run. Expected: FAIL.
- [ ] Step 3: implement.
- [ ] Step 4: run the full suite with `mvn test`. Expected: all pass. Update any older single-owner test whose expectation the spec changed, and note each in the ledger.
- [ ] Step 5: commit `feat(fleet): every owner screen shows only that owner's data; City-ride screens are platform-only`.

### Task 8: Frontend

**Files:**
- Create:
  - `pages/owner/OwnerVehicles.jsx`
  - `pages/owner/OwnerPricing.jsx` (category rates plus assignment mode; embeds `CityFaresPanel` only when platform)
  - `components/owner/AssignDriverModal.jsx`
  - `utils/carCategories.js` (+ test)
- Modify:
  - `utils/api.js`: new functions; remove the pricing ones
  - `App.jsx` routes
  - `DashboardLayout.jsx`: owner nav from `getOwnerMe().platform`; notification recipient = owner id from the token
  - `OwnerDrivers.jsx` + `VehicleFields.jsx`: no City toggle for owner drivers; usual-car select; checklist and approve UI; independent list on the platform only
  - `OwnerBookings.jsx`: "needs a driver" badge + assign modal
  - `OwnerRevenue.jsx`: remove the global price
  - `OwnerProfile.jsx`: from `/me`
  - `pages/user/NewBooking.jsx` + `components/journey/QuickBooking.jsx`: category select from `/public/trip-rates`, estimate from the selected rate
- Test: `carCategories.test.js` (label/seat list, price estimate per km/hour); `npm run build`.

- [ ] Step 1: write the failing vitest `carCategories.test.js`:
  - `estimateTripPrice({perKm:18, perHour:250}, {km:100})` → 1800;
  - hourly → 250×hours;
  - `categoryLabel('TEMPO_TRAVELLER')` → 'Tempo Traveller'.
- [ ] Step 2: `npm test -- carCategories`. Expected: FAIL.
- [ ] Step 3: implement the util and the pages.
- [ ] Step 4: `npm test` and `npm run build`. Expected: PASS, build OK.
- [ ] Step 5: commit `feat(fleet): owner vehicles, pricing, assignment and car category on booking`.

### Task 9: Open the portal, docs, local run

**Files:**
- Modify:
  - `OwnerAuthService`: remove `mayUsePortal`/`PORTAL_NOT_OPEN`
  - `application.yml`: remove `app.owners.portal-open-to-all`
  - `OwnerChangePassword.jsx`, `OwnerLoginTest`
  - `docs/API_REFERENCE.md` (new and removed endpoints)
  - `.env.example`
- Test: `OwnerLoginTest.approvedOwnerGetsDashboardToken`.

- [ ] Step 1: change the test so an approved non-platform owner gets a token after the password change. Run → FAIL.
- [ ] Step 2: remove the gate; update the docs.
- [ ] Step 3: `mvn test`, `npm test`. Expected: all pass.
- [ ] Step 4: back up the local DB (`pg_dump -Fc namma_journey > scratchpad/pre-v27.dump`), start the app on 8095, and confirm in the log that V27/V28 applied.
- [ ] Step 5: local run via the API:
  - NJO-0002: add a vehicle and a driver, record checks, approve the driver;
  - set an SUV rate and MANUAL mode;
  - log in as NJO-0001: bookings show only NJO-0001 Trips; NJO-0002 sees none of them (by-id lookup → 404);
  - NJO-0002 → `/api/owner/cities` gives 403.
- [ ] Step 6: commit `feat(fleet): open the owner portal to every approved owner`.
