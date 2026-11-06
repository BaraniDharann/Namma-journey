-- Where a travels owner runs trips, chosen at signup from a search over India's states and
-- cities: INDIA, STATE:<state> or CITY:<state>:<city> (ServiceAreas). Owner signup used to offer
-- only the admin's City-ride zones (cities), which is empty until the admin draws one, so owners
-- had nothing to pick. owner_cities stays for owners who chose zones before.
CREATE TABLE public.owner_service_areas (
    owner_id bigint NOT NULL REFERENCES public.owners (id) ON DELETE CASCADE,
    area varchar(160) NOT NULL,
    PRIMARY KEY (owner_id, area)
);
