-- Create travel_bookings table
CREATE TABLE IF NOT EXISTS travel_bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    user_phone VARCHAR(10) NOT NULL,
    from_place VARCHAR(255) NOT NULL,
    to_place VARCHAR(255) NOT NULL,
    travel_days INTEGER NOT NULL,
    distance_km DOUBLE PRECISION NOT NULL,
    estimated_time_minutes BIGINT NOT NULL,
    route_details TEXT,
    booking_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Create index for faster queries
CREATE INDEX idx_travel_bookings_user_id ON travel_bookings(user_id);
CREATE INDEX idx_travel_bookings_booking_date ON travel_bookings(booking_date);
