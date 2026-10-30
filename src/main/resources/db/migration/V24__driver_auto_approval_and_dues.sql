--
-- V24 - Self-signup drivers approved automatically; drivers pay their commission in the app.
--

--
-- 1. Automatic approval of INDEPENDENT drivers.
--
-- When a self-signup driver confirms their email, auto_approve_at is set a short delay ahead
-- (15 minutes by default). The owner's Telegram group is told at once and can Reject or Hold in
-- that window; otherwise, if the automatic checks pass, the driver is approved when it arrives.
-- NULL means "the owner decides": held, failed a check, a re-upload after a rejection, or an
-- owner-fleet driver (approved when the owner creates them, never queued).
--
ALTER TABLE public.drivers
    ADD COLUMN auto_approve_at timestamp(6) without time zone;

-- Applications already waiting when this ships are first-time ones the owner never looked at
-- (reviewed_at IS NULL): give them the same deadline they would have had, which for most has
-- already passed, so they are approved on the first run if their checks pass.
UPDATE public.drivers
   SET auto_approve_at = COALESCE(submitted_at, now()) + interval '15 minutes'
 WHERE fleet = 'INDEPENDENT'
   AND verification_status = 'UNDER_REVIEW'
   AND email_verified = true
   AND reviewed_at IS NULL;

CREATE INDEX idx_drivers_auto_approve_at
    ON public.drivers (auto_approve_at)
    WHERE auto_approve_at IS NOT NULL;

--
-- 2. Commission a driver says they have paid the owner by UPI, waiting for the owner to check
--    their bank and confirm. Confirming writes the COLLECTION ledger entry (V21); until then
--    the driver's balance is unchanged, because only the owner can see whether money arrived.
--
CREATE TABLE public.driver_dues_payments (
    id bigserial PRIMARY KEY,
    driver_id bigint NOT NULL REFERENCES public.drivers (id),
    amount numeric(12,2) NOT NULL,
    utr character varying(64) NOT NULL,
    status character varying(16) NOT NULL DEFAULT 'PENDING',
    submitted_at timestamp(6) without time zone NOT NULL DEFAULT now(),
    decided_at timestamp(6) without time zone,
    decided_by character varying(255),
    telegram_message_id bigint,
    CONSTRAINT driver_dues_amount_positive CHECK (amount > 0),
    CONSTRAINT driver_dues_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

-- One UTR, one claim: a driver cannot submit the same bank reference twice to have one payment
-- counted twice. A rejected claim frees its UTR, so a typo can be corrected and resubmitted.
CREATE UNIQUE INDEX uq_driver_dues_utr
    ON public.driver_dues_payments (upper(utr))
    WHERE status <> 'REJECTED';

CREATE INDEX idx_driver_dues_driver ON public.driver_dues_payments (driver_id, submitted_at DESC);
CREATE INDEX idx_driver_dues_pending ON public.driver_dues_payments (submitted_at) WHERE status = 'PENDING';
