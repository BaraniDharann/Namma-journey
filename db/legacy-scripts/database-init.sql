-- Travel Booking Platform - Database Initialization Script

-- Create Database
CREATE DATABASE travel_booking_db;

-- Connect to database
\c travel_booking_db;

-- Users Table (Auto-created by Hibernate, but schema provided for reference)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(10) UNIQUE NOT NULL,
    login_type VARCHAR(20) NOT NULL CHECK (login_type IN ('GOOGLE', 'PHONE')),
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Drivers Table
CREATE TABLE IF NOT EXISTS drivers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mobile VARCHAR(10) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    license_number VARCHAR(50) UNIQUE NOT NULL,
    aadhaar_number VARCHAR(12) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_DRIVER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Owners Table
CREATE TABLE IF NOT EXISTS owners (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_OWNER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create Indexes for Performance
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_drivers_mobile ON drivers(mobile);
CREATE INDEX idx_owners_email ON owners(email);

-- Insert Default Owner Account
--
-- REMOVED. This block seeded an owner account -- full control of the platform -- with a BCrypt
-- hash of a password that was printed in this very file. Every deployment that ran this script
-- ended up with the same known administrator credential.
--
-- Create the first owner through the application instead, which does not hand out a shared
-- secret. Set OWNER_BOOTSTRAP_SECRET, then:
--
--   POST /api/auth/owner/create-admin   (header: X-Bootstrap-Secret)
--
-- and clear OWNER_BOOTSTRAP_SECRET again afterwards. See DEPLOYMENT_CHECKLIST.md.

-- Insert Sample Driver for Testing (Optional)
--
-- REMOVED for the same reason as the owner block above: it seeded a login with a password
-- published alongside it. Create drivers as the owner through POST /api/owner/drivers, which
-- generates a random password and emails it to the driver.

-- Verify Tables Created
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Display Owner Account
SELECT id, email, role, created_at FROM owners;

COMMIT;
