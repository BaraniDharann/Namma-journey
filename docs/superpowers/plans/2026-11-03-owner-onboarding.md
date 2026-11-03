# Multi-owner Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Many travels owners can sign up with their documents. A NammaJourney admin verifies them (Aadhaar automatically through UIDAI's offline Secure QR, the rest by hand on free official portals) and approves or rejects them. An approved owner receives an Owner ID and password by email.

**Architecture:**
- New `ROLE_ADMIN` with its own `admins` table and JWT login.
- The `owners` table gains business, KYC, bank and review-status columns, and allows many rows.
- Signup is a public multipart endpoint. It runs the existing `AadhaarQrVerifier` on the raw Aadhaar upload, then stores documents as sensitive uploads (signed URLs only) and queues outbox emails.
- Approval generates the code and password and emails them directly.
- An hourly job reminds the admin at 36 hours.

**Tech Stack:** Spring Boot 3.4 / Java 17, JPA + Flyway (Postgres), JUnit 5 + Mockito, Testcontainers for migration and endpoint tests, React 18 + Vite + Tailwind, vitest.

**Spec:** `docs/superpowers/specs/2026-11-02-owner-onboarding-design.md`

## Global Constraints

- Build and run locally only. No deploy or CI config changes in this plan.
- Every email is sent from `MAIL_USERNAME` (`nammajourney463@gmail.com` in local `.env`). Admin alerts go to `app.admin.notify-email: ${ADMIN_NOTIFY_EMAIL:nammajourney463@gmail.com}`.
- Use free official sources only. Never call a paid KYC API.
- Never store the full Aadhaar number. Keep `aadhaar_last4` only, plus the image and the QR result.
- PAN, bank account number and UPI use `@Convert(converter = EncryptedStringConverter.class)`. The API shows them only masked: PAN `XXXXX1234A`, account `XXXXXX6789`.
- Owner documents are images only (JPEG/PNG/WebP), max 5 MB each. They are stored with `FileStorageService.storeImage(file, "owners", <kind>)`. `uploads/owners/` is a sensitive prefix, so documents are served only through `SignedUploadUrls` links, and only the admin API hands those links out.
- Owner Owner ID format `NJO-%04d`, from sequence `owner_code_seq`. The migrated existing owner is `NJO-0001`, so the sequence starts at 2.
- Generated owner password: 12 characters from `SecureRandom`, using the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789` (no look-alike characters).
- Review deadline: a reminder at 36 hours, overdue at 48 hours, counted from `submitted_at`.
- Resubmit link: random 32-byte token, stored as a SHA-256 hash, valid 14 days, usable once.
- City rides and all existing owner dashboard behaviour stay unchanged. Sub-project 2 scopes them per owner.
- Code style: match the surrounding code, meaning field-injected services, Javadoc that explains *why*, and `@DisplayName` on tests.

## Review Focus

1. **Aadhaar photo that is a PDF, HEIC or blurry.** The QR result should be `UNREADABLE` and signup should still succeed. The admin then falls back to UIDAI's scanner app. Test: Task 4 `signupSucceedsWhenAadhaarQrUnreadable`.
2. **Owner submits the same email or PAN twice, with different casing or spaces.** The second signup is refused with "already registered". Test: Task 4 `duplicateEmailOrPanIsRefusedIgnoringCaseAndSpaces`.
3. **Admin clicks Approve twice, or two admins approve at once.** Only one Owner ID and one credentials email are produced. Test: Task 6 `approveIsOnlyAllowedFromPending`, backed by an `@Version` column on `Owner`.
4. **Credentials email fails because SMTP is down.** The approval stays, the response says `credentialsEmailed=false`, and "Resend credentials" works. Test: Task 6 `approveSurvivesEmailFailure`.
5. **The owner of a pending application tries to log in, or a token from before the password change is reused.** Login gives a clear 403 message, and the old setup token is refused after the change. Tests: Task 8 `pendingAndRejectedCannotLogIn`, `setupTokenDiesAfterPasswordChange`.

---

### Task 1: Schema V26 and entities

**Files:**
- Create: `src/main/resources/db/migration/V26__multi_owner_onboarding.sql`
- Create: `src/main/java/com/travelplatform/entity/Admin.java`, `OwnerStatus.java`, `OwnerVerificationCheck.java`, `OwnerAadhaarQr.java`
- Modify: `src/main/java/com/travelplatform/entity/Owner.java`
- Create: `src/main/java/com/travelplatform/repository/AdminRepository.java`, `OwnerVerificationCheckRepository.java`, `OwnerAadhaarQrRepository.java`
- Modify: `src/main/java/com/travelplatform/repository/OwnerRepository.java`
- Modify: `playwright-tests/helpers/db.ts` (insert owners with `status='APPROVED'`, `owner_code`)
- Test: `src/test/java/com/travelplatform/migration/MultiOwnerMigrationTest.java`

**Interfaces:**
- Produces:
  - `enum OwnerStatus { PENDING_REVIEW, APPROVED, REJECTED }`
  - `enum OwnerVerificationCheck.Type { PAN, AADHAAR, REGISTRATION_DOC, BANK }` and `enum OwnerVerificationCheck.Result { VERIFIED, FAILED }`
  - `Owner` new fields, with getters and setters:
    - `ownerCode`, `status` (default `PENDING_REVIEW`)
    - `travelsName`, `contactName`, `phone`
    - `panNumber` (encrypted), `panHash`, `aadhaarLast4`, `aadhaarConsentAt`
    - `registrationDocPath`, `panDocPath`, `aadhaarDocPath`
    - `bankAccountHolder`, `bankAccountNumber` (encrypted), `bankIfsc`, `bankName`, `upiId` (encrypted)
    - `rejectionReason`, `submittedAt`, `reviewedAt`, `reviewedByAdminId`, `adminRemindedAt`
    - `mustChangePassword`, `resubmitTokenHash`, `resubmitTokenExpiresAt`
    - `@ManyToMany Set<City> cities` (join table `owner_cities`)
    - `@Version long version`
    - `password` becomes nullable
  - `Admin {id, email, password, tokenVersion, createdAt}`
  - `OwnerAadhaarQr`: `ownerId` is the primary key. Fields: `status` (String of `AadhaarSecureQr.Status`), `name`, `dob`, `gender`, `state`, `pincode`, `last4`, `photoJpeg` (`byte[]`, bytea, nullable), `checkedAt`.
  - `OwnerRepository` adds:
    - `Optional<Owner> findByOwnerCode(String)`
    - `Optional<Owner> findByEmailIgnoreCase(String)`
    - `boolean existsByPanHash(String)`
    - `List<Owner> findByStatusOrderBySubmittedAtAsc(OwnerStatus)`
    - `List<Owner> findByStatusAndSubmittedAtBeforeAndAdminRemindedAtIsNull(OwnerStatus, LocalDateTime)`
    - `Optional<Owner> findByResubmitTokenHash(String)`
    - `@Query(value = "select nextval('owner_code_seq')", nativeQuery = true) long nextOwnerCode()`
  - `AdminRepository.findByEmailIgnoreCase(String)`
  - `OwnerVerificationCheckRepository.findByOwnerId(Long)`. Primary key `(owner_id, check_type)` via `@IdClass`.

- [ ] **Step 1: Write the failing migration test** `MultiOwnerMigrationTest`. Copy the Testcontainers setup from `CityFaresMigrationTest`.
  - `existingOwnerBecomesNJO0001Approved`:
    1. Migrate to target `25`.
    2. Insert an owner row `(email 'boss@x.com', password 'h', role 'ROLE_OWNER', created_at now())`.
    3. Migrate to latest.
    4. Assert `owner_code = 'NJO-0001'`, `status = 'APPROVED'`, `must_change_password = false`, and `password = 'h'`.
  - `newOwnerDefaultsToPendingAndCodesStartAt2`:
    1. On the latest schema, insert an owner with only `email` and `role`.
    2. Assert `status = 'PENDING_REVIEW'` and `password` is null.
    3. Assert `select nextval('owner_code_seq')` returns `2`.
  - `freshDatabaseHasNoOwners`: after migrating an empty DB to latest, `owners` has 0 rows and `admins` exists.
- [ ] **Step 2: Run it and confirm it fails.** Run: `mvn -o test -Dtest=MultiOwnerMigrationTest`. Expected: FAIL (column `owner_code` does not exist). With no Docker it is skipped, so run it on a machine that has Docker.
- [ ] **Step 3: Write `V26__multi_owner_onboarding.sql`**
  - `CREATE TABLE admins`
  - `ALTER TABLE owners`: add the spec columns, `version bigint not null default 0`, and `status varchar(20) not null default 'APPROVED'`.
  - Backfill: `UPDATE owners SET owner_code='NJO-0001' WHERE id=(SELECT min(id) FROM owners)`. Any other existing rows get `'NJO-' || lpad(id::text,4,'0')`; there are none today.
  - Then `ALTER COLUMN status SET DEFAULT 'PENDING_REVIEW'` and `ALTER COLUMN password DROP NOT NULL`.
  - `CREATE SEQUENCE owner_code_seq START 2`
  - Unique indexes on `owner_code`, `pan_hash` and `lower(email)`.
  - `CREATE TABLE owner_cities (owner_id → owners, city_id → cities, PK both)`, `owner_verification_checks`, and `owner_aadhaar_qr` (FK `owner_id` with `on delete cascade`).
- [ ] **Step 4: Update the entities and repositories** as listed in Interfaces. Then update `playwright-tests/helpers/db.ts` so its owner insert sets `status='APPROVED', owner_code='NJO-9001'`.
- [ ] **Step 5: Run the tests.** `mvn -o test -Dtest=MultiOwnerMigrationTest` should PASS. `mvn -o test -Dtest=SecurityEndpointsTest` should still PASS; it saves an `Owner` with password `"x"`.
- [ ] **Step 6: Commit.** `git commit -m "feat(owners): V26 schema for many owners, admins and owner verification"`

---

### Task 2: Admin role, bootstrap and login

**Files:**
- Create: `src/main/java/com/travelplatform/service/AdminAuthService.java`
- Create: `src/main/java/com/travelplatform/controller/AdminAuthController.java` (`/api/auth/admin`)
- Create: `src/main/java/com/travelplatform/dto/AdminLoginRequest.java` (`email`, `password`, both `@NotBlank`)
- Modify: `src/main/java/com/travelplatform/security/TokenVersionService.java`
- Modify: `src/main/java/com/travelplatform/config/SecurityConfig.java`
- Modify: `src/main/resources/application.yml`, `.env.example`
- Test: `src/test/java/com/travelplatform/service/AdminAuthServiceTest.java`
- Test: `src/test/java/com/travelplatform/security/SecurityEndpointsTest.java` (extend it)

**Interfaces:**
- Consumes: `AdminRepository`, `JwtUtil.generateToken(String subject, String role, long version)`, `CredentialCheck.matches(...)`
- Produces:
  - `AuthResponse AdminAuthService.bootstrap(AdminLoginRequest req, String providedSecret)`
  - `AuthResponse AdminAuthService.login(AdminLoginRequest req)`
  - `TokenVersionService.revokeAdmin(Admin)`
  - Role string `"ROLE_ADMIN"`. JWT subject is the admin id.
  - Config `app.bootstrap.admin-secret: ${ADMIN_BOOTSTRAP_SECRET:}`
  - Endpoints `POST /api/auth/admin/bootstrap` (header `X-Bootstrap-Secret`) and `POST /api/auth/admin/login`

- [ ] **Step 1: Write the failing tests in `AdminAuthServiceTest`.** Use mocks, wired the way `DriverSignupServiceTest` does.
  - `bootstrapIsNotFoundWhenSecretUnset`: throws `ResourceNotFoundException`.
  - `bootstrapRefusesWrongSecret`: throws `BadCredentialsException`.
  - `bootstrapOnlyWhileNoAdminExists`: `admins.count()` is 1, so it throws `IllegalArgumentException`.
  - `bootstrapCreatesAdminAndReturnsAdminToken`: saved admin has a bcrypt password, and the response role is `ROLE_ADMIN`.
  - `loginRejectsUnknownEmailAndWrongPasswordAlike`: both throw `BadCredentialsException("Invalid credentials")`.
- [ ] **Step 2: Extend `SecurityEndpointsTest`**
  - `adminEndpointsAreAdminOnly`: `GET /api/admin/owners` gives 401 with no token, 403 with the owner token and 403 with the user token. With an admin token it is anything except 401/403; the controller arrives in Task 6, so expect 404 until then.
  - `adminTokenDiesWhenAdminRevoked`.
- [ ] **Step 3: Run the tests and confirm they fail.** `mvn -o test -Dtest='AdminAuthServiceTest,SecurityEndpointsTest'` should FAIL to compile.
- [ ] **Step 4: Implement.**
  - Mirror `OwnerAuthService.createAdmin`, including the constant-time secret compare.
  - In `TokenVersionService.lookup`, add `case "ROLE_ADMIN"`.
  - In `SecurityConfig`, add `.requestMatchers("/api/admin/**").hasRole("ADMIN")` before the `/api/owner/**` rule.
  - In yml, add `app.bootstrap.admin-secret` and `app.admin.notify-email`. Document both in `.env.example`.
- [ ] **Step 5: Run the tests and confirm they pass.** Same command, PASS.
- [ ] **Step 6: Commit.** `git commit -m "feat(admin): admin role with one-time bootstrap and login"`

---

### Task 3: Owner application checks

**Files:**
- Create: `src/main/java/com/travelplatform/service/OwnerApplicationChecks.java`
- Modify: `src/main/java/com/travelplatform/service/DriverApplicationChecks.java` (make `validAadhaar` `public static`)
- Test: `src/test/java/com/travelplatform/service/OwnerApplicationChecksTest.java`

**Interfaces:**
- Produces, all `public static` and pure:
  - `boolean validPan(String)` uses `^[A-Z]{5}[0-9]{4}[A-Z]$` after trim and uppercasing.
  - `boolean validIfsc(String)` uses `^[A-Z]{4}0[A-Z0-9]{6}$` after trim and uppercasing.
  - `String normalisePan(String)`: trim, remove inner spaces, uppercase.
  - `String panHash(String)`: hex SHA-256 of the normalised PAN.
  - `String maskPan(String)` gives `"XXXXX" + last 5`.
  - `String maskAccount(String)` gives `"XXXXXX" + last 4`.
  - `String aadhaarLast4(String)`: digits only, last 4.

- [ ] **Step 1: Write the failing tests.**
  - `validPan` accepts `"abcde1234f"` and `" ABCDE1234F "`, and rejects `"ABCD1234F"` and `"ABCDE12345"`.
  - `validIfsc` accepts `"SBIN0001234"` and rejects `"SBIN1001234"`.
  - `panHash(" abcde 1234f")` equals `panHash("ABCDE1234F")`.
  - `maskPan("ABCDE1234F")` is `"XXXXX1234F"`.
  - `maskAccount("12345678906789")` is `"XXXXXX6789"`.
  - `aadhaarLast4("2345 6789 0124")` is `"0124"`.
- [ ] **Step 2: Run them and confirm they fail.** `mvn -o test -Dtest=OwnerApplicationChecksTest` should FAIL.
- [ ] **Step 3: Implement** in `OwnerApplicationChecks.java`, styled like `DriverApplicationChecks` (final class, private constructor).
- [ ] **Step 4: Run them and confirm they pass.**
- [ ] **Step 5: Commit.** `git commit -m "feat(owners): PAN, IFSC and masking checks for owner applications"`

---

### Task 4: Owner signup

**Files:**
- Create: `src/main/java/com/travelplatform/dto/OwnerSignupForm.java`, a `@ModelAttribute` form with `@NotBlank` fields:
  - `travelsName`, `contactName`, `email` (`@Email`), `phone` (`^[6-9]\d{9}$`)
  - `cityIds` (`List<Long>`, `@NotEmpty`)
  - `panNumber`, `aadhaarNumber`
  - `bankAccountHolder`, `bankAccountNumber` (`^\d{9,18}$`), `bankIfsc`, `bankName`
  - `upiId` (`^[\w.-]{2,}@[a-zA-Z]{2,}$`)
  - `aadhaarConsent` (`@AssertTrue`)
  - files `registrationDoc`, `panDoc`, `aadhaarDoc`
- Create: `src/main/java/com/travelplatform/service/OwnerSignupService.java`
- Create: `src/main/java/com/travelplatform/service/AadhaarPhotoConverter.java`
- Modify: `pom.xml` (add `com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0`, which reads the QR's JPEG 2000 photo)
- Modify: `src/main/java/com/travelplatform/controller/AuthController.java` (`POST /api/auth/owner/signup`, multipart)
- Modify: `src/main/java/com/travelplatform/storage/SignedUploadUrls.java` (add `"uploads/owners/"` to `SENSITIVE_PREFIXES`)
- Modify: `src/main/java/com/travelplatform/service/EmailService.java`
- Modify: `src/main/java/com/travelplatform/controller/` public cities endpoint: add `GET /api/public/cities`, returning `[{id, name}]` of active cities, if no public listing exists yet.
- Test: `src/test/java/com/travelplatform/service/OwnerSignupServiceTest.java`
- Test: `src/test/java/com/travelplatform/service/AadhaarPhotoConverterTest.java`

**Interfaces:**
- Consumes:
  - `AadhaarQrVerifier.verifyImage(byte[])` and `AadhaarSecureQr.Result`
  - `OwnerApplicationChecks.*` and `DriverApplicationChecks.validAadhaar`
  - `FileStorageService.storeImage(MultipartFile, "owners", kind)`, where kind is `"registration"`, `"pan"` or `"aadhaar"`
  - `Outbox` via `EmailService`
- Produces:
  - `OwnerSignupService.SignupResult signup(OwnerSignupForm form)` with `record SignupResult(Long ownerId, OffsetDateTime reviewBy)`
  - `void OwnerSignupService.applyForm(Owner owner, OwnerSignupForm form, boolean documentsOptional)`. This is package-private and reused by resubmit in Task 7.
  - `byte[] AadhaarPhotoConverter.toJpeg(byte[] jpeg2000)`, which returns null if it cannot decode.
  - `EmailService.sendOwnerApplicationReceived(String to, String travelsName, LocalDateTime reviewBy)` (queued)
  - `EmailService.sendAdminNewOwnerApplication(Owner owner, LocalDateTime reviewBy)`: queued to `app.admin.notify-email`. Its link uses `${app.frontend-base-url:http://localhost:5173}/admin/owners/{id}`; add that property to yml.

- [ ] **Step 1: Write the failing tests in `OwnerSignupServiceTest`.** Use mocks; `AadhaarQrVerifier` is mocked to return a chosen `Result`.
  - `signupCreatesPendingOwnerWithEncryptedKycAndQrResult`:
    - Status is `PENDING_REVIEW`, `password` is null, `ownerCode` is null.
    - `aadhaarLast4` is `"0124"`, and `panHash` is set.
    - `aadhaarConsentAt` and `submittedAt` are set.
    - Three `storeImage(…, "owners", kind)` calls are made.
    - An `OwnerAadhaarQr` is saved with status `VALID` and name `"Ravi Kumar"`.
    - `reviewBy` is `submittedAt + 48h`.
  - `aadhaarQrRunsOnTheOriginalUploadBytes`: the verifier receives exactly the uploaded bytes, before compression.
  - `queuesAdminAndOwnerEmails`: both email methods are called once each.
  - `duplicateEmailOrPanIsRefusedIgnoringCaseAndSpaces`:
    - `findByEmailIgnoreCase` finds an existing owner (not `REJECTED`), so the call throws `IllegalArgumentException("This email is already registered")`.
    - `existsByPanHash` returns true, so it throws `"This PAN is already registered"`.
  - `invalidNumbersAreRefusedBeforeAnythingIsStored`:
    - A bad PAN, bad IFSC, or an Aadhaar that fails its check digit each throws `IllegalArgumentException` with a field-specific message.
    - An unknown city id throws as well.
    - In every case `storeImage` is never called.
  - `signupSucceedsWhenAadhaarQrUnreadable`: the verifier returns `UNREADABLE`, the owner is saved, and the QR row status is `UNREADABLE`.
- [ ] **Step 2: Write the failing `AadhaarPhotoConverterTest`.**
  - `garbageGivesNull`: `toJpeg(new byte[]{1,2,3})` returns null.
  - `jpeg2000RoundTrip`: write a 20×20 image as `"jpeg2000"` with ImageIO (the plugin is on the classpath), convert it, and check the result starts with `FF D8`.
- [ ] **Step 3: Run them and confirm they fail.** `mvn test -Dtest='OwnerSignupServiceTest,AadhaarPhotoConverterTest'` (online, the first time, so the new dependency downloads) should FAIL.
- [ ] **Step 4: Implement.** The order inside `signup` matters:
  1. Validate everything.
  2. Check duplicates.
  3. Read `aadhaarDoc.getBytes()` and verify the QR.
  4. Store the files.
  5. Save the `Owner`.
  6. Save the `OwnerAadhaarQr`, with the photo converted through `AadhaarPhotoConverter`.
  7. Queue the emails.

  Annotate it `@Transactional`. File size over 5 MB throws `IllegalArgumentException("Each document must be 5 MB or smaller")`. `AuthController` maps it like `driverSignup` and returns `201 {ownerId, reviewBy}`.
- [ ] **Step 5: Run them and confirm they pass.**
- [ ] **Step 6: Commit.** `git commit -m "feat(owners): public owner signup with offline Aadhaar QR check"`

---

### Task 5: Admin review API and verification checklist

**Files:**
- Create: `src/main/java/com/travelplatform/service/OwnerReviewService.java`
- Create: `src/main/java/com/travelplatform/controller/AdminOwnerController.java` (`/api/admin/owners`)
- Create: `src/main/java/com/travelplatform/dto/AdminOwnerSummary.java`, `AdminOwnerDetail.java`, `OwnerCheckRequest.java` (`result` `@NotNull`, `note`)
- Test: `src/test/java/com/travelplatform/service/OwnerReviewServiceTest.java`

**Interfaces:**
- Consumes: the repositories from Task 1, `SignedUploadUrls.signIfSensitive(String)`, `OwnerApplicationChecks.mask*`
- Produces:
  - `List<AdminOwnerSummary> list(OwnerStatus)`. Each summary has `{id, ownerCode, travelsName, cities, submittedAt, hoursWaiting, overdue}`, with overdue meaning more than 48 hours.
  - `AdminOwnerDetail detail(Long id)`, which includes:
    - all non-secret fields
    - `panMasked`, `bankAccountMasked`, `upiId`
    - `documents {registration, pan, aadhaar}` as signed URLs
    - `aadhaarQr {status, name, dob, gender, state, pincode, last4, last4MatchesTyped, photoDataUrl}`
    - `checks` (map from type to `{result, note, checkedAt, checkedBy}`)
    - `canApprove`
  - `void recordCheck(Long ownerId, OwnerVerificationCheck.Type, OwnerCheckRequest, Long adminId)`
  - Endpoints:
    - `GET /api/admin/owners?status=PENDING_REVIEW`
    - `GET /api/admin/owners/{id}`
    - `PUT /api/admin/owners/{id}/checks/{type}`
    - The admin id comes from the authentication principal.

- [ ] **Step 1: Write the failing tests.**
  - `listSortsOldestFirstAndFlagsOverdue`: an owner submitted 49 hours ago has `overdue=true` and one 10 hours ago has `false`.
  - `detailNeverExposesFullSecrets`:
    - The serialised detail (Jackson `ObjectMapper`) does not contain the full PAN, the full account number, or any 12-digit Aadhaar.
    - Document URLs contain `sig=`.
  - `failedCheckNeedsNote`: `FAILED` with a blank note throws `IllegalArgumentException`.
  - `recordCheckIsAudited`: the saved check has the `adminId` and `checkedAt`.
  - `canApproveOnlyWhenAllFourVerified`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** `photoDataUrl` is `"data:image/jpeg;base64,…"` when `photoJpeg` is not null. Log every check with `log.info("Owner {} check {} = {} by admin {}", …)` as the audit line, and leave the note out of the log.
- [ ] **Step 4: Run them and confirm they pass.** Also run `SecurityEndpointsTest`: the admin token on `/api/admin/owners` now gives 200.
- [ ] **Step 5: Commit.** `git commit -m "feat(admin): owner review list, detail and verification checklist"`

---

### Task 6: Approve, reject and resend credentials

**Files:**
- Modify: `OwnerReviewService.java`, `AdminOwnerController.java`
- Create: `src/main/java/com/travelplatform/service/OwnerCredentials.java`, which generates the password and resubmit tokens and hashes the tokens.
- Modify: `EmailService.java`
- Test: `OwnerReviewServiceTest.java` (extend it)

**Interfaces:**
- Produces:
  - `ApprovalResult approve(Long ownerId, Long adminId)` with `record ApprovalResult(String ownerCode, boolean credentialsEmailed)`
  - `void reject(Long ownerId, String reason, Long adminId)`
  - `ApprovalResult resendCredentials(Long ownerId, Long adminId)`, allowed only for `APPROVED` owners whose `mustChangePassword` is true.
  - `OwnerCredentials.newPassword()`, `newResubmitToken()` (URL-safe base64 of 32 bytes), and `hash(String token)` (hex SHA-256)
  - `boolean EmailService.sendOwnerCredentialsNow(String to, String travelsName, String ownerCode, String password)`. It is **synchronous**, not `@Async`. It returns false on any exception, logs without the password, and is built with `sendNow`'s MIME code.
  - `EmailService.sendOwnerRejected(String to, String travelsName, String reason, String resubmitUrl)` (queued)
  - Endpoints:
    - `POST /api/admin/owners/{id}/approve` returns `{ownerCode, credentialsEmailed}`
    - `POST /api/admin/owners/{id}/reject` with body `{reason}` (`@NotBlank`, max 500)
    - `POST /api/admin/owners/{id}/resend-credentials`

- [ ] **Step 1: Write the failing tests.**
  - `approveNeedsAllChecksVerified`: with one check missing it throws `IllegalStateException`, and the controller maps that to 409, naming the missing checks.
  - `approveAssignsCodeAndOneTimePassword`:
    - `nextOwnerCode()` returns 7, so the code is `"NJO-0007"`.
    - The status is `APPROVED`, `mustChangePassword` is true, the password is a bcrypt match of the emailed one, and the review trail is set.
  - `approveIsOnlyAllowedFromPending`: approving an `APPROVED` or `REJECTED` owner throws `IllegalStateException`.
  - `approveSurvivesEmailFailure`: `sendOwnerCredentialsNow` returns false, the status is still `APPROVED`, and `credentialsEmailed` is false.
  - `resendGeneratesANewPassword`: the hash changes and the email is sent again.
  - `rejectStoresReasonAndResubmitTokenHash`:
    - The status is `REJECTED`, and the stored hash equals `hash(token in emailed URL)`.
    - Expiry is now plus 14 days.
    - The URL is `${app.frontend-base-url}/owner/signup?resubmit=<token>`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** On concurrent approve, the `@Version` check throws `ObjectOptimisticLockingFailureException`. Map it to 409 `"This application was just reviewed by someone else"`.
- [ ] **Step 4: Run them and confirm they pass.**
- [ ] **Step 5: Commit.** `git commit -m "feat(admin): approve owners with emailed Owner ID and password, reject with resubmit link"`

---

### Task 7: Resubmit after rejection

**Files:**
- Modify: `OwnerSignupService.java`, `AuthController.java`
- Create: `src/main/java/com/travelplatform/dto/OwnerResubmitPrefill.java`
- Test: `OwnerSignupServiceTest.java` (extend it)

**Interfaces:**
- Consumes: `OwnerCredentials.hash`, `applyForm(owner, form, documentsOptional=true)`
- Produces:
  - `OwnerResubmitPrefill prefill(String token)`, returning `{travelsName, contactName, email, phone, cityIds, bankAccountHolder, bankIfsc, bankName, panMasked, rejectionReason}`. It returns no account number, UPI or Aadhaar; those must be typed again.
  - `SignupResult resubmit(String token, OwnerSignupForm form)`
  - Endpoints `GET /api/auth/owner/resubmit/{token}` and `POST /api/auth/owner/resubmit/{token}` (multipart; documents optional)

- [ ] **Step 1: Write the failing tests.**
  - `prefillOmitsSecrets`.
  - `resubmitReopensSameRowAndClearsToken`: same id, status `PENDING_REVIEW`, new `submittedAt`, `adminRemindedAt` null, `rejectionReason` null, token hash null, previous checks deleted, and the admin email is queued again.
  - `resubmitKeepsDocumentsNotReplaced`: when only `panDoc` is sent, `storeImage` is called once and the other paths are unchanged. The Aadhaar QR is re-checked only if `aadhaarDoc` was sent.
  - `expiredOrUsedTokenIsRefused`: both throw `IllegalArgumentException("This link has expired. Please contact support.")`.
  - `resubmitMayKeepOwnPanAndEmail`: the duplicate checks exclude the owner's own row.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them and confirm they pass.**
- [ ] **Step 5: Commit.** `git commit -m "feat(owners): rejected owners fix and resubmit their application"`

---

### Task 8: Owner login by ID or email, first-login password change, retire create-admin

**Files:**
- Modify: `OwnerAuthService.java`, `AuthController.java`, `dto/OwnerLoginRequest.java` (rename `email` to `login` and accept `email` as a JSON alias via `@JsonAlias("email")`), `dto/AuthResponse.java` (add `Boolean mustChangePassword`, `String ownerCode`)
- Create: `src/main/java/com/travelplatform/controller/OwnerAccountController.java` (`POST /api/owner/change-password`, body `{currentPassword, newPassword}` where `newPassword` is at least 8 characters)
- Modify: `TokenVersionService.java`, `SecurityConfig.java`
- Delete: `OwnerAuthService.createAdmin`, the `/owner/create-admin` mapping, `dto/AdminCreateRequest.java` (if unused elsewhere), the `app.bootstrap.owner-secret` yml key, and the matching `ApplicationConfigDefaultsTest` case (replace it with the same assertion for `app.bootstrap.admin-secret`)
- Modify: `.env.example`, `DEPLOYMENT_CHECKLIST.md`, `docs/guides/TESTING_GUIDE.md`, `docs/guides/OTP_SETUP_GUIDE.md`, `docs/guides/DRIVER_SETUP_GUIDE.md`, `db/legacy-scripts/database-init.sql`, `render.yaml`. Replace each owner-bootstrap instruction with the admin bootstrap plus owner signup.
- Test: `src/test/java/com/travelplatform/service/OwnerLoginTest.java`, plus `SecurityEndpointsTest` (extend it)

**Interfaces:**
- Produces:
  - Login by `ownerCode` (when it matches `^NJO-\d{4,}$`, case-insensitive) or by email.
  - Role `"ROLE_OWNER_SETUP"` is issued when `mustChangePassword` is set. It may call only `/api/owner/change-password`. `TokenVersionService` resolves it against `owners`, the same as `ROLE_OWNER`.
  - `AuthResponse OwnerAuthService.changePassword(Long ownerId, String current, String next)`: clears the flag, bumps `tokenVersion` through `revokeOwner`, and returns a fresh `ROLE_OWNER` token.

- [ ] **Step 1: Write the failing tests.**
  - `logsInWithOwnerCodeOrEmail`.
  - `pendingAndRejectedCannotLogIn`:
    - Pending gives `OwnerNotApprovedException` (403) with message `"Your application is under review"`.
    - Rejected gives `"Your application was rejected: <reason>"`.
    - Both are thrown only *after* the password matched. Before that, the answer is the generic `"Invalid credentials"`, so the endpoint does not reveal application status to strangers.
  - `firstLoginGetsSetupToken`: the role is `ROLE_OWNER_SETUP` and `mustChangePassword` is true.
  - `changePasswordIssuesOwnerToken`.
  - `setupTokenDiesAfterPasswordChange`: `isCurrent` is false for the old version.
  - In `SecurityEndpointsTest`: `setupTokenOnlyReachesChangePassword`. `GET /api/owner/bookings` with a setup token gives 403, and `POST /api/owner/change-password` is not 403.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
  - In `SecurityConfig`, add `.requestMatchers("/api/owner/change-password").hasAnyRole("OWNER","OWNER_SETUP")` *before* `/api/owner/**`.
  - Map `OwnerNotApprovedException` to 403 `{error}` in the existing global exception handler.
- [ ] **Step 4: Run them and confirm they pass,** then run the whole suite: `mvn -o test` should report 0 failures.
- [ ] **Step 5: Commit.** `git commit -m "feat(owners): log in with Owner ID, change password on first login; retire owner bootstrap"`

---

### Task 9: 36-hour reminder to the admin

**Files:**
- Create: `src/main/java/com/travelplatform/service/OwnerReviewReminderJob.java`
- Modify: `EmailService.java` (`sendAdminReviewReminder(Owner owner, LocalDateTime deadline)`, queued to the notify email)
- Test: `src/test/java/com/travelplatform/service/OwnerReviewReminderJobTest.java`

**Interfaces:**
- Produces: `@Scheduled(fixedRate = 3_600_000, initialDelay = 300_000) void remind()` and package-private `void remind(Clock clock)` for tests.

- [ ] **Step 1: Write the failing tests.**
  - `remindsOnceAfter36Hours`: the repository returns one owner, the email is sent, `adminRemindedAt` is set, and the query cutoff is now minus 36 hours.
  - `doesNotRemindTwice`: covered by the repository query's `AdminRemindedAtIsNull`. Assert the exact repository method is used.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** Annotate it `@Transactional`.
- [ ] **Step 4: Run them and confirm they pass.**
- [ ] **Step 5: Commit.** `git commit -m "feat(admin): remind the admin of owner applications waiting 36 hours"`

---

### Task 10: Frontend

**Files:**
- Modify: `frontend/src/utils/api.js`. Add:
  - `ownerSignup(formData)`
  - `ownerResubmitPrefill(token)`, `ownerResubmit(token, formData)`
  - `ownerChangePassword(data)`
  - `adminLogin(data)`
  - `adminOwners(status)`, `adminOwner(id)`, `adminOwnerCheck(id, type, data)`, `adminApproveOwner(id)`, `adminRejectOwner(id, reason)`, `adminResendCredentials(id)`
  - `publicCities()`
- Create: `frontend/src/utils/ownerForm.js` (pure validators `validPan`, `validIfsc`, `validAadhaar`, `validUpi`, `validAccount`, mirroring Task 3) and `frontend/src/utils/ownerForm.test.js`
- Create: `frontend/src/pages/OwnerSignupPage.jsx`
  - The form follows `DriverSignupPage` styling and `AuthShell`, with a city multi-select from `publicCities()`, three image inputs, and the Aadhaar consent checkbox using the spec text.
  - In `?resubmit=` mode it pre-fills and shows the rejection reason.
  - On success it shows "Application received. We will verify within 48 hours (by <reviewBy>)."
- Modify: `frontend/src/pages/OwnerLoginPage.jsx`. The label becomes "Owner ID or email", it shows the 403 message as is, it navigates to `/owner/change-password` when `mustChangePassword`, and it adds a "New travels owner? Sign up" link.
- Create: `frontend/src/pages/owner/OwnerChangePassword.jsx`
- Create: `frontend/src/pages/AdminLoginPage.jsx`, `frontend/src/pages/admin/AdminOwners.jsx` (tabs Pending/Approved/Rejected, overdue badge) and `frontend/src/pages/admin/AdminOwnerDetail.jsx`. The detail page has:
  - the document viewer
  - the QR result panel (UIDAI signature badge, QR photo next to the Aadhaar image, last-4 match)
  - four check rows, each with an "Open official site" link, ✔ and ✘ buttons, and a note
  - Approve, disabled until `canApprove`, which shows the Owner ID and a warning plus a "Resend credentials" button if `credentialsEmailed` is false
  - Reject with a reason box
- Modify: `frontend/src/App.jsx`:
  - routes `/owner/signup`, `/owner/change-password` (role `ROLE_OWNER_SETUP`), `/admin/login`, `/admin/owners`, `/admin/owners/:id` (role `ROLE_ADMIN`)
  - `getDashboard` gets `ROLE_ADMIN → /admin/owners` and `ROLE_OWNER_SETUP → /owner/change-password`
- Modify: `frontend/src/components/DashboardLayout.jsx` (`ROLE_LABEL.ROLE_ADMIN = 'Admin'`, nav entry)

Official-site links, as constants in `AdminOwnerDetail.jsx`:
- PAN: `https://eportal.incometax.gov.in/iec/foservices/#/pre-login/verifyYourPAN`
- Aadhaar: `https://myaadhaar.uidai.gov.in/verifyAadhaar` (for the scanner-app fallback, the UI says "Scan with UIDAI's 'Aadhaar QR Scanner' app")
- IFSC: `https://www.rbi.org.in/Scripts/IFSCMICRDetails.aspx`
- GST: `https://services.gst.gov.in/services/searchtp`

- [ ] **Step 1: Write the failing `ownerForm.test.js`** with the same cases as Task 3, plus a valid and an invalid Aadhaar (`"234567890124"` valid by Verhoeff; take the example from `DriverApplicationChecks` tests).
- [ ] **Step 2: Run it and confirm it fails.** `cd frontend && npx vitest run src/utils/ownerForm.test.js` should FAIL.
- [ ] **Step 3: Implement `ownerForm.js`, then the pages, routes and API functions.**
- [ ] **Step 4: Run the checks.** `cd frontend && npx vitest run && npm run lint && npm run build`: all should pass.
- [ ] **Step 5: Commit.** `git commit -m "feat(web): owner signup, admin owner review and first-login password change"`

---

### Task 11: Local end-to-end check

No new code. This is the verification that proves the feature on a real stack.

- [ ] **Step 1: Set up local `.env`.**
  - `MAIL_USERNAME=nammajourney463@gmail.com`, plus a Gmail App Password in `MAIL_PASSWORD`
  - `ADMIN_NOTIFY_EMAIL=nammajourney463@gmail.com`
  - `ADMIN_BOOTSTRAP_SECRET=<random>`
  - `APP_ENCRYPTION_KEY` (`openssl rand -base64 32`)
  - `AADHAAR_QR_CERTIFICATES=<path to UIDAI .cer downloaded from uidai.gov.in>`
- [ ] **Step 2: Start the backend and frontend,** then bootstrap the admin: `curl -X POST localhost:8080/api/auth/admin/bootstrap -H "X-Bootstrap-Secret: …" -H "Content-Type: application/json" -d '{"email":"nammajourney463@gmail.com","password":"…"}'`. Expected: 200 with a `ROLE_ADMIN` token.
- [ ] **Step 3: Sign up at `/owner/signup`** with a real Aadhaar card image. Expected: two emails arrive at nammajourney463@gmail.com, the admin alert and the receipt (use a second address as the owner email to see both).
- [ ] **Step 4: Open the application at `/admin/owners`.**
  - Expected: the QR panel shows "UIDAI signature valid" with the right name and photo. This is the first real-card test of `AadhaarSecureQr`.
  - If the fields are shifted, fix the field order in `AadhaarSecureQr` against the real payload before continuing.
- [ ] **Step 5: Mark the four checks and approve.** Expected: a credentials email with `NJO-0002`. Log in at `/owner/login` with the Owner ID. Expected: the change-password screen appears, and after changing the password the owner reaches `/owner/dashboard`.
- [ ] **Step 6: Reject a second test application and resubmit it** through the emailed link. Expected: it is back in Pending.
- [ ] **Step 7: Confirm the existing owner still works.** Log in as the existing owner, now `NJO-0001`, with the old email and password. Expected: the dashboard works as before.
- [ ] **Step 8: Record the results** in the PR description. Do not deploy.
