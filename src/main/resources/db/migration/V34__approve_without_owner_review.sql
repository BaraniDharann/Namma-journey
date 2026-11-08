-- Only travels owners are reviewed (by the admin). Once an owner is approved, the buses they add
-- go live without a second review, and City-ride drivers who sign up themselves are approved
-- automatically instead of waiting for an owner.

-- Buses already waiting for the admin go live now (the app checked their seats, papers and photo
-- when they were submitted).
UPDATE public.buses
   SET status = 'APPROVED', reviewed_at = now(), rejection_reason = NULL
 WHERE status = 'PENDING';

-- City-ride drivers still waiting (including those held for an owner's look) are picked up by the
-- automatic approval on its next round, which approves them and tells each driver.
UPDATE public.drivers
   SET auto_approve_at = now()
 WHERE fleet = 'INDEPENDENT'
   AND verification_status = 'UNDER_REVIEW'
   AND email_verified = true;
