-- Money as NUMERIC(12,2): exact paise instead of binary floating point.
--
-- double precision cannot represent most decimal fractions (0.10 is stored as
-- 0.1000000000000000055...), so fares picked up dust when multiplied and revenue totals drifted
-- when summed. Worse, fares were never rounded at all: a 461.237 km trip at 14/km was stored as
-- 6457.318 while the UPI link asked the passenger for 6457.32.
--
-- NUMERIC(12,2) holds up to 9,999,999,999.99 exactly. Existing values are rounded half-up to the
-- paisa, which is what every screen and UPI link already showed for them.
--
-- Distances and coordinates stay double precision: they are measurements, not money.

ALTER TABLE public.travel_bookings
    ALTER COLUMN total_amount TYPE numeric(12,2) USING round(total_amount::numeric, 2),
    ALTER COLUMN total_amount SET DEFAULT 0,
    ALTER COLUMN price_per_hour_at_booking TYPE numeric(12,2) USING round(price_per_hour_at_booking::numeric, 2);

ALTER TABLE public.payments
    ALTER COLUMN amount TYPE numeric(12,2) USING round(amount::numeric, 2);

ALTER TABLE public.pricing_config
    ALTER COLUMN price_per_km TYPE numeric(12,2) USING round(price_per_km::numeric, 2),
    ALTER COLUMN price_per_hour TYPE numeric(12,2) USING round(price_per_hour::numeric, 2);

ALTER TABLE public.travel_packages
    ALTER COLUMN price_per_person TYPE numeric(12,2) USING round(price_per_person::numeric, 2);

ALTER TABLE public.package_bookings
    ALTER COLUMN price_per_person TYPE numeric(12,2) USING round(price_per_person::numeric, 2),
    ALTER COLUMN total_amount TYPE numeric(12,2) USING round(total_amount::numeric, 2);
