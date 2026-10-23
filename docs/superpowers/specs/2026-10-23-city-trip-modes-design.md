# City mode and Trip mode

**Status:** approved 2026-10-23 · builds on [Ride Now dispatch](2026-10-23-ride-now-dispatch-design.md)

## Goal

Namma Journey offers two services:

- **City mode** — rides for right now, like Rapido. The traveller picks **Bike, Auto or Car**
  and the nearest suitable driver is offered the ride (the Ride Now engine).
- **Trip mode** — devotional, holiday and outstation trips booked for dates. **Cars only**,
  assigned by date as today.

City mode also keeps car drivers earning between long trips.

## Decisions

**Vehicles and modes**

- One vehicle per driver: type (`BIKE`, `AUTO`, `CAR`), registration number and model. Cars
  also record AC and number of seats.
- Each driver has two switches: `serves_city` and `serves_trip`.
  - Bike and auto drivers are City only. Turning `serves_trip` on for them is refused.
  - Car drivers may switch on either or both.
- Every existing driver becomes a 4-seat AC car serving both modes, so nothing breaks.

**City bookings**

- Always Ride Now: dated today and dispatched to the nearest driver.
- Always distance-priced. There are no dates and no hour-based option.
- At most `app.city.max-km` (default 50) by road.
- Passenger limits:

  | Vehicle | Passengers |
  |---|---|
  | Bike | 1 |
  | Auto | 3 |
  | Car | 7 |

  A car ride is only offered to cars with at least that many seats.
- No AC choice.

**Trip bookings**

- Always scheduled.
- Assigned only to cars whose driver has `serves_trip` on.
- An AC trip is assigned only to an AC car.
- Every existing booking becomes a Trip.

**Owner assignment**

- The owner may override the mode switches.
- The owner may not override the vehicle type: a bike ride never gets a car, and a Trip never
  gets a bike or auto.

**City fares**

- One row per vehicle type: base fare, per km, per minute and minimum fare.
- `fare = max(minimum, base + perKm × km + perMinute × minutes)`, rounded to the paisa, using
  the road distance and time from `RoutingService`.
- The starting fares are placeholders, and the owner must set real ones:

  | Vehicle | Base | Per km | Per minute | Minimum |
  |---|---|---|---|---|
  | Bike | ₹20 | ₹6 | ₹1 | ₹30 |
  | Auto | ₹30 | ₹12 | ₹1.5 | ₹40 |
  | Car | ₹50 | ₹16 | ₹2 | ₹80 |

**Trip pricing** is unchanged: the existing per-km and per-hour rates.

## Data (V14)

- **`drivers`:**
  - `vehicle_type varchar(8) NOT NULL DEFAULT 'CAR'`
  - `vehicle_number varchar(20)`
  - `vehicle_model varchar(60)`
  - `vehicle_ac boolean NOT NULL DEFAULT true`
  - `vehicle_seats int NOT NULL DEFAULT 4`
  - `serves_city boolean NOT NULL DEFAULT true`
  - `serves_trip boolean NOT NULL DEFAULT true`
- **`travel_bookings`:**
  - `service_mode varchar(8) NOT NULL DEFAULT 'TRIP'`
  - `vehicle_type varchar(8) NOT NULL DEFAULT 'CAR'`
- **`city_fares`:** primary key `vehicle_type`, the four fare columns as `numeric(10,2)`,
  `updated_at`, `updated_by`. Seeded with the placeholder rows above.

## API

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/user/{id}/bookings` | traveller | Gains `serviceMode` (`CITY`\|`TRIP`, default `TRIP`) and `vehicleType`. Replaces the `rideNow` flag. |
| `GET /api/user/city-fares/estimate?fromLat&fromLon&toLat&toLon` | traveller | Road distance and minutes, plus the fare and passenger limit for each vehicle type. |
| `GET /api/owner/city-fares` | owner | List the city fares. |
| `PUT /api/owner/city-fares/{vehicleType}` | owner | Set one vehicle type's fares. |
| `PUT /api/owner/drivers/{id}/vehicle` | owner | Set a driver's vehicle details and mode switches. |
| `POST /api/owner/drivers` (create) | owner | Gains optional vehicle fields. |
| `PUT /api/driver/{id}/modes` | driver | Turn their own City and Trip switches on or off. |

Responses gain:

- **bookings:** `serviceMode`, `vehicleType`
- **drivers:** `vehicleType`, `vehicleNumber`, `vehicleModel`, `vehicleAc`, `vehicleSeats`,
  `servesCity`, `servesTrip`

## Matching

- **City (DispatchService).** `findDispatchableAmong` also requires:
  - `serves_city`
  - the booking's `vehicle_type`
  - `vehicle_seats >= travel_members`
- **Trip.**
  - `findAvailableDrivers` and `findAvailableDriversExcluding` also require `vehicle_type = CAR`
    and `serves_trip`.
  - The AC match is applied to the candidates in Java, which keeps the repository signatures
    and their tests unchanged.

## Screens

- **Traveller, New booking:**
  - It starts with a choice: **City ride** or **Trip**.
  - City ride: pickup, drop, passengers, then fare cards for Bike, Auto and Car (from the
    estimate), then book. After booking, the live driver search panel shows.
  - Trip: the existing form, with dates, booking type, AC and temple shortcuts.
- **Traveller, My bookings:** each booking shows its mode and vehicle.
- **Driver dashboard:**
  - A vehicle summary.
  - City rides and Trips switches next to Online. Trips is hidden for bike and auto.
  - The offer card names the vehicle and mode.
- **Owner:**
  - Drivers: the create form gains vehicle fields; the list and details show the vehicle; the
    vehicle and modes can be edited.
  - Bookings: a City/Trip filter, with the mode shown on each row.
  - Revenue page: a City fares panel next to the existing pricing.

## Out of scope

- Several vehicles per driver.
- Surge pricing.
- Helmet and safety checks.
- Driver allowance, night halt and tolls on trips.
- Razorpay, which comes next.

## Implementation plan

1. **Migration V14** and the entities: `VehicleType`, `ServiceMode`, `CityFare`, and the new
   fields on `Driver` and `TravelBooking`.
2. **Fares.**
   - `CityFareService`: read, set, estimate.
   - Tests for the formula, the minimum fare and rounding.
3. **Booking.**
   - `UserService.createBooking` branches on `serviceMode`.
   - City rides check the distance, the passenger limit and the fare.
   - Trips stay as today, with the AC filter added.
   - The `rideNow` flag is removed.
4. **Matching.**
   - The repository filters above.
   - `DispatchService` passes the vehicle type and seats.
   - Driver rejection keeps the AC filter.
   - Owner assignment checks the vehicle type.
   - Tests for each.
5. **Drivers.**
   - Vehicle fields on create, the owner vehicle update, and the driver's own mode switches.
   - The response mapping.
   - Bike and auto drivers are forced to City only.
6. **Endpoints**, with the security annotations.
7. **Frontend.**
   - API helpers.
   - The NewBooking mode choice and City ride form.
   - Driver dashboard switches.
   - Owner drivers, bookings and fares.
   - Labels on the offer card and on My bookings.
8. **Verify.**
   - Full backend suite, frontend build, lint and tests.
   - Extend the constraint test for V14 defaults (it runs only with Docker).
