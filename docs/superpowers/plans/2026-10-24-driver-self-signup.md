# Driver Self-Signup and Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** City drivers sign themselves up with their documents. The owner approves or rejects
each one, and only approved drivers can work. Owner-added drivers become the Trip fleet.

**Architecture:** Two new columns on `drivers`, `fleet` and `verification_status`, plus the
review fields. Two small services:

- `DriverSignupService`: sign-up and email check
- `DriverVerificationService`: approve, reject, re-submit, status

The approval rule is enforced at every place a driver gets work: going online, location reports,
the City dispatch query, the Trip queries and owner assignment. Frontend: a public sign-up page,
an application-status screen for drivers, and an owner applications queue.

**Tech Stack:** Spring Boot 3.4, JPA/Flyway (Postgres), JUnit 5 + Mockito, React 18 + Vite.

**Spec:** [docs/superpowers/specs/2026-10-24-driver-self-signup-design.md](../specs/2026-10-24-driver-self-signup-design.md)

## Global Constraints

- **Kinds of driver:** `fleet` is `OWNER_FLEET` or `INDEPENDENT`. All existing drivers, and
  every owner-created driver, are `OWNER_FLEET` + `APPROVED`. Every self-signup is
  `INDEPENDENT` + `UNDER_REVIEW`, offline (`status = INACTIVE`), `servesTrip = false`,
  `firstLogin = false`, `emailVerified = false`.
- **Verification states:** `UNDER_REVIEW`, `APPROVED`, `REJECTED`. Allowed moves: under review →
  approved, under review → rejected, rejected → under review (on re-upload).
- **Documents:** all four required at sign-up, images only: `photo`, `licensePhoto`,
  `aadhaarPhoto`, `rcPhoto`. Store them with `FileStorageService.storeFile(file, "photo" | "license" | "aadhaar" | "rc")`.
  Add `"uploads/drivers/rc_"` to `SignedUploadUrls.SENSITIVE_PREFIXES`.
- **Rejection reason:** 5–500 characters after trimming.
- **Number plate:** required for `INDEPENDENT` drivers, unique case-insensitively across all
  drivers, validated by `DriverVehicles` (its existing pattern).
- **Password:** at least 8 characters. Contact check is by email code, using the existing
  `OtpService`. No SMS.
- **Messages, as written:**
  - Going online while unapproved: `Your account is still being verified.`
  - Duplicates: `Mobile number already registered`, `Email already registered`,
    `License number already registered`, `Aadhaar number already registered`,
    `Vehicle number already registered`
- **House style:** comments explain *why*. Optional collaborators in hand-built test classes use
  `@Autowired(required = false)` field injection. Every new endpoint has `@PreAuthorize`, except
  the two public `/api/auth/driver/signup*` endpoints, which sit under the already
  rate-limited `/api/auth/**`.
- **Commit trailer:** `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. **A sign-up that fails halfway**, for example a duplicate found after the files were stored.
   No driver row is left behind, and no orphan files remain: the duplicate checks run before
   any files are stored. Test: Task 2 `duplicateRefusedBeforeAnyFileStored`.
2. **An unapproved driver calling the API directly**, bypassing the app. They cannot go online,
   report a location, or be picked for a City ride or a Trip. Tests: Task 4.
3. **The owner approving an application the driver is re-uploading at the same moment.** The
   last state follows the allowed moves, and a stale approve is refused through `@Version` /
   the optimistic lock. Test: Task 3 `approveOnlyFromUnderReview`.
4. **A rejected driver re-uploading only some documents.** The documents they don't replace are
   kept, and they still return to under review. Test: Task 3 `partialReuploadKeepsOthers`.
5. **Existing drivers after the upgrade.** They keep working unchanged: `OWNER_FLEET` and
   `APPROVED`, so trips and online still work. Test: Task 1 migration test, plus the existing
   suites staying green.

---

### Task 1: Data model

**Files:**
- Create: `src/main/resources/db/migration/V16__driver_self_signup.sql`
- Modify:
  - `entity/Driver.java`
  - `dto/DriverDetailsResponse.java`: add `fleet`, `verificationStatus`, `rejectionReason`,
    `rcPhoto` (signed), `submittedAt`
  - `storage/SignedUploadUrls.java`
  - `service/DriverVehicles.java`
  - `service/OwnerService.java` and `service/DriverService.java`: populate the new response fields
- Test:
  - `service/DriverVehiclesTest.java` (extend)
  - `migration/DriverOverlapConstraintTest.java` (extend; needs Docker)

**Interfaces:**
- Produces:
  - `Driver.Fleet { OWNER_FLEET, INDEPENDENT }` with field `fleet` (default `OWNER_FLEET`)
  - `Driver.Verification { UNDER_REVIEW, APPROVED, REJECTED }` with field `verificationStatus`
    (default `APPROVED`)
  - New `Driver` fields: `rejectionReason`, `rcPhoto`, `submittedAt`, `reviewedAt`, `reviewedBy`
  - `Driver.isApproved() -> boolean`
  - `DriverDetailsResponse.withVerification(Driver d, java.util.function.UnaryOperator<String> sign) -> DriverDetailsResponse`

- [ ] **Step 1: Write the failing tests.**
  - `independentDriverCannotTakeTrips`: `setModes(independent, null, true)` throws
    `IllegalArgumentException`.
  - `applyForcesTripsOffForIndependent`
  - Docker-gated `driverSelfSignupColumns`:
    - existing rows read back as `OWNER_FLEET` / `APPROVED`
    - `UPDATE … SET fleet='INDEPENDENT', serves_trip=true` fails with SQLSTATE `23514`
    - two drivers with plates `ka01ab1234` and `KA01AB1234` fail with `23505`
- [ ] **Step 2: Run** `mvn -o -q test -Dtest=DriverVehiclesTest`. Expected: FAIL.
- [ ] **Step 3: Write the migration**, exactly as in the spec's *Data (migration V16)* section:
  - Null out duplicate plates first, keeping the lowest id.
  - Then create `CREATE UNIQUE INDEX uq_drivers_vehicle_number ON drivers (upper(vehicle_number)) WHERE vehicle_number IS NOT NULL;`
- [ ] **Step 4: Implement** the entity fields, `DriverVehicles` (independent drivers never take
  trips), the response fields and the RC signed links.
- [ ] **Step 5: Run** the full suite, `mvn -o -q test`. Expected: 0 failures, with only Docker
  tests skipped.
- [ ] **Step 6: Commit** `feat(driver): fleet and verification status on drivers`.

### Task 2: Self sign-up and email check

**Files:**
- Create:
  - `service/DriverSignupService.java`
  - `dto/DriverSelfSignupForm.java`, for the multipart text fields
- Modify:
  - `controller/AuthController.java`
  - `service/DriverAuthService.java`: remove the unreachable `signup`. Login resends the email
    code when `emailVerified` is false and `fleet == INDEPENDENT`.
  - `service/NotificationService.java`: `notifyDriverApplication(Driver d, boolean resubmitted)`,
    an owner in-app notice
- Test: `service/DriverSignupServiceTest.java`

**Interfaces:**
- Consumes: `DriverVehicles.apply`, `FileStorageService.storeFile`, `OtpService.sendOtp` /
  `verifyOtp`, `JwtUtil.generateToken`
- Produces:
  - `DriverSignupService.signup(DriverSelfSignupForm f, MultipartFile photo, MultipartFile license, MultipartFile aadhaar, MultipartFile rc) -> Map<String,Object>`,
    returning `{email, message: "We've emailed you a code to confirm your email."}`
  - `DriverSignupService.verifyEmail(String email, String otp) -> Map<String,Object>`, with the
    same keys as `DriverAuthService.login` on success
  - Endpoints `POST /api/auth/driver/signup` (multipart) and
    `POST /api/auth/driver/signup/verify-email` (JSON `{email, otp}`)

- [ ] **Step 1: Write the failing tests:**
  - `signupCreatesIndependentDriverUnderReview`: check `fleet`, `verificationStatus`,
    `status = INACTIVE`, `servesTrip = false`, the vehicle fields, all four document paths set,
    `submittedAt` not null, and that the code was sent to the email
  - `duplicateRefusedBeforeAnyFileStored`: one case each for mobile, email, licence, Aadhaar and
    plate; `storeFile` is never called
  - `missingDocumentRefused`
  - `numberPlateRequired`
  - `shortPasswordRefused`
  - `verifyEmailIssuesToken`
  - `verifyEmailWrongCodeRefused`
  - `loginWithUnverifiedEmailResendsCode`
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** Order of work: check and look for duplicates, then store the files,
  then save the driver, then send the email after the commit (through `AfterCommitDispatcher`),
  then send the owner notice.
- [ ] **Step 4: Run** `mvn -o -q test -Dtest='DriverSignupServiceTest,AuthEnumerationTest'`.
  Expected: PASS.
- [ ] **Step 5: Commit** `feat(driver): self sign-up with documents and email check`.

### Task 3: Owner review and driver re-upload

**Files:**
- Create: `service/DriverVerificationService.java`
- Modify:
  - `controller/OwnerController.java`
  - `controller/DriverController.java`
  - `service/NotificationService.java`: `notifyDriverApproved(Driver)` and
    `notifyDriverRejected(Driver)`, each sending in-app and email
  - `service/EmailService.java`: two simple emails
- Test: `service/DriverVerificationServiceTest.java`

**Interfaces:**
- Produces:
  - `DriverVerificationService.applications(Driver.Verification status) -> List<DriverDetailsResponse>`:
    oldest `submittedAt` first, `INDEPENDENT` only, signed links
  - `approve(Long driverId, Long ownerId) -> DriverDetailsResponse`
  - `reject(Long driverId, String reason, Long ownerId) -> DriverDetailsResponse`
  - `resubmit(Long driverId, MultipartFile photo, MultipartFile license, MultipartFile aadhaar, MultipartFile rc) -> DriverDetailsResponse`:
    any subset of the four; at least one required
  - `status(Long driverId) -> Map<String,Object>` returning `{status, rejectionReason, missingDocuments}`
  - Endpoints, as in the spec's API table:
    - `GET /api/owner/driver-applications`
    - `POST /api/owner/drivers/{id}/approve`
    - `POST /api/owner/drivers/{id}/reject`
    - `PUT /api/driver/{id}/documents`
    - `GET /api/driver/{id}/verification`
  - Approve and reject evict the `allDrivers` and `driverById` caches.

- [ ] **Step 1: Write the failing tests:**
  - `approveOnlyFromUnderReview`: approving an approved or rejected driver is refused
  - `rejectNeedsReasonOf5To500`
  - `rejectRecordsReasonAndReviewer`
  - `resubmitOnlyWhenRejected`
  - `partialReuploadKeepsOthers`: only the RC is replaced; the old photo, licence and Aadhaar
    paths stay; the status becomes `UNDER_REVIEW` and the reason is cleared
  - `approvalNotifiesDriver`
  - `applicationsOldestFirst`
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** A replaced file is deleted from storage after the new one is saved,
  the same way `DriverService.uploadEndTripPhoto` does it.
- [ ] **Step 4: Run** `mvn -o -q test -Dtest=DriverVerificationServiceTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(driver): owner approves or rejects applications; drivers re-upload`.

### Task 4: Only approved drivers work

**Files:**
- Modify:
  - `service/DriverService.java`: `updateAvailability`
  - `controller/DriverController.java` and `controller/LocationWebSocketController.java`: location
    reports
  - `repository/DriverRepository.java`: `findDispatchableAmong` and `TRIP_CAR`
  - `service/OwnerService.java`: `assignDriver`
- Test:
  - `service/DriverApprovalGateTest.java`
  - `service/OwnerServiceAssignNotifyTest.java` (extend)

**Interfaces:**
- Consumes: `Driver.isApproved()`, `Driver.Fleet`

- [ ] **Step 1: Write the failing tests:**
  - `unapprovedCannotGoOnline`: the message is `Your account is still being verified.`
  - `approvedCanGoOnline`
  - `unapprovedPresenceReportRefused`: the presence endpoint's service-level check throws
    `AccessDeniedException`
  - `tripCannotGoToIndependentDriver`
  - `cannotAssignUnapprovedDriver`
  - For the queries, assert the JPQL strings contain `verificationStatus` and
    `OWNER_FLEET` through a reflection test on the `@Query` values, following the
    `TRIP_CAR` constant pattern.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Presence and location checks go through one helper,
    `DriverService.requireApproved(Long driverId)`, cached for 60 s in a Caffeine cache keyed by
    driver id, so location reports don't hit the database every time.
  - The cache is evicted on approve and reject.
- [ ] **Step 4: Run** the full suite, `mvn -o -q test`. Expected: 0 failures, with only Docker
  tests skipped.
- [ ] **Step 5: Commit** `feat(driver): only approved drivers go online, report location or get work`.

### Task 5: Driver-facing screens

**Files:**
- Create:
  - `frontend/src/pages/DriverSignupPage.jsx`
  - `frontend/src/components/driver/ApplicationGate.jsx`
- Modify:
  - `frontend/src/App.jsx`: a public route `/driver/signup`, redirecting to the dashboard when
    already logged in
  - `frontend/src/pages/DriverLoginPage.jsx`: a "New driver? Sign up" link, plus handling the
    unverified-email login response with the same code screen
  - `frontend/src/components/DashboardLayout.jsx`: put `ApplicationGate` outside
    `TelegramGate`, wrapping it
  - `frontend/src/utils/api.js`: `driverSignup(formData)`, `driverVerifySignupEmail(email, otp)`,
    `getDriverVerification(id)`, `resubmitDriverDocuments(id, formData)`
- Test: `frontend/src/components/driver/applicationGate.test.js`, testing a pure helper
  `gateView(verification)`:
  - `APPROVED` → `'app'`
  - `UNDER_REVIEW` → `'review'`
  - `REJECTED` → `'rejected'`
  - missing → `'loading'`

**Interfaces:**
- Consumes: Task 2 and 3 endpoints; `VehicleFields` with a new `hideTrips` prop

- [ ] **Step 1: Write the failing vitest** for `gateView`. Run `npm test`. Expected: FAIL.
- [ ] **Step 2: Build the sign-up page.**
  - The three steps from the spec, then the code screen.
  - On success, log the driver in through `AuthContext` the same way `DriverLoginPage` does,
    and go to `/driver/dashboard`.
  - The style matches `DriverLoginPage` / `auth.css`.
- [ ] **Step 3: Build `ApplicationGate`.**
  - Under review: a short status message.
  - Rejected: the reason, plus upload tiles for the four documents, then Resubmit.
- [ ] **Step 4: Run** `npm test`, `npm run build` and `npx eslint` on the changed files.
  Expected: tests PASS, `✓ built`, lint 0 errors.
- [ ] **Step 5: Commit** `feat(driver-ui): sign-up page and application status screen`.

### Task 6: Owner applications queue

**Files:**
- Create: `frontend/src/pages/owner/OwnerDriverApplications.jsx`
- Modify:
  - `frontend/src/App.jsx`: route `/owner/driver-applications`
  - every owner page's `navItems`: add `{ path: '/owner/driver-applications', label: 'Applications' }`
    after Drivers
  - `components/dash/Icon.jsx` NAV list: `/driver-applications$/` → `'id'`
  - `frontend/src/pages/owner/OwnerDrivers.jsx`: the create tab's title becomes "Add Trip fleet
    driver"; list chips show the fleet
  - `frontend/src/utils/api.js`: `getDriverApplications(status)`, `approveDriver(id)`,
    `rejectDriver(id, reason)`
- Test: `npm run build` / lint. A pure helper `rejectReasonError(text)` returns null when the
  trimmed length is 5–500, and a message otherwise, with a vitest.

- [ ] **Step 1: Write the failing vitest** for `rejectReasonError`. Run it. Expected: FAIL.
- [ ] **Step 2: Build the page.**
  - Tabs: Waiting / Rejected / Approved.
  - Cards show the four document images, opening into a full-size viewer.
  - Approve; Reject opens a reason box with live validation.
  - The count of waiting applications shows in the page header.
- [ ] **Step 3: Run** `npm test`, `npm run build` and lint. Expected: PASS, `✓ built`, 0 errors.
- [ ] **Step 4: Commit** `feat(owner-ui): driver applications queue`.
