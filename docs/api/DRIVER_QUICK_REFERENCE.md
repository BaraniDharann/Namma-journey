# Driver Creation Workflow - Quick Reference

## 🚀 Quick Start

### 1️⃣ Setup (One-time)
```bash
# Run migration
psql -U postgres -d travel_booking_db -f database-migration-driver-verification.sql

# Update .env
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM_NAME=Namma Journey

# Start server
mvn spring-boot:run
```

### 2️⃣ Owner Login
```bash
POST /api/auth/owner/login
{
  "email": "owner@example.com",
  "password": "YOUR_OWNER_PASSWORD"
}
# Save token
```

### 3️⃣ Create Driver
```bash
POST /api/owner/drivers
Authorization: Bearer {ownerToken}
{
  "name": "Driver Name",
  "mobile": "9123456789",
  "email": "driver@example.com",
  "licenseNumber": "DL1420110012345",
  "aadhaarNumber": "123456789012",
  "photo": "url",
  "licensePhoto": "url",
  "aadhaarPhoto": "url"
}
# OTP sent to driver email
```

### 4️⃣ Verify Email
```bash
POST /api/owner/drivers/verify
Authorization: Bearer {ownerToken}
{
  "email": "driver@example.com",
  "otp": "123456"
}
# Credentials sent to driver email
```

### 5️⃣ Driver Login
```bash
POST /api/auth/driver/login
{
  "mobile": "9123456789",
  "password": "password_from_email"
}
# Returns firstLogin: true
```

### 6️⃣ Change Password
```bash
POST /api/auth/driver/change-password
Authorization: Bearer {driverToken}
{
  "oldPassword": "password_from_email",
  "newPassword": "MyNewPassword@123"
}
# firstLogin set to false
```

---

## 📋 Endpoints Summary

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/owner/drivers` | POST | OWNER | Create driver |
| `/api/owner/drivers/verify` | POST | OWNER | Verify email |
| `/api/auth/driver/login` | POST | Public | Driver login |
| `/api/auth/driver/change-password` | POST | DRIVER | Change password |

---

## 🔑 Key Fields

### Driver Entity
- `firstLogin` - true until password changed
- `emailVerified` - true after OTP verification
- `photo` - Driver profile photo URL
- `licensePhoto` - License document URL
- `aadhaarPhoto` - Aadhaar document URL

---

## 📧 Email Flow

1. **OTP Email** → Driver receives OTP
2. **Credentials Email** → Driver receives username & password
3. **Driver logs in** → Changes password
4. **Normal login** → No password change required

---

## ⚠️ Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Email not verified" | OTP not verified | Owner must verify with OTP |
| "Invalid OTP" | Wrong/expired OTP | Check email, resend if needed |
| "Old password incorrect" | Wrong old password | Use password from email |
| OTP not received | Mail configuration issue | Check API key, sender email |

---

## 🔍 Database Queries

```sql
-- Check driver status
SELECT id, name, email, email_verified, first_login 
FROM drivers WHERE email = 'driver@example.com';

-- Check OTP
SELECT email, otp, verified, expiry_time 
FROM otp WHERE email = 'driver@example.com' 
ORDER BY created_at DESC LIMIT 1;

-- Update verification manually (if needed)
UPDATE drivers SET email_verified = true 
WHERE email = 'driver@example.com';
```

---

## 📦 Files Reference

| File | Purpose |
|------|---------|
| `DRIVER_CREATION_WORKFLOW.md` | Complete workflow documentation |
| `DRIVER_SETUP_GUIDE.md` | Setup instructions |
| `DRIVER_IMPLEMENTATION_SUMMARY.md` | Implementation details |
| `Driver-Creation-Workflow.postman_collection.json` | Postman tests |
| `database-migration-driver-verification.sql` | Database migration |

---

## 🛠️ Troubleshooting

**OTP not received?**
```bash
# Test mail delivery
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

**Check logs:**
```bash
tail -f logs/spring.log | grep -iE "email|otp|mail|smtp"
```

**Verify mail delivery:**
- Check the backend log for a mail send failure
- Confirm MAIL_USERNAME and MAIL_PASSWORD are a Gmail account and app password
- Verify sender email is authenticated

---

## 📞 Support

- **Documentation:** See `DRIVER_SETUP_GUIDE.md`
- **Email:** support@travelplatform.com
- **Logs:** `logs/spring.log`

---

## ✅ Checklist

- [ ] Database migration run
- [ ] MAIL_USERNAME / MAIL_PASSWORD configured
- [ ] Sender email verified
- [ ] Owner account created
- [ ] Application running
- [ ] Postman collection imported
- [ ] Test driver created
- [ ] OTP received
- [ ] Email verified
- [ ] Credentials received
- [ ] Password changed
- [ ] Login successful

---

**Version:** 1.0  
**Last Updated:** 2024  
**Status:** Production Ready
