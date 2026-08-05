-- Add missing columns to travel_bookings table

ALTER TABLE travel_bookings 
ADD COLUMN IF NOT EXISTS from_date DATE,
ADD COLUMN IF NOT EXISTS to_date DATE,
ADD COLUMN IF NOT EXISTS travel_members INTEGER,
ADD COLUMN IF NOT EXISTS ac_type VARCHAR(20),
ADD COLUMN IF NOT EXISTS driver_id BIGINT,
ADD COLUMN IF NOT EXISTS total_amount DOUBLE PRECISION;

-- Update existing records with default values if needed
UPDATE travel_bookings 
SET total_amount = 0 
WHERE total_amount IS NULL;

-- Make total_amount NOT NULL after setting defaults
ALTER TABLE travel_bookings 
ALTER COLUMN total_amount SET NOT NULL;
