# Multi-owner onboarding (sub-project 1 of 4)

## Context

Today NammaJourney has exactly one owner. That account is both the business that runs the fleet and the platform admin. `POST /api/auth/owner/create-admin` refuses to run once an owner exists.

The goal is a marketplace for **Trips**. Many travels owners sign up in their own cities. NammaJourney verifies each one within 48 hours. Customers then compare the owners in their pickup city and book one. City rides are out of scope and stay unchanged.

The work is split into four sub-projects, each with its own spec and plan:

1. **Admin and owner onboarding.** This spec.
2. **Owners run their own fleet.** Each owner gets the full current owner dashboard, limited to their own data: vehicles as a separate entity, drivers, per-km prices, and the cities they serve.
3. **Compare and book Trips.** The customer sees the approved owners serving the pickup city, with each owner's price, and books one. That owner assigns a driver. The driver accepts the trip, ends it and collects cash or UPI **directly for the owner**.
4. **Monthly commission.** The owner owes 5% of every trip, cash or UPI. On the 1st of each month the owner gets a statement email, pays NammaJourney's UPI, enters the UTR, and the admin confirms. This reuses the driver-dues pattern.

## Decisions

| Topic | Decision |
|---|---|
| Name | We keep the name "Owner" and do not introduce "operator". The owners table now allows many rows. |
| Existing owner | Becomes owner `NJO-0001` with status `APPROVED`. It keeps all current drivers and bookings, and is asked to fill in its business details on next login. |
| Admin | New `ROLE_ADMIN` with its own `admins` table and `/admin` login (email and password). The first admin is `nammajourney463@gmail.com`, created once with `ADMIN_BOOTSTRAP_SECRET`. |
| Owner location | The owner picks one or more cities from the admin-managed `City` list. |
| Credentials | The owner never chooses a password at signup. On approval the system generates an Owner ID and a random password and emails both. The owner must change the password on first login. |
| Email | Every email is sent from `nammajourney463@gmail.com` (`MAIL_USERNAME`, Gmail App Password). Admin alerts go to `ADMIN_NOTIFY_EMAIL=nammajourney463@gmail.com`. |
| Environment | Built and tested locally only. No deploy in this sub-project. |
| Document verification | **Free official sources only. No paid KYC API.** The admin checks each owner by hand, using the government sites in the verification checklist below. Approve stays disabled until every check is marked verified. |
| Aadhaar | **UIDAI offline verification, run automatically and free** (`AadhaarQrVerifier`). At signup the backend reads the Secure QR on the uploaded Aadhaar image and checks UIDAI's digital signature with UIDAI's public certificate (`AADHAAR_QR_CERTIFICATES`). No API call is made and nothing leaves the server. **The admin still approves by hand**, using the result. The owner must tick an explicit consent box at signup. |
| Driver verification | Owners add their own drivers and **approve them by hand**, checking driving licence and RC on Parivahan. This is designed in sub-project 2, not here. |

## Data model (migration V26)

### `admins` (new)
`id`, `email` (unique), `password` (bcrypt), `token_version`, `created_at`.

### `owners` (extended)

| Column | Notes |
|---|---|
| `owner_code` | `NJO-0001`, … Unique and nullable until approval. Assigned from a DB sequence. |
| `status` | `PENDING_REVIEW` / `APPROVED` / `REJECTED`. Not null. Existing row is `APPROVED`. |
| `travels_name`, `contact_name`, `phone` | Required for new signups. Nullable for the migrated row. |
| `pan_number` | Encrypted with `EncryptedStringConverter`. A hash column `pan_hash` is unique and used for the duplicate check. |
| `aadhaar_last4` | Only the last 4 digits are stored. The full number is validated with its check digit at signup and then discarded. |
| `registration_doc_key`, `pan_doc_key`, `aadhaar_doc_key` | Keys in the existing `FileStore` (local, S3 or DB, using the same setting as driver documents). |
| `bank_account_holder`, `bank_ifsc`, `bank_name` | Plain text. |
| `bank_account_number`, `upi_id` | Encrypted. |
| `rejection_reason`, `submitted_at`, `reviewed_at`, `reviewed_by_admin_id` | Review trail. |
| `admin_reminded_at` | Set when the 36-hour reminder is sent, so it is sent only once. |
| `must_change_password` | Set to true when credentials are generated. |
| `password` | Becomes **nullable**: pending and rejected owners have none. |

### `owner_cities` (new)
`owner_id`, `city_id`, primary key on both.

### `owners`: consent
`aadhaar_consent_at` records when the owner ticked the consent box. It is required for new signups.

### `owner_verification_checks` (new)
`owner_id`, `check_type` (`PAN` / `AADHAAR` / `REGISTRATION_DOC` / `BANK`), `result` (`VERIFIED` / `FAILED`), `note`, `checked_by_admin_id`, `checked_at`. The primary key is (`owner_id`, `check_type`). Every change is also written to the application log, so there is an audit trail of who checked what and when.

## Flows

### 1. Owner signup: `POST /api/auth/owner/signup` (public, multipart)

1. Validate the fields:
   - PAN matches `[A-Z]{5}[0-9]{4}[A-Z]`
   - IFSC matches `[A-Z]{4}0[A-Z0-9]{6}`
   - Aadhaar passes its check digit (reuse `DriverApplicationChecks`)
   - at least one city is given and every city exists
  - the Aadhaar consent box is ticked ("I consent to NammaJourney verifying my Aadhaar offline for owner registration"). The time is stored in `aadhaar_consent_at`.
   - each document is an image or PDF of at most 5 MB
2. Reject the signup if the email or PAN hash already belongs to an owner, unless that owner is `REJECTED` (see Resubmit).
3. Store the documents and save the owner as `PENDING_REVIEW` with `submitted_at = now`.
4. Queue two outbox emails:
   - **To `ADMIN_NOTIFY_EMAIL`:** "New owner signup: {travels name}, {cities}. Review by {submitted_at + 48h}", with a link to `/admin/owners/{id}`.
   - **To the owner:** "Application received. We will verify within 48 hours."

### 2. Admin review (`/api/admin/**`, `ROLE_ADMIN` only)

- `GET /api/admin/owners?status=` lists owners. Each row shows submitted time, hours waiting and whether it is overdue (more than 48 hours).
- `GET /api/admin/owners/{id}` returns details, masked bank and PAN values, and the results of each automatic check.
- `GET /api/admin/owners/{id}/documents/{type}` streams a document, or returns a signed URL on S3.
- **Verification checklist** (`PUT /api/admin/owners/{id}/checks/{type}`, body `{result, note}`). The admin checks each item on a free official source and records the result:

  | Check | How the admin verifies it (free) |
  |---|---|
  | `PAN` | Income Tax e-filing portal, "Verify Your PAN": the PAN is active and the name matches |
  | `AADHAAR` | Prefilled by the automatic QR check, which runs at signup and is stored in `owner_aadhaar_qr` (status, name, DOB, gender, state, pincode, last 4 digits and photo; never the full number). The admin sees "UIDAI signature valid ✔", the QR name next to the travels/contact name, and the QR photo. The QR photo is JPEG 2000, so the server converts it for the browser. The admin then marks the check. If the status is `UNREADABLE` or `NOT_SECURE_QR` (blurry photo, or an old card without a Secure QR), the admin either scans the card with UIDAI's free QR scanner app or rejects the application and asks for a clearer photo. `INVALID_SIGNATURE` means the card was edited, so reject. |
  | `REGISTRATION_DOC` | Compare the business name and number with the travels name. Where the registration number can be looked up (for example GST or Udyam), check it on that portal. |
  | `BANK` | Account holder name matches the owner or travels name, and the IFSC is valid (RBI IFSC list) |

  The admin screen shows a button per check that opens the official site in a new tab. A `FAILED` check needs a note, and that note is suggested as the rejection reason.
- **Approve** (`POST /api/admin/owners/{id}/approve`):
  - Allowed only from `PENDING_REVIEW`, and only when **all four checks are `VERIFIED`**. Otherwise it returns 409 with the list of missing or failed checks.
  - Assigns `owner_code` from the sequence, generates a 12-character random password, stores its bcrypt hash, sets `must_change_password = true` and fills in the review trail.
  - Sends the credentials email to the owner **directly**, not through the outbox, the same way `sendDriverCredentials` works today, so the password is never written to the database: "Approved. Owner ID {code}, password {password}, login link".
  - If sending fails, the approval still stands. The admin then gets a "Resend credentials" button, which generates a new password and sends the email again.
- **Reject** (`POST /api/admin/owners/{id}/reject`, reason required):
  - Sets the status to `REJECTED`.
  - Queues an email with the reason and a link to `/owner/signup?resubmit={token}`.

### 3. Resubmit

The owner opens the resubmit link, which carries a signed, single-use token valid for 14 days. The signup form opens pre-filled. The existing documents are kept unless the owner uploads replacements. Submitting it updates the same row back to `PENDING_REVIEW` with a new `submitted_at`. The admin alert email is sent again.

### 4. Owner login: `POST /api/auth/owner/login` (changed)

- Accepts the Owner ID or the email, plus the password.
- `PENDING_REVIEW` returns 403 "Your application is under review".
- `REJECTED` returns 403 "Your application was rejected: {reason}".
- If `must_change_password` is set, the response flags it. The frontend sends the owner to a change-password screen, and the backend allows only `POST /api/owner/change-password` until it is cleared.

### 5. 48-hour deadline

`OwnerReviewReminderJob` is scheduled hourly, like the existing sweepers. It finds owners that are `PENDING_REVIEW`, have `submitted_at` more than 36 hours ago and `admin_reminded_at` still null. It queues one reminder email per owner to `ADMIN_NOTIFY_EMAIL` and sets `admin_reminded_at`.

### 6. Admin bootstrap: `POST /api/auth/admin/bootstrap`

Requires the header `X-Bootstrap-Secret` = `ADMIN_BOOTSTRAP_SECRET`. It works only while the `admins` table is empty.

### Removed single-owner assumptions (this sub-project)

- `create-admin` (owner bootstrap) is deleted. Owners now come only from signup and approval.
- Every place that looks up "the" owner (for example `findAll().get(0)` or the Telegram owner alerts) is listed in the implementation plan. Each one is pointed at owner `NJO-0001` for now and properly scoped per owner in sub-project 2.

## Frontend

- `/owner/signup`: public form with fields, a multi-select of cities, and uploads for the three documents. On submit it shows a "within 48 hours" confirmation.
- Owner login page: accepts the Owner ID or the email, shows the pending or rejected message, and redirects to the change-password screen when required.
- `/admin/login` and `/admin/owners`, with Pending, Approved and Rejected tabs:
  - The Pending tab is sorted by oldest first, with an overdue badge on anything past 48 hours.
  - The detail view shows the document viewer, the automatic format-check results, and the verification checklist: for each of PAN, Aadhaar, registration document and bank, an "Open official site" button, ✔ Verified / ✘ Failed buttons and a note field.
  - Approve stays disabled until all four checks are verified. Reject is always available and needs a reason box.
- Owner profile: the migrated `NJO-0001` is prompted to complete its business details.

## Security

- `SecurityConfig`: `/api/admin/**` requires `ROLE_ADMIN`. `/api/auth/owner/signup` and `/api/auth/admin/bootstrap` are public and rate-limited with the existing limiter.
- Owner documents are never served to `ROLE_OWNER` or `ROLE_USER`, only to `ROLE_ADMIN`.
- PAN, bank account and UPI are encrypted at rest, and the API returns them masked (`XXXXX1234A`, `XXXXXX6789`).
- The generated password exists only in memory and in the email that is sent directly. It is never stored in plain text and never written to the outbox.

## Testing

- **Unit:** PAN, IFSC and Aadhaar validation; `owner_code` formatting; the reminder job's selection rule.
- **Integration (Spring Boot test):**
  - signup creates the owner as pending and queues 2 emails
  - a duplicate email or PAN is rejected
  - signup without Aadhaar consent is rejected
  - approve returns 409 until all four checks are verified, and a failed check without a note is rejected
  - approve sets the code and password and sends the credentials email
  - reject followed by resubmit
  - login is blocked while pending or rejected
  - `must_change_password` blocks every endpoint except change-password
  - only the admin can reach the admin endpoints and documents
- **Migration:** V26 on a copy of the current schema. The existing owner becomes `NJO-0001` / `APPROVED` and can still log in.
- **Manual local run:** sign up as a new owner, receive the real emails at nammajourney463@gmail.com, approve, log in with the emailed credentials, and change the password.

## Out of scope (later sub-projects)

- Per-owner vehicles, drivers, prices and dashboard scoping (2). This includes owners manually approving their own drivers, with a driving licence and RC checklist on free Parivahan sources (Sarathi "DL Status", Vahan / mParivahan).
- Paid KYC APIs and DigiLocker integration. These are not planned. The checklist is built so an automatic verifier could fill it in later.
- Customer compare-and-book (3)
- Commission statements and payments (4)
- City rides: unchanged
