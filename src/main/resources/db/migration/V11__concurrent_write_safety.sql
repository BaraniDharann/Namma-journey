-- Two requests changing the same row must not silently overwrite each other.

-- Optimistic locking for payments and package bookings (TravelBooking already has one). Without
-- it the owner's "verified" could be overwritten back to "pending" by a QR refresh that had read
-- the row a moment earlier. Hibernate bumps the version on every update and refuses a write
-- based on an older one.
ALTER TABLE public.payments         ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE public.package_bookings ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- One review per booking, enforced by the database: two simultaneous submissions both passed the
-- service's "already reviewed?" check and both inserted. Keep the earliest review if duplicates
-- already exist, so the constraint can be added.
DELETE FROM public.reviews r
USING public.reviews earlier
WHERE r.booking_id = earlier.booking_id
  AND (earlier.created_at, earlier.id) < (r.created_at, r.id);

ALTER TABLE public.reviews ADD CONSTRAINT uq_reviews_booking_id UNIQUE (booking_id);

-- Revenue reports now sum VERIFIED payments by the day they were verified.
CREATE INDEX idx_payments_status_verified ON public.payments (status, verified_date);
