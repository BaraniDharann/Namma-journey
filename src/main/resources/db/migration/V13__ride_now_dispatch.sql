-- Ride Now: a trip wanted immediately is offered to the nearest online driver, one driver at a
-- time, instead of being handed to whichever free driver the date query returns first.
-- See docs/superpowers/specs/2026-10-23-ride-now-dispatch-design.md.

-- SCHEDULED is every booking made so far: a trip for a future date, assigned by date.
ALTER TABLE public.travel_bookings
    ADD COLUMN dispatch_mode varchar(16) NOT NULL DEFAULT 'SCHEDULED',
    -- Where the nearest-driver search stands. NULL for scheduled bookings, which never search.
    ADD COLUMN dispatch_state varchar(16),
    -- When the search began; bounds how long it keeps retrying before the owner takes over.
    ADD COLUMN dispatch_started_at timestamp(6) without time zone,
    -- Deadline for the driver currently holding the offer. Past it, silence counts as a decline.
    ADD COLUMN offer_expires_at timestamp(6) without time zone,
    -- How far the offered driver was from the pickup, shown on their offer card.
    ADD COLUMN offer_distance_km double precision,
    -- When the driver ended the trip. The booking stays STARTED until it is paid, which can wait
    -- on the owner verifying a UPI transfer - but the driver is free from this moment.
    ADD COLUMN trip_ended_at timestamp(6) without time zone;

-- The expiry sweep runs every few seconds and only ever wants open offers.
CREATE INDEX idx_travel_bookings_open_dispatch
    ON public.travel_bookings (dispatch_state, offer_expires_at)
    WHERE dispatch_state IN ('SEARCHING', 'OFFERED');

-- A driver who has ended a trip is no longer occupied by it, even while it awaits payment.
-- Without this a driver waiting on a UPI verification could not be given the next ride: the
-- constraint still counted the ended trip as STARTED and refused every new assignment for the day.
ALTER TABLE public.travel_bookings DROP CONSTRAINT no_driver_overlap;
ALTER TABLE public.travel_bookings
    ADD CONSTRAINT no_driver_overlap
    EXCLUDE USING gist (
        driver_id WITH =,
        daterange(from_date, to_date, '[]') WITH &&
    )
    WHERE (driver_id IS NOT NULL AND status IN ('PENDING', 'CONFIRMED', 'STARTED') AND trip_ended_at IS NULL);
