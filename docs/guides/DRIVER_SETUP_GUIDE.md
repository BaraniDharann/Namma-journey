# Driver Creation Workflow - Setup Guide

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 14+
- Gmail SMTP Account (for email)
- Running Travel Booking Platform backend

---

## Step 1: Database Migration

Run the migration script to add new columns:

```bash
psql -U your_username -d travel_booking_db -f database-migration-driver-verification.sql
```

Or manually execute:

```sql
ALTER TABLE drivers 
ADD COLUMN IF NOT EXISTS first_login BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_drivers_email ON drivers(email);
CREATE INDEX IF NOT EXISTS idx_drivers_email_verified ON drivers(email_verified);
```

---

## Step 2: Gmail SMTP Configuration

### 2.1 Create Gmail SMTP Account
1. Go to https://Gmail SMTP.com
2. Sign up for free account (100 emails/day free tier)
3. Verify your email address

### 2.2 Create API Key
1. Login to Gmail SMTP Dashboard
2. Go to **Settings** → **API Keys**
3. Click **Create API Key**
4. Name: `Travel-Platform-API`
5. Permissions: **Full Access** or **Mail Send** only
6. Copy the API key (you won't see it again!)

### 2.3 Verify Sender Email
1. Go to **Settings** → **Sender Authentication**
2. Click **Verify a Single Sender**
3. Fill in your details:
   - From Name: `Namma Journey`
   - From Email: `noreply@yourdomain.com`
   - Reply To: `support@yourdomain.com`
4. Check your email and verify

**Note:** For testing, you can use your personal email as sender.

---

## Step 3: Environment Configuration

Update your `.env` file:

```env
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/travel_booking_db
DB_USERNAME=postgres
DB_PASSWORD=your_password

# JWT Configuration
JWT_SECRET=your_jwt_secret_key_minimum_32_characters_long
JWT_EXPIRATION=86400000

# Gmail SMTP Email Configuration
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM_NAME=Namma Journey

# OTP Configuration
OTP_EXPIRY_MINUTES=5

# Server Configuration
SERVER_PORT=8080
```

---

## Step 4: Verify Dependencies

Check `pom.xml` includes Gmail SMTP:

```xml
<dependency>
    <groupId>com.Gmail SMTP</groupId>
    <artifactId>Gmail SMTP-java</artifactId>
    <version>4.10.2</version>
</dependency>
```

---

## Step 5: Build and Run

```bash
# Clean and build
mvn clean install

# Run application
mvn spring-boot:run
```

Check logs for:
```
Started TravelPlatformApplication in X seconds
```

---

## Step 6: Create Owner Account

If not already created:

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

Password: `YOUR_OWNER_PASSWORD`

---

## Step 7: Test the Workflow

### 7.1 Owner Login

```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "owner@example.com",
    "password": "YOUR_OWNER_PASSWORD"
  }'
```

Save the token from response.

### 7.2 Create Driver

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -d '{
    "name": "Test Driver",
    "mobile": "9876543210",
    "email": "testdriver@example.com",
    "licenseNumber": "DL1420110012345",
    "aadhaarNumber": "123456789012",
    "photo": "https://example.com/photo.jpg",
    "licensePhoto": "https://example.com/license.jpg",
    "aadhaarPhoto": "https://example.com/aadhaar.jpg"
  }'
```

**Expected Response:**
```json
{
  "id": 1,
  "name": "Test Driver",
  "email": "testdriver@example.com",
  "mobile": "9876543210",
  "message": "Driver created successfully. OTP sent to driver's email for verification."
}
```

**Check:** Driver should receive OTP email.

### 7.3 Verify Driver Email

Check the OTP in driver's email inbox, then:

```bash
curl -X POST http://localhost:8080/api/owner/drivers/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -d '{
    "email": "testdriver@example.com",
    "otp": "123456"
  }'
```

**Expected Response:**
```json
{
  "message": "Driver email verified successfully. Login credentials sent to driver's email.",
  "success": true
}
```

**Check:** Driver should receive credentials email with username and password.

### 7.4 Driver First Login

Use credentials from email:

```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9876543210",
    "password": "PASSWORD_FROM_EMAIL"
  }'
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_DRIVER",
  "userId": 1,
  "firstLogin": true,
  "message": "First login detected. Please change your password."
}
```

Save the driver token.

### 7.5 Change Password

```bash
curl -X POST http://localhost:8080/api/auth/driver/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_DRIVER_TOKEN" \
  -d '{
    "oldPassword": "PASSWORD_FROM_EMAIL",
    "newPassword": "MyNewPassword@123"
  }'
```

**Expected Response:**
```json
{
  "message": "Password changed successfully"
}
```

### 7.6 Login with New Password

```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{
    "mobile": "9876543210",
    "password": "MyNewPassword@123"
  }'
```

**Expected Response:**
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

## Step 8: Import Postman Collection

1. Open Postman
2. Click **Import**
3. Select `Driver-Creation-Workflow.postman_collection.json`
4. Update variables:
   - `baseUrl`: `http://localhost:8080`
5. Run requests in sequence

---

## Troubleshooting

### Issue: OTP Email Not Received

**Check:**
1. Gmail SMTP API key is correct in `.env`
2. Sender email is verified in Gmail SMTP
3. Check application logs for errors
4. Check Gmail SMTP Activity Feed for delivery status
5. Check spam folder

**Solution:**
```bash
# Test mail delivery connection
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email": "your-test-email@example.com"}'
```

### Issue: Credentials Email Not Sent

**Check:**
1. OTP verification was successful
2. Driver email is correct in database
3. Check application logs for Gmail SMTP errors

**Solution:**
```sql
-- Check driver status
SELECT id, name, email, email_verified, first_login 
FROM drivers 
WHERE email = 'testdriver@example.com';
```

### Issue: "Email not verified" Error on Login

**Cause:** Driver email not verified by owner

**Solution:**
1. Owner must verify driver email with OTP
2. Check `email_verified` column in database:
```sql
UPDATE drivers 
SET email_verified = true 
WHERE email = 'testdriver@example.com';
```

### Issue: Database Column Not Found

**Cause:** Migration not run

**Solution:**
```bash
psql -U postgres -d travel_booking_db -f database-migration-driver-verification.sql
```

### Issue: Gmail SMTP 401 Unauthorized

**Cause:** Invalid API key

**Solution:**
1. Generate new API key in Gmail SMTP
2. Update `.env` file
3. Restart application

---

## Photo Upload Implementation (Future)

### Option 1: AWS S3

```java
@PostMapping("/upload/driver-photo")
public ResponseEntity<String> uploadPhoto(@RequestParam("file") MultipartFile file) {
    String url = s3Service.uploadFile(file);
    return ResponseEntity.ok(url);
}
```

### Option 2: Cloudinary

```java
@PostMapping("/upload/driver-photo")
public ResponseEntity<String> uploadPhoto(@RequestParam("file") MultipartFile file) {
    String url = cloudinaryService.uploadImage(file);
    return ResponseEntity.ok(url);
}
```

### Option 3: Local Storage (Development Only)

```java
@PostMapping("/upload/driver-photo")
public ResponseEntity<String> uploadPhoto(@RequestParam("file") MultipartFile file) {
    String filename = fileStorageService.storeFile(file);
    String url = "/uploads/" + filename;
    return ResponseEntity.ok(url);
}
```

---

## Security Considerations

1. **Photo Validation**
   - Validate file types (JPEG, PNG only)
   - Limit file size (max 5MB)
   - Scan for malware

2. **OTP Security**
   - 5-minute expiry
   - One-time use only
   - Rate limiting (max 3 attempts)

3. **Password Policy**
   - Minimum 6 characters
   - Require special characters
   - BCrypt encryption

4. **Email Security**
   - Use verified sender domain
   - SPF/DKIM/DMARC records
   - Monitor bounce rates

---

## Monitoring

### Check OTP Status
```sql
SELECT email, otp, verified, created_at, expiry_time 
FROM otp 
ORDER BY created_at DESC 
LIMIT 10;
```

### Check Driver Verification Status
```sql
SELECT id, name, email, mobile, email_verified, first_login, created_at 
FROM drivers 
ORDER BY created_at DESC;
```

### Check Email Logs
Check application logs for:
```
OTP email sent successfully to: driver@example.com
Driver credentials email sent successfully to: driver@example.com
```

---

## Production Checklist

- [ ] Gmail SMTP production API key configured
- [ ] Custom domain for sender email
- [ ] SPF/DKIM/DMARC records configured
- [ ] Database migration applied
- [ ] Photo upload endpoint implemented
- [ ] Cloud storage configured (S3/Cloudinary)
- [ ] Rate limiting enabled
- [ ] Email templates customized
- [ ] Error monitoring setup
- [ ] Backup strategy for photos
- [ ] GDPR compliance for photo storage

---

## Support

For issues:
- Check logs: `logs/spring.log`
- Email: support@travelplatform.com
- Documentation: `DRIVER_CREATION_WORKFLOW.md`

---

## License
Proprietary - Travel Booking Platform
