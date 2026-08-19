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
│DriverRepository │         │  Gmail SMTP API    │
│                  │         │                  │
│ - save()         │         │ - POST /mail/    │
│ - existsByXxx()  │         │   send           │
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

**End of Visual Architecture Document**
