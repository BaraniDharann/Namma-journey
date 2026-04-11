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
-- Password: owner@123 (BCrypt encrypted)
INSERT INTO owners (email, password, role, created_at) 
VALUES (
    'admin@travelplatform.com', 
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ROLE_OWNER',
    CURRENT_TIMESTAMP
) ON CONFLICT (email) DO NOTHING;

-- Insert Sample Driver for Testing (Optional)
-- Password: driver@123 (BCrypt encrypted)
INSERT INTO drivers (name, mobile, password, license_number, aadhaar_number, role, status, created_at)
VALUES (
    'Test Driver',
    '9999999999',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'TEST123456789',
    '999999999999',
    'ROLE_DRIVER',
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (mobile) DO NOTHING;

-- Verify Tables Created
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;

-- Display Owner Account
SELECT id, email, role, created_at FROM owners;

COMMIT;
