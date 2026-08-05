-- Add driver_id column to travel_bookings table for automatic driver allocation
ALTER TABLE travel_bookings 
ADD COLUMN driver_id BIGINT;

-- Add foreign key constraint (optional, for referential integrity)
ALTER TABLE travel_bookings 
ADD CONSTRAINT fk_driver 
FOREIGN KEY (driver_id) REFERENCES drivers(id);

-- Create index for better query performance
CREATE INDEX idx_driver_bookings ON travel_bookings(driver_id);
CREATE INDEX idx_booking_dates ON travel_bookings(from_date, to_date);
