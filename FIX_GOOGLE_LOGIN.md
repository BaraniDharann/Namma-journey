# Fix Google Login - Phone Column Error

## Problem
Google login fails with error: `null value in column "phone" violates not-null constraint`

## Solution

### Step 1: Fix Database (Run in PostgreSQL)

Open PostgreSQL and run:

```sql
-- Connect to database
\c namma_journey

-- Make phone column nullable
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;

-- Verify change
\d users
```

### Step 2: Restart Application

```bash
# Stop the application (Ctrl+C)
# Then restart
mvn spring-boot:run
```

### Step 3: Test Google Login Again

```bash
curl --request POST \
  --url http://localhost:8080/api/auth/user/login \
  --header 'content-type: application/json' \
  --data '{
  "loginType": "GOOGLE",
  "token": "YOUR_GOOGLE_TOKEN_HERE"
}'
```

## Expected Result

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "uuid-here",
  "message": "Authentication successful"
}
```

## What Changed

1. ✅ User entity now has optional `phone` field
2. ✅ Database `phone` column is now nullable
3. ✅ Google token is decoded to extract real email and name
4. ✅ Google users can login without phone number

## Quick Fix (Windows)

Open Command Prompt as Administrator:

```cmd
psql -U postgres -d namma_journey -c "ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;"
```

Then restart your Spring Boot application.
