-- Create pricing_config table
CREATE TABLE IF NOT EXISTS pricing_config (
    id BIGSERIAL PRIMARY KEY,
    price_per_km DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by BIGINT NOT NULL
);

-- Add totalAmount column to travel_bookings
ALTER TABLE travel_bookings 
ADD COLUMN IF NOT EXISTS total_amount DOUBLE PRECISION;

-- Insert default pricing (10 rupees per km)
INSERT INTO pricing_config (price_per_km, updated_at, updated_by) 
VALUES (10.0, NOW(), 1);

-- Update existing bookings with calculated amount (if any exist)
UPDATE travel_bookings 
SET total_amount = distance_km * 10.0 
WHERE total_amount IS NULL;

-- Make total_amount NOT NULL after updating existing records
ALTER TABLE travel_bookings 
ALTER COLUMN total_amount SET NOT NULL;
