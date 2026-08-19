# Payment System Flow Diagrams

## Complete Payment Workflow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        TRAVEL BOOKING PLATFORM                       │
│                          PAYMENT SYSTEM                              │
└─────────────────────────────────────────────────────────────────────┘

┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   USER   │     │  DRIVER  │     │  OWNER   │     │   UPI    │
│          │     │          │     │          │     │   APP    │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │  1. Create     │                │                │
     │  Booking       │                │                │
     ├───────────────>│                │                │
     │                │                │                │
     │                │  2. Accept     │                │
     │                │  Booking       │                │
     │<───────────────┤                │                │
     │                │                │                │
     │  3. Initiate   │                │                │
     │  Payment       │                │                │
     │  (UPI/CASH)    │                │                │
     ├────────────────┼────────────────┼───────────────>│
     │                │                │                │
     │  4. Get UPI    │                │                │
     │  Deep Link     │                │                │
     │<───────────────┼────────────────┼────────────────┤
     │                │                │                │
     │  5. Open UPI   │                │                │
     │  App & Pay     │                │                │
     ├───────────────>│                │                │
     │                │                │                │
     │                │  6. Call Owner │                │
     │                │  to Verify     │                │
     │                ├───────────────>│                │
     │                │                │                │
     │                │                │  7. Check      │
     │                │                │  Payment       │
     │                │                │  Received      │
     │                │                │<───────────────┤
     │                │                │                │
     │                │                │  8. Verify     │
     │                │                │  Payment       │
     │                │                │  in System     │
     │                │                │                │
     │                │                │  9. Trip       │
     │                │                │  COMPLETED     │
     │                │                │                │
     │                │                │ 10. RCM        │
     │                │                │  Calculation   │
     │                │                │                │
```

---

## UPI Payment Flow (Detailed)

```
┌─────────────────────────────────────────────────────────────────┐
│                      UPI PAYMENT FLOW                            │
└─────────────────────────────────────────────────────────────────┘

USER                    SYSTEM                  UPI APP
  │                       │                        │
  │  POST /payment        │                        │
  │  {method: "UPI"}      │                        │
  ├──────────────────────>│                        │
  │                       │                        │
  │                       │ Generate UPI Link      │
  │                       │ upi://pay?pa=...       │
  │                       │                        │
  │  Return UPI Link      │                        │
  │<──────────────────────┤                        │
  │                       │                        │
  │  Click UPI Link       │                        │
  ├───────────────────────┼───────────────────────>│
  │                       │                        │
  │                       │        Auto-Open       │
  │                       │        Google Pay      │
  │                       │        PhonePe         │
  │                       │        Paytm           │
  │                       │                        │
  │  Authenticate & Pay   │                        │
  ├───────────────────────┼───────────────────────>│
  │                       │                        │
  │  Payment Success      │                        │
  │<──────────────────────┼────────────────────────┤
  │                       │                        │
  │  Driver Calls Owner   │                        │
  │  to Verify            │                        │
  │                       │                        │
  │                       │  Owner Verifies        │
  │                       │  POST /verify          │
  │                       │<───────────────────────┤
  │                       │                        │
  │                       │  Trip COMPLETED        │
  │                       │  RCM Triggered         │
  │                       │                        │
```

---

## Cash Payment Flow (Detailed)

```
┌─────────────────────────────────────────────────────────────────┐
│                     CASH PAYMENT FLOW                            │
└─────────────────────────────────────────────────────────────────┘

USER                DRIVER              SYSTEM              OWNER
  │                   │                   │                   │
  │  POST /payment    │                   │                   │
  │  {method: "CASH"} │                   │                   │
  ├───────────────────┼──────────────────>│                   │
  │                   │                   │                   │
  │  Payment Created  │                   │                   │
  │  Status: PENDING  │                   │                   │
  │<──────────────────┼───────────────────┤                   │
  │                   │                   │                   │
  │  Pay Cash         │                   │                   │
  │  ₹2500            │                   │                   │
  ├──────────────────>│                   │                   │
  │                   │                   │                   │
  │                   │  POST /cash-payment                   │
  │                   │  {amount: 2500}   │                   │
  │                   ├──────────────────>│                   │
  │                   │                   │                   │
  │                   │  Cash Marked      │                   │
  │                   │  Status: PENDING  │                   │
  │                   │<──────────────────┤                   │
  │                   │                   │                   │
  │                   │  Inform Owner     │                   │
  │                   ├───────────────────┼──────────────────>│
  │                   │                   │                   │
  │                   │                   │  GET /pending     │
  │                   │                   │<──────────────────┤
  │                   │                   │                   │
  │                   │                   │  POST /verify     │
  │                   │                   │<──────────────────┤
  │                   │                   │                   │
  │                   │                   │  Trip COMPLETED   │
  │                   │                   │  RCM Triggered    │
  │                   │                   │                   │
```

---

## Payment Status State Machine

```
┌─────────────────────────────────────────────────────────────────┐
│                   PAYMENT STATUS FLOW                            │
└─────────────────────────────────────────────────────────────────┘

                    ┌──────────────┐
                    │   BOOKING    │
                    │  CONFIRMED   │
                    └──────┬───────┘
                           │
                           │ User Initiates Payment
                           │
                           ▼
                    ┌──────────────┐
                    │   PAYMENT    │
                    │   PENDING    │
                    └──────┬───────┘
                           │
                ┌──────────┴──────────┐
                │                     │
         UPI Payment           Cash Payment
                │                     │
                ▼                     ▼
        ┌──────────────┐      ┌──────────────┐
        │ User Pays    │      │ Driver Marks │
        │ via UPI App  │      │ Cash Received│
        └──────┬───────┘      └──────┬───────┘
               │                     │
               └──────────┬──────────┘
                          │
                          │ Driver Calls Owner
                          │
                          ▼
                   ┌──────────────┐
                   │    Owner     │
                   │   Verifies   │
                   │   Payment    │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │   PAYMENT    │
                   │   VERIFIED   │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │     TRIP     │
                   │  COMPLETED   │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │     RCM      │
                   │ CALCULATION  │
                   └──────────────┘
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SYSTEM ARCHITECTURE                           │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐
│   Frontend   │
│  (Mobile/Web)│
└──────┬───────┘
       │
       │ REST API
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│                      SPRING BOOT                              │
│                                                               │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │   User     │  │   Driver   │  │   Owner    │            │
│  │ Controller │  │ Controller │  │ Controller │            │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘            │
│        │               │               │                    │
│        └───────────────┼───────────────┘                    │
│                        │                                    │
│                        ▼                                    │
│              ┌─────────────────┐                           │
│              │ Payment Service │                           │
│              │                 │                           │
│              │ - UPI Link Gen  │                           │
│              │ - Payment Track │                           │
│              │ - Verification  │                           │
│              └────────┬────────┘                           │
│                       │                                    │
│                       ▼                                    │
│              ┌─────────────────┐                           │
│              │    Payment      │                           │
│              │   Repository    │                           │
│              └────────┬────────┘                           │
└───────────────────────┼─────────────────────────────────────┘
                        │
                        ▼
              ┌─────────────────┐
              │   PostgreSQL    │
              │                 │
              │  - payments     │
              │  - bookings     │
              │  - users        │
              │  - drivers      │
              └─────────────────┘
```

---

## UPI Deep Link Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                  UPI DEEP LINK FORMAT                            │
└─────────────────────────────────────────────────────────────────┘

upi://pay?pa=your-upi-id@yourbank&pn=Namma%20Journey&am=2500.00&cu=INR&tn=Booking%3A123e4567

│         │  │                        │ │                │  │       │  │                      │
│         │  │                        │ │                │  │       │  │                      │
│         │  └─ Payee UPI ID         │ └─ Payee Name    │  │       │  └─ Transaction Note   │
│         │                           │                  │  │       │                        │
│         └─ Payment Action           └─ Parameter       │  │       └─ Parameter             │
│                                                        │  │                                │
└─ UPI Protocol                                         │  └─ Currency                      │
                                                        │                                   │
                                                        └─ Amount                           │

┌─────────────────────────────────────────────────────────────────┐
│                    WHAT HAPPENS WHEN CLICKED                     │
└─────────────────────────────────────────────────────────────────┘

Mobile Device
     │
     │ User Clicks UPI Link
     │
     ▼
┌─────────────────┐
│  Android/iOS    │
│  Detects UPI    │
│  Protocol       │
└────────┬────────┘
         │
         │ Shows App Chooser
         │
         ▼
┌─────────────────────────────────────┐
│  Choose UPI App:                    │
│  ☑ Google Pay                       │
│  ☐ PhonePe                          │
│  ☐ Paytm                            │
│  ☐ BHIM                             │
└────────┬────────────────────────────┘
         │
         │ User Selects App
         │
         ▼
┌─────────────────┐
│  UPI App Opens  │
│  Pre-filled:    │
│  - To: Owner    │
│  - Amount: ₹2500│
│  - Note: BookID │
└────────┬────────┘
         │
         │ User Authenticates
         │ (PIN/Biometric)
         │
         ▼
┌─────────────────┐
│  Payment Done   │
│  ✓ Success      │
└─────────────────┘
```

---

## Database Schema Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                   DATABASE RELATIONSHIPS                         │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐
│    users     │
│──────────────│
│ id (UUID)    │◄─────────┐
│ name         │          │
│ email        │          │
│ phone        │          │
└──────────────┘          │
                          │
                          │ user_id (FK)
                          │
┌──────────────┐          │
│   drivers    │          │
│──────────────│          │
│ id (BIGINT)  │◄─────┐   │
│ name         │      │   │
│ mobile       │      │   │
│ license_no   │      │   │
└──────────────┘      │   │
                      │   │
                      │   │ driver_id (FK)
                      │   │
┌──────────────────────┐  │
│  travel_bookings     │  │
│──────────────────────│  │
│ id (UUID)            │◄─┼──┐
│ user_id (FK)         ├──┘  │
│ driver_id (FK)       ├─────┘
│ from_place           │
│ to_place             │
│ total_amount         │
│ status               │
└──────┬───────────────┘
       │
       │ booking_id (FK)
       │
       ▼
┌──────────────────────┐
│      payments        │
│──────────────────────│
│ id (UUID)            │
│ booking_id (FK)      │
│ user_id (FK)         │
│ driver_id (FK)       │
│ amount               │
│ payment_method       │
│ status               │
│ upi_transaction_id   │
│ payment_date         │
│ verified_date        │
└──────────────────────┘
```

---

## Security Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      SECURITY CHECKS                             │
└─────────────────────────────────────────────────────────────────┘

Request
  │
  ▼
┌─────────────────┐
│ JWT Token       │
│ Validation      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Role Check      │
│ USER/DRIVER/    │
│ OWNER           │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Booking         │
│ Ownership       │
│ Verification    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Booking Status  │
│ Check           │
│ (CONFIRMED?)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Duplicate       │
│ Payment Check   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Process         │
│ Payment         │
└─────────────────┘
```

---

## Complete User Journey

```
┌─────────────────────────────────────────────────────────────────┐
│                    COMPLETE USER JOURNEY                         │
└─────────────────────────────────────────────────────────────────┘

Day 1: Booking
├─ User searches for travel
├─ User creates booking
├─ System calculates route & price
└─ Booking Status: PENDING

Day 2: Confirmation
├─ Driver receives notification
├─ Driver accepts booking
└─ Booking Status: CONFIRMED

Day 3: Payment
├─ User initiates payment
├─ User selects UPI or CASH
│
├─ IF UPI:
│  ├─ User gets UPI deep link
│  ├─ User clicks link
│  ├─ UPI app opens
│  ├─ User pays
│  └─ Driver calls owner
│
└─ IF CASH:
   ├─ Driver collects cash
   └─ Driver marks in system

Day 4: Verification
├─ Owner checks payment
├─ Owner verifies in system
├─ Trip Status: COMPLETED
└─ RCM calculation triggered

Day 5: Settlement
├─ Calculate driver commission
├─ Calculate platform fee
└─ Generate revenue report
```

This visual documentation provides a complete understanding of the payment system flow!
