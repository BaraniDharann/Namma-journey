# Driver Creation & Verification Workflow

## Overview
Complete workflow for owner to create driver accounts with email verification, photo uploads, and password management.

---

## Workflow Steps

### Step 1: Owner Creates Driver Account
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

**What Happens:**
- Driver account created with photos
- OTP sent to driver's email
- Driver status: `emailVerified=false`, `firstLogin=true`

---

### Step 2: Owner Verifies Driver Email with OTP
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

**What Happens:**
- OTP verified
- New random password generated
- Login credentials emailed to driver
- Driver status: `emailVerified=true`, `firstLogin=true`

**Email Sent to Driver:**
```
Subject: Your Driver Account Credentials - Namma Journey

Dear Suresh Sharma,

Your driver account has been created successfully. Here are your login credentials:

Username (Mobile): 9123456789
Password: aB3#xY9@kL

Please keep these credentials secure and change your password after first login.

Best regards,
Namma Journey Team
```

---

### Step 3: Driver First Login
**Endpoint:** `POST /api/auth/driver/login`  
**Authorization:** None (public endpoint)

**Request Body:**
```json
{
  "mobile": "9123456789",
  "password": "aB3#xY9@kL"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "firstLogin": true,
  "message": "First login detected. Please change your password."
}
```

**What Happens:**
- Driver authenticated
- `firstLogin=true` flag indicates password change required
- JWT token issued

---

### Step 4: Driver Changes Password
**Endpoint:** `POST /api/auth/driver/change-password`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "oldPassword": "aB3#xY9@kL",
  "newPassword": "MyNewSecure@123"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

**What Happens:**
- Old password verified
- New password set
- `firstLogin=false` flag updated
- Driver can now login normally

---

### Step 5: Driver Subsequent Logins
**Endpoint:** `POST /api/auth/driver/login`

**Request Body:**
```json
{
  "mobile": "9123456789",
  "password": "MyNewSecure@123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "firstLogin": false,
  "message": "Authentication successful"
}
```

---

## Database Schema Updates

### Drivers Table - New Columns
```sql
ALTER TABLE drivers 
ADD COLUMN first_login BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
```

---

## Photo Upload Guidelines

### Supported Formats
- JPEG, PNG, WebP
- Maximum size: 5MB per photo

### Photo Types
1. **Driver Photo** - Profile picture
2. **License Photo** - Clear image of driving license
3. **Aadhaar Photo** - Clear image of Aadhaar card

### Storage Options
- Cloud storage (AWS S3, Cloudinary, etc.)
- Store URLs in database
- Implement file upload endpoint separately

---

## Error Responses

### Driver Creation Errors

**Mobile Already Registered (400)**
```json
{
  "error": "Mobile number already registered"
}
```

**Email Already Registered (400)**
```json
{
  "error": "Email already registered"
}
```

**License Already Registered (400)**
```json
{
  "error": "License number already registered"
}
```

**OTP Send Failed (500)**
```json
{
  "error": "Failed to send OTP to driver email"
}
```

---

### Verification Errors

**Invalid OTP (200)**
```json
{
  "message": "Invalid or expired OTP",
  "success": false
}
```

**Driver Not Found (400)**
```json
{
  "error": "Driver not found with email: suresh@example.com"
}
```

---

### Login Errors

**Email Not Verified (400)**
```json
{
  "error": "Email not verified. Please contact admin."
}
```

**Invalid Credentials (401)**
```json
{
  "error": "Invalid password"
}
```

**Account Inactive (400)**
```json
{
  "error": "Driver account is inactive"
}
```

---

### Password Change Errors

**Incorrect Old Password (401)**
```json
{
  "error": "Old password is incorrect"
}
```

**Validation Error (400)**
```json
{
  "newPassword": "Password must be at least 6 characters"
}
```

---

## Mail configuration

### Setup Steps

1. **Enable 2-Step Verification and create a Gmail app password**
   - Sign up at https://Gmail SMTP.com
   - Verify your email

2. **Create API Key**
   - Go to Settings > API Keys
   - Create new API key with "Mail Send" permissions
   - Copy the API key

3. **Verify Sender Email**
   - Go to Settings > Sender Authentication
   - Verify your sender email address

4. **Update .env File**
```env
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM_NAME=Namma Journey
```

---

## Testing with cURL

### 1. Owner Login
```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@example.com",
    "password": "YOUR_OWNER_PASSWORD"
  }'
```

### 2. Create Driver
```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -d '{
    "name": "Suresh Sharma",
    "mobile": "9123456789",
    "email": "suresh@example.com",
    "licenseNumber": "DL1420110012345",
    "aadhaarNumber": "123456789012",
    "photo": "https://example.com/photo.jpg",
    "licensePhoto": "https://example.com/license.jpg",
    "aadhaarPhoto": "https://example.com/aadhaar.jpg"
  }'
```

### 3. Verify Driver Email
```bash
curl -X POST http://localhost:8080/api/owner/drivers/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -d '{
    "email": "suresh@example.com",
    "otp": "123456"
  }'
```

### 4. Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9123456789",
    "password": "aB3#xY9@kL"
  }'
```

### 5. Change Password
```bash
curl -X POST http://localhost:8080/api/auth/driver/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_DRIVER_TOKEN" \
  -d '{
    "oldPassword": "aB3#xY9@kL",
    "newPassword": "MyNewSecure@123"
  }'
```

---

## Security Features

✅ Email verification with OTP  
✅ Secure password generation (10 characters with special chars)  
✅ BCrypt password encryption  
✅ First login detection  
✅ Forced password change on first login  
✅ Photo document verification  
✅ Role-based access control  
✅ JWT token authentication  

---

## Implementation Checklist

- [x] Add photo fields to Driver entity
- [x] Add firstLogin and emailVerified flags
- [x] Update AdminDriverService with OTP flow
- [x] Create verification endpoint
- [x] Update driver login to check email verification
- [x] Add password change endpoint
- [x] Update DriverAuthService with password change
- [x] Gmail SMTP email integration
- [x] OTP generation and verification
- [x] Update DTOs and controllers
- [ ] Implement file upload endpoint for photos
- [ ] Add database migration script
- [ ] Update frontend to handle photo uploads
- [ ] Add password strength validation
- [ ] Implement rate limiting for OTP

---

## Next Steps

1. **File Upload Implementation**
   - Create `/api/upload/driver-photo` endpoint
   - Integrate with cloud storage (AWS S3/Cloudinary)
   - Return photo URLs

2. **Database Migration**
   - Run migration to add new columns
   - Update existing driver records

3. **Frontend Integration**
   - Photo upload UI
   - OTP verification form
   - Password change form
   - First login detection

4. **Additional Features**
   - Resend OTP functionality
   - Password reset via email
   - Driver profile management
   - Document verification status

---

## Support

For issues or questions:
- Email: support@travelplatform.com
- Documentation: /docs/driver-management

---

## License
Proprietary - Travel Booking Platform
