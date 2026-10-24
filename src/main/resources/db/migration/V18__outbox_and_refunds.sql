-- 1. Transactional outbox.
--
--    Emails (and now refunds) were handed to an in-memory thread pool after commit. A restart,
--    deploy or crash between commit and send lost them for good, and a failed send was only
--    logged. A row here is written in the same transaction as the change it describes, so it
--    exists exactly when that change does, and the relay keeps retrying it until it is done.
--
--    status: PENDING (waiting or retrying), DONE (handled, or handed to SQS, which retries from
--    there), DEAD (gave up after max attempts - needs a person).
CREATE TABLE public.outbox_events (
    id           bigserial PRIMARY KEY,
    type         character varying(64)  NOT NULL,
    payload      text                   NOT NULL,
    status       character varying(16)  NOT NULL DEFAULT 'PENDING',
    attempts     integer                NOT NULL DEFAULT 0,
    available_at timestamp(6) without time zone NOT NULL DEFAULT now(),
    created_at   timestamp(6) without time zone NOT NULL DEFAULT now(),
    processed_at timestamp(6) without time zone,
    last_error   character varying(1000),
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'DONE', 'DEAD'))
);

-- The relay's only query: due PENDING rows, oldest first. Partial, so DONE history costs nothing.
CREATE INDEX idx_outbox_due ON public.outbox_events (available_at, id) WHERE status = 'PENDING';

-- 2. Refunds for online payments.
--
--    A passenger who cancels a prepaid trip before it starts gets the fare back automatically.
--    REFUND_PENDING: refund requested from Razorpay, money not yet returned.
--    REFUNDED: Razorpay confirmed the refund (refund.processed webhook).
--    Neither is VERIFIED, so a refunded fare leaves owner revenue.
ALTER TABLE public.payments DROP CONSTRAINT payments_status_check;
ALTER TABLE public.payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'REFUND_PENDING', 'REFUNDED'));

ALTER TABLE public.payments
    ADD COLUMN razorpay_refund_id character varying(64),
    ADD COLUMN refunded_at timestamp(6) without time zone;
