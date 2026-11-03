-- Each travels owner sees only their own rows, enforced by Postgres itself (row-level security).
--
-- While an owner's request is being served, the app switches that request's connection to the role
-- nj_owner_scope and sets nj.owner_id (and nj.platform for NJO-0001), then resets both before the
-- connection goes back to the pool (OwnerScopedDataSource). The switch is what makes the rule
-- apply: the app's own login may be a superuser, and superusers bypass row-level security even
-- when it is forced. Every other connection (customers, drivers, admins, background jobs, Flyway)
-- is not nj_owner_scope and works as before.
--
-- The app's queries also filter by owner explicitly; this is the backstop for any query that
-- forgets, including lookups by id and hand-written SQL.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nj_owner_scope') THEN
        CREATE ROLE nj_owner_scope NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO nj_owner_scope;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nj_owner_scope;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO nj_owner_scope;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nj_owner_scope;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO nj_owner_scope;
-- Lets the app's login switch to the role (a superuser already can).
GRANT nj_owner_scope TO CURRENT_USER;

-- True for the scoped owner's own rows, and for platform rows (no owner) when the owner is the
-- platform owner. False when no owner is set: a scoped connection without a setting sees nothing.
CREATE FUNCTION public.nj_owner_visible(row_owner bigint) RETURNS boolean
    LANGUAGE sql STABLE AS $$
    SELECT COALESCE(current_setting('nj.owner_id', true), '') <> ''
       AND (row_owner = NULLIF(current_setting('nj.owner_id', true), '')::bigint
            OR (row_owner IS NULL AND current_setting('nj.platform', true) = 'on'))
$$;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['drivers', 'vehicles', 'owner_trip_rates', 'travel_bookings', 'payments',
                             'reviews', 'trip_driver_photos', 'travel_packages', 'package_bookings'] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY app_unscoped ON public.%I USING (current_user <> ''nj_owner_scope'') '
                       || 'WITH CHECK (current_user <> ''nj_owner_scope'')', t);
        EXECUTE format('CREATE POLICY owner_scoped ON public.%I TO nj_owner_scope '
                       || 'USING (public.nj_owner_visible(owner_id)) WITH CHECK (public.nj_owner_visible(owner_id))', t);
    END LOOP;
END $$;

-- A check belongs to whoever can see its driver (the drivers rule above applies inside EXISTS).
ALTER TABLE public.driver_verification_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.driver_verification_checks FORCE ROW LEVEL SECURITY;
CREATE POLICY app_unscoped ON public.driver_verification_checks
    USING (current_user <> 'nj_owner_scope') WITH CHECK (current_user <> 'nj_owner_scope');
CREATE POLICY owner_scoped ON public.driver_verification_checks TO nj_owner_scope
    USING (EXISTS (SELECT 1 FROM public.drivers d WHERE d.id = driver_id))
    WITH CHECK (EXISTS (SELECT 1 FROM public.drivers d WHERE d.id = driver_id));
