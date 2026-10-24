-- Uploads stored in Postgres (app.storage.type=db).
--
-- Hosts like Render's free plan have no persistent disk: every deploy deleted the uploaded driver
-- licences, Aadhaar and RC scans, and trip photos. Kept here they live as long as the database,
-- need no extra service, and are backed up with it.
--
-- data is AES-256-GCM ciphertext (FieldEncryption.encryptBytes) of an already-compressed JPEG
-- (ImageCompressor, typically 150-600 KB): an identity document is never readable from a
-- database dump or backup without APP_ENCRYPTION_KEY.
CREATE TABLE public.stored_files (
    file_key     character varying(512) PRIMARY KEY,
    content_type character varying(100) NOT NULL,
    size_bytes   integer                NOT NULL,
    data         bytea                  NOT NULL,
    created_at   timestamp(6) without time zone NOT NULL DEFAULT now()
);
