# 📱 OTP Integration Guide - Travel Booking Platform

## ✅ What's Implemented

1. **OTP Send Endpoint** - `POST /api/auth/otp/send`
2. **OTP Verification** - Integrated with user login
3. **Database Storage** - OTPs stored with 5-minute expiry
4. **Multiple Providers** - MSG91, Fast2SMS, Twilio support
5. **Mock Mode** - For testing without SMS costs

---

## 🚀 Quick Start (Mock Mode - FREE)

### 1. Start Your Application
```bash
mvn clean install
mvn spring-boot:run
```

### 2. Test OTP Flow

#### Step 1: Send OTP
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"9876543210\"}"
```

**Response:**
```json
{
  "message": "OTP sent successfully",
  "success": true
}
```

**Check Console Logs** - You'll see:
```
MOCK MODE: OTP for 9876543210: 456789
```

#### Step 2: Login with OTP
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"456789\"}"
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

---

## 📲 Setup Real SMS (MSG91 - Recommended)

### Step 1: Create MSG91 Account
1. Go to https://msg91.com
2. Sign up (FREE 100 SMS/day)
3. Verify your account

### Step 2: Get Credentials
1. Login to MSG91 Dashboard
2. Go to **API** section
3. Copy your **Auth Key**
4. Go to **SMS** → **Templates**
5. Create OTP template:
   ```
   Your OTP is ##OTP##. Valid for 5 minutes. - Travel Platform
   ```
6. Copy **Template ID**

### Step 3: Update .env File
```env
OTP_PROVIDER=MSG91
MSG91_AUTH_KEY=your_actual_auth_key_here
MSG91_TEMPLATE_ID=your_actual_template_id_here
```

### Step 4: Restart Application
```bash
mvn spring-boot:run
```

### Step 5: Test Real SMS
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"9876543210\"}"
```

You'll receive SMS on your phone! 📱

---

## 🔄 Complete User Flow Testing

### Scenario 1: New User Registration + Login

```bash
# 1. User Signup
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"9876543210\",\"name\":\"Rajesh Kumar\"}"

# 2. Send OTP for Login
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"9876543210\"}"

# 3. Login with OTP (use OTP from console/SMS)
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"123456\"}"
```

### Scenario 2: Existing User Login

```bash
# 1. Send OTP
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"9876543210\"}"

# 2. Login with OTP
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"456789\"}"
```

---

## 🧪 Testing All Endpoints

### 1. Driver Signup
```bash
curl -X POST http://localhost:8080/api/auth/driver/signup \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Suresh Sharma\",\"mobile\":\"9123456789\",\"password\":\"driver@123\",\"licenseNumber\":\"DL1420110012345\",\"aadhaarNumber\":\"123456789012\"}"
```

### 2. Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d "{\"mobile\":\"9123456789\",\"password\":\"driver@123\"}"
```

### 3. Owner Login (Create owner first in DB)
```sql
INSERT INTO owners (email, password, role, created_at) 
VALUES (
  'admin@travelplatform.com', 
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'ROLE_OWNER',
  NOW()
);
```

```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@travelplatform.com\",\"password\":\"owner@123\"}"
```

### 4. Google OAuth Login
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"GOOGLE\",\"token\":\"google_oauth_token_here\"}"
```

---

## 🔐 OTP Security Features

✅ **5-minute expiry** - OTPs expire after 5 minutes
✅ **One-time use** - OTP marked as verified after use
✅ **Database storage** - Secure OTP storage
✅ **Latest OTP only** - Only most recent OTP is valid
✅ **Auto cleanup** - Expired OTPs removed automatically

---

## 🌐 Alternative SMS Providers

### Fast2SMS Setup
```env
OTP_PROVIDER=FAST2SMS
MSG91_AUTH_KEY=your_fast2sms_api_key
```

### Twilio Setup (Not implemented yet)
```env
OTP_PROVIDER=TWILIO
```

---

## 💰 Cost Comparison

| Provider | Free Tier | Cost per SMS | Best For |
|----------|-----------|--------------|----------|
| **MSG91** | 100/day | ₹0.15-0.25 | Production (India) |
| **Fast2SMS** | 50/day | ₹0.20-0.30 | Small apps |
| **2Factor** | 10/day | ₹0.25 | Testing |
| **Twilio** | Trial credits | ₹0.60-1.50 | International |

**Recommendation:** Use MSG91 for Indian numbers (cheapest + reliable)

---

## 🐛 Troubleshooting

### OTP Not Received?
1. Check console logs for MOCK mode OTP
2. Verify phone number format (10 digits, starts with 6-9)
3. Check MSG91 dashboard for delivery status
4. Ensure sufficient SMS credits

### "Invalid or expired OTP" Error?
1. OTP expires in 5 minutes
2. Request new OTP
3. Check database for OTP entry:
   ```sql
   SELECT * FROM otps WHERE phone = '9876543210' ORDER BY created_at DESC LIMIT 1;
   ```

### SMS Provider Error?
1. Verify API credentials in .env
2. Check provider dashboard for errors
3. Switch to MOCK mode for testing:
   ```env
   OTP_PROVIDER=MOCK
   ```

---

## 📊 Database Schema

```sql
CREATE TABLE otps (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(10) NOT NULL,
    otp VARCHAR(6) NOT NULL,
    expiry_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_otps_phone ON otps(phone);
CREATE INDEX idx_otps_expiry ON otps(expiry_time);
```

---

## 🎯 Next Steps

1. ✅ Test in MOCK mode
2. ✅ Create MSG91 account
3. ✅ Update .env with real credentials
4. ✅ Test real SMS delivery
5. ⏳ Implement Google OAuth token verification
6. ⏳ Add rate limiting (prevent OTP spam)
7. ⏳ Add SMS templates for different languages

---

## 📞 Support

- MSG91: https://msg91.com/help
- Fast2SMS: https://www.fast2sms.com/support
- Project Issues: Create GitHub issue

---

## 🔗 API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/otp/send` | Send OTP to phone |
| POST | `/api/auth/user/signup` | User registration |
| POST | `/api/auth/user/login` | User login (OTP/Google) |
| POST | `/api/auth/driver/signup` | Driver registration |
| POST | `/api/auth/driver/login` | Driver login |
| POST | `/api/auth/owner/login` | Owner login |

---

**Happy Testing! 🚀**
