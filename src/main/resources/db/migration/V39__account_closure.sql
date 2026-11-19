-- Self-service account closure.
--
-- Travellers, drivers and owners can close their own account from the Profile screen. Bookings,
-- payments, ledgers and reviews keep pointing at the row (tax and payout history must survive),
-- so the row stays: its personal details are wiped, its password is replaced with an unusable
-- one, its tokens are revoked, and closed_at records when it happened.
ALTER TABLE public.users ADD COLUMN closed_at timestamp;
ALTER TABLE public.owners ADD COLUMN closed_at timestamp;
ALTER TABLE public.drivers ADD COLUMN closed_at timestamp;
