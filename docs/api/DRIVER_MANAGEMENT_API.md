# UPDATED API DOCUMENTATION - Driver Management

## Changes Summary

### What Changed:
1. **Driver Signup Removed**: Public driver signup endpoint (`POST /api/auth/driver/signup`) has been REMOVED
2. **Admin-Only Driver Creation**: Only OWNER role can create drivers via `POST /api/owner/drivers`
3. **Auto-Generated Credentials**: System generates secure password and emails credentials to driver
4. **Enhanced Driver Profile**: Added photo, license photo, aadhaar photo, and email fields

---

## NEW ENDPOINT: Admin Create Driver

### Endpoint
**POST** `/api/owner/drivers`

### Authentication Required
- **Role**: `ROLE_OWNER`
- **Header**: `Authorization: Bearer <OWNER_JWT_TOKEN>`

### Request Body
```json
{
  "name": "Suresh Sharma",
  "mobile": "9123456789",
  "email": "suresh.sharma@example.com",
  "licenseNumber": "DL1420110012345",
  "aadhaarNumber": "123456789012",
  "photo": "https://storage.example.com/drivers/photo123.jpg",
  "licensePhoto": "https://storage.example.com/drivers/license123.jpg",
  "aadhaarPhoto": "https://storage.example.com/drivers/aadhaar123.jpg"
}
```

### Field Validations
- **name**: Required, non-empty
- **mobile**: Required, 10-digit Indian number (starts with 6-9)
- **email**: Required, valid email format
- **licenseNumber**: Required, unique
- **aadhaarNumber**: Required, 12-digit number, unique
- **photo**: Optional, URL/Base64 string
- **licensePhoto**: Optional, URL/Base64 string
- **aadhaarPhoto**: Optional, URL/Base64 string

### Success Response (201 Created)
```json
{
  "driverId": 1,
  "name": "Suresh Sharma",
  "email": "suresh.sharma@example.com",
  "mobile": "9123456789",
  "message": "Driver created successfully. Credentials sent to email."
}
```

### Email Sent to Driver
The driver will receive an email with:
- **Username**: Mobile number (9123456789)
- **Password**: Auto-generated 10-character secure password (e.g., `aB3@xY9#mK`)

### Error Responses

#### 400 Bad Request - Duplicate Mobile
```json
{
  "error": "Mobile number already registered"
}
```

#### 400 Bad Request - Duplicate Email
```json
{
  "error": "Email already registered"
}
```

#### 400 Bad Request - Duplicate License
```json
{
  "error": "License number already registered"
}
```

#### 400 Bad Request - Duplicate Aadhaar
```json
{
  "error": "Aadhaar number already registered"
}
```

#### 401 Unauthorized
```json
{
  "error": "Unauthorized"
}
```

#### 403 Forbidden - Not Owner Role
```json
{
  "error": "Access Denied"
}
```

---

## UPDATED: Driver Login (Unchanged)

### Endpoint
**POST** `/api/auth/driver/login`

### Request Body
```json
{
  "mobile": "9123456789",
  "password": "aB3@xY9#mK"
}
```

### Response (200 OK)
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "message": "Authentication successful"
}
```

**Note**: Driver uses the password received via email to login

---

## Complete Workflow

### Step 1: Owner Login
```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@example.com",
    "password": "YOUR_OWNER_PASSWORD"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.OWNER_TOKEN...",
  "role": "ROLE_OWNER",
  "userId": 1,
  "message": "Authentication successful"
}
```

### Step 2: Owner Creates Driver
```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.OWNER_TOKEN..." \
  -d '{
    "name": "Suresh Sharma",
    "mobile": "9123456789",
    "email": "suresh.sharma@example.com",
    "licenseNumber": "DL1420110012345",
    "aadhaarNumber": "123456789012",
    "photo": "https://example.com/photo.jpg",
    "licensePhoto": "https://example.com/license.jpg",
    "aadhaarPhoto": "https://example.com/aadhaar.jpg"
  }'
```

**Response:**
```json
{
  "driverId": 1,
  "name": "Suresh Sharma",
  "email": "suresh.sharma@example.com",
  "mobile": "9123456789",
  "message": "Driver created successfully. Credentials sent to email."
}
```

### Step 3: Driver Receives Email
Driver receives email at `suresh.sharma@example.com`:

```
Subject: Your Driver Account Credentials - Namma Journey

Dear Suresh Sharma,

Your driver account has been created successfully. Here are your login credentials:

Username (Mobile): 9123456789
Password: aB3@xY9#mK

Please keep these credentials secure and change your password after first login.

Best regards,
Namma Journey Team
```

### Step 4: Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9123456789",
    "password": "aB3@xY9#mK"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.DRIVER_TOKEN...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "message": "Authentication successful"
}
```

---

## Updated Database Schema

### Drivers Table
```sql
CREATE TABLE drivers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    mobile VARCHAR(10) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255) NOT NULL,
    license_number VARCHAR(50) UNIQUE NOT NULL,
    aadhaar_number VARCHAR(12) UNIQUE NOT NULL,
    photo VARCHAR(1000),
    license_photo VARCHAR(1000),
    aadhaar_photo VARCHAR(1000),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

---

## Updated Project Structure

```
com.travelplatform
├── controller
│   ├── AuthController.java          # Removed driver signup endpoint
│   └── AdminController.java         # NEW: Admin endpoints for driver management
├── service
│   ├── AdminDriverService.java      # NEW: Admin driver creation logic
│   ├── EmailService.java            # NEW: Send credentials via email
│   ├── DriverAuthService.java       # Login only (signup removed)
│   └── ...
├── dto
│   ├── AdminCreateDriverRequest.java    # NEW: Admin create driver DTO
│   ├── DriverCreationResponse.java      # NEW: Driver creation response
│   └── ...
└── entity
    └── Driver.java                  # Updated with email and photo fields
```

---

## Security Notes

✅ Only OWNER role can create drivers  
✅ Auto-generated secure 10-character passwords  
✅ Credentials sent via email (Gmail SMTP)  
✅ All sensitive fields (mobile, email, license, aadhaar) are unique  
✅ Passwords encrypted with BCrypt  
✅ JWT-based authentication for all roles  

---

## Testing Checklist

- [ ] Owner can login successfully
- [ ] Owner can create driver with all fields
- [ ] Driver receives email with credentials
- [ ] Driver can login with received credentials
- [ ] Non-owner cannot access `/api/owner/drivers`
- [ ] Duplicate mobile/email/license/aadhaar rejected
- [ ] Public driver signup endpoint returns 404

---

## Migration Notes

If you have existing drivers created via old signup endpoint:
1. They can continue to login normally
2. Add email field manually if needed
3. Photo fields are optional and can be null

---

## Environment Variables Required

```env
# Gmail SMTP (for OTP and driver-credential email)
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM_NAME=Namma Journey

# Database
DB_URL=jdbc:postgresql://localhost:5432/travel_booking_db
DB_USERNAME=your_username
DB_PASSWORD=your_password

# JWT
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000
```

---

## Contact
For support: support@travelplatform.com
