-- "Is this driver busy on these dates?" is asked for every Trip assignment and every City ride
-- offer, as a NOT EXISTS per candidate driver. Only active, unfinished bookings can make a driver
-- busy, and those are a small slice of a table that keeps every booking ever made, so the index
-- holds just that slice, keyed the way the probe looks it up.
CREATE INDEX idx_travel_bookings_driver_active
    ON public.travel_bookings (driver_id, from_date, to_date)
    WHERE status IN ('PENDING', 'CONFIRMED', 'STARTED') AND trip_ended_at IS NULL;
