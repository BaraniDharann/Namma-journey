-- Real City fares in place of V14's placeholders, priced for Bengaluru.
--
-- AUTO is the government meter: Bengaluru Urban RTA, effective 1 Aug 2025 - Rs 36 for the
-- first 2 km, Rs 18 a km after. base 0 + 18/km with a 36 minimum is that meter exactly
-- (18 x 2 = 36), and no per-minute charge because the meter has none while moving.
-- BIKE and CAR sit below what Rapido, Ola and Uber charge for the same ride.
--
-- Only rows still on the V14 placeholders change: a fare the owner has already set is theirs.
UPDATE public.city_fares
   SET base_fare = 10.00, per_km = 6.00, per_minute = 0.25, minimum_fare = 25.00, updated_at = now()
 WHERE vehicle_type = 'BIKE'
   AND base_fare = 20.00 AND per_km = 6.00 AND per_minute = 1.00 AND minimum_fare = 30.00;

UPDATE public.city_fares
   SET base_fare = 0.00, per_km = 18.00, per_minute = 0.00, minimum_fare = 36.00, updated_at = now()
 WHERE vehicle_type = 'AUTO'
   AND base_fare = 30.00 AND per_km = 12.00 AND per_minute = 1.50 AND minimum_fare = 40.00;

UPDATE public.city_fares
   SET base_fare = 40.00, per_km = 14.00, per_minute = 1.00, minimum_fare = 80.00, updated_at = now()
 WHERE vehicle_type = 'CAR'
   AND base_fare = 50.00 AND per_km = 16.00 AND per_minute = 2.00 AND minimum_fare = 80.00;
