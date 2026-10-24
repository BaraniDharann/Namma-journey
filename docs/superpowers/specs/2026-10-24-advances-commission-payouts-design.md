# Trip Advances, City Commission and Driver Payouts — Design

**Status:** decisions approved in chat on 2026-10-24.
**Parts:** 2 and 3 of the driver programme ([part 1](2026-10-24-driver-self-signup-design.md)).
Razorpay checkout, refunds and the outbox are already built.

## Decisions

| Question | Decision |
|---|---|
| Trip advance | **20 % online, required.** A Trip is held for the passenger until the advance is paid. |
| Advance on cancellation | **Full refund until 24 h before the trip day starts**; after that the advance is kept, but only if the trip was CONFIRMED (a car was actually blocked). |
| City commission | **Per city, set by the owner, default 10 %.** Frozen onto each ride when it completes. |
| Driver payouts | **Owner pays by UPI; the app keeps the ledger.** Drivers add a payout UPI ID, stored encrypted. |

## Part 2: Trip advances

### Data (V20)
- `payments.purpose`: `FARE` (default; every existing row) or `ADVANCE`.
  - `uq_payments_booking_id` is replaced by `UNIQUE (booking_id, purpose)`: one advance and one
    fare payment per booking at most.
  - `PaymentRepository.findByBookingId` keeps its meaning, *the fare payment*, so every existing
    caller (cash, UPI verify, end-trip, summary, cancel) is unchanged. The advance is read with
    `findAdvance(bookingId)`.
- `travel_bookings.advance_amount` (null = no advance required: City rides and every booking from
  before V20), `advance_paid_at`, `hold_expires_at`.

### Flow
1. `createBooking` for a TRIP sets `advance_amount = round(total × 20 %)` to the rupee, and
   `hold_expires_at = now + 30 min`. The driver is still picked, so the dates stay held, but **not
   notified yet**.
2. The passenger pays from the booking screen. `POST …/payment/razorpay/order` is unchanged as an
   API: the server picks the purpose. While an advance is due it creates an `ADVANCE` order for the
   advance amount; afterwards a `FARE` order for the **balance** (`total − advance`).
3. Advance settles (checkout signature or webhook): payment VERIFIED, `advance_paid_at` set,
   hold cleared, booking stays PENDING. **Now** the driver is notified (in-app, email, Telegram)
   and the passenger gets an "advance received" notice.
4. Until the advance is paid, the driver cannot accept and the owner cannot assign.
5. `AdvanceHoldSweeper` (every minute) cancels bookings whose hold expired unpaid. That frees the
   driver's dates. Pending advance rows become FAILED.
6. The balance is paid the existing ways (online, UPI QR, cash). Every amount that used
   `booking.totalAmount` now uses `PaymentService.amountDue(booking)` = total − paid advance.

### Cancellation
- Advance refunded in full when the booking was never CONFIRMED, or when it is cancelled before
  `fromDate 00:00 IST − 24 h`. Otherwise the advance stays VERIFIED and remains revenue.
- A prepaid fare payment is refunded in full, as before.
- Money arriving for a booking that is already cancelled (a webhook racing the hold sweeper) is
  refunded automatically instead of being logged for a person.

### Revenue
Money still sums every VERIFIED payment. Trip, distance and day counts come from FARE payments
only, so a trip with an advance is not counted twice.

## Part 3: City commission and payouts

### Data (V21)
- `cities.commission_percent NUMERIC(5,2) NOT NULL DEFAULT 10`, range 0–50.
- `drivers.payout_upi_id`: AES-256-GCM ciphertext (`v1:` + base64(iv‖ct‖tag)), through a JPA
  `AttributeConverter`. Key: `APP_ENCRYPTION_KEY` (base64, 32 bytes). Without a key the app
  starts, but saving a UPI ID is refused ("payouts are not set up").
- `driver_ledger_entries`: `driver_id`, `booking_id` (nullable), `type`, signed `amount`, `fare`,
  `commission_percent`, `commission`, `reference` (UTR), `note`, `created_by`, `created_at`.
  `UNIQUE (booking_id, type)` makes ride entries idempotent.

### Ledger rules
They apply only to **City rides by INDEPENDENT drivers**. Owner-fleet drivers are paid outside the
app. Balance = Σ amount: positive means the platform owes the driver; negative means the driver
owes the platform.

| Event | Entry |
|---|---|
| Ride completed, paid online or by UPI to the owner | `RIDE_EARNING` **+(fare − commission)** |
| Ride completed, paid in cash to the driver | `CASH_COMMISSION` **−commission** |
| Owner pays the driver by UPI | `PAYOUT` **−amount**, with the UTR |
| Driver pays commission owed to the owner | `COLLECTION` **+amount**, with the UTR |

- Commission = `round(fare × percent / 100, 2)`, using the ride's city rate at completion.
- An entry is written in the same transaction as the completion: `markCashReceived`, `settle`
  (for a trip that is over), and the prepaid branch of `generateTripEndQr`.

### Screens
- **Owner → Cities:** a commission % field per city.
- **Owner → Payouts (new):** each independent driver's balance and UPI ID (decrypted for the
  owner only), with "Record payout" / "Record collection" (amount ≤ balance, UTR required).
- **Driver → Profile:** payout UPI ID (masked after saving) and an earnings panel with the
  balance and recent ledger entries.

## Out of scope
- RazorpayX automatic payouts. The ledger is built so they can post `PAYOUT` entries later.
- Encrypting Aadhaar. It is unique-indexed, so it needs a deterministic hash and a re-keying
  migration; that is tracked separately.
- SMS (on hold).
