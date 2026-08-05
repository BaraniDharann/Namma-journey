-- Migration: Add Driver Verification and Photo Fields
-- Date: 2024
-- Description: Adds email verification, first login tracking, and photo fields to drivers table

-- Add new columns to drivers table
ALTER TABLE drivers 
ADD COLUMN IF NOT EXISTS first_login BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;

-- Update existing drivers to have email_verified = false
UPDATE drivers 
SET email_verified = false, first_login = true 
WHERE email_verified IS NULL OR first_login IS NULL;

-- Add comments to columns
COMMENT ON COLUMN drivers.first_login IS 'Indicates if driver needs to change password on first login';
COMMENT ON COLUMN drivers.email_verified IS 'Indicates if driver email has been verified via OTP';
COMMENT ON COLUMN drivers.photo IS 'URL to driver profile photo';
COMMENT ON COLUMN drivers.license_photo IS 'URL to driver license photo';
COMMENT ON COLUMN drivers.aadhaar_photo IS 'URL to driver Aadhaar card photo';

-- Create index for email lookups
CREATE INDEX IF NOT EXISTS idx_drivers_email ON drivers(email);
CREATE INDEX IF NOT EXISTS idx_drivers_email_verified ON drivers(email_verified);

-- Verify migration
SELECT 
    column_name, 
    data_type, 
    is_nullable, 
    column_default
FROM information_schema.columns
WHERE table_name = 'drivers'
AND column_name IN ('first_login', 'email_verified', 'photo', 'license_photo', 'aadhaar_photo')
ORDER BY ordinal_position;

-- Sample query to check driver verification status
-- SELECT id, name, email, email_verified, first_login, created_at 
-- FROM drivers 
-- ORDER BY created_at DESC;
