-- Fix: Make phone column nullable for Google OAuth users
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
