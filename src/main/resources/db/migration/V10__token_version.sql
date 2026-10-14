-- Server-side token revocation. Each login token carries the account's token_version; password
-- resets and logout bump it, and JwtAuthenticationFilter refuses tokens with an older value. Before
-- this a stolen token stayed valid for its full lifetime even after the victim reset their password.
ALTER TABLE public.users   ADD COLUMN token_version bigint NOT NULL DEFAULT 0;
ALTER TABLE public.drivers ADD COLUMN token_version bigint NOT NULL DEFAULT 0;
ALTER TABLE public.owners  ADD COLUMN token_version bigint NOT NULL DEFAULT 0;
