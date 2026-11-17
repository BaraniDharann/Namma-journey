-- Aadhaar and PAN numbers are no longer stored readable. Each is kept as a bcrypt hash (one-way),
-- a keyed HMAC for "already registered?" lookups (bcrypt's salt makes it unsearchable), and the
-- last few characters for a masked display. See IdNumberHash.
--
-- Existing rows are converted at startup by IdNumberBackfill, which needs the app's encryption key
-- for the HMAC and so cannot run in SQL. It empties drivers.aadhaar_number and owners.pan_number as
-- it goes; a later migration drops those two columns once every environment has been converted.

ALTER TABLE public.drivers
    ADD COLUMN aadhaar_hash character varying(64),
    ADD COLUMN aadhaar_bcrypt character varying(60),
    ADD COLUMN aadhaar_last4 character varying(4);
CREATE UNIQUE INDEX drivers_aadhaar_hash_key ON public.drivers (aadhaar_hash);

-- The readable column may now be empty, and its uniqueness moves to aadhaar_hash.
ALTER TABLE public.drivers ALTER COLUMN aadhaar_number DROP NOT NULL;
ALTER TABLE public.drivers DROP CONSTRAINT IF EXISTS uk34x3gwter5i46jdm3tjmw7jp3;

-- The last 4 digits need no key, so they are filled here.
UPDATE public.drivers
   SET aadhaar_last4 = right(regexp_replace(aadhaar_number, '\D', '', 'g'), 4)
 WHERE aadhaar_number IS NOT NULL;

ALTER TABLE public.owners
    ADD COLUMN pan_bcrypt character varying(60),
    ADD COLUMN pan_masked character varying(10),
    ADD COLUMN aadhaar_bcrypt character varying(60);
