-- Online payments through Razorpay Checkout.
--
-- 1. RAZORPAY joins UPI and CASH as a payment method. The baseline check constraint lists the
--    allowed values, so it is replaced rather than left to reject every online payment.
--
-- 2. razorpay_order_id is the order the passenger pays against. The webhook finds the payment
--    by it, so it is unique and indexed.
--
-- 3. razorpay_payment_id is the charge that settled the order. Unique, so the same Razorpay
--    payment can never be booked against two fares.

ALTER TABLE public.payments DROP CONSTRAINT payments_payment_method_check;
ALTER TABLE public.payments
    ADD CONSTRAINT payments_payment_method_check
    CHECK (payment_method IN ('UPI', 'CASH', 'RAZORPAY'));

ALTER TABLE public.payments
    ADD COLUMN razorpay_order_id character varying(64),
    ADD COLUMN razorpay_payment_id character varying(64);

ALTER TABLE public.payments
    ADD CONSTRAINT uq_payments_razorpay_order_id UNIQUE (razorpay_order_id);
ALTER TABLE public.payments
    ADD CONSTRAINT uq_payments_razorpay_payment_id UNIQUE (razorpay_payment_id);
