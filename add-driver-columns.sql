-- Add missing columns to drivers table
ALTER TABLE drivers 
ADD COLUMN IF NOT EXISTS first_login BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
