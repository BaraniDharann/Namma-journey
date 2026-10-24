-- Trip advances: a Trip is held for the passenger until 20 % of the fare is paid online.
--
-- 1. A booking can now have two payments: the ADVANCE and the FARE (the balance, or the whole
--    fare when no advance applies). Every existing row is a FARE payment. The one-per-booking
--    constraint from V4 becomes one-per-booking-per-purpose, which keeps its original guarantee
--    (two concurrent "pay" calls cannot create two fare rows) for each purpose.
ALTER TABLE public.payments
    ADD COLUMN purpose character varying(16) NOT NULL DEFAULT 'FARE',
    ADD CONSTRAINT payments_purpose_check CHECK (purpose IN ('FARE', 'ADVANCE'));

ALTER TABLE public.payments DROP CONSTRAINT uq_payments_booking_id;
ALTER TABLE public.payments
    ADD CONSTRAINT uq_payments_booking_purpose UNIQUE (booking_id, purpose);

-- 2. On the booking: what advance is required (null = none: City rides and every booking made
--    before this migration), when it was paid, and until when the unpaid hold lasts.
ALTER TABLE public.travel_bookings
    ADD COLUMN advance_amount numeric(12,2),
    ADD COLUMN advance_paid_at timestamp(6) without time zone,
    ADD COLUMN hold_expires_at timestamp(6) without time zone,
    ADD CONSTRAINT travel_bookings_advance_positive CHECK (advance_amount IS NULL OR advance_amount > 0);

-- The hold sweeper's query: unpaid holds, by expiry. Tiny, because a hold lives 30 minutes.
CREATE INDEX idx_travel_bookings_advance_hold ON public.travel_bookings (hold_expires_at)
    WHERE advance_paid_at IS NULL AND hold_expires_at IS NOT NULL AND status = 'PENDING';
