# Driver Creation Workflow - Updated Implementation

## Complete Flow

### Step 1: Owner Creates Driver
**Endpoint:** `POST /api/owner/drivers`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```json
{
  "name": "Suresh Sharma",
  "mobile": "9123456789",
  "email": "suresh.sharma@example.com",
  "licenseNumber": "DL1420110012345",
  "aadhaarNumber": "123456789012",
  "photo": "file",
  "licensePhoto": "file",
  "aadhaarPhoto": "file"
}
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
- Driver account created with temporary password
- Email sent to driver with username (mobile) and temporary password
- Driver status: `firstLogin = true`, `emailVerified = false`

---

### Step 2: Driver First Login
**Endpoint:** `POST /api/auth/driver/login`

**Request:**
```json
{
  "mobile": "9123456789",
  "password": "aB3#xY9@kL"
}
```

**Response (First Login):**
```json
{
  "firstLogin": true,
  "email": "suresh.sharma@example.com",
  "message": "First login detected. OTP sent to your email. Please verify to set new password."
}
```

**What Happens:**
- System detects first login
- OTP sent to driver's email
- Driver must verify OTP to proceed

---

### Step 3: Driver Verifies OTP and Sets New Password
**Endpoint:** `POST /api/auth/driver/verify-otp`

**Request:**
```json
{
  "email": "suresh.sharma@example.com",
  "otp": "123456",
  "newPassword": "MyNewPassword@123"
}
```

**Response:**
```json
{
  "message": "Password set successfully. You can now login with your new password."
}
```

**What Happens:**
- OTP verified
- New password set
- Driver status: `firstLogin = false`, `emailVerified = true`

---

### Step 4: Driver Normal Login
**Endpoint:** `POST /api/auth/driver/login`

**Request:**
```json
{
  "mobile": "9123456789",
  "password": "MyNewPassword@123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "firstLogin": false,
  "message": "Authentication successful"
}
```

**What Happens:**
- Driver logs in with new password
- JWT token issued
- Normal authentication flow

---

## Email Templates

### 1. Initial Credentials Email (Step 1)
**Subject:** Your Driver Account Credentials - Namma Journey

**Content:**
```
Dear [Driver Name],

Your driver account has been verified successfully. Here are your login credentials:

Username (Mobile): [Mobile Number]
Temporary Password: [Generated Password]

Important: You must change your password after first login for security reasons.

Login URL: http://localhost:8080/api/auth/driver/login

Best regards,
Namma Journey Team
```

### 2. OTP Email (Step 2)
**Subject:** Your OTP for Namma Journey

**Content:**
```
Your OTP Code

Your OTP for Namma Journey is: [OTP]

This OTP is valid for 5 minutes.

If you didn't request this OTP, please ignore this email.

Best regards,
Namma Journey Team
```

---

## Database Changes

Driver entity fields:
- `firstLogin` (boolean) - true when created, false after password change
- `emailVerified` (boolean) - false when created, true after OTP verification

---

## API Summary

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/api/owner/drivers` | POST | OWNER | Create driver account |
| `/api/auth/driver/login` | POST | Public | Driver login (handles first login) |
| `/api/auth/driver/verify-otp` | POST | Public | Verify OTP and set new password |

---

## Testing Flow

1. **Owner creates driver:**
```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer OWNER_TOKEN" \
  -F "name=Suresh Sharma" \
  -F "mobile=9123456789" \
  -F "email=suresh@example.com" \
  -F "licenseNumber=DL123456" \
  -F "aadhaarNumber=123456789012"
```

2. **Driver receives email with credentials**

3. **Driver first login:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9123456789","password":"aB3#xY9@kL"}'
```

4. **Driver receives OTP email**

5. **Driver verifies OTP and sets password:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"suresh@example.com","otp":"123456","newPassword":"MyNewPass@123"}'
```

6. **Driver normal login:**
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9123456789","password":"MyNewPass@123"}'
```

---

## Security Features

✅ Temporary password generated securely  
✅ OTP verification required on first login  
✅ Forced password change after first login  
✅ Email verification through OTP  
✅ Passwords encrypted with BCrypt  
✅ JWT token-based authentication  

---

## Files Modified

1. `OwnerController.java` - Added driver creation endpoint
2. `AdminDriverService.java` - Updated to send credentials immediately
3. `DriverAuthService.java` - Added OTP verification and first login handling
4. `AuthController.java` - Added verify-otp endpoint
5. `EmailService.java` - Updated email template
6. `DriverSetPasswordRequest.java` - New DTO for password setting
7. `DriverVerifyOtpRequest.java` - New DTO for OTP verification

---

## Configuration Required

Add to `application.yml`:
```yaml
sendgrid:
  api:
    key: ${SENDGRID_API_KEY}
  from:
    email: noreply@nammajourney.com
    name: Namma Journey

otp:
  expiry:
    minutes: 5
  test:
    mode: false
```
