-- Identity documents are checked before a login is created (IdentityDocumentChecker).

-- What the check found on a driver's Aadhaar. PASS: UIDAI's signature verified and the number and
-- name match - City-ride drivers are approved automatically only then. REVIEW: nothing wrong found
-- but not proven (no UIDAI certificate configured), so a person decides. Rows from before this
-- migration were never checked and stay NULL, which also means "a person decides".
ALTER TABLE public.drivers
    ADD COLUMN doc_check character varying(10),
    ADD COLUMN doc_check_notes character varying(500);

-- One Aadhaar, one owner account. Keyed hash (as pan_hash), so the full number is still never stored.
-- Existing owners have only the last 4 digits on file and stay NULL until they resubmit.
ALTER TABLE public.owners
    ADD COLUMN aadhaar_hash character varying(64);
CREATE UNIQUE INDEX owners_aadhaar_hash_key ON public.owners (aadhaar_hash);

-- What the automatic checks found on an owner's Aadhaar and PAN, shown to the admin beside the
-- manual checklist. Advisory: owners are still approved by the admin.
ALTER TABLE public.owners
    ADD COLUMN doc_check_notes character varying(1000);
