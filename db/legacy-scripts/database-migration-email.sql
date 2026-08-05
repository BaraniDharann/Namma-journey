-- Migration script to change from phone OTP to email OTP
-- Run this script to update your existing database

-- Step 1: Update users table
ALTER TABLE users DROP COLUMN IF EXISTS phone;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- Step 2: Update otps table
ALTER TABLE otps DROP COLUMN IF EXISTS phone;
ALTER TABLE otps ADD COLUMN IF NOT EXISTS email VARCHAR(255) NOT NULL;

-- Step 3: Create index for better performance
CREATE INDEX IF NOT EXISTS idx_otps_email ON otps(email);
CREATE INDEX IF NOT EXISTS idx_otps_expiry ON otps(expiry_time);

-- Step 4: Clean up old data (optional)
-- TRUNCATE TABLE otps;
-- TRUNCATE TABLE users;

COMMIT;
