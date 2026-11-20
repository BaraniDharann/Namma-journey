-- The driver's "End ride" QR is now a Razorpay Payment Link instead of a plain upi:// link to a
-- fixed UPI ID. Razorpay confirms the payment by webhook (payment_link.paid), so no owner has to
-- check a bank statement and tap verify.
--
-- razorpay_link_id (plink_...) is how that webhook finds the payment: unique where set.
-- razorpay_link_url is the short_url the QR encodes, kept so an open link is shown again rather
-- than a new one minted on every tap.

ALTER TABLE public.payments
    ADD COLUMN razorpay_link_id character varying(64),
    ADD COLUMN razorpay_link_url character varying(255);

CREATE UNIQUE INDEX uq_payments_razorpay_link_id
    ON public.payments (razorpay_link_id)
    WHERE razorpay_link_id IS NOT NULL;
