-- Add password column to users table
ALTER TABLE users ADD COLUMN password VARCHAR(255);

-- Update existing users with NULL password (for Google OAuth users)
UPDATE users SET password = NULL WHERE login_type = 'GOOGLE';
