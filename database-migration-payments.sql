-- Database Migration: Payment System
-- Run this script to add payment tracking functionality

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    user_id UUID NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('UPI', 'CASH')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')),
    upi_transaction_id VARCHAR(255),
    driver_id BIGINT,
    payment_date TIMESTAMP,
    verified_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES travel_bookings(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_payment_method ON payments(payment_method);

-- Add comment
COMMENT ON TABLE payments IS 'Tracks all payment transactions for bookings with UPI and cash methods';

-- UPI link expiry (5 minutes)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS upi_link_expires_at TIMESTAMP;
