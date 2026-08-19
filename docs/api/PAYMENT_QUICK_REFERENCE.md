# Payment System - Quick Reference

## Setup

1. **Run Database Migration:**
```bash
psql -U postgres -d travel_booking_db -f database-migration-payments.sql
```

2. **Update .env:**
```env
OWNER_UPI_ID=your-upi-id@yourbank
OWNER_UPI_NAME=Namma Journey
```

3. **Restart Application:**
```bash
mvn spring-boot:run
```

---

## API Quick Reference

### User Initiates UPI Payment
```bash
POST /api/user/{userId}/bookings/{bookingId}/payment
Body: {"paymentMethod":"UPI"}
```

### User Initiates Cash Payment
```bash
POST /api/user/{userId}/bookings/{bookingId}/payment
Body: {"paymentMethod":"CASH"}
```

### Driver Marks Cash Received
```bash
POST /api/driver/{driverId}/bookings/{bookingId}/cash-payment
Body: {"amountReceived":2500.00}
```

### Owner Views Pending Payments
```bash
GET /api/owner/payments/pending
```

### Owner Verifies Payment
```bash
POST /api/owner/payments/{paymentId}/verify
```

### User Views Payment History
```bash
GET /api/user/{userId}/payments
```

---

## Payment Flow Summary

### UPI Flow
1. User → Initiate UPI payment → Get deep link
2. User → Click link → Opens UPI app → Pay
3. Driver → Call owner → Confirm payment received
4. Owner → Verify payment → Trip completed

### Cash Flow
1. User → Initiate cash payment
2. Driver → Collect cash → Mark received
3. Owner → Verify payment → Trip completed

---

## UPI Deep Link Example

```
upi://pay?pa=your-upi-id@yourbank&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567
```

**Opens:** Google Pay / PhonePe / Paytm automatically

---

## Testing Sequence

```bash
# 1. User creates booking
POST /api/user/{userId}/bookings

# 2. Driver accepts booking
POST /api/driver/{driverId}/bookings/{bookingId}/action
Body: {"action":"ACCEPT"}

# 3. User initiates payment
POST /api/user/{userId}/bookings/{bookingId}/payment
Body: {"paymentMethod":"UPI"}

# 4. Owner checks pending payments
GET /api/owner/payments/pending

# 5. Owner verifies payment
POST /api/owner/payments/{paymentId}/verify
```

---

## Key Features

✅ Free UPI deep linking (no payment gateway)  
✅ Auto-opens UPI apps (Google Pay, PhonePe, Paytm)  
✅ Cash payment support  
✅ Owner verification workflow  
✅ Trip completion on payment verification  
✅ RCM calculation trigger  

---

## Configuration

**UPI ID:** your-upi-id@yourbank  
**UPI Name:** Namma Journey  
**Payment Methods:** UPI, CASH  
**Payment Status:** PENDING, VERIFIED, FAILED  

---

## Database Tables

- `payments` - Payment records
- `travel_bookings` - Booking status updated to COMPLETED
- Foreign keys to `users`, `drivers`, `bookings`

---

## Error Prevention

- ✅ Only CONFIRMED bookings can be paid
- ✅ One payment per booking
- ✅ Only assigned driver can mark cash
- ✅ Only owner can verify payments
- ✅ Payment verification completes trip

---

## Documentation

📚 **Full Documentation:** [PAYMENT_API_DOCUMENTATION.md](PAYMENT_API_DOCUMENTATION.md)
