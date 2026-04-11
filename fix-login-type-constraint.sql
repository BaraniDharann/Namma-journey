-- Fix login_type constraint to allow EMAIL
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_login_type_check;
ALTER TABLE users ADD CONSTRAINT users_login_type_check CHECK (login_type IN ('GOOGLE', 'PHONE', 'EMAIL'));
