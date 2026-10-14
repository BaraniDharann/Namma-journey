-- Wrong guesses per OTP. Without a counter a 6-digit code could be guessed for its whole
-- 5-minute life; OtpService now kills a code after 5 misses.
ALTER TABLE public.otps ADD COLUMN attempts integer NOT NULL DEFAULT 0;
