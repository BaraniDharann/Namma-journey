# Driver self-signup and verification

**Status:** design approved in chat on 2026-10-24.
**Part:** 1 of 3. Part 2 covers Razorpay and Trip advances; part 3 covers City commission and
driver payouts.

## Goal

City drivers on bikes, autos and cars **sign themselves up** and upload their documents. The
owner reviews and approves or rejects each one. Only **approved** drivers can go online and
receive City rides.

The owner no longer creates City drivers. Owner-added drivers remain the **Trip fleet**.

## Decisions

- **Two kinds of driver**, stored in a new column, `drivers.fleet`:

  | Fleet | Who | City rides | Trips |
  |---|---|---|---|
  | `OWNER_FLEET` | Every existing driver, and every driver the owner creates | Optional, owner switches it on | Yes |
  | `INDEPENDENT` | Every self-signed-up driver | Yes | Never (database check) |

- **Verification status**, in a new column `drivers.verification_status`:
  - The states are `UNDER_REVIEW`, `APPROVED` and `REJECTED`.
  - Existing drivers and owner-created drivers are `APPROVED`.
  - A self-signup starts at `UNDER_REVIEW`, because every document is required at sign-up.
- **Only approved drivers can work.** An unapproved driver is refused when they try to:
  - go online
  - send a location report
  - be considered for a City ride
  - be assigned a Trip
- **Contact check is by email.** Signing up sends a one-time code by email, using the existing
  OTP service. The app has no SMS provider, so mobile checks by SMS are out of scope; they can
  come later with an SMS provider such as MSG91.
- **Documents.** All four are required, image files only:

  | Document | Status |
  |---|---|
  | Photo | existing field |
  | Driving licence | existing field |
  | Aadhaar | existing field |
  | Vehicle RC | **new**, `drivers.rc_photo` |

  - They are stored through `FileStorageService`, like today's driver documents.
  - The owner sees them through the existing expiring signed links; the RC is added to the
    sensitive-files list.
- **Vehicle details at sign-up:**
  - type (bike, auto or car)
  - number plate, which is **required** for independent drivers and **unique** across drivers
  - model
  - for cars, seats and AC

  These reuse `VehicleFields` on the frontend and `DriverVehicles` on the backend.
- **A rejection needs a reason** of 5 to 500 characters. A rejected driver sees the reason, can
  re-upload any of the four documents, and goes back to `UNDER_REVIEW`.
- **Notifications:**
  - The owner gets an in-app notice for each new or re-submitted application.
  - The driver gets an in-app notice and an email when approved or rejected.
- **Drivers still connect Telegram.** The Telegram connect step stays required for all drivers.
  The approval screen comes **before** it in the driver app.

## Data (migration V16)

**`drivers`:**
- `fleet varchar(12) NOT NULL DEFAULT 'OWNER_FLEET'`, with `CHECK IN ('OWNER_FLEET','INDEPENDENT')`
- `verification_status varchar(12) NOT NULL DEFAULT 'APPROVED'`, with
  `CHECK IN ('UNDER_REVIEW','APPROVED','REJECTED')`
- `rejection_reason varchar(500)`
- `rc_photo varchar(1000)`
- `submitted_at timestamp`, `reviewed_at timestamp`, `reviewed_by bigint`
- `CHECK (NOT serves_trip OR fleet = 'OWNER_FLEET')`
- A partial unique index on `upper(vehicle_number)` where it is not null.

  Before the index is created, any duplicate number plates already in the table must be cleared:
  keep the lowest driver id and set the others to null. Otherwise the migration fails.

## API

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/auth/driver/signup` | public | Multipart form. See *Sign-up rules* below. |
| `POST /api/auth/driver/signup/verify-email` | public | `{email, otp}`. Marks the email verified and returns `{token, role, userId, name, …}`, the same shape as login. |
| `PUT /api/driver/{id}/documents` | that driver | Multipart, any of the four documents. Allowed only while `REJECTED`. Moves the driver to `UNDER_REVIEW` and clears the reason. |
| `GET /api/driver/{id}/verification` | that driver | `{status, rejectionReason, missingDocuments[]}` |
| `GET /api/owner/driver-applications?status=UNDER_REVIEW` | owner | List with signed document links, oldest first. |
| `POST /api/owner/drivers/{id}/approve` | owner | `UNDER_REVIEW` → `APPROVED`. Records `reviewed_by` and `reviewed_at`. |
| `POST /api/owner/drivers/{id}/reject` | owner | `{reason}`. `UNDER_REVIEW` → `REJECTED`. |

**Sign-up rules**

- **Form fields:**
  - `name`, `mobile`, `email`, `password` (8 or more characters)
  - `licenseNumber`, `aadhaarNumber`
  - `vehicleType`, `vehicleNumber`, `vehicleModel`, `vehicleSeats`, `vehicleAc`
  - files: `photo`, `licensePhoto`, `aadhaarPhoto`, `rcPhoto`
- **What it creates:**
  - an `INDEPENDENT`, `UNDER_REVIEW` driver who is **offline** (`status` = INACTIVE)
  - with `servesTrip` false, `firstLogin` false and `emailVerified` false
  - then it emails a one-time code
- **Duplicates:** a mobile, email, licence, Aadhaar or number plate that is already registered is
  refused with the same "already registered" messages that owner-created drivers get.
- **Order of work:**
  1. Check the request and the duplicates.
  2. Store the files.
  3. Write the database row.
  4. Send the email after the commit.

  This matches how `AdminDriverService` creates a driver.
- **Rate limiting:** `/api/auth/**` already has a tight per-IP limit, and that applies here too.

**Driver login, existing endpoint, with one change**

- An unverified email gets a code resent instead of a token, using the same response shape as
  the first-login case.
- Unapproved drivers **can** log in. The app then shows their application status.

**Gating**

| Where | Rule |
|---|---|
| `DriverService.updateAvailability(ACTIVE)` | Refused unless `APPROVED`: "Your account is still being verified." |
| Location reports (`DriverController` presence and location) | Refused unless `APPROVED`. |
| `DriverRepository.findDispatchableAmong` | Adds `verificationStatus = 'APPROVED'`. |
| Trip queries (`TRIP_CAR`) | Add `fleet = 'OWNER_FLEET'` and `verificationStatus = 'APPROVED'`. |
| `OwnerService.assignDriver` | Requires an approved driver. A Trip also requires `OWNER_FLEET`. |
| `DriverVehicles.setModes(…, servesTrip = true)` | Refused for `INDEPENDENT` drivers. |

## Screens

**Public page `/driver/signup`, linked from the driver login page as "New driver? Sign up"**

- A three-step form:
  1. **About you:** name, mobile, email, password.
  2. **Vehicle:** `VehicleFields`, without the Trips switch.
  3. **Documents:** four upload tiles, each with a preview, plus the licence and Aadhaar numbers.
- Then **verify email:** a code-entry screen that logs the driver in.

**Driver app, while not approved**

`DriverDashboard` and the driver pages show an **application status** screen in place of the
dashboard. It goes in `DashboardLayout`, before the Telegram step.

- **Under review:** "We're checking your documents, usually within 24 hours."
- **Rejected:** the reason, plus a re-upload form for the documents.

**Owner "Driver applications" screen, `/owner/driver-applications`, in the owner nav**

- A queue of applications, oldest first, with a badge showing how many are waiting.
- Each card shows:
  - the four documents, opening into a large view
  - the entered numbers and the vehicle
  - Approve, or Reject with a required reason

**The owner's "Add driver" form now creates Trip fleet drivers.**

## Testing

**`DriverSignupServiceTest`**
- A sign-up creates an `INDEPENDENT`, `UNDER_REVIEW`, offline driver with the vehicle details,
  and sends the email code.
- Each duplicate field is refused with no files stored.
- A missing document is refused.
- A plate that is not a valid number plate is refused.
- An independent driver cannot take Trips.

**`DriverVerificationServiceTest`**
- Approve and reject move through the allowed states.
- A reject reason is required.
- Approving twice is refused.
- Re-uploading is allowed only while rejected, and goes back to under review.

**Gating tests**
- An unapproved driver cannot go online or report a location.
- An unapproved driver is not offered City rides.
- A Trip cannot be assigned to an independent or unapproved driver.

**Migration test (needs Docker)**
- Existing drivers become `OWNER_FLEET` and `APPROVED`.
- An independent driver with `serves_trip` on is refused.
- A duplicate number plate is refused.

**Frontend:** build, lint and the existing tests pass.

## Out of scope

- SMS codes to the mobile number.
- Automatic document checks (OCR, DigiLocker).
- Bank details, which come in part 3.
- Driver ratings and suspension.
