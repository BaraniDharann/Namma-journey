-- Drivers can now sign themselves up (INDEPENDENT) and wait for the owner's approval. Every row
-- that exists today was made by the owner, so the defaults keep them OWNER_FLEET and APPROVED.
ALTER TABLE drivers
    ADD COLUMN fleet               varchar(12) NOT NULL DEFAULT 'OWNER_FLEET',
    ADD COLUMN verification_status varchar(12) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN rejection_reason    varchar(500),
    ADD COLUMN rc_photo            varchar(1000),
    ADD COLUMN submitted_at        timestamp,
    ADD COLUMN reviewed_at         timestamp,
    ADD COLUMN reviewed_by         bigint;

ALTER TABLE drivers
    ADD CONSTRAINT drivers_fleet_valid CHECK (fleet IN ('OWNER_FLEET', 'INDEPENDENT')),
    ADD CONSTRAINT drivers_verification_valid CHECK (verification_status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    -- Trips are assigned by the owner, so a driver who is not in the owner's fleet never takes them.
    ADD CONSTRAINT drivers_independent_no_trips CHECK (NOT serves_trip OR fleet = 'OWNER_FLEET');

-- One plate, one driver, however it was typed. Owners could save the same plate twice before this,
-- so clear the later duplicates (keeping the lowest id) or the index below would fail the migration.
UPDATE drivers d SET vehicle_number = NULL
WHERE d.vehicle_number IS NOT NULL
  AND EXISTS (SELECT 1 FROM drivers o
              WHERE o.id < d.id AND upper(o.vehicle_number) = upper(d.vehicle_number));

CREATE UNIQUE INDEX uq_drivers_vehicle_number ON drivers (upper(vehicle_number)) WHERE vehicle_number IS NOT NULL;
