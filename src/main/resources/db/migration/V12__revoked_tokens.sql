-- Per-session logout. Each JWT carries a unique id (jti); logging out records that one id here
-- until the token would have expired anyway, so the session ends on THIS device only. Bumping
-- the account's token_version (V10) is kept for password resets, which should end every session.
CREATE TABLE public.revoked_tokens (
    jti        character varying(64)    NOT NULL PRIMARY KEY,
    expires_at timestamp with time zone NOT NULL
);

-- Rows are useless once their token has expired; the hourly purge scans by this.
CREATE INDEX idx_revoked_tokens_expires ON public.revoked_tokens (expires_at);
