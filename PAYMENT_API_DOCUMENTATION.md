# Payment System API Documentation

## Overview
Two payment methods for travel bookings:
1. **UPI Payment** - User pays via UPI apps (Google Pay, PhonePe, Paytm) to owner's UPI ID
2. **Cash Payment** - Driver collects cash and marks it in the system

After payment verification by owner, trip is marked as COMPLETED and RCM calculation is triggered.

---

## Payment Flow

### UPI Payment Flow
```
1. User creates booking → Status: PENDING
2. Driver accepts booking → Status: CONFIRMED
3. User initiates UPI payment → Gets UPI deep link
4. User opens UPI app (auto-opens Google Pay/PhonePe/Paytm)
5. User completes payment in UPI app
6. Driver calls owner to verify payment
7. Owner checks payment received
8. Owner verifies payment in system → Trip: COMPLETED
9. RCM calculation triggered
```

### Cash Payment Flow (UPDATED)
```
1. User creates booking → Status: PENDING
2. Driver accepts booking → Status: CONFIRMED
3. User initiates cash payment
4. Driver collects cash from user
5. Driver marks cash received in system → Payment: VERIFIED
6. Trip automatically completed → Trip: COMPLETED
7. RCM calculation triggered
```

---

## API Endpoints

### 1. INITIATE PAYMENT (User)
**Endpoint:** `POST /api/user/{userId}/bookings/{bookingId}/payment`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body (UPI):**
```json
{
  "paymentMethod": "UPI"
}
```

**Request Body (Cash):**
```json
{
  "paymentMethod": "CASH"
}
```

**Response (200 OK) - UPI:**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "UPI",
  "status": "PENDING",
  "upiDeepLink": "upi://pay?pa=sarasarathy86@okicici&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567-e89b-12d3-a456-426614174000",
  "createdAt": "2024-01-15T10:30:00",
  "message": "Open UPI app to complete payment. After payment, driver will call owner to verify."
}
```

**Response (200 OK) - Cash:**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "CASH",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00",
  "message": "Pay cash to driver. Driver will mark payment received."
}
```

---

### 2. MARK CASH RECEIVED (Driver)
**Endpoint:** `POST /api/driver/{driverId}/bookings/{bookingId}/cash-payment`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "amountReceived": 2500.00
}
```

**Response (200 OK):**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "CASH",
  "status": "VERIFIED",
  "message": "Cash payment received. Trip completed. RCM calculation will be processed."
}
```

---

### 3. VIEW PENDING PAYMENTS (Owner)
**Endpoint:** `GET /api/owner/payments/pending`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
[
  {
    "paymentId": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "amount": 2500.00,
    "paymentMethod": "UPI",
    "status": "PENDING",
    "createdAt": "2024-01-15T10:30:00"
  },
  {
    "paymentId": "876e5432-d10a-11c2-9345-315503063888",
    "bookingId": "234f5678-f90c-23e4-b567-537725285111",
    "amount": 3200.00,
    "paymentMethod": "CASH",
    "status": "PENDING",
    "createdAt": "2024-01-15T11:00:00"
  }
]
```

---

### 4. VERIFY PAYMENT (Owner)
**Endpoint:** `POST /api/owner/payments/{paymentId}/verify`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
{
  "paymentId": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 2500.00,
  "paymentMethod": "UPI",
  "status": "VERIFIED",
  "message": "Payment verified. Trip completed. RCM calculation will be processed."
}
```

---

### 5. VIEW USER PAYMENTS (User)
**Endpoint:** `GET /api/user/{userId}/payments`  
**Authorization:** Bearer token with `ROLE_USER`

**Response (200 OK):**
```json
[
  {
    "paymentId": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "amount": 2500.00,
    "paymentMethod": "UPI",
    "status": "VERIFIED",
    "createdAt": "2024-01-15T10:30:00"
  }
]
```

---

## UPI Deep Link Format

The system generates UPI deep links that automatically open UPI apps:

```
upi://pay?pa={UPI_ID}&pn={NAME}&am={AMOUNT}&cu=INR&tn={NOTE}
```

**Parameters:**
- `pa` - Payee UPI ID (sarasarathy86@okicici)
- `pn` - Payee Name (Namma Journey)
- `am` - Amount (e.g., 2500.00)
- `cu` - Currency (INR)
- `tn` - Transaction Note (Booking ID)

**Example:**
```
upi://pay?pa=sarasarathy86@okicici&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567
```

When user clicks this link on mobile:
- ✅ Auto-opens Google Pay / PhonePe / Paytm
- ✅ Pre-fills amount and recipient
- ✅ User just needs to authenticate and confirm

---

## Configuration

### Environment Variables (.env)
```env
OWNER_UPI_ID=sarasarathy86@okicici
OWNER_UPI_NAME=Namma Journey
```

### Application Configuration (application.yml)
```yaml
payment:
  upi:
    id: ${OWNER_UPI_ID:sarasarathy86@okicici}
    name: ${OWNER_UPI_NAME:Namma Journey}
```

---

## Database Schema

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    user_id UUID NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL, -- 'UPI' or 'CASH'
    status VARCHAR(20) NOT NULL, -- 'PENDING', 'VERIFIED', 'FAILED'
    upi_transaction_id VARCHAR(255),
    driver_id BIGINT,
    payment_date TIMESTAMP,
    verified_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (booking_id) REFERENCES travel_bookings(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
);
```

---

## Testing with cURL

### 1. Initiate UPI Payment
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/234f5678-f90c-23e4-b567-537725285111/payment \
  -H "Authorization: Bearer YOUR_USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"paymentMethod":"UPI"}'
```

### 2. Initiate Cash Payment
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/234f5678-f90c-23e4-b567-537725285111/payment \
  -H "Authorization: Bearer YOUR_USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"paymentMethod":"CASH"}'
```

### 3. Driver Marks Cash Received
```bash
curl -X POST http://localhost:8080/api/driver/1/bookings/234f5678-f90c-23e4-b567-537725285111/cash-payment \
  -H "Authorization: Bearer YOUR_DRIVER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amountReceived":2500.00}'
```

### 4. Owner Views Pending Payments
```bash
curl -X GET http://localhost:8080/api/owner/payments/pending \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

### 5. Owner Verifies Payment
```bash
curl -X POST http://localhost:8080/api/owner/payments/987e6543-e21b-12d3-a456-426614174999/verify \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

---

## Business Rules

1. **Payment can only be initiated for CONFIRMED bookings**
2. **One payment per booking** (duplicate payments prevented)
3. **Only assigned driver can mark cash received**
4. **Only owner can verify payments**
5. **Payment verification automatically completes trip**
6. **Trip completion triggers RCM calculation**

---

## Error Handling

### Booking Not Confirmed (400)
```json
{
  "error": "Booking must be confirmed before payment"
}
```

### Payment Already Completed (400)
```json
{
  "error": "Payment already completed"
}
```

### Unauthorized Driver (403)
```json
{
  "error": "Unauthorized: Booking not assigned to you"
}
```

### Invalid Payment Method (400)
```json
{
  "error": "Payment method must be UPI or CASH"
}
```

---

## Advantages of This Solution

✅ **Free** - No payment gateway fees  
✅ **Simple** - Direct UPI transfer to owner  
✅ **Fast** - Instant UPI payments  
✅ **Flexible** - Supports both UPI and cash  
✅ **Secure** - Uses official UPI protocol  
✅ **Universal** - Works with all UPI apps  
✅ **Trackable** - Complete payment history  
✅ **Owner Control** - Manual verification prevents fraud  

---

## Next Steps

1. Run database migration: `database-migration-payments.sql`
2. Update `.env` file with UPI details
3. Restart application
4. Test payment flow with Postman/cURL
5. Implement RCM calculation logic (future enhancement)

---

## RCM Calculation (Future)

After payment verification, the system will calculate:
- **Total Revenue** from trip
- **Driver Commission** (percentage)
- **Platform Fee**
- **Net Revenue** for owner

This will be implemented in the next phase.
