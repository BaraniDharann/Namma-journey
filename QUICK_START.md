# 🚀 QUICK START - Test Your API Now!

## ⚡ Start Application (5 seconds)

```bash
mvn spring-boot:run
```

Wait for: `Started TravelPlatformApplication`

---

## 📱 Test OTP Flow (Copy-Paste Ready)

### Step 1: Send OTP
```bash
curl -X POST http://localhost:8080/api/auth/otp/send -H "Content-Type: application/json" -d "{\"phone\":\"9876543210\"}"
```

**✅ Expected Response:**
```json
{"message":"OTP sent successfully","success":true}
```

**📋 Check Console** - You'll see:
```
MOCK MODE: OTP for 9876543210: 456123
```

---

### Step 2: User Signup (First Time)
```bash
curl -X POST http://localhost:8080/api/auth/user/signup -H "Content-Type: application/json" -d "{\"phone\":\"9876543210\",\"name\":\"Rajesh Kumar\"}"
```

---

### Step 3: Login with OTP
**Replace `456123` with OTP from console**

```bash
curl -X POST http://localhost:8080/api/auth/user/login -H "Content-Type: application/json" -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"456123\"}"
```

**✅ Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-...",
  "message": "Authentication successful"
}
```

---

## 🎯 All Endpoints Quick Test

### 1️⃣ Driver Signup
```bash
curl -X POST http://localhost:8080/api/auth/driver/signup -H "Content-Type: application/json" -d "{\"name\":\"Suresh Sharma\",\"mobile\":\"9123456789\",\"password\":\"driver@123\",\"licenseNumber\":\"DL1420110012345\",\"aadhaarNumber\":\"123456789012\"}"
```

### 2️⃣ Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login -H "Content-Type: application/json" -d "{\"mobile\":\"9123456789\",\"password\":\"driver@123\"}"
```

### 3️⃣ Owner Login (Create in DB first)
```sql
INSERT INTO owners (email, password, role, created_at) 
VALUES ('admin@travelplatform.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ROLE_OWNER', NOW());
```

```bash
curl -X POST http://localhost:8080/api/auth/owner/login -H "Content-Type: application/json" -d "{\"email\":\"admin@travelplatform.com\",\"password\":\"owner@123\"}"
```

---

## 📲 Setup Real SMS (MSG91)

### 1. Create Account
- Go to: https://msg91.com
- Sign up (FREE 100 SMS/day)

### 2. Get Credentials
- Dashboard → API → Copy **Auth Key**
- SMS → Templates → Create template → Copy **Template ID**

### 3. Update .env
```env
OTP_PROVIDER=MSG91
MSG91_AUTH_KEY=your_auth_key_here
MSG91_TEMPLATE_ID=your_template_id_here
```

### 4. Restart & Test
```bash
mvn spring-boot:run
```

Now OTPs will be sent via SMS! 📱

---

## 🛠️ Alternative Testing Methods

### Option 1: Windows Batch File
```bash
scripts/test-api.bat
```

### Option 2: Postman
1. Import: `Travel_Booking_API.postman_collection.json`
2. Run requests in order

### Option 3: Manual cURL
See `OTP_SETUP_GUIDE.md` for detailed commands

---

## 🐛 Common Issues

### "Connection refused"
- ✅ Check if app is running: `mvn spring-boot:run`
- ✅ Verify port 8080 is free

### "Invalid or expired OTP"
- ✅ Use OTP from console logs (MOCK mode)
- ✅ OTP expires in 5 minutes
- ✅ Request new OTP

### "Phone number already registered"
- ✅ Use different phone number
- ✅ Or skip signup, directly login

---

## 📊 What's Working Now

✅ OTP Generation (6-digit random)
✅ OTP Storage (Database with expiry)
✅ OTP Verification (One-time use)
✅ SMS Integration (MSG91/Fast2SMS ready)
✅ Mock Mode (Free testing)
✅ User Authentication (Phone OTP)
✅ Driver Authentication (Password)
✅ Owner Authentication (Email/Password)
✅ Google OAuth (Token-based)

---

## 🎯 Next Steps

1. ✅ Test in MOCK mode (FREE)
2. 📱 Setup MSG91 for real SMS
3. 🔐 Implement Google token verification
4. 🚦 Add rate limiting
5. 🌍 Multi-language SMS templates

---

## 📞 SMS Provider Comparison

| Provider | Free Tier | Cost/SMS | Setup Time |
|----------|-----------|----------|------------|
| **MSG91** | 100/day | ₹0.15 | 5 min |
| **Fast2SMS** | 50/day | ₹0.20 | 5 min |
| **2Factor** | 10/day | ₹0.25 | 5 min |
| **Twilio** | Trial | ₹0.60 | 10 min |

**Recommendation:** MSG91 (Best for India)

---

## 📚 Documentation Files

- `OTP_SETUP_GUIDE.md` - Complete setup guide
- `README.md` - Project overview
- `scripts/test-api.bat` - Windows testing script
- `scripts/test-api.sh` - Linux/Mac testing script
- `Travel_Booking_API.postman_collection.json` - Postman collection

---

**🎉 You're Ready to Test! Start with Step 1 above.**
