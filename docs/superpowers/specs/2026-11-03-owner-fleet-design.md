# Sub-project 2: each owner's own fleet, prices and dashboard

Status: approved in brainstorming on 2026-11-03. Builds on sub-project 1 (owner onboarding, V26).

## Goal

Every approved travels owner gets the whole owner dashboard, scoped to their own data. Each owner:

- adds cars (vehicles) and drivers;
- approves their own drivers after a free licence/RC check;
- sets per-km and per-hour Trip prices per car category;
- chooses automatic or manual driver assignment.

**City rides are not part of any owner's fleet.** They belong to the platform: independent drivers, city fares, driver applications, payouts and dues. NJO-0001 (the platform owner, `app.owners.platform-owner-code`) manages those on separate **platform screens**. NJO-0001's own Trip screens show only NJO-0001's own Trips.

Until sub-project 3 (compare and book) exists, every new Trip goes to NJO-0001.

## Decisions (from brainstorming)

| Topic | Decision |
|---|---|
| Platform pieces (cities, city fares, independent drivers, payouts/dues, server health) | Stay with NJO-0001 as platform screens |
| Packages | Every owner makes their own |
| Trip prices | Per-km and per-hour, per owner, per car category |
| Car categories | `HATCHBACK`, `SEDAN`, `SUV`, `TEMPO_TRAVELLER` |
| Driver ↔ car | Each driver has a usual car (`default_vehicle_id`), which can be swapped per trip |
| Assignment | Each owner picks `AUTO` or `MANUAL`. NJO-0001 defaults to AUTO (today's behaviour); new owners default to MANUAL |
| Scoping | Postgres row-level security, plus explicit `owner_id` filters in owner queries |
| City rides | Independent drivers only. Owner-fleet drivers and vehicles are Trips only |

## 1. Data (migration V27)

### New tables
- **`vehicles`**
  - Columns: `id`, `owner_id` (not null, FK owners), `category`, `registration_number` (unique on its upper-case, spaceless form), `model`, `seats`, `ac`, `rc_photo`, `active`, `created_at`.
  - Check constraints:
    - category is one of the four;
    - seats between 1 and 30.
- **`driver_verification_checks`**
  - Columns: `driver_id`, `type` (`LICENCE` on Sarathi, `RC` on Vahan/mParivahan), `result` (`VERIFIED`/`FAILED`), `note`, `checked_by_owner_id`, `checked_at`.
  - Primary key: (`driver_id`, `type`).
- **`owner_trip_rates`**
  - Columns: `owner_id`, `category`, `per_km`, `per_hour`, `updated_at`.
  - Primary key: (`owner_id`, `category`).
  - Check constraint: both prices > 0.

### New columns
- **`owners.assignment_mode`**: `AUTO`/`MANUAL`. Not null, default `MANUAL`. NJO-0001 is set to `AUTO`.
- **`drivers`**:
  - `owner_id` (FK owners). Null for independent drivers.
  - `default_vehicle_id` (FK vehicles).
  - Check constraints:
    - `drivers_owner_fleet_has_owner`: `fleet = 'OWNER_FLEET'` ⇔ `owner_id IS NOT NULL`.
    - **`drivers_fleet_no_city`**: `NOT serves_city OR fleet = 'INDEPENDENT'`.
- **`travel_bookings`**:
  - `owner_id`: null for City rides.
  - `car_category`: null for City rides.
  - `vehicle_id`.
  - New `no_vehicle_overlap` exclusion constraint, mirroring `no_driver_overlap`: one vehicle cannot be on two active trips on overlapping dates.
- **`travel_packages.owner_id`** (not null after backfill). It replaces trusting the client-sent `ownerId` request parameter; `created_by` is kept for history.
- **`payments.owner_id`, `reviews.owner_id`, `trip_driver_photos.owner_id`**: filled by `BEFORE INSERT` triggers from the booking's `owner_id`.
- **`package_bookings.owner_id`**: filled by a trigger from the package.
- The entities map these four trigger-filled columns read-only (`insertable = false, updatable = false`), so no insert site in Java changes.

### Moving existing data
1. Every `OWNER_FLEET` driver, every Trip and its payments, reviews and photos, and every package and package booking get `owner_id` = NJO-0001.
2. Every `OWNER_FLEET` driver gets `serves_city = false`.
3. Each `OWNER_FLEET` driver's current car becomes a `vehicles` row of NJO-0001, which becomes their `default_vehicle_id`.
   - Category comes from seats: ≤ 5 → `SEDAN`, 6–8 → `SUV`, ≥ 9 → `TEMPO_TRAVELLER`.
   - A driver with no `vehicle_number` gets the placeholder registration `TEMP-<driver id>`. The Vehicles screen flags it "registration missing" so the owner can fix it.
   - The placeholder vehicle is active, so NJO-0001's automatic assignment keeps working.
4. Existing Trips get `car_category` from their driver's new vehicle (`SEDAN` when there is no driver) and that `vehicle_id`.
5. NJO-0001 gets an `owner_trip_rates` row for each of the four categories, priced at the latest `pricing_config` per-km and per-hour values (defaults 10.00 and 150.00 when the table is empty).
   - `pricing_config` is left in place, unused.
6. Owner notifications (`recipient_id = 'owner'`) become NJO-0001's: `recipient_id` = NJO-0001's id as text.
7. Independent drivers, City rides and the driver ledger are untouched.

A fresh database (no owners) still migrates: every backfill is a no-op.

### Row-level security
- **Database role.** V27 creates a `NOLOGIN` role `nj_owner_scope`, if it doesn't already exist, since roles are cluster-wide.
  - It is granted `SELECT/INSERT/UPDATE/DELETE` on all tables and `USAGE, SELECT` on all sequences, with matching default privileges.
  - It is granted to `CURRENT_USER`.
- **Tables under the rule.**
  - These tables get `ENABLE` and `FORCE ROW LEVEL SECURITY`: `drivers`, `vehicles`, `driver_verification_checks`, `owner_trip_rates`, `travel_bookings`, `payments`, `reviews`, `trip_driver_photos`, `travel_packages`, `package_bookings`.
  - For `driver_verification_checks`, the rule checks the owner through the driver row.
- **Two policies on each table.**
  - `app_unscoped`, for everyone except `nj_owner_scope`: `USING (current_user <> 'nj_owner_scope')`. Customers, drivers, admins, background jobs and Flyway keep working as today.
  - `owner_scoped`, for `nj_owner_scope`: `USING / WITH CHECK nj_owner_visible(owner_id)`.
- **`nj_owner_visible(row_owner)`** is true only when the setting `nj.owner_id` is set and either:
  - `row_owner` equals it, or
  - `row_owner IS NULL` and `nj.platform = 'on'`.

  An owner-scope connection with no owner setting sees nothing.
- **Why `SET ROLE` is needed.** The local database user (`postgres`) and the test container user are superusers, and superusers bypass RLS even when it is forced. Switching to `nj_owner_scope` is what makes the rule apply.
- **Setting the scope per connection.**
  - A `DataSource` wrapper (`OwnerScopedDataSource`, installed by a `BeanPostProcessor` around Hikari) reads a request-bound `OwnerScope`.
  - When a connection is taken while an owner request is in progress, it runs `SET ROLE nj_owner_scope` and sets `nj.owner_id` and `nj.platform`.
  - When the connection is closed, it runs `RESET ROLE` and resets both settings before the connection returns to the pool.
  - If the reset fails, the connection is evicted (`HikariDataSource.evictConnection`), never reused.
- **Who gets a scope.** `OwnerScopeFilter` runs after `JwtAuthenticationFilter`. For `ROLE_OWNER` only, it binds the scope: the owner id is the JWT subject, and `platform` means the owner's code equals the platform owner code (cached per owner id). It always clears the scope in `finally`. Other roles get no scope.

Notifications are **not** under RLS: an owner's actions write notifications to customers and drivers. Owner feeds are per owner (`recipient_id` = owner id, role `ROLE_OWNER`). `NotificationController.assertOwnRecipient` allows an owner only their own feed. The "owner may read any user's or driver's feed" path is removed.

## 2. Behaviour

### Scoping in the code (RLS is the backstop)
- **`OwnerScope` bean**: `ownerId()`, `isPlatform()` and `requirePlatform()`. `requirePlatform()` throws `AccessDeniedException`, which returns 403.
- **Owner-screen queries filter by owner explicitly**, so NJO-0001's Trip screens exclude platform (null-owner) rows:
  - bookings: `serviceMode = TRIP AND ownerId = :me`;
  - revenue sums and series;
  - pending payments;
  - reviews;
  - drivers: `fleet = OWNER_FLEET AND ownerId = :me`;
  - vehicles, packages, package bookings.
- **Lookups by id** (booking, driver, vehicle, package, payment, package booking) on owner endpoints go through RLS, then check `ownerId == me`. Another owner's record returns **404**.
- **Caches.**
  - Revenue caches and the `allDrivers`/`driverById` caches gain the owner id in their key; evictions stay `allEntries`.
  - `pricing`/`hourlyPricing` are replaced by a `tripRates` cache keyed by owner id.

### Screens
| Screen | Every owner | NJO-0001 only (platform screens, 403 for other owners) |
|---|---|---|
| Dashboard, Bookings, Payments, Reviews, Revenue | Own Trips only | City rides list, City-ride pending payments, City revenue (`/api/owner/platform/...`) |
| Vehicles (new) | Add/edit/deactivate; category, registration, model, seats, AC, RC photo; "registration missing" badge for `TEMP-` | — |
| Drivers | Own fleet; add driver (pending), checklist, approve/reject, usual car; no City-ride option | Independent drivers list, driver applications, approve/reject independents |
| Pricing (new tab, replaces the global price) | Per-km/per-hour per category; assignment mode AUTO/MANUAL | City fares |
| Packages, Package bookings | Own | — |
| Cities, Payouts/Dues, `/actuator/**` | 403 | ✔ |
| Profile | Travels name, Owner ID, cities, bank (masked), UPI, from `GET /api/owner/me` | — |

- The frontend hides platform navigation unless `GET /api/owner/me` returns `platform: true`.
- `/actuator/**` requires `ROLE_OWNER` plus platform. It is checked in `SecurityConfig` with an authorization manager that calls `OwnerScope`.

### Drivers added by an owner
- **Adding a driver.** `POST /api/owner/drivers` creates the driver with:
  - `owner_id` = me and `fleet = OWNER_FLEET`;
  - `verification_status = UNDER_REVIEW`;
  - `serves_city = false` and `serves_trip = true`;
  - `default_vehicle_id` set to the chosen car, which must be one of my active vehicles.

  No credentials are emailed yet.
- **Checklist.** `PUT /api/owner/drivers/{id}/checks/{LICENCE|RC}` records the check, with a note required for `FAILED`.
- **Approving.** `POST /api/owner/drivers/{id}/approve` needs both checks `VERIFIED`, otherwise 409. Approval emails the driver's login, using the existing credentials email.
- **Rejecting** needs a reason.
- **Independent drivers** keep the existing approve/reject, now platform-only. The same `/drivers/{id}/approve` route dispatches by fleet.
- **The City-ride switch is gone for owner-fleet drivers.**
  - Turning City rides on for an owner-fleet driver gives a 400 with a clear message, from both the owner's driver form and the driver app's mode switch. The database constraint is the backstop.
  - `VehicleFields` drops the City toggle for owner drivers.

### City-ride dispatch: independent drivers only
- `DriverCard` gains `fleet`, and `MatchingWorker.eligible()` requires `INDEPENDENT`.
- `DriverPresenceService.report` does not add owner-fleet drivers to the city GEO set.
- `findDispatchableAmong` adds `d.fleet = INDEPENDENT`.

### Customer books a Trip
- The Trip form and the landing-page quick booking gain a **car category** choice, populated from `GET /api/public/trip-rates`. That endpoint returns the platform owner's priced categories with per-km and per-hour rates, and replaces `/api/public/pricing` and `/api/owner/pricing/current`.
- **Pricing.** `UserService.buildBooking` sets `owner_id` = platform owner, `car_category`, and the price from that owner's rate for the category: per-km × distance, or per-hour × hours. A category the owner hasn't priced gives 400 "This car type is not available". `updateBooking` re-prices the same way.
- **AUTO owners.** The app picks the first free approved active driver of that owner who has a free active vehicle of the category with `seats ≥ travel_members` and AC if `acType = AC`.
  - The driver's usual car is preferred, then the lowest driver id, then the lowest vehicle id.
  - It is one native query returning (driver_id, vehicle_id).
  - The booking gets both. A lost race (an overlap constraint) retries, as today.
- **MANUAL owners.** The booking is saved PENDING with no driver. The owner gets the in-app notification and an email: "Trip needs a driver".
- **Assigning by hand.**
  - `GET /api/owner/bookings/{id}/assign-options` lists my free approved drivers and my free active vehicles of the category with enough seats. Each driver's usual car is marked.
  - `POST /api/owner/bookings/{id}/assign-driver` takes `{driverId, vehicleId?}`. When `vehicleId` is omitted, it uses the driver's usual car.
  - Errors:
    - **400** for a wrong category, an inactive vehicle, or too few seats;
    - **409** for a busy driver or car;
    - **404** for someone else's driver, car or booking.
- **The driver rejects a Trip.** AUTO owners re-offer it to the next free driver and car (same query, excluding decliners). MANUAL owners get it back as PENDING with no driver, plus a notification.
- **Paying for a Trip.** The UPI link points to the booking owner's `upi_id`, falling back to `payment.upi.id` when the owner has none. Razorpay advances stay on the platform account (sub-project 4 settles commission).

### Live tracking
`BookingAccessGuard.canAccess` for `ROLE_OWNER` allows:
- the booking's own owner, when `ownerId == me`;
- the platform owner for City rides, when `ownerId` is null.

### Portal gate
At the end, `app.owners.portal-open-to-all` and the "portal opens soon" gate are removed, and every approved owner gets the dashboard. `OwnerChangePassword` drops the no-token waiting state.

## 3. Errors and tests

**Errors:**
- 404 for another owner's record;
- 403 for platform screens;
- 400 for validation (category, seats, inactive car, unpriced category, City on owner-fleet);
- 409 for conflicts (busy driver or car, approve without checks).

**Tests** (TDD; Testcontainers where SQL or RLS is involved):
- **RLS.** Owner A cannot read owner B's rows through a list, a by-id lookup, or a native revenue query. The platform owner also sees null-owner rows. An owner-scope connection without a setting sees nothing. After a scoped request, the next pooled connection is unscoped (role and settings reset).
- **Migration.** NJO-0001 owns the existing drivers, trips, packages and notifications. Owner-fleet drivers have `serves_city = false`. Vehicles are created with categories inferred from seats, and `TEMP-` where the number is missing. Rates are copied from `pricing_config`. A fresh database migrates.
- **Service scoping:** bookings, drivers, revenue, payments, reviews and packages each return only the caller's data, and platform endpoints return 403 for non-platform owners.
- **Assignment:** AUTO picks the usual car first; category, seats and AC are respected. MANUAL leaves the Trip unassigned and notifies. Rejection re-offers (AUTO) or hands back (MANUAL). Assign-options lists only free, own and suitable entries.
- **Driver approval:** approval needs both checks; credentials are emailed on approval only.
- **City dispatch:** an owner-fleet driver is never a City candidate, and the DB constraint refuses `serves_city` on owner-fleet drivers.
- **Pricing:** price by category; an unpriced category is refused.
- **Existing tests** that assume one owner are updated.
- **Local run:** NJO-0001 and NJO-0002 each see only their own data.

## Out of scope
- Choosing an owner when booking (sub-project 3).
- Commission bills (sub-project 4).
- Dropping `pricing_config` and the driver `vehicle_*` columns. They're kept and unused for owner-fleet drivers; independent drivers still use them.
