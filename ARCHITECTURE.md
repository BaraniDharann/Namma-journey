# Visual Architecture - Driver Management System

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     TRAVEL BOOKING PLATFORM                      │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│    OWNER     │         │    DRIVER    │         │     USER     │
│   (Admin)    │         │  (Service)   │         │  (Customer)  │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │
       │ 1. Login               │ 3. Receive Email       │
       ├────────────────────────┼────────────────────────┤
       │                        │                        │
       │ 2. Create Driver       │                        │
       ├───────────────────────►│                        │
       │                        │                        │
       │                        │ 4. Login with          │
       │                        │    Credentials         │
       │                        ├────────────────────────┤
       │                        │                        │
       │                        │ 5. Provide Service     │
       │                        │◄───────────────────────┤
       │                        │                        │
```

---

## Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    DRIVER CREATION FLOW                          │
└─────────────────────────────────────────────────────────────────┘

OWNER                    BACKEND                    EMAIL SERVICE
  │                         │                            │
  │  POST /api/owner/       │                            │
  │  drivers                │                            │
  ├────────────────────────►│                            │
  │  + Bearer Token         │                            │
  │                         │                            │
  │                         │ 1. Validate Token          │
  │                         │    (ROLE_OWNER)            │
  │                         │                            │
  │                         │ 2. Validate Request        │
  │                         │    (mobile, email, etc)    │
  │                         │                            │
  │                         │ 3. Check Duplicates        │
  │                         │    (DB Query)              │
  │                         │                            │
  │                         │ 4. Generate Password       │
  │                         │    (10 chars secure)       │
  │                         │                            │
  │                         │ 5. Hash Password           │
  │                         │    (BCrypt)                │
  │                         │                            │
  │                         │ 6. Save to Database        │
  │                         │                            │
  │                         │ 7. Send Email              │
  │                         ├───────────────────────────►│
  │                         │    (username + password)   │
  │                         │                            │
  │                         │                            │ Gmail SMTP
  │                         │                            │ API Call
  │                         │                            │
  │                         │◄───────────────────────────┤
  │                         │    Email Sent              │
  │                         │                            │
  │  201 Created            │                            │
  │◄────────────────────────┤                            │
  │  {driverId, message}    │                            │
  │                         │                            │
```

---

## Database Schema

```
┌─────────────────────────────────────────────────────────────────┐
│                         DRIVERS TABLE                            │
├──────────────────┬──────────────┬──────────────┬────────────────┤
│ Column           │ Type         │ Constraints  │ Description    │
├──────────────────┼──────────────┼──────────────┼────────────────┤
│ id               │ BIGSERIAL    │ PRIMARY KEY  │ Auto increment │
│ name             │ VARCHAR(255) │ NOT NULL     │ Driver name    │
│ mobile           │ VARCHAR(10)  │ UNIQUE       │ Phone number   │
│ email            │ VARCHAR(255) │ UNIQUE       │ Email address  │
│ password         │ VARCHAR(255) │ NOT NULL     │ BCrypt hash    │
│ license_number   │ VARCHAR(50)  │ UNIQUE       │ License ID     │
│ aadhaar_number   │ VARCHAR(12)  │ UNIQUE       │ Aadhaar ID     │
│ photo            │ VARCHAR(1000)│ NULL         │ Photo URL      │
│ license_photo    │ VARCHAR(1000)│ NULL         │ License URL    │
│ aadhaar_photo    │ VARCHAR(1000)│ NULL         │ Aadhaar URL    │
│ role             │ VARCHAR(50)  │ NOT NULL     │ ROLE_DRIVER    │
│ status           │ VARCHAR(20)  │ NOT NULL     │ ACTIVE/INACTIVE│
│ created_at       │ TIMESTAMP    │ NOT NULL     │ Creation time  │
└──────────────────┴──────────────┴──────────────┴────────────────┘
```

---

## Security Model

```
┌─────────────────────────────────────────────────────────────────┐
│                      ROLE-BASED ACCESS CONTROL                   │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐
│  ROLE_OWNER  │
└──────┬───────┘
       │
       ├─► POST   /api/owner/drivers        ✅ Allowed
       ├─► GET    /api/owner/**             ✅ Allowed
       ├─► POST   /api/auth/owner/login     ✅ Allowed
       ├─► POST   /api/driver/**            ❌ Denied
       └─► POST   /api/user/**              ❌ Denied

┌──────────────┐
│ ROLE_DRIVER  │
└──────┬───────┘
       │
       ├─► POST   /api/driver/**            ✅ Allowed
       ├─► POST   /api/auth/driver/login    ✅ Allowed
       ├─► POST   /api/owner/**             ❌ Denied
       └─► POST   /api/user/**              ❌ Denied

┌──────────────┐
│  ROLE_USER   │
└──────┬───────┘
       │
       ├─► POST   /api/user/**              ✅ Allowed
       ├─► POST   /api/auth/user/login      ✅ Allowed
       ├─► POST   /api/auth/user/signup     ✅ Allowed
       ├─► POST   /api/owner/**             ❌ Denied
       └─► POST   /api/driver/**            ❌ Denied

┌──────────────┐
│   PUBLIC     │
└──────┬───────┘
       │
       ├─► POST   /api/auth/**              ✅ Allowed
       ├─► POST   /api/owner/**             ❌ Denied (401)
       ├─► POST   /api/driver/**            ❌ Denied (401)
       └─► POST   /api/user/**              ❌ Denied (401)
```

---

## Component Interaction

```
┌─────────────────────────────────────────────────────────────────┐
│                      COMPONENT DIAGRAM                           │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│ AdminController  │
│                  │
│ POST /api/owner/ │
│      drivers     │
└────────┬─────────┘
         │
         │ calls
         ▼
┌──────────────────┐         ┌──────────────────┐
│AdminDriverService│────────►│  EmailService    │
│                  │ sends   │                  │
│ - createDriver() │ email   │ - sendDriver     │
│ - generatePwd()  │         │   Credentials()  │
└────────┬─────────┘         └────────┬─────────┘
         │                            │
         │ uses                       │ uses
         ▼                            ▼
┌──────────────────┐         ┌──────────────────┐
│DriverRepository │         │  JavaMailSender  │
│                  │         │                  │
│ - save()         │         │ - send()         │
│ - existsByXxx()  │         │                  │
└────────┬─────────┘         └──────────────────┘
         │
         │ persists
         ▼
┌──────────────────┐
│   PostgreSQL     │
│                  │
│  drivers table   │
└──────────────────┘
```

---

## Authentication Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    JWT AUTHENTICATION FLOW                       │
└─────────────────────────────────────────────────────────────────┘

CLIENT                  FILTER                  SERVICE
  │                       │                        │
  │  Request with         │                        │
  │  Bearer Token         │                        │
  ├──────────────────────►│                        │
  │                       │                        │
  │                       │ 1. Extract Token       │
  │                       │    from Header         │
  │                       │                        │
  │                       │ 2. Validate Token      │
  │                       │    (JwtUtil)           │
  │                       │                        │
  │                       │ 3. Extract userId      │
  │                       │    and role            │
  │                       │                        │
  │                       │ 4. Create Auth         │
  │                       │    Object              │
  │                       │                        │
  │                       │ 5. Set Security        │
  │                       │    Context             │
  │                       │                        │
  │                       │ 6. Forward Request     │
  │                       ├───────────────────────►│
  │                       │                        │
  │                       │                        │ 7. Check Role
  │                       │                        │    @PreAuthorize
  │                       │                        │
  │                       │                        │ 8. Execute
  │                       │                        │    Business Logic
  │                       │                        │
  │                       │◄───────────────────────┤
  │                       │    Response            │
  │◄──────────────────────┤                        │
  │  Response             │                        │
  │                       │                        │
```

---

## Email Template Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                      EMAIL STRUCTURE                             │
└─────────────────────────────────────────────────────────────────┘

From: Namma Journey <noreply@travelplatform.com>
To: driver@example.com
Subject: Your Driver Account Credentials - Namma Journey

┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ╔══════════════════════════════════════════════════════════╗  │
│  ║         Welcome to Namma Journey!                        ║  │
│  ╚══════════════════════════════════════════════════════════╝  │
│                                                                  │
│  Dear [Driver Name],                                            │
│                                                                  │
│  Your driver account has been created successfully.             │
│  Here are your login credentials:                               │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐    │
│  │  Username (Mobile): 9876543210                         │    │
│  │  Password: aB3@xY9#mK                                  │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                  │
│  Please keep these credentials secure and change your           │
│  password after first login.                                    │
│                                                                  │
│  You can now login to the driver portal using these             │
│  credentials.                                                   │
│                                                                  │
│  Best regards,                                                  │
│  Namma Journey Team                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## File Structure

```
Travel Booking Platform/
│
├── src/main/java/com/travelplatform/
│   │
│   ├── controller/
│   │   ├── AuthController.java          [MODIFIED] ❌ Removed driver signup
│   │   └── AdminController.java         [NEW] ✅ Admin endpoints
│   │
│   ├── service/
│   │   ├── AuthService.java             [MODIFIED] ❌ Removed driverSignup()
│   │   ├── DriverAuthService.java       [UNCHANGED] Login only
│   │   ├── AdminDriverService.java      [NEW] ✅ Driver creation logic
│   │   └── EmailService.java            [NEW] ✅ Send credentials
│   │
│   ├── repository/
│   │   └── DriverRepository.java        [MODIFIED] ✅ Added existsByEmail()
│   │
│   ├── entity/
│   │   └── Driver.java                  [MODIFIED] ✅ Added email, photos
│   │
│   ├── dto/
│   │   ├── AdminCreateDriverRequest.java    [NEW] ✅ Admin DTO
│   │   ├── DriverCreationResponse.java      [NEW] ✅ Response DTO
│   │   └── DriverSignupRequest.java         [DEPRECATED] ⚠️ Not used
│   │
│   └── config/
│       └── SecurityConfig.java          [UNCHANGED] RBAC configured
│
├── DRIVER_MANAGEMENT_API.md             [NEW] ✅ API Documentation
├── IMPLEMENTATION_SUMMARY.md            [NEW] ✅ Change Summary
├── TESTING_GUIDE.md                     [NEW] ✅ Testing Instructions
├── DEPLOYMENT_CHECKLIST.md              [NEW] ✅ Deployment Guide
└── README.md                            [ORIGINAL] ⚠️ Needs update
```

---

## API Endpoint Comparison

```
┌─────────────────────────────────────────────────────────────────┐
│                    BEFORE vs AFTER                               │
└─────────────────────────────────────────────────────────────────┘

BEFORE (Old System):
─────────────────────────────────────────────────────────────────
POST /api/auth/driver/signup          ✅ Public (Anyone)
POST /api/auth/driver/login           ✅ Public (Anyone)

AFTER (New System):
─────────────────────────────────────────────────────────────────
POST /api/auth/driver/signup          ❌ REMOVED
POST /api/auth/driver/login           ✅ Public (Anyone)
POST /api/owner/drivers               ✅ Protected (ROLE_OWNER only)
```

---

## Password Generation Algorithm

```
┌─────────────────────────────────────────────────────────────────┐
│                  PASSWORD GENERATION                             │
└─────────────────────────────────────────────────────────────────┘

Character Set:
┌────────────────────────────────────────────────────────────────┐
│ A-Z (26) + a-z (26) + 0-9 (10) + @#$ (3) = 65 characters      │
└────────────────────────────────────────────────────────────────┘

Algorithm:
1. Use SecureRandom (cryptographically strong)
2. Generate 10 random characters
3. Each character randomly selected from 65-char set
4. Result: ~65^10 = 1.3 × 10^18 possible combinations

Example Output:
┌────────────────────────────────────────────────────────────────┐
│ aB3@xY9#mK                                                     │
│ K9#mX@2bYa                                                     │
│ Z7$nM@4cWp                                                     │
└────────────────────────────────────────────────────────────────┘

Security:
✅ High entropy (10 chars from 65-char set)
✅ Includes uppercase, lowercase, numbers, special chars
✅ Cryptographically secure random generation
✅ BCrypt hashed before storage
```

---

## Error Handling Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    ERROR HANDLING                                │
└─────────────────────────────────────────────────────────────────┘

Request
  │
  ├─► No Token ──────────────────────► 401 Unauthorized
  │
  ├─► Invalid Token ─────────────────► 401 Unauthorized
  │
  ├─► Wrong Role (USER/DRIVER) ──────► 403 Forbidden
  │
  ├─► Validation Error ──────────────► 400 Bad Request
  │   (Invalid mobile, email, etc)
  │
  ├─► Duplicate Mobile ──────────────► 400 Bad Request
  │
  ├─► Duplicate Email ───────────────► 400 Bad Request
  │
  ├─► Duplicate License ─────────────► 400 Bad Request
  │
  ├─► Duplicate Aadhaar ─────────────► 400 Bad Request
  │
  ├─► Database Error ────────────────► 500 Internal Server Error
  │
  ├─► Email Send Failure ────────────► 201 Created (with warning)
  │   (Driver created but email failed)
  │
  └─► Success ───────────────────────► 201 Created
```

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                  PRODUCTION ARCHITECTURE                         │
└─────────────────────────────────────────────────────────────────┘

                    ┌──────────────┐
                    │   Internet   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Load Balancer│
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼─────┐    ┌─────▼─────┐   ┌─────▼─────┐
    │  App      │    │  App      │   │  App      │
    │  Server 1 │    │  Server 2 │   │  Server 3 │
    └─────┬─────┘    └─────┬─────┘   └─────┬─────┘
          │                │                │
          └────────────────┼────────────────┘
                           │
                    ┌──────▼───────┐
                    │  PostgreSQL  │
                    │   Database   │
                    └──────────────┘

External Services:
┌──────────────┐
│  Gmail SMTP    │ ◄─── Email Delivery
└──────────────┘

┌──────────────┐
│  AWS S3      │ ◄─── Photo Storage (Future)
└──────────────┘
```

---

## City Ride Dispatch (Batch Matching)

City rides (Ride Now) are not offered one at a time to whoever is nearest. Every 2 seconds
(`app.dispatch.round-interval-ms`), each city's waiting rides and free drivers are paired in one
batch so that total pickup time is least. Trips (booked for dates) keep date-based assignment
(`DriverRepository.findFirstFreeTripDriver`).

```
 booking committed ──► RideQueue.enqueue ──┐
                                           ▼
 MatchingWorker.tick (every 2 s, per city this server holds the lock of)
   1. expire offers past their 15 s deadline ──► ride back to waiting, driver IDLE, declined
   2. read waiting rides (oldest first); > 2 min ──► NO_DRIVER, owner notified
   3. per ride: radius 1 / 3 / 5 km after 0 / 20 / 40 s waited
        DriverPresenceService.findWithin (nearest 30) ─► minus declined
        DriverStateStore.cards (one batched read) ─► IDLE, active, City, vehicle, seats
        first 10 ─► EtaService ─► cost = ETA − 2 × seconds waited (ETA > 15 min dropped)
   4. AssignmentSolver: union-find groups; Hungarian ≤ 300 rides, greedy above
   5. per pair: CAS driver IDLE→OFFERED ─► booking OFFERED (one short transaction)
        ─► RideQueue.markOffered ─► push OFFER to the driver
```

**State.** Postgres stays the record; `no_driver_overlap` and the booking's `@Version` still guard
every write, so a stale working copy costs at most a round, never a double booking. Redis holds the
working copy a round needs (all keys prefixed `travelplatform::`): per-city location sets
(`geo:city:{id}`), driver cards (`driver:{id}`: IDLE / OFFERED / BUSY, vehicle, seats, switches),
waiting rides (`city:{id}:waiting`, `ride:{id}`), declined drivers (`ride:{id}:declined`), offers
by deadline (`city:{id}:offers`, `driver:{id}:offer`) and city locks (`lock:match:city:{id}`).
With `app.cache.type` other than `redis`, in-memory twins of every store take over
(`DispatchStoresConfig`), which is correct for a single instance.

**Keeping the copy right.** Store writes happen after the database change commits
(`DriverStateUpdates`, `DispatchService`). The driver cards are rebuilt from Postgres every minute
for every driver seen in the last ten (`reconcile`). A server that takes a city whose queue is empty
rebuilds it from the city's SEARCHING and OFFERED bookings, and every held city is merged from them
about once a minute, so a lost queue write never strands a ride.

**Spreading cities.** Each city is run by one server at a time (`SET NX PX 5000`, renewed every
round, renew and release checked in Lua). A server takes at most `ceil(active cities / live
servers) + 1` cities, live servers being those that heartbeat in the last 10 s. A dead server's
cities are taken over when its locks expire, within 5 s.

**Driver answers.** Accept (`DriverService.acceptBooking`) settles the search through
`DispatchService.markMatched`: the ride leaves the queue and the driver is BUSY. Decline
(`DispatchService.release`) records the rejection, frees the driver and re-queues the ride for the
next round. A traveller's cancel removes the ride and withdraws a held offer. The driver app learns
of an offer by a STOMP push and, as a fallback, polls `GET /api/driver/{id}/offer` every 20 s,
which reads the queue and then that one booking.

---

**End of Visual Architecture Document**
