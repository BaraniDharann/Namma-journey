-- Idempotency-Key ledger (see IdempotencyFilter).
--
-- One row per (account + method + path, client key). The composite primary key is what makes a
-- duplicate safe: INSERT ... ON CONFLICT DO NOTHING lets exactly one of two simultaneous requests
-- claim the key, on every application instance at once, with no lock held across the request.
--
-- response_body is NULL while the first request is still running (IN_PROGRESS) and holds the
-- answer once it succeeded, so a repeat can be sent the same bytes. Rows older than 24 hours are
-- deleted hourly by JdbcIdempotencyStore.purgeExpired().
CREATE TABLE public.idempotency_keys (
    scope            character varying(512) NOT NULL,
    idem_key         character varying(255) NOT NULL,
    request_hash     character(64)          NOT NULL,
    response_status  integer,
    content_type     character varying(255),
    response_body    bytea,
    created_at       timestamp with time zone NOT NULL DEFAULT now(),
    completed_at     timestamp with time zone,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (scope, idem_key)
);

-- The hourly purge scans by age.
CREATE INDEX idx_idempotency_keys_created ON public.idempotency_keys (created_at);
