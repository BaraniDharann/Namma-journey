# Owner cars: City / Travel / Off modes

Date: 2026-11-10. Status: approved in chat.

## Goal

A travels owner decides, per car, whether it does City rides or Travel (outstation) trips, and
can move a car between the two at any time. Example: an owner with 4 cars and 2 buses keeps the
buses on bus travel, puts 2 cars + drivers on City rides (nearby rides are offered to them
automatically) and keeps 2 cars on Travel. The owner may also give a Travel booking to a City car.

## Decisions (from the user)

1. Both: the owner switches a car's mode any time, and may put a Travel booking on any free car.
2. City drivers of an owner use the driver app like independent drivers: go Online, accept/reject offers.
3. Fixed pairing: a City car has exactly one driver, chosen by the owner.
4. Money from an owner's City ride belongs to the owner, less the city commission. Owners pay
   their drivers outside the app.

## Data (V35)

- `vehicles.mode` CITY | TRAVEL | OFF, default TRAVEL (existing cars behave as before).
- `vehicles.city_driver_id` FK drivers; required when mode = CITY, null otherwise; unique
  (a driver drives at most one City car).
- Drop `drivers_fleet_no_city`. An owner-fleet driver's `serves_city` is true exactly while they
  are paired to a City car; the app keeps it in sync, and the driver's City vehicle columns
  (`vehicle_type` = CAR, seats, AC, number, model) are copied from the paired car.
- Tempo Travellers cannot be City cars. Hatchback / Sedan / SUV are CAR for City matching.
- `owner_ledger_entries`: money between the platform and a fleet owner for the owner's City rides
  (RIDE_EARNING, CASH_COMMISSION, PAYOUT, COLLECTION), same shape as `driver_ledger_entries`.

## Rules

- To CITY: car active, not a Tempo Traveller, no unfinished Travel booking, driver belongs to
  the owner, is not paired to another City car.
- To TRAVEL / OFF: allowed unless the paired driver is on an unfinished City ride. The driver is
  removed from presence (offline for City) at once; their Travel availability is untouched.
- Deactivating a City car = switching it OFF first.
- A City car with a Travel booking covering today is not offered City rides (the driver is BUSY
  if they drive it; if someone else drives it the car is checked too). It returns by itself
  when the trip ends.
- Travel auto-pick (`FREE_TRIP_PAIR_SQL`) only takes TRAVEL cars. The owner's manual
  assign screen still lists free City cars, after Travel cars, marked as City cars.
- An accepted City ride by an owner-fleet driver records `vehicle_id` = the paired car, so the
  `no_vehicle_overlap` constraint protects it.

## Matching

`MatchingWorker.eligible` no longer requires INDEPENDENT. `DriverCardLoader` marks an owner-fleet
driver BUSY when their City car has a Travel booking covering today. Cards are refreshed
(`profileChanged`) on every pairing / mode change.

## Money

On completion, an owner-fleet City ride writes an owner ledger entry (online: owed fare less
commission; cash: owner owes commission). The fleet owner sees the balance and entries on
Revenue; the platform owner records UPI payouts/collections with a UTR on Payouts.

## Screens

- Owner Vehicles: City / Travel / Off switch per car, driver picker for City, cars grouped by mode.
- Driver app: an owner driver without a City car sees no Online-for-City toggle and a note.
- Owner Payouts (platform owner): fleet owner balances. Owner Revenue: City ride balance.

## Testing

Unit tests for the mode rules, eligibility, Travel auto-pick filter and owner ledger; migration
runs under the existing Testcontainers/H2 setup.
