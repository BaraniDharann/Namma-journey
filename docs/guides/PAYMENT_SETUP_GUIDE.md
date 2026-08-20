# Payment System Setup Guide

## 🚀 Quick Setup (5 Minutes)

### Step 1: Run Database Migration
```bash
psql -U postgres -d travel_booking_db -f database-migration-payments.sql
```

### Step 2: Update Environment Variables
Edit `.env` file:
```env
OWNER_UPI_ID=your-upi-id@yourbank
OWNER_UPI_NAME=Namma Journey
```

### Step 3: Restart Application
```bash
mvn spring-boot:run
```

### Step 4: Test Payment Flow
```bash
# Run test script
test-payment-api.bat
```

✅ **Done! Payment system is ready.**

---

## 📋 Detailed Setup Instructions

### Prerequisites
- ✅ PostgreSQL database running
- ✅ Application running on port 8080
- ✅ User, Driver, Owner accounts created
- ✅ At least one booking created

---

## 1️⃣ Database Setup

### Option A: Using psql Command Line
```bash
# Connect to database
psql -U postgres -d travel_booking_db

# Run migration
\i database-migration-payments.sql

# Verify table created
\dt payments

# Check table structure
\d payments
```

### Option B: Using pgAdmin
1. Open pgAdmin
2. Connect to `travel_booking_db`
3. Open Query Tool
4. Copy contents of `database-migration-payments.sql`
5. Execute query
6. Verify `payments` table created

### Verify Migration Success
```sql
-- Check if table exists
SELECT * FROM payments LIMIT 1;

-- Should return empty result (no error)
```

---

## 2️⃣ Configuration Setup

### Update .env File
```env
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5433/postgres
DB_USERNAME=postgres
DB_PASSWORD=your_password

# JWT Configuration
JWT_SECRET=your_jwt_secret
JWT_EXPIRATION=86400000

# Payment Configuration (ADD THESE)
OWNER_UPI_ID=your-upi-id@yourbank
OWNER_UPI_NAME=Namma Journey

# Server Configuration
SERVER_PORT=8080
```

### Verify Configuration
```bash
# Check if environment variables are loaded
echo %OWNER_UPI_ID%
echo %OWNER_UPI_NAME%
```

---

## 3️⃣ Application Restart

### Stop Application
```bash
# Press Ctrl+C in terminal where app is running
```

### Clean Build
```bash
mvn clean install
```

### Start Application
```bash
mvn spring-boot:run
```

### Verify Application Started
```bash
# Check logs for:
# - "Started TravelPlatformApplication"
# - No errors related to Payment entities
```

---

## 4️⃣ Testing Setup

### Get Authentication Tokens

#### 1. Login as User
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"EMAIL\",\"email\":\"user@example.com\",\"password\":\"password123\"}"
```
**Save the token:** `USER_TOKEN`

#### 2. Login as Driver
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d "{\"mobile\":\"9123456789\",\"password\":\"YOUR_DRIVER_PASSWORD\"}"
```
**Save the token:** `DRIVER_TOKEN`

#### 3. Login as Owner
```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"owner@example.com\",\"password\":\"YOUR_OWNER_PASSWORD\"}"
```
**Save the token:** `OWNER_TOKEN`

---

## 5️⃣ Create Test Booking

### Create Booking
```bash
curl -X POST http://localhost:8080/api/user/{USER_ID}/bookings \
  -H "Authorization: Bearer {USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"userName\":\"Test User\",
    \"userPhone\":\"9876543210\",
    \"fromPlace\":\"Chennai\",
    \"toPlace\":\"Madurai\",
    \"fromDate\":\"2024-02-01\",
    \"toDate\":\"2024-02-03\",
    \"travelMembers\":4,
    \"acType\":\"AC\"
  }"
```
**Save:** `BOOKING_ID`

### Driver Accepts Booking
```bash
curl -X POST http://localhost:8080/api/driver/{DRIVER_ID}/bookings/{BOOKING_ID}/action \
  -H "Authorization: Bearer {DRIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"action\":\"ACCEPT\"}"
```

---

## 6️⃣ Test Payment Flow

### Test 1: UPI Payment

#### Step 1: User Initiates UPI Payment
```bash
curl -X POST http://localhost:8080/api/user/{USER_ID}/bookings/{BOOKING_ID}/payment \
  -H "Authorization: Bearer {USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"paymentMethod\":\"UPI\"}"
```

**Expected Response:**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "upiDeepLink": "upi://pay?pa=your-upi-id@yourbank&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567",
  "message": "Open UPI app to complete payment..."
}
```

**Save:** `PAYMENT_ID`

#### Step 2: Owner Views Pending Payments
```bash
curl -X GET http://localhost:8080/api/owner/payments/pending \
  -H "Authorization: Bearer {OWNER_TOKEN}"
```

#### Step 3: Owner Verifies Payment
```bash
curl -X POST http://localhost:8080/api/owner/payments/{PAYMENT_ID}/verify \
  -H "Authorization: Bearer {OWNER_TOKEN}"
```

**Expected Response:**
```json
{
  "status": "VERIFIED",
  "message": "Payment verified. Trip completed. RCM calculation will be processed."
}
```

---

### Test 2: Cash Payment

#### Step 1: User Initiates Cash Payment
```bash
curl -X POST http://localhost:8080/api/user/{USER_ID}/bookings/{BOOKING_ID}/payment \
  -H "Authorization: Bearer {USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"paymentMethod\":\"CASH\"}"
```

#### Step 2: Driver Marks Cash Received
```bash
curl -X POST http://localhost:8080/api/driver/{DRIVER_ID}/bookings/{BOOKING_ID}/cash-payment \
  -H "Authorization: Bearer {DRIVER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"amountReceived\":2500.00}"
```

#### Step 3: Owner Verifies Payment
```bash
curl -X POST http://localhost:8080/api/owner/payments/{PAYMENT_ID}/verify \
  -H "Authorization: Bearer {OWNER_TOKEN}"
```

---

## 7️⃣ Verify Database Records

### Check Payment Record
```sql
SELECT * FROM payments WHERE booking_id = 'YOUR_BOOKING_ID';
```

### Check Booking Status
```sql
SELECT id, status FROM travel_bookings WHERE id = 'YOUR_BOOKING_ID';
```

**Expected:** `status = 'COMPLETED'`

---

## 🧪 Automated Testing Script

### Update test-payment-api.bat
```batch
set USER_TOKEN=your_actual_user_token
set DRIVER_TOKEN=your_actual_driver_token
set OWNER_TOKEN=your_actual_owner_token
set USER_ID=your_actual_user_id
set BOOKING_ID=your_actual_booking_id
set DRIVER_ID=your_actual_driver_id
set PAYMENT_ID=your_actual_payment_id
```

### Run Tests
```bash
test-payment-api.bat
```

---

## 🔍 Troubleshooting

### Issue 1: Table Not Found
**Error:** `relation "payments" does not exist`

**Solution:**
```bash
# Re-run migration
psql -U postgres -d travel_booking_db -f database-migration-payments.sql
```

---

### Issue 2: Configuration Not Loaded
**Error:** `Could not resolve placeholder 'payment.upi.id'`

**Solution:**
1. Check `.env` file has `OWNER_UPI_ID`
2. Restart application
3. Verify with: `echo %OWNER_UPI_ID%`

---

### Issue 3: Booking Not Confirmed
**Error:** `Booking must be confirmed before payment`

**Solution:**
```bash
# Driver must accept booking first
POST /api/driver/{driverId}/bookings/{bookingId}/action
Body: {"action":"ACCEPT"}
```

---

### Issue 4: Unauthorized Access
**Error:** `401 Unauthorized`

**Solution:**
1. Check token is valid
2. Check token not expired (24 hours)
3. Re-login to get new token

---

### Issue 5: Payment Already Exists
**Error:** `Payment already completed`

**Solution:**
- Each booking can only have one payment
- Create a new booking for testing

---

## ✅ Verification Checklist

- [ ] Database migration successful
- [ ] `payments` table exists
- [ ] Environment variables configured
- [ ] Application started without errors
- [ ] User token obtained
- [ ] Driver token obtained
- [ ] Owner token obtained
- [ ] Test booking created
- [ ] Driver accepted booking
- [ ] UPI payment tested successfully
- [ ] Cash payment tested successfully
- [ ] Owner can view pending payments
- [ ] Owner can verify payments
- [ ] Booking status changes to COMPLETED
- [ ] Payment records in database

---

## 📱 Mobile Testing (UPI Deep Link)

### Test on Android/iOS Device

1. **Get UPI Deep Link:**
   - Initiate payment via API
   - Copy `upiDeepLink` from response

2. **Send Link to Mobile:**
   - Email link to yourself
   - Or use QR code generator

3. **Click Link on Mobile:**
   - Should auto-open UPI app
   - Should pre-fill amount and recipient

4. **Complete Payment:**
   - Authenticate in UPI app
   - Confirm payment

5. **Verify in System:**
   - Driver calls owner
   - Owner verifies payment

---

## 🎯 Success Criteria

### UPI Payment Success
✅ User gets UPI deep link  
✅ Link opens UPI app on mobile  
✅ Amount and recipient pre-filled  
✅ Payment completes in UPI app  
✅ Owner can verify payment  
✅ Trip status changes to COMPLETED  

### Cash Payment Success
✅ User initiates cash payment  
✅ Driver can mark cash received  
✅ Owner can verify payment  
✅ Trip status changes to COMPLETED  

---

## 📚 Next Steps

1. **Test in Production:**
   - Use real UPI ID
   - Test with actual payments
   - Monitor payment flow

2. **Implement RCM Calculation:**
   - Calculate driver commission
   - Calculate platform fee
   - Generate revenue reports

3. **Add Notifications:**
   - Email on payment received
   - SMS to driver on payment
   - Push notification to user

4. **Analytics Dashboard:**
   - Payment success rate
   - UPI vs Cash distribution
   - Daily/Monthly revenue

---

## 🆘 Support

**Documentation:**
- [Payment API Documentation](../api/PAYMENT_API_DOCUMENTATION.md)
- [Payment Flow Diagrams](../api/PAYMENT_FLOW_DIAGRAMS.md)
- [Quick Reference](../api/PAYMENT_QUICK_REFERENCE.md)

**Need Help?**
- Check troubleshooting section above
- Review error logs in console
- Verify database records

---

## 🎉 Setup Complete!

Your payment system is now ready to process UPI and cash payments!

**Owner UPI ID:** your-upi-id@yourbank  
**Payment Methods:** UPI, CASH  
**Cost:** FREE (no payment gateway fees)
