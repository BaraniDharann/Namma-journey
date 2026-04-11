# Travel Booking Platform - Backend API (Email OTP Version)

## Overview
Production-ready backend for an Indian car travel booking platform with **Email OTP Authentication** using SendGrid.

## Tech Stack
- Java 17
- Spring Boot 3.4.1
- Spring Security
- JWT Authentication
- PostgreSQL
- SendGrid Email Service
- JPA (Hibernate)
- Maven
- REST APIs

## Roles
1. **USER** - Customers booking travel (Email/Google Login)
2. **DRIVER** - Drivers providing service (Mobile + Password)
3. **OWNER** - Platform administrators (Email + Password)

---

## Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL 14+
- SendGrid Account (Free tier available)

### Database Setup
```sql
CREATE DATABASE namma_journey;
```

### Configuration
Update `.env` file:
```env
DB_URL=jdbc:postgresql://localhost:5432/namma_journey
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
SERVER_PORT=8080

# SendGrid Configuration
SENDGRID_API_KEY=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
SENDGRID_FROM_EMAIL=noreply@nammajourney.com
SENDGRID_FROM_NAME=Namma Journey
OTP_EXPIRY_MINUTES=5
```

### Run Application
```bash
mvn clean install
mvn spring-boot:run
```

Server runs on: `http://localhost:8080`

---

## API Endpoints

### 1. SEND OTP (Email)
**Endpoint:** `POST /api/auth/otp/send`

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

**Response (200 OK):**
```json
{
  "message": "OTP sent successfully to email",
  "success": true
}
```

---

### 2. USER SIGNUP (Email-based)
**Endpoint:** `POST /api/auth/user/signup`

**Request Body:**
```json
{
  "email": "rajesh@example.com",
  "name": "Rajesh Kumar"
}
```

**Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

---

### 3. USER LOGIN (Email OTP)
**Endpoint:** `POST /api/auth/user/login`

**Request Body:**
```json
{
  "loginType": "EMAIL",
  "email": "rajesh@example.com",
  "otp": "123456"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

---

### 4. USER LOGIN (Google OAuth)
**Endpoint:** `POST /api/auth/user/login`

**Request Body:**
```json
{
  "loginType": "GOOGLE",
  "token": "google_oauth_token_here"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

---

### 5. DRIVER SIGNUP
**Endpoint:** `POST /api/auth/driver/signup`

**Request Body:**
```json
{
  "name": "Suresh Sharma",
  "mobile": "9123456789",
  "password": "driver@123",
  "licenseNumber": "DL1420110012345",
  "aadhaarNumber": "123456789012"
}
```

**Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "message": "Authentication successful"
}
```

---

### 6. DRIVER LOGIN
**Endpoint:** `POST /api/auth/driver/login`

**Request Body:**
```json
{
  "mobile": "9123456789",
  "password": "driver@123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "message": "Authentication successful"
}
```

---

### 7. OWNER LOGIN
**Endpoint:** `POST /api/auth/owner/login`

**Request Body:**
```json
{
  "email": "admin@travelplatform.com",
  "password": "owner@123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_OWNER",
  "userId": 1,
  "message": "Authentication successful"
}
```

---

## Authentication Flow

### Email OTP Login Flow
1. User enters email
2. Call `POST /api/auth/otp/send` with email
3. User receives OTP via email (6-digit code)
4. User enters OTP
5. Call `POST /api/auth/user/login` with email + OTP
6. Receive JWT token

### Google OAuth Login Flow
1. User clicks "Login with Google"
2. Get Google OAuth token from frontend
3. Call `POST /api/auth/user/login` with token
4. Receive JWT token

### Using JWT Token
Include token in subsequent requests:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## SendGrid Setup

### 1. Create SendGrid Account
- Go to https://sendgrid.com
- Sign up for free account (100 emails/day)

### 2. Create API Key
- Navigate to Settings > API Keys
- Create new API key with "Mail Send" permissions
- Copy the API key to `.env` file

### 3. Verify Sender Email
- Go to Settings > Sender Authentication
- Verify your sender email address
- Use verified email in `SENDGRID_FROM_EMAIL`

### 4. Email Template
The OTP email includes:
- Professional HTML formatting
- Large, bold OTP code
- Expiry time information
- Security notice

---

## Database Schema

### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    login_type VARCHAR(20) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### OTPs Table
```sql
CREATE TABLE otps (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp VARCHAR(6) NOT NULL,
    expiry_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE
);
```

### Drivers Table
```sql
CREATE TABLE drivers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mobile VARCHAR(10) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    license_number VARCHAR(50) UNIQUE NOT NULL,
    aadhaar_number VARCHAR(12) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

### Owners Table
```sql
CREATE TABLE owners (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

---

## Testing with cURL

### Send OTP
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
```

### User Signup
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"rajesh@example.com","name":"Rajesh Kumar"}'
```

### User Login with OTP
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"EMAIL","email":"rajesh@example.com","otp":"123456"}'
```

### Authenticated Request
```bash
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Error Responses

### Validation Error (400)
```json
{
  "email": "Invalid email format",
  "name": "Name is required"
}
```

### Authentication Error (401)
```json
{
  "error": "Invalid or expired OTP"
}
```

### Resource Not Found (404)
```json
{
  "error": "User not found with email: user@example.com"
}
```

### Duplicate Entry (400)
```json
{
  "error": "Email already registered"
}
```

---

## Security Features

✅ JWT-based stateless authentication  
✅ BCrypt password encryption  
✅ Role-based access control (RBAC)  
✅ Token expiration (24 hours)  
✅ Email OTP verification (5 minutes expiry)  
✅ SendGrid secure email delivery  
✅ Input validation  
✅ Global exception handling  
✅ CSRF protection disabled (stateless API)  

---

## Migration from Phone OTP

If you're migrating from phone-based OTP:

1. Run the migration script:
```bash
psql -U postgres -d namma_journey -f database-migration-email.sql
```

2. Update `.env` with SendGrid credentials
3. Rebuild and restart application
4. Update frontend to use email instead of phone

---

## License
Proprietary - Travel Booking Platform

## Contact
For support, contact: support@nammajourney.com
