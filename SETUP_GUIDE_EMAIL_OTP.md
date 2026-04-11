# Quick Setup Guide - Email OTP Migration

## What Changed?

### ✅ Completed Changes:
1. **Phone OTP → Email OTP**: All authentication now uses email instead of phone
2. **SendGrid Integration**: Professional email delivery service
3. **User Login Types**: EMAIL and GOOGLE (removed PHONE)
4. **Database Schema**: Updated to use email fields
5. **API Endpoints**: Updated to accept email instead of phone

---

## Setup Steps

### 1. Update Dependencies
```bash
mvn clean install
```
This will download the SendGrid Java library.

### 2. Configure SendGrid
Your `.env` file is already updated with:
```env
SENDGRID_API_KEY=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
SENDGRID_FROM_EMAIL=noreply@nammajourney.com
SENDGRID_FROM_NAME=Namma Journey
OTP_EXPIRY_MINUTES=5
```

**Important**: Verify your sender email in SendGrid:
- Go to https://app.sendgrid.com/settings/sender_auth
- Verify `noreply@nammajourney.com` or use your verified email

### 3. Update Database
Run the migration script:
```bash
psql -U postgres -d namma_journey -f database-migration-email.sql
```

Or manually in pgAdmin/DBeaver:
```sql
-- Remove phone column from users
ALTER TABLE users DROP COLUMN IF EXISTS phone;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- Update otps table
ALTER TABLE otps DROP COLUMN IF EXISTS phone;
ALTER TABLE otps ADD COLUMN IF NOT EXISTS email VARCHAR(255) NOT NULL;
```

### 4. Start Application
```bash
mvn spring-boot:run
```

---

## Testing

### Test 1: Send OTP
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"your-email@example.com\"}"
```

Check your email inbox for the OTP!

### Test 2: User Signup
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@example.com\",\"name\":\"Test User\"}"
```

### Test 3: User Login with OTP
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"EMAIL\",\"email\":\"test@example.com\",\"otp\":\"123456\"}"
```

---

## API Changes Summary

### Old (Phone OTP):
```json
POST /api/auth/otp/send
{
  "phone": "9876543210"
}

POST /api/auth/user/signup
{
  "phone": "9876543210",
  "name": "User"
}

POST /api/auth/user/login
{
  "loginType": "PHONE",
  "phone": "9876543210",
  "otp": "123456"
}
```

### New (Email OTP):
```json
POST /api/auth/otp/send
{
  "email": "user@example.com"
}

POST /api/auth/user/signup
{
  "email": "user@example.com",
  "name": "User"
}

POST /api/auth/user/login
{
  "loginType": "EMAIL",
  "email": "user@example.com",
  "otp": "123456"
}
```

---

## Files Modified

### Backend Files:
- ✅ `UserSignupRequest.java` - Changed phone to email
- ✅ `UserLoginRequest.java` - Changed phone to email
- ✅ `User.java` - Removed phone, made email required, changed LoginType
- ✅ `Otp.java` - Changed phone to email
- ✅ `OtpRepository.java` - Updated methods for email
- ✅ `UserRepository.java` - Removed phone methods
- ✅ `OtpService.java` - Complete rewrite for SendGrid
- ✅ `UserAuthService.java` - Updated to use email
- ✅ `AuthController.java` - Updated OTP endpoint
- ✅ `application.yml` - Added SendGrid config
- ✅ `.env` - Added SendGrid credentials
- ✅ `pom.xml` - Added SendGrid dependency

### New Files:
- ✅ `OtpSendRequest.java` - New DTO for email OTP
- ✅ `database-migration-email.sql` - Migration script
- ✅ `README_EMAIL_OTP.md` - Updated documentation
- ✅ `test-email-api.bat` - Test script
- ✅ `Travel-Booking-Email-OTP.postman_collection.json` - Postman collection
- ✅ `SETUP_GUIDE_EMAIL_OTP.md` - This file

---

## Troubleshooting

### Issue: SendGrid 401 Unauthorized
**Solution**: Check your API key is correct and has "Mail Send" permissions

### Issue: Email not received
**Solution**: 
1. Check spam folder
2. Verify sender email in SendGrid
3. Check SendGrid activity logs: https://app.sendgrid.com/email_activity

### Issue: Database error about phone column
**Solution**: Run the migration script to update schema

### Issue: Compilation errors
**Solution**: Run `mvn clean install` to download SendGrid dependency

---

## SendGrid Free Tier Limits
- 100 emails per day
- Perfect for development and testing
- Upgrade for production use

---

## Next Steps

1. ✅ Test OTP sending to your email
2. ✅ Test user signup and login flow
3. ✅ Verify Google OAuth still works
4. ✅ Update frontend to use email fields
5. ✅ Deploy to production

---

## Support

For issues:
1. Check application logs: `tail -f logs/application.log`
2. Check SendGrid activity: https://app.sendgrid.com/email_activity
3. Review this guide

---

## Production Checklist

Before going to production:
- [ ] Verify sender domain in SendGrid
- [ ] Set up DKIM/SPF records
- [ ] Upgrade SendGrid plan if needed
- [ ] Use environment-specific API keys
- [ ] Enable SendGrid email validation
- [ ] Set up monitoring and alerts
- [ ] Test email deliverability
- [ ] Update frontend application
- [ ] Update mobile apps (if any)
- [ ] Notify existing users about the change

---

Good luck! 🚀
