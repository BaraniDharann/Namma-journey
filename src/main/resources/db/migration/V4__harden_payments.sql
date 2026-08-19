-- Payment integrity hardening.
--
-- 1. payments.booking_id had no uniqueness guarantee, while the code reads it through
--    findByBookingId(...) -> Optional<Payment>. Two concurrent initiate-payment calls for the
--    same booking therefore both saw "no existing payment" and both inserted a row. From that
--    point on every read of that booking's payment threw NonUniqueResultException, which left
--    the booking permanently unpayable. The unique index below makes the second insert fail
--    cleanly instead, and the service now translates that into a retry.
--
-- 2. Nothing recorded WHO marked a payment verified. Verification is a manual owner action
--    that moves money in the real world, so it needs an actor on the row.

-- Collapse any duplicates that already exist before the constraint can be applied. A VERIFIED
-- row always wins; otherwise the most recently created one does. Ordering is fully determined
-- (id breaks any remaining tie) so the outcome does not depend on physical row order.
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY booking_id
               ORDER BY CASE WHEN status = 'VERIFIED' THEN 0 ELSE 1 END,
                        created_at DESC,
                        id
           ) AS rn
    FROM public.payments
)
DELETE FROM public.payments
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

ALTER TABLE public.payments
    ADD CONSTRAINT uq_payments_booking_id UNIQUE (booking_id);

-- Principal (JWT subject) of the owner who verified the payment. Null for rows that are still
-- pending, and for anything verified before this column existed.
ALTER TABLE public.payments
    ADD COLUMN verified_by character varying(255);

-- getPendingPayments() scans by status on every owner dashboard load.
CREATE INDEX idx_payments_status ON public.payments (status);
