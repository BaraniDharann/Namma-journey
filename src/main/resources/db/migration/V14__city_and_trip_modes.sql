-- City mode and Trip mode. City rides (bike, auto or car) go to the nearest driver right now;
-- trips (devotional, holiday, outstation) are booked for dates and driven only by cars.
-- See docs/superpowers/specs/2026-10-23-city-trip-modes-design.md.

-- Every driver so far drove a car and took every kind of trip, so that is what they become.
ALTER TABLE public.drivers
    ADD COLUMN vehicle_type varchar(8) NOT NULL DEFAULT 'CAR',
    ADD COLUMN vehicle_number varchar(20),
    ADD COLUMN vehicle_model varchar(60),
    ADD COLUMN vehicle_ac boolean NOT NULL DEFAULT true,
    ADD COLUMN vehicle_seats integer NOT NULL DEFAULT 4,
    ADD COLUMN serves_city boolean NOT NULL DEFAULT true,
    ADD COLUMN serves_trip boolean NOT NULL DEFAULT true,
    ADD CONSTRAINT drivers_vehicle_type_check CHECK (vehicle_type IN ('BIKE', 'AUTO', 'CAR')),
    -- Trips are by car. A bike or auto taking a 400 km pilgrimage is refused by the database too.
    ADD CONSTRAINT drivers_trip_needs_car CHECK (NOT serves_trip OR vehicle_type = 'CAR'),
    ADD CONSTRAINT drivers_vehicle_seats_check CHECK (vehicle_seats BETWEEN 1 AND 20);

-- Every booking so far was a trip by car.
ALTER TABLE public.travel_bookings
    ADD COLUMN service_mode varchar(8) NOT NULL DEFAULT 'TRIP',
    ADD COLUMN vehicle_type varchar(8) NOT NULL DEFAULT 'CAR',
    ADD CONSTRAINT travel_bookings_service_mode_check CHECK (service_mode IN ('CITY', 'TRIP')),
    ADD CONSTRAINT travel_bookings_vehicle_type_check CHECK (vehicle_type IN ('BIKE', 'AUTO', 'CAR'));

-- City fares, one row per vehicle type. Edited in place by the owner; trips keep pricing_config.
CREATE TABLE public.city_fares (
    vehicle_type varchar(8) NOT NULL,
    base_fare numeric(10,2) NOT NULL,
    per_km numeric(10,2) NOT NULL,
    per_minute numeric(10,2) NOT NULL,
    minimum_fare numeric(10,2) NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL DEFAULT now(),
    updated_by bigint,
    CONSTRAINT city_fares_pkey PRIMARY KEY (vehicle_type),
    CONSTRAINT city_fares_vehicle_type_check CHECK (vehicle_type IN ('BIKE', 'AUTO', 'CAR')),
    CONSTRAINT city_fares_non_negative CHECK (base_fare >= 0 AND per_km >= 0 AND per_minute >= 0 AND minimum_fare >= 0)
);

-- Placeholders so City mode works on day one. The owner sets the real fares.
INSERT INTO public.city_fares (vehicle_type, base_fare, per_km, per_minute, minimum_fare) VALUES
    ('BIKE', 20.00,  6.00, 1.00, 30.00),
    ('AUTO', 30.00, 12.00, 1.50, 40.00),
    ('CAR',  50.00, 16.00, 2.00, 80.00);
