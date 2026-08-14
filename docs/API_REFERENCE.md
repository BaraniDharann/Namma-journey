# Travel Booking Platform - Backend API

## Overview
Production-ready backend for an Indian car travel booking platform for temple visits, devotional trips, tours, and long-distance travel.

## Tech Stack
- Java 17
- Spring Boot 3.2.0
- Spring Security
- JWT Authentication
- PostgreSQL
- JPA (Hibernate)
- Maven
- REST APIs

## Roles
1. **USER** - Customers booking travel
2. **DRIVER** - Drivers providing service
3. **OWNER** - Platform administrators

---

## Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL 14+

### Database Setup
```sql
CREATE DATABASE travel_booking_db;
```

### Configuration
Update `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/travel_booking_db
    username: your_username
    password: your_password
```

### Run Application
```bash
mvn clean install
mvn spring-boot:run
```

Server runs on: `http://localhost:8080`

---

## API Endpoints

### 1. SEND OTP (Email Verification)
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

**Note:** Mock OTP is `123456` for testing

---

### 2. USER SIGNUP (Email + Password)
**Endpoint:** `POST /api/auth/user/signup`

**Request Body:**
```json
{
  "email": "user@example.com",
  "name": "Rajesh Kumar",
  "otp": "123456",
  "password": "MyPassword@123"
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

**Flow:**
1. User requests OTP via `/api/auth/otp/send`
2. User receives OTP in email
3. User signs up with email, name, OTP, and password
4. Account created with password stored securely

---

### 3. USER LOGIN (Email + Password)
**Endpoint:** `POST /api/auth/user/login`

**Request Body:**
```json
{
  "loginType": "EMAIL",
  "email": "user@example.com",
  "password": "MyPassword@123"
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

**Note:** After signup, users login with email/password only (no OTP required)

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

**Validations:**
- Mobile: 10-digit Indian number starting with 6-9
- Password: Minimum 6 characters
- Aadhaar: 12-digit number
- License, Mobile, Aadhaar must be unique

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

**Note:** Owner accounts must be created directly in database (no signup endpoint)

---

## Creating Owner Account (SQL)

```sql
INSERT INTO owners (email, password, role, created_at) 
VALUES (
  'admin@travelplatform.com', 
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- password: owner@123
  'ROLE_OWNER',
  NOW()
);
```

---

## Authentication Flow

### Using JWT Token
After login/signup, include the token in subsequent requests:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Role-Based Access
- `/api/user/**` - Requires `ROLE_USER`
- `/api/driver/**` - Requires `ROLE_DRIVER`
- `/api/owner/**` - Requires `ROLE_OWNER`
- `/api/auth/**` - Public (no authentication)

---

## Error Responses

### Validation Error (400)
```json
{
  "phone": "Invalid Indian phone number",
  "name": "Name is required"
}
```

### Authentication Error (401)
```json
{
  "error": "Invalid credentials"
}
```

### Resource Not Found (404)
```json
{
  "error": "User not found with phone: 9876543210"
}
```

### Duplicate Entry (400)
```json
{
  "error": "Phone number already registered"
}
```

---

## Project Structure

```
com.travelplatform
├── config
│   ├── SecurityConfig.java          # Spring Security configuration
│   ├── JwtAuthenticationFilter.java # JWT token validation filter
│   └── JwtUtil.java                 # JWT utility methods
├── controller
│   └── AuthController.java          # Authentication endpoints
├── service
│   ├── AuthService.java             # Main auth service facade
│   ├── UserAuthService.java         # User authentication logic
│   ├── DriverAuthService.java       # Driver authentication logic
│   └── OwnerAuthService.java        # Owner authentication logic
├── repository
│   ├── UserRepository.java          # User data access
│   ├── DriverRepository.java        # Driver data access
│   └── OwnerRepository.java         # Owner data access
├── entity
│   ├── User.java                    # User entity
│   ├── Driver.java                  # Driver entity
│   └── Owner.java                   # Owner entity
├── dto
│   ├── UserSignupRequest.java       # User signup DTO
│   ├── UserLoginRequest.java        # User login DTO
│   ├── DriverSignupRequest.java     # Driver signup DTO
│   ├── DriverLoginRequest.java      # Driver login DTO
│   ├── OwnerLoginRequest.java       # Owner login DTO
│   └── AuthResponse.java            # Authentication response DTO
├── exception
│   ├── GlobalExceptionHandler.java  # Centralized exception handling
│   └── ResourceNotFoundException.java
└── TravelPlatformApplication.java   # Main application class
```

---

## Security Features

✅ JWT-based stateless authentication  
✅ BCrypt password encryption  
✅ Role-based access control (RBAC)  
✅ Token expiration (24 hours)  
✅ Input validation  
✅ Global exception handling  
✅ CSRF protection disabled (stateless API)  

---

## Testing with cURL

### User Signup
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'

curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","name":"Rajesh Kumar","otp":"123456","password":"MyPassword@123"}'
```

### User Login
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"EMAIL","email":"user@example.com","password":"MyPassword@123"}'
```

### Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9123456789","password":"driver@123"}'
```

### Authenticated Request
```bash
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Database Schema

### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(10) UNIQUE,
    password VARCHAR(255),
    login_type VARCHAR(20) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
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

## Next Phase Features (Not Implemented)
- Vehicle management
- Booking system
- Payment integration
- Trip tracking
- Driver-vehicle assignment
- User dashboard
- Admin panel

---

## License
Proprietary - Travel Booking Platform

## Contact
For support, contact: support@travelplatform.com


---

## 🆕 Driver Creation Workflow (Owner-Managed)

### Overview
Owners can create driver accounts with email verification, photo uploads, and secure credential management.

### Workflow Steps

**1. Owner Creates Driver** → **2. OTP Sent to Driver Email** → **3. Owner Verifies OTP** → **4. Credentials Sent to Driver** → **5. Driver Changes Password**

### API Endpoints

### 8. CREATE DRIVER (Owner Only)
**Endpoint:** `POST /api/owner/drivers`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request Body:**
```json
{
  "name": "Suresh Sharma",
  "mobile": "9123456789",
  "email": "suresh.sharma@example.com",
  "licenseNumber": "DL1420110012345",
  "aadhaarNumber": "123456789012",
  "photo": "https://example.com/photos/driver.jpg",
  "licensePhoto": "https://example.com/photos/license.jpg",
  "aadhaarPhoto": "https://example.com/photos/aadhaar.jpg"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Suresh Sharma",
  "email": "suresh.sharma@example.com",
  "mobile": "9123456789",
  "message": "Driver created successfully. OTP sent to driver's email for verification."
}
```

---

### 9. VERIFY DRIVER EMAIL (Owner Only)
**Endpoint:** `POST /api/owner/drivers/verify`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request Body:**
```json
{
  "email": "suresh.sharma@example.com",
  "otp": "123456"
}
```

**Response (200 OK):**
```json
{
  "message": "Driver email verified successfully. Login credentials sent to driver's email.",
  "success": true
}
```

---

### 10. DRIVER CHANGE PASSWORD
**Endpoint:** `POST /api/auth/driver/change-password`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "oldPassword": "aB3#xY9@kL",
  "newPassword": "MyNewPassword@123"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

---

### Email Configuration (SendGrid)

Add to `.env` file:
```env
SENDGRID_API_KEY=SG.your_api_key_here
SENDGRID_FROM_EMAIL=noreply@nammajourney.com
SENDGRID_FROM_NAME=Namma Journey
OTP_EXPIRY_MINUTES=5
```

### Database Migration

Run migration to add new columns:
```bash
psql -U postgres -d travel_booking_db -f database-migration-driver-verification.sql
```

Or manually:
```sql
ALTER TABLE drivers 
ADD COLUMN first_login BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
```

---

### Complete Documentation

📚 **Detailed Guides:**
- **[Driver Creation Workflow](guides/DRIVER_CREATION_WORKFLOW.md)** - Complete workflow documentation
- **[Setup Guide](guides/DRIVER_SETUP_GUIDE.md)** - Step-by-step setup instructions
- **[Quick Reference](api/DRIVER_QUICK_REFERENCE.md)** - Quick command reference

📦 **Testing:**
- **Postman Collection:** `Driver-Creation-Workflow.postman_collection.json`
- **Database Migration:** `database-migration-driver-verification.sql`

---

### Key Features

✅ Email verification with OTP  
✅ Photo document uploads (Driver, License, Aadhaar)  
✅ Secure password generation  
✅ First login detection  
✅ Forced password change  
✅ SendGrid email integration  
✅ Role-based access control  

---

## 🆕 Review & Rating System

### Overview
Users can rate completed journeys and provide feedback about driver attitude. Owners can view all customer reviews to improve business operations.

### API Endpoints

### 11. SUBMIT REVIEW (User Only)
**Endpoint:** `POST /api/user/{userId}/bookings/{bookingId}/reviews`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body:**
```json
{
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous."
}
```

**Response (201 Created):**
```json
{
  "id": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "driverName": "Suresh Sharma",
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous.",
  "createdAt": "2024-01-15T10:30:00"
}
```

---

### 12. VIEW ALL REVIEWS (Owner Only)
**Endpoint:** `GET /api/owner/reviews`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
[
  {
    "id": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "userName": "Rajesh Kumar",
    "driverName": "Suresh Sharma",
    "rating": 5,
    "feedback": "Excellent service! Driver was very professional.",
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

### Review Features

✅ User ratings (1-5 stars)  
✅ Written feedback (up to 1000 characters)  
✅ Only for completed bookings  
✅ One review per booking  
✅ Owner dashboard for all reviews  
✅ Sorted by date (newest first)  

📚 **Full Documentation:** [Review & Rating API](api/REVIEW_RATING_API.md)

---

## 🆕 Payment System

### Overview
Dual payment system supporting UPI and cash payments with owner verification workflow.

### Payment Methods

#### 1. UPI Payment (Digital)
- User gets UPI deep link
- Auto-opens Google Pay / PhonePe / Paytm
- Direct payment to owner UPI: `sarasarathy86@okicici`
- Driver calls owner to verify
- Owner verifies → Trip completed

#### 2. Cash Payment (Physical)
- Driver collects cash from user
- Driver marks cash received in system
- **Payment automatically verified**
- **Trip automatically completed**

### API Endpoints

### 13. INITIATE PAYMENT (User Only)
**Endpoint:** `POST /api/user/{userId}/bookings/{bookingId}/payment`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body (UPI):**
```json
{
  "paymentMethod": "UPI"
}
```

**Response (201 Created):**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "UPI",
  "status": "PENDING",
  "upiDeepLink": "upi://pay?pa=sarasarathy86@okicici&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567",
  "message": "Open UPI app to complete payment. After payment, driver will call owner to verify."
}
```

---

### 14. MARK CASH RECEIVED (Driver Only)
**Endpoint:** `POST /api/driver/{driverId}/bookings/{bookingId}/cash-payment`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "amountReceived": 2500.00
}
```

**Response (200 OK):**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "CASH",
  "status": "VERIFIED",
  "message": "Cash payment received. Trip completed. RCM calculation will be processed."
}
```

---

### 15. VERIFY PAYMENT (Owner Only)
**Endpoint:** `POST /api/owner/payments/{paymentId}/verify`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "UPI",
  "status": "VERIFIED",
  "message": "Payment verified. Trip completed. RCM calculation will be processed."
}
```

---

### 16. VIEW PENDING PAYMENTS (Owner Only)
**Endpoint:** `GET /api/owner/payments/pending`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
[
  {
    "paymentId": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "amount": 2500.00,
    "paymentMethod": "UPI",
    "status": "PENDING",
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

### Payment Features

✅ Free UPI deep linking (no payment gateway fees)  
✅ Auto-opens UPI apps (Google Pay, PhonePe, Paytm)  
✅ Cash payment support  
✅ Owner verification workflow  
✅ Trip completion on payment verification  
✅ RCM calculation trigger  
✅ Complete payment history  

📚 **Full Documentation:** [Payment System API](api/PAYMENT_API_DOCUMENTATION.md)

---
