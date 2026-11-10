-- A travels owner chooses, per car, whether it does City rides or Travel trips (or neither).
--
-- A City car has exactly one driver, picked by the owner; that driver goes Online in the driver
-- app and is offered nearby City rides like an independent driver. The car stays the source of
-- truth: the app copies its type, seats and AC onto the paired driver for City matching and keeps
-- the driver's serves_city true exactly while the pairing lasts.

ALTER TABLE public.vehicles
    ADD COLUMN mode character varying(10) NOT NULL DEFAULT 'TRAVEL'
        CONSTRAINT vehicles_mode_check CHECK (mode IN ('CITY', 'TRAVEL', 'OFF')),
    ADD COLUMN city_driver_id bigint REFERENCES public.drivers (id) ON DELETE SET NULL,
    -- A City car always has its driver; a Travel or Off car has none.
    ADD CONSTRAINT vehicles_city_has_driver CHECK ((mode = 'CITY') = (city_driver_id IS NOT NULL)),
    -- Tempo Travellers are for Trips only.
    ADD CONSTRAINT vehicles_city_not_tempo CHECK (mode <> 'CITY' OR category <> 'TEMPO_TRAVELLER');

-- Cars already switched off keep being off.
UPDATE public.vehicles SET mode = 'OFF' WHERE active = false;

-- A driver drives at most one City car.
CREATE UNIQUE INDEX uq_vehicles_city_driver ON public.vehicles (city_driver_id) WHERE city_driver_id IS NOT NULL;

-- Deleting a paired driver clears the pairing (ON DELETE SET NULL); the car must then leave City
-- mode too, or vehicles_city_has_driver would refuse the delete.
CREATE FUNCTION public.nj_vehicle_driver_gone() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.mode = 'CITY' AND NEW.city_driver_id IS NULL THEN
        NEW.mode := 'OFF';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_vehicle_driver_gone BEFORE UPDATE OF city_driver_id ON public.vehicles
    FOR EACH ROW EXECUTE FUNCTION public.nj_vehicle_driver_gone();

-- Owner-fleet drivers may now take City rides, through a City car.
ALTER TABLE public.drivers DROP CONSTRAINT drivers_fleet_no_city;

-- The money between the platform and a travels owner for the City rides their cars do: the same
-- shape and sign as driver_ledger_entries (positive = the platform owes the owner).
CREATE TABLE public.owner_ledger_entries (
    id bigserial PRIMARY KEY,
    owner_id bigint NOT NULL REFERENCES public.owners (id),
    booking_id uuid REFERENCES public.travel_bookings (id),
    driver_id bigint REFERENCES public.drivers (id) ON DELETE SET NULL,
    vehicle_id bigint REFERENCES public.vehicles (id) ON DELETE SET NULL,
    type character varying(20) NOT NULL
        CONSTRAINT owner_ledger_type_check CHECK (type IN ('RIDE_EARNING', 'CASH_COMMISSION', 'PAYOUT', 'COLLECTION')),
    amount numeric(12, 2) NOT NULL,
    fare numeric(12, 2),
    commission_percent numeric(5, 2),
    commission numeric(12, 2),
    reference character varying(64),
    note character varying(255),
    created_by character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL DEFAULT now()
);

CREATE INDEX idx_owner_ledger_owner ON public.owner_ledger_entries (owner_id, created_at DESC);
-- One earning or cash-commission entry per ride, so a retried completion cannot pay twice.
CREATE UNIQUE INDEX uq_owner_ledger_ride ON public.owner_ledger_entries (booking_id, type)
    WHERE type IN ('RIDE_EARNING', 'CASH_COMMISSION');

-- Row-level security (V28): an owner sees their own entries; the platform owner, who settles
-- them, sees every owner's.
ALTER TABLE public.owner_ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.owner_ledger_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY app_unscoped ON public.owner_ledger_entries USING (current_user <> 'nj_owner_scope')
    WITH CHECK (current_user <> 'nj_owner_scope');
CREATE POLICY owner_scoped ON public.owner_ledger_entries TO nj_owner_scope
    USING (public.nj_owner_visible(owner_id) OR current_setting('nj.platform', true) = 'on')
    WITH CHECK (public.nj_owner_visible(owner_id) OR current_setting('nj.platform', true) = 'on');
