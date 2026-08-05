-- Performance indexes for scalability (1000+ concurrent users)

-- Composite index for revenue queries (status + booking_date range)
CREATE INDEX IF NOT EXISTS idx_bookings_status_date ON travel_bookings(status, booking_date);

-- Index for driver assignment queries
CREATE INDEX IF NOT EXISTS idx_bookings_driver_id ON travel_bookings(driver_id);

-- Index for notification lookups (frequently polled)
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, recipient_role, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(recipient_id, recipient_role, read) WHERE read = false;

-- Index for payment status lookups
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at DESC);

-- Index for OTP expiry cleanup
CREATE INDEX IF NOT EXISTS idx_otp_expiry ON otps(expiry_time);

-- Index for review lookups by driver
CREATE INDEX IF NOT EXISTS idx_reviews_driver_id ON reviews(driver_id);
CREATE INDEX IF NOT EXISTS idx_reviews_booking_id ON reviews(booking_id);
