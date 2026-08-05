-- Fix: Add STARTED status to travel_bookings status check constraint
-- The constraint was blocking start-trip because STARTED was not in allowed values

ALTER TABLE travel_bookings DROP CONSTRAINT IF EXISTS travel_bookings_status_check;

ALTER TABLE travel_bookings ADD CONSTRAINT travel_bookings_status_check
  CHECK (status IN ('PENDING', 'CONFIRMED', 'STARTED', 'COMPLETED', 'CANCELLED'));
