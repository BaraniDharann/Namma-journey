# Driver Creation Workflow - Step-by-Step API Requests

## Prerequisites
- Owner must be logged in and have a valid JWT token
- Gmail SMTP configured (MAIL_USERNAME and a Gmail app password)
- PostgreSQL database running

---

## STEP 1: Owner Login

**Endpoint:** `POST /api/auth/owner/login`

**Request:**
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
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlJPTEVfT1dORVIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDA4NjQwMH0.xxx",
  "role": "ROLE_OWNER",
  "userId": 1,
  "message": "Authentication successful"
}
```

**Save the token:** `OWNER_TOKEN=eyJhbGciOiJIUzI1NiJ9...`

---

## STEP 2: Owner Creates Driver

**Endpoint:** `POST /api/owner/drivers`

**Request (with files):**
```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer OWNER_TOKEN" \
  -F "name=Suresh Sharma" \
  -F "mobile=9123456789" \
  -F "email=suresh.sharma@example.com" \
  -F "licenseNumber=DL1420110012345" \
  -F "aadhaarNumber=123456789012" \
  -F "photo=@/path/to/driver-photo.jpg" \
  -F "licensePhoto=@/path/to/license.jpg" \
  -F "aadhaarPhoto=@/path/to/aadhaar.jpg"
```

**Request (without files - for testing):**
```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer OWNER_TOKEN" \
  -F "name=Suresh Sharma" \
  -F "mobile=9123456789" \
  -F "email=suresh.sharma@example.com" \
  -F "licenseNumber=DL1420110012345" \
  -F "aadhaarNumber=123456789012"
```

**Response:**
```json
{
  "id": 1,
  "name": "Suresh Sharma",
  "email": "suresh.sharma@example.com",
  "mobile": "9123456789",
  "message": "Driver created successfully. Login credentials sent to driver's email."
}
```

**What Happens:**
- Driver account created in database
- Temporary password generated (e.g., `aB3#xY9@kL`)
- Email sent to driver with:
  - Username: `9123456789`
  - Temporary Password: `aB3#xY9@kL`

**Email Received by Driver:**
```
Subject: Your Driver Account Credentials - Namma Journey

Dear Suresh Sharma,

Your driver account has been verified successfully. Here are your login credentials:

Username (Mobile): 9123456789
Temporary Password: aB3#xY9@kL

Important: You must change your password after first login for security reasons.

Login URL: http://localhost:8080/api/auth/driver/login

Best regards,
Namma Journey Team
```

---

## STEP 3: Driver First Login (Triggers OTP)

**Endpoint:** `POST /api/auth/driver/login`

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9123456789",
    "password": "aB3#xY9@kL"
  }'
```

**Response:**
```json
{
  "firstLogin": true,
  "email": "suresh.sharma@example.com",
  "message": "First login detected. OTP sent to your email. Please verify to set new password."
}
```

**What Happens:**
- System detects `firstLogin = true`
- OTP generated (e.g., `123456`)
- OTP sent to driver's email
- No JWT token issued yet

**Email Received by Driver:**
```
Subject: Your OTP for Namma Journey

Your OTP Code

Your OTP for Namma Journey is: 123456

This OTP is valid for 5 minutes.

If you didn't request this OTP, please ignore this email.

Best regards,
Namma Journey Team
```

---

## STEP 4: Driver Verifies OTP and Sets New Password

**Endpoint:** `POST /api/auth/driver/verify-otp`

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "suresh.sharma@example.com",
    "otp": "123456",
    "newPassword": "MyNewPassword@123"
  }'
```

**Response:**
```json
{
  "message": "Password set successfully. You can now login with your new password."
}
```

**What Happens:**
- OTP verified
- New password set: `MyNewPassword@123`
- Driver status updated: `firstLogin = false`, `emailVerified = true`
- Driver can now login normally

---

## STEP 5: Driver Normal Login (After Password Change)

**Endpoint:** `POST /api/auth/driver/login`

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9123456789",
    "password": "MyNewPassword@123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlJPTEVfRFJJVkVSIiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE3MDAwODY0MDB9.xxx",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "firstLogin": false,
  "message": "Authentication successful"
}
```

**What Happens:**
- Driver authenticated successfully
- JWT token issued
- Driver can now access driver endpoints

---

## STEP 6: Driver Accesses Protected Endpoints

**Example:** Get driver profile

**Request:**
```bash
curl -X GET http://localhost:8080/api/driver/profile \
  -H "Authorization: Bearer DRIVER_TOKEN"
```

---

## Complete Flow Summary

```
┌─────────────────────────────────────────────────────────────┐
│ STEP 1: Owner Login                                         │
│ POST /api/auth/owner/login                                  │
│ → Get OWNER_TOKEN                                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 2: Owner Creates Driver                                │
│ POST /api/owner/drivers                                     │
│ → Driver receives email with username & temp password      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 3: Driver First Login                                  │
│ POST /api/auth/driver/login                                 │
│ (with temp password)                                        │
│ → OTP sent to driver's email                               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 4: Driver Verifies OTP & Sets New Password            │
│ POST /api/auth/driver/verify-otp                           │
│ → Password changed, account verified                       │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 5: Driver Normal Login                                 │
│ POST /api/auth/driver/login                                 │
│ (with new password)                                         │
│ → Get DRIVER_TOKEN                                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ STEP 6: Driver Uses Protected Endpoints                     │
│ Use DRIVER_TOKEN for all driver APIs                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Postman Collection

### 1. Owner Login
- **Method:** POST
- **URL:** `{{base_url}}/api/auth/owner/login`
- **Body (JSON):**
```json
{
  "email": "owner@example.com",
  "password": "YOUR_OWNER_PASSWORD"
}
```
- **Tests:** Save token to environment variable

### 2. Create Driver
- **Method:** POST
- **URL:** `{{base_url}}/api/owner/drivers`
- **Headers:** `Authorization: Bearer {{owner_token}}`
- **Body (form-data):**
  - name: Suresh Sharma
  - mobile: 9123456789
  - email: suresh.sharma@example.com
  - licenseNumber: DL1420110012345
  - aadhaarNumber: 123456789012

### 3. Driver First Login
- **Method:** POST
- **URL:** `{{base_url}}/api/auth/driver/login`
- **Body (JSON):**
```json
{
  "mobile": "9123456789",
  "password": "aB3#xY9@kL"
}
```

### 4. Verify OTP & Set Password
- **Method:** POST
- **URL:** `{{base_url}}/api/auth/driver/verify-otp`
- **Body (JSON):**
```json
{
  "email": "suresh.sharma@example.com",
  "otp": "123456",
  "newPassword": "MyNewPassword@123"
}
```

### 5. Driver Normal Login
- **Method:** POST
- **URL:** `{{base_url}}/api/auth/driver/login`
- **Body (JSON):**
```json
{
  "mobile": "9123456789",
  "password": "MyNewPassword@123"
}
```
- **Tests:** Save token to environment variable

---

## Error Scenarios

### Invalid OTP
**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "suresh.sharma@example.com",
    "otp": "999999",
    "newPassword": "MyNewPassword@123"
  }'
```

**Response (400):**
```json
{
  "error": "Invalid or expired OTP"
}
```

### Expired OTP
OTP expires after 5 minutes. Request new OTP by logging in again.

### Duplicate Mobile Number
**Response (400):**
```json
{
  "error": "Mobile number already registered"
}
```

### Invalid Password Format
**Response (400):**
```json
{
  "newPassword": "Password must be at least 6 characters"
}
```

---

## Testing Tips

1. **Test Mode:** Set `otp.test.mode=true` in application.yml to see OTP in console logs
2. **Check Emails:** Watch the backend log for mail send failures
3. **Database Check:** Query drivers table to verify status changes
4. **Token Expiry:** JWT tokens expire after 24 hours

---

## Environment Variables

```bash
# Base URL
base_url=http://localhost:8080

# Owner Credentials
owner_email=owner@example.com
owner_password=YOUR_OWNER_PASSWORD

# Driver Test Data
driver_mobile=9123456789
driver_email=suresh.sharma@example.com
```
