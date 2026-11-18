-- City-ride zones from where drivers actually work.
--
-- Until now a City ride ran only inside a circle the admin drew by hand (cities). With one circle
-- (Chennai) a rider in Kanchipuram was refused, and a driver online there was silently left out of
-- every search. Now a driver picks State -> District -> Town, and that town's zone is created for
-- them (PlaceService / ZoneService). The admin can still draw, edit or switch off any zone.

-- 1. The place directory: the towns a driver can pick. Seeded for Tamil Nadu (every district HQ
--    plus the larger towns), Puducherry and Bengaluru; towns elsewhere are added by drivers from a
--    map search (verified = false until the admin looks).
CREATE TABLE public.places (
    id bigserial PRIMARY KEY,
    state varchar(60) NOT NULL,
    district varchar(80) NOT NULL,
    name varchar(80) NOT NULL,
    lat double precision NOT NULL,
    lon double precision NOT NULL,
    kind varchar(8) NOT NULL DEFAULT 'TOWN',
    radius_km double precision NOT NULL DEFAULT 10,
    verified boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT places_kind_check CHECK (kind IN ('METRO', 'TOWN')),
    CONSTRAINT places_radius_check CHECK (radius_km >= 1 AND radius_km <= 100),
    CONSTRAINT places_coords_check CHECK (lat BETWEEN -90 AND 90 AND lon BETWEEN -180 AND 180)
);
CREATE UNIQUE INDEX places_state_district_name_key ON public.places (lower(state), lower(district), lower(name));
CREATE INDEX places_state_idx ON public.places (state);

-- 2. Zones know where they are and what they are: a METRO searches 1/3/5 km, a TOWN 2/5/8 km.
ALTER TABLE public.cities
    ADD COLUMN place_id bigint REFERENCES public.places (id),
    ADD COLUMN state varchar(60),
    ADD COLUMN district varchar(80),
    ADD COLUMN kind varchar(8) NOT NULL DEFAULT 'METRO',
    ADD COLUMN auto_created boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT cities_kind_check CHECK (kind IN ('METRO', 'TOWN'));
CREATE UNIQUE INDEX cities_place_id_key ON public.cities (place_id) WHERE place_id IS NOT NULL;

-- 3. Where each driver says they work.
ALTER TABLE public.drivers
    ADD COLUMN home_place_id bigint REFERENCES public.places (id),
    ADD COLUMN home_zone_id bigint REFERENCES public.cities (id);
CREATE INDEX drivers_home_zone_idx ON public.drivers (home_zone_id) WHERE home_zone_id IS NOT NULL;

-- 4. Seed. Radius: metros 25 km, district headquarters 12 km, other towns 8 km.
INSERT INTO public.places (state, district, name, lat, lon, kind, radius_km) VALUES
-- Tamil Nadu: metros
('Tamil Nadu', 'Chennai', 'Chennai', 13.0827, 80.2707, 'METRO', 25),
('Tamil Nadu', 'Coimbatore', 'Coimbatore', 11.0168, 76.9558, 'METRO', 20),
('Tamil Nadu', 'Madurai', 'Madurai', 9.9252, 78.1198, 'METRO', 18),
('Tamil Nadu', 'Tiruchirappalli', 'Tiruchirappalli', 10.7905, 78.7047, 'METRO', 18),
('Tamil Nadu', 'Salem', 'Salem', 11.6643, 78.1460, 'METRO', 15),
-- Tamil Nadu: district headquarters
('Tamil Nadu', 'Ariyalur', 'Ariyalur', 11.1401, 79.0786, 'TOWN', 12),
('Tamil Nadu', 'Chengalpattu', 'Chengalpattu', 12.6921, 79.9707, 'TOWN', 12),
('Tamil Nadu', 'Cuddalore', 'Cuddalore', 11.7480, 79.7714, 'TOWN', 12),
('Tamil Nadu', 'Dharmapuri', 'Dharmapuri', 12.1357, 78.1602, 'TOWN', 12),
('Tamil Nadu', 'Dindigul', 'Dindigul', 10.3624, 77.9695, 'TOWN', 12),
('Tamil Nadu', 'Erode', 'Erode', 11.3410, 77.7172, 'TOWN', 12),
('Tamil Nadu', 'Kallakurichi', 'Kallakurichi', 11.7380, 78.9590, 'TOWN', 12),
('Tamil Nadu', 'Kancheepuram', 'Kanchipuram', 12.8342, 79.7036, 'TOWN', 12),
('Tamil Nadu', 'Kanniyakumari', 'Nagercoil', 8.1833, 77.4119, 'TOWN', 12),
('Tamil Nadu', 'Karur', 'Karur', 10.9601, 78.0766, 'TOWN', 12),
('Tamil Nadu', 'Krishnagiri', 'Krishnagiri', 12.5186, 78.2137, 'TOWN', 12),
('Tamil Nadu', 'Mayiladuthurai', 'Mayiladuthurai', 11.1035, 79.6550, 'TOWN', 12),
('Tamil Nadu', 'Nagapattinam', 'Nagapattinam', 10.7672, 79.8449, 'TOWN', 12),
('Tamil Nadu', 'Namakkal', 'Namakkal', 11.2189, 78.1674, 'TOWN', 12),
('Tamil Nadu', 'Nilgiris', 'Udhagamandalam (Ooty)', 11.4102, 76.6950, 'TOWN', 12),
('Tamil Nadu', 'Perambalur', 'Perambalur', 11.2342, 78.8807, 'TOWN', 12),
('Tamil Nadu', 'Pudukkottai', 'Pudukkottai', 10.3833, 78.8001, 'TOWN', 12),
('Tamil Nadu', 'Ramanathapuram', 'Ramanathapuram', 9.3639, 78.8395, 'TOWN', 12),
('Tamil Nadu', 'Ranipet', 'Ranipet', 12.9224, 79.3326, 'TOWN', 12),
('Tamil Nadu', 'Sivaganga', 'Sivaganga', 9.8433, 78.4809, 'TOWN', 12),
('Tamil Nadu', 'Tenkasi', 'Tenkasi', 8.9594, 77.3152, 'TOWN', 12),
('Tamil Nadu', 'Thanjavur', 'Thanjavur', 10.7870, 79.1378, 'TOWN', 12),
('Tamil Nadu', 'Theni', 'Theni', 10.0104, 77.4768, 'TOWN', 12),
('Tamil Nadu', 'Thoothukudi', 'Thoothukudi', 8.7642, 78.1348, 'TOWN', 12),
('Tamil Nadu', 'Tirunelveli', 'Tirunelveli', 8.7139, 77.7567, 'TOWN', 12),
('Tamil Nadu', 'Tirupathur', 'Tirupathur', 12.4961, 78.5730, 'TOWN', 12),
('Tamil Nadu', 'Tiruppur', 'Tiruppur', 11.1085, 77.3411, 'TOWN', 12),
('Tamil Nadu', 'Tiruvallur', 'Tiruvallur', 13.1431, 79.9086, 'TOWN', 12),
('Tamil Nadu', 'Tiruvannamalai', 'Tiruvannamalai', 12.2253, 79.0747, 'TOWN', 12),
('Tamil Nadu', 'Tiruvarur', 'Tiruvarur', 10.7661, 79.6344, 'TOWN', 12),
('Tamil Nadu', 'Vellore', 'Vellore', 12.9165, 79.1325, 'TOWN', 12),
('Tamil Nadu', 'Viluppuram', 'Viluppuram', 11.9401, 79.4861, 'TOWN', 12),
('Tamil Nadu', 'Virudhunagar', 'Virudhunagar', 9.5680, 77.9624, 'TOWN', 12),
-- Tamil Nadu: other towns
('Tamil Nadu', 'Chengalpattu', 'Tambaram', 12.9249, 80.1000, 'TOWN', 8),
('Tamil Nadu', 'Chengalpattu', 'Guduvancheri', 12.8447, 80.0596, 'TOWN', 8),
('Tamil Nadu', 'Chengalpattu', 'Maraimalai Nagar', 12.7959, 80.0244, 'TOWN', 8),
('Tamil Nadu', 'Chengalpattu', 'Mamallapuram', 12.6208, 80.1945, 'TOWN', 8),
('Tamil Nadu', 'Kancheepuram', 'Sriperumbudur', 12.9675, 79.9419, 'TOWN', 8),
('Tamil Nadu', 'Kancheepuram', 'Walajabad', 12.7920, 79.8230, 'TOWN', 8),
('Tamil Nadu', 'Kancheepuram', 'Uthiramerur', 12.6160, 79.7570, 'TOWN', 8),
('Tamil Nadu', 'Tiruvallur', 'Avadi', 13.1067, 80.0970, 'TOWN', 8),
('Tamil Nadu', 'Tiruvallur', 'Ponneri', 13.3380, 80.1950, 'TOWN', 8),
('Tamil Nadu', 'Tiruvallur', 'Gummidipoondi', 13.4074, 80.1083, 'TOWN', 8),
('Tamil Nadu', 'Tiruvallur', 'Tiruttani', 13.1757, 79.6157, 'TOWN', 8),
('Tamil Nadu', 'Ranipet', 'Arakkonam', 13.0843, 79.6706, 'TOWN', 8),
('Tamil Nadu', 'Ranipet', 'Walajapet', 12.9250, 79.3660, 'TOWN', 8),
('Tamil Nadu', 'Vellore', 'Gudiyatham', 12.9446, 78.8735, 'TOWN', 8),
('Tamil Nadu', 'Tirupathur', 'Vaniyambadi', 12.6822, 78.6202, 'TOWN', 8),
('Tamil Nadu', 'Tirupathur', 'Ambur', 12.7916, 78.7166, 'TOWN', 8),
('Tamil Nadu', 'Krishnagiri', 'Hosur', 12.7409, 77.8253, 'TOWN', 10),
('Tamil Nadu', 'Dharmapuri', 'Harur', 12.0520, 78.4800, 'TOWN', 8),
('Tamil Nadu', 'Tiruvannamalai', 'Arani', 12.6700, 79.2850, 'TOWN', 8),
('Tamil Nadu', 'Tiruvannamalai', 'Polur', 12.5120, 79.1240, 'TOWN', 8),
('Tamil Nadu', 'Viluppuram', 'Tindivanam', 12.2340, 79.6550, 'TOWN', 8),
('Tamil Nadu', 'Kallakurichi', 'Ulundurpet', 11.6900, 79.2900, 'TOWN', 8),
('Tamil Nadu', 'Cuddalore', 'Chidambaram', 11.3990, 79.6930, 'TOWN', 8),
('Tamil Nadu', 'Cuddalore', 'Neyveli', 11.6088, 79.4870, 'TOWN', 8),
('Tamil Nadu', 'Cuddalore', 'Virudhachalam', 11.5180, 79.3240, 'TOWN', 8),
('Tamil Nadu', 'Mayiladuthurai', 'Sirkazhi', 11.2390, 79.7360, 'TOWN', 8),
('Tamil Nadu', 'Nagapattinam', 'Velankanni', 10.6800, 79.8500, 'TOWN', 8),
('Tamil Nadu', 'Tiruvarur', 'Mannargudi', 10.6650, 79.4510, 'TOWN', 8),
('Tamil Nadu', 'Thanjavur', 'Kumbakonam', 10.9617, 79.3881, 'TOWN', 10),
('Tamil Nadu', 'Thanjavur', 'Pattukkottai', 10.4230, 79.3190, 'TOWN', 8),
('Tamil Nadu', 'Pudukkottai', 'Aranthangi', 10.1700, 78.9900, 'TOWN', 8),
('Tamil Nadu', 'Tiruchirappalli', 'Srirangam', 10.8620, 78.6930, 'TOWN', 6),
('Tamil Nadu', 'Tiruchirappalli', 'Manapparai', 10.6080, 78.4250, 'TOWN', 8),
('Tamil Nadu', 'Karur', 'Kulithalai', 10.9360, 78.4230, 'TOWN', 8),
('Tamil Nadu', 'Namakkal', 'Tiruchengode', 11.3800, 77.8940, 'TOWN', 8),
('Tamil Nadu', 'Namakkal', 'Rasipuram', 11.4600, 78.1840, 'TOWN', 8),
('Tamil Nadu', 'Salem', 'Attur', 11.5960, 78.6020, 'TOWN', 8),
('Tamil Nadu', 'Salem', 'Mettur', 11.7860, 77.8000, 'TOWN', 8),
('Tamil Nadu', 'Erode', 'Gobichettipalayam', 11.4550, 77.4420, 'TOWN', 8),
('Tamil Nadu', 'Erode', 'Bhavani', 11.4450, 77.6820, 'TOWN', 8),
('Tamil Nadu', 'Tiruppur', 'Udumalaipettai', 10.5850, 77.2470, 'TOWN', 8),
('Tamil Nadu', 'Tiruppur', 'Dharapuram', 10.7380, 77.5310, 'TOWN', 8),
('Tamil Nadu', 'Coimbatore', 'Pollachi', 10.6609, 77.0048, 'TOWN', 8),
('Tamil Nadu', 'Coimbatore', 'Mettupalayam', 11.2990, 76.9340, 'TOWN', 8),
('Tamil Nadu', 'Nilgiris', 'Coonoor', 11.3530, 76.7950, 'TOWN', 6),
('Tamil Nadu', 'Dindigul', 'Palani', 10.4500, 77.5200, 'TOWN', 8),
('Tamil Nadu', 'Dindigul', 'Kodaikanal', 10.2381, 77.4892, 'TOWN', 6),
('Tamil Nadu', 'Theni', 'Bodinayakanur', 10.0110, 77.3490, 'TOWN', 8),
('Tamil Nadu', 'Theni', 'Cumbum', 9.7370, 77.2820, 'TOWN', 8),
('Tamil Nadu', 'Madurai', 'Melur', 10.0320, 78.3380, 'TOWN', 8),
('Tamil Nadu', 'Madurai', 'Thirumangalam', 9.8230, 77.9840, 'TOWN', 8),
('Tamil Nadu', 'Sivaganga', 'Karaikudi', 10.0730, 78.7800, 'TOWN', 10),
('Tamil Nadu', 'Ramanathapuram', 'Rameswaram', 9.2876, 79.3129, 'TOWN', 6),
('Tamil Nadu', 'Ramanathapuram', 'Paramakudi', 9.5440, 78.5910, 'TOWN', 8),
('Tamil Nadu', 'Virudhunagar', 'Sivakasi', 9.4530, 77.8020, 'TOWN', 8),
('Tamil Nadu', 'Virudhunagar', 'Rajapalayam', 9.4510, 77.5540, 'TOWN', 8),
('Tamil Nadu', 'Virudhunagar', 'Aruppukkottai', 9.5090, 78.0960, 'TOWN', 8),
('Tamil Nadu', 'Tenkasi', 'Sankarankovil', 9.1720, 77.5440, 'TOWN', 8),
('Tamil Nadu', 'Tirunelveli', 'Ambasamudram', 8.7100, 77.4520, 'TOWN', 8),
('Tamil Nadu', 'Thoothukudi', 'Tiruchendur', 8.4970, 78.1190, 'TOWN', 8),
('Tamil Nadu', 'Thoothukudi', 'Kovilpatti', 9.1710, 77.8690, 'TOWN', 8),
('Tamil Nadu', 'Kanniyakumari', 'Kanyakumari', 8.0883, 77.5385, 'TOWN', 6),
('Tamil Nadu', 'Kanniyakumari', 'Marthandam', 8.3080, 77.2210, 'TOWN', 8),
-- Puducherry
('Puducherry', 'Puducherry', 'Puducherry', 11.9416, 79.8083, 'TOWN', 12),
('Puducherry', 'Karaikal', 'Karaikal', 10.9254, 79.8380, 'TOWN', 8),
-- Karnataka (City fares were first priced for Bengaluru, V25)
('Karnataka', 'Bengaluru Urban', 'Bengaluru', 12.9716, 77.5946, 'METRO', 25);

-- 5. Zones drawn before this migration keep working; one whose name matches a seeded place is
--    linked to it, so a driver who picks that town joins the existing zone instead of a copy.
UPDATE public.cities c
SET place_id = p.id, state = p.state, district = p.district, kind = p.kind
FROM public.places p
WHERE c.place_id IS NULL AND lower(c.name) = lower(p.name);
