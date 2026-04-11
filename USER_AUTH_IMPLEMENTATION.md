# User Authentication Flow - Implementation Summary

## Overview
Implemented a secure email-based authentication system where users only need OTP verification during signup, and can login with email/password thereafter.

---

## Authentication Flow

### 1️⃣ First Time (Signup)
```
User → Send OTP Request → Receive OTP in Email → Signup with (Email + Name + OTP + Password) → Account Created → JWT Token Returned
```

**API Calls:**
1. `POST /api/auth/otp/send` - Request OTP
2. `POST /api/auth/user/signup` - Complete signup with OTP and password

### 2️⃣ Subsequent Logins (After Logout)
```
User → Login with (Email + Password) → JWT Token Returned
```

**API Call:**
- `POST /api/auth/user/login` - Login with credentials (no OTP needed)

---

## Changes Made

### 1. Database Schema
**File:** `database-migration-user-password.sql`

Added `password` column to `users` table:
```sql
ALTER TABLE users ADD COLUMN password VARCHAR(255);
```

### 2. Entity Update
**File:** `User.java`

Added password field:
```java
@Column
private String password;
```

### 3. DTO Updates

**UserSignupRequest.java** - Added fields:
```java
private String otp;      // Required for verification
private String password; // User's chosen password
```

**UserLoginRequest.java** - Added field:
```java
private String password; // For email/password login
```

### 4. Service Logic
**File:** `UserAuthService.java`

**Signup Method:**
- Validates OTP before account creation
- Encrypts password using BCrypt
- Creates user account with password
- Returns JWT token

**Login Method:**
- For EMAIL login: Validates email/password (no OTP)
- For GOOGLE login: Uses OAuth token (unchanged)
- Returns JWT token

---

## API Documentation

### Send OTP
```http
POST /api/auth/otp/send
Content-Type: application/json

{
  "email": "user@example.com"
}
```

### User Signup (First Time)
```http
POST /api/auth/user/signup
Content-Type: application/json

{
  "email": "user@example.com",
  "name": "Rajesh Kumar",
  "otp": "123456",
  "password": "MyPassword@123"
}
```

### User Login (Subsequent)
```http
POST /api/auth/user/login
Content-Type: application/json

{
  "loginType": "EMAIL",
  "email": "user@example.com",
  "password": "MyPassword@123"
}
```

---

## Security Features

✅ **OTP Verification** - Required only during signup  
✅ **BCrypt Encryption** - Passwords hashed with BCrypt  
✅ **JWT Authentication** - Stateless token-based auth  
✅ **One-Time Setup** - Password set once during signup  
✅ **No Repeated OTP** - Login uses password only  

---

## Testing Steps

### 1. Run Database Migration
```bash
psql -U postgres -d travel_booking_db -f database-migration-user-password.sql
```

### 2. Test Signup Flow
```bash
# Step 1: Request OTP
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'

# Step 2: Signup with OTP
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User","otp":"123456","password":"Test@123"}'
```

### 3. Test Login Flow
```bash
# Login with email/password (no OTP needed)
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"EMAIL","email":"test@example.com","password":"Test@123"}'
```

---

## Benefits

✅ **Better UX** - Users don't need OTP every time  
✅ **Secure** - Password-based authentication with encryption  
✅ **Email Verified** - OTP ensures email ownership during signup  
✅ **Flexible** - Supports both email/password and Google OAuth  
✅ **Standard Flow** - Follows industry-standard authentication patterns  

---

## Migration Notes

- Existing Google OAuth users are unaffected (password is NULL for them)
- Email-based users created before this update will need to use "forgot password" flow
- Mock OTP `123456` works for testing purposes

---

## Next Steps (Optional Enhancements)

- Implement "Forgot Password" flow for email users
- Add password strength validation
- Implement account lockout after failed attempts
- Add email verification link as alternative to OTP
- Implement refresh token mechanism
