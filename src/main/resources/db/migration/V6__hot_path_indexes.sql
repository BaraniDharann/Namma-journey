-- Indexes for the queries that run on every dashboard load.
--
-- The baseline schema has primary keys and a handful of unique constraints but no secondary
-- indexes at all, so every one of the lookups below was a full table scan. Invisible with a few
-- hundred rows; linear slowdown on every page as bookings and notifications accumulate.
--
-- Plain CREATE INDEX briefly blocks writes to each table while it builds. At this data size that
-- is well under a second. Once tables hold millions of rows, add future indexes with
-- CREATE INDEX CONCURRENTLY in a migration of their own (Flyway: executeInTransaction=false).

-- Traveller's "My bookings": findByUserId(userId), shown newest first.
CREATE INDEX IF NOT EXISTS idx_travel_bookings_user_date
    ON public.travel_bookings (user_id, booking_date DESC);

-- Driver's trip list: findByDriverId(driverId), every status. The no_driver_overlap index (V5)
-- only covers active trips, so it cannot answer this one.
CREATE INDEX IF NOT EXISTS idx_travel_bookings_driver
    ON public.travel_bookings (driver_id);

-- Owner revenue reports: status = 'COMPLETED' AND booking_date BETWEEN start AND end.
CREATE INDEX IF NOT EXISTS idx_travel_bookings_status_date
    ON public.travel_bookings (status, booking_date);

-- Notification bell, polled by every signed-in screen: list, unread count and mark-all-read are
-- all keyed on (recipient_id, recipient_role).
CREATE INDEX IF NOT EXISTS idx_notifications_recipient
    ON public.notifications (recipient_id, recipient_role, created_at DESC);

-- Duplicate-notification guard: existsByBookingIdAndType(bookingId, type).
CREATE INDEX IF NOT EXISTS idx_notifications_booking_type
    ON public.notifications (booking_id, type);

-- OTP verification: latest unverified, unexpired code for an email.
CREATE INDEX IF NOT EXISTS idx_otps_email_created
    ON public.otps (email, created_at DESC);

-- Traveller's payment history: findByUserId.
CREATE INDEX IF NOT EXISTS idx_payments_user
    ON public.payments (user_id);

-- Review lookups: one per booking, and a driver's rating page.
CREATE INDEX IF NOT EXISTS idx_reviews_booking
    ON public.reviews (booking_id);
CREATE INDEX IF NOT EXISTS idx_reviews_driver
    ON public.reviews (driver_id);

-- End-of-trip photo, read on every booking card that renders.
CREATE INDEX IF NOT EXISTS idx_trip_driver_photos_booking
    ON public.trip_driver_photos (booking_id);

-- Traveller's package bookings, newest first.
CREATE INDEX IF NOT EXISTS idx_package_bookings_user_date
    ON public.package_bookings (user_id, booking_date DESC);
