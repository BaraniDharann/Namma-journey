# Quick Testing Guide - Admin Driver Creation

## Prerequisites
- Application running on `http://localhost:8080`
- Owner account exists in database
- Gmail SMTP configured for email

---

## Step-by-Step Testing

### Step 1: Login as Owner (Admin)

```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@example.com",
    "password": "YOUR_OWNER_PASSWORD"
  }'
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_OWNER",
  "userId": 1,
  "message": "Authentication successful"
}
```

**Copy the token** from response for next step.

---

### Step 2: Create Driver (as Owner)

Replace `<OWNER_TOKEN>` with token from Step 1:

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -d '{
    "name": "Rajesh Kumar",
    "mobile": "9876543210",
    "email": "rajesh.kumar@example.com",
    "licenseNumber": "DL1420110012345",
    "aadhaarNumber": "123456789012",
    "photo": "https://example.com/photos/rajesh.jpg",
    "licensePhoto": "https://example.com/licenses/rajesh_license.jpg",
    "aadhaarPhoto": "https://example.com/aadhaar/rajesh_aadhaar.jpg"
  }'
```

**Expected Response:**
```json
{
  "driverId": 1,
  "name": "Rajesh Kumar",
  "email": "rajesh.kumar@example.com",
  "mobile": "9876543210",
  "message": "Driver created successfully. Credentials sent to email."
}
```

---

### Step 3: Check Driver's Email

Driver receives email at `rajesh.kumar@example.com`:

```
Subject: Your Driver Account Credentials - Namma Journey

Dear Rajesh Kumar,

Your driver account has been created successfully. Here are your login credentials:

Username (Mobile): 9876543210
Password: aB3@xY9#mK

Please keep these credentials secure and change your password after first login.

Best regards,
Namma Journey Team
```

**Note the password** from email.

---

### Step 4: Login as Driver

Use mobile and password from email:

```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9876543210",
    "password": "aB3@xY9#mK"
  }'
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "message": "Authentication successful"
}
```

---

## Negative Test Cases

### Test 1: Create Driver Without Token (Should Fail)

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Driver",
    "mobile": "9999999999",
    "email": "test@example.com",
    "licenseNumber": "DL9999999999",
    "aadhaarNumber": "999999999999"
  }'
```

**Expected**: `401 Unauthorized`

---

### Test 2: Create Driver with USER Token (Should Fail)

First login as user, then try to create driver:

```bash
# Login as user
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "9111111111",
    "name": "Test User"
  }'

# Try to create driver with USER token
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -d '{
    "name": "Test Driver",
    "mobile": "9999999999",
    "email": "test@example.com",
    "licenseNumber": "DL9999999999",
    "aadhaarNumber": "999999999999"
  }'
```

**Expected**: `403 Forbidden`

---

### Test 3: Duplicate Mobile Number (Should Fail)

Try creating driver with same mobile twice:

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -d '{
    "name": "Another Driver",
    "mobile": "9876543210",
    "email": "another@example.com",
    "licenseNumber": "DL9999999999",
    "aadhaarNumber": "999999999999"
  }'
```

**Expected**: `400 Bad Request`
```json
{
  "error": "Mobile number already registered"
}
```

---

### Test 4: Invalid Mobile Format (Should Fail)

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -d '{
    "name": "Test Driver",
    "mobile": "1234567890",
    "email": "test@example.com",
    "licenseNumber": "DL9999999999",
    "aadhaarNumber": "999999999999"
  }'
```

**Expected**: `400 Bad Request`
```json
{
  "mobile": "Invalid Indian mobile number"
}
```

---

### Test 5: Invalid Aadhaar Format (Should Fail)

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -d '{
    "name": "Test Driver",
    "mobile": "9999999999",
    "email": "test@example.com",
    "licenseNumber": "DL9999999999",
    "aadhaarNumber": "12345"
  }'
```

**Expected**: `400 Bad Request`
```json
{
  "aadhaarNumber": "Invalid Aadhaar number"
}
```

---

## Postman Collection

### Import this JSON into Postman:

```json
{
  "info": {
    "name": "Travel Platform - Driver Management",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "1. Owner Login",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"email\": \"owner@example.com\",\n  \"password\": \"YOUR_OWNER_PASSWORD\"\n}"
        },
        "url": "http://localhost:8080/api/auth/owner/login"
      }
    },
    {
      "name": "2. Create Driver",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"},
          {"key": "Authorization", "value": "Bearer {{ownerToken}}"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"name\": \"Rajesh Kumar\",\n  \"mobile\": \"9876543210\",\n  \"email\": \"rajesh.kumar@example.com\",\n  \"licenseNumber\": \"DL1420110012345\",\n  \"aadhaarNumber\": \"123456789012\",\n  \"photo\": \"https://example.com/photo.jpg\",\n  \"licensePhoto\": \"https://example.com/license.jpg\",\n  \"aadhaarPhoto\": \"https://example.com/aadhaar.jpg\"\n}"
        },
        "url": "http://localhost:8080/api/owner/drivers"
      }
    },
    {
      "name": "3. Driver Login",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"mobile\": \"9876543210\",\n  \"password\": \"<PASSWORD_FROM_EMAIL>\"\n}"
        },
        "url": "http://localhost:8080/api/auth/driver/login"
      }
    }
  ]
}
```

---

## Database Verification

### Check if driver was created:

```sql
SELECT id, name, mobile, email, license_number, aadhaar_number, role, status, created_at
FROM drivers
WHERE mobile = '9876543210';
```

### Check password is encrypted:

```sql
SELECT password FROM drivers WHERE mobile = '9876543210';
-- Should see BCrypt hash like: $2a$10$...
```

---

## Troubleshooting

### Problem: 401 Unauthorized
**Solution**: Check if owner token is valid and not expired

### Problem: Email not received
**Solution**: 
1. Check Gmail SMTP API key in environment variables
2. Check Gmail SMTP dashboard for email status
3. Check spam folder
4. Verify email address is valid

### Problem: 500 Internal Server Error
**Solution**: Check application logs for detailed error message

### Problem: Driver table not found
**Solution**: Ensure database is running and JPA has created tables

---

## Environment Setup Checklist

- [ ] PostgreSQL running on port 5432
- [ ] Database `travel_booking_db` created
- [ ] Owner account exists in database
- [ ] MAIL_USERNAME / MAIL_PASSWORD configured
- [ ] Application running on port 8080
- [ ] All environment variables set

---

## Quick Commands

### Create the first owner account
```bash
# Set OWNER_BOOTSTRAP_SECRET in .env, restart, then:
curl -X POST http://localhost:8080/api/auth/owner/create-admin \
  -H "Content-Type: application/json" \
  -H "X-Bootstrap-Secret: $OWNER_BOOTSTRAP_SECRET" \
  -d '{"email":"owner@example.com","password":"YOUR_OWNER_PASSWORD","name":"Owner"}'
```

Clear `OWNER_BOOTSTRAP_SECRET` again afterwards — while it is set, that endpoint mints owner
tokens, which is full control of the platform. Do not insert the row by hand: the password
column holds a BCrypt hash, and copying one out of a guide gives every deployment that followed
the same guide the same known password.

### View All Drivers (SQL)
```sql
SELECT * FROM drivers ORDER BY created_at DESC;
```

### Delete Test Driver (SQL)
```sql
DELETE FROM drivers WHERE mobile = '9876543210';
```

---

## Success Indicators

✅ Owner login returns token  
✅ Driver creation returns 201 status  
✅ Driver receives email with credentials  
✅ Driver can login with received password  
✅ Unauthorized requests return 401/403  
✅ Duplicate entries return 400  

---

**Happy Testing! 🚀**
