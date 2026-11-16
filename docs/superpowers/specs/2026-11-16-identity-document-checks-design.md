# Identity document checks before a login is created

Date: 2026-11-16 · Branch: `feat/document-verification`

## Goal

Stop fake logins: a driver or owner account is created only from a genuine Aadhaar that belongs
to the person signing up. Everything runs on our own server, offline. No KYC vendor, nothing sent out.

## The check (`IdentityDocumentChecker`)

For an Aadhaar upload, in order:

1. **Usable file:** a JPG/PNG of at least 10 KB and 500 px on the long side.
2. **Card colours (`CardLook`):** the saffron and green bands. This is a first filter that turns away selfies and
   plates. It proves nothing on its own, because colours are easy to copy.
3. **Secure QR + UIDAI signature (`AadhaarQrVerifier`):** an edited or forged card fails here.
4. **Matches the form:** the QR's last 4 digits match the typed number, and the QR name matches the typed
   name (`NameMatch`: initials and word order are ignored, and one letter may differ in a long word).

| Outcome | When |
|---|---|
| PASS | Signature VALID and the number and name match |
| REVIEW | Everything matches, but the signature could not be checked (no UIDAI certificate configured) |
| REJECT | Unreadable, old unsigned QR, signature invalid, number or name mismatch, or not a card |

PAN: file + format + colours only (there's no offline proof), so a PAN gets REVIEW at best.

## Per flow

| Flow | REJECT | REVIEW | PASS |
|---|---|---|---|
| City driver self-signup | refused with the reason, nothing stored | account kept, held for the admin | approved the moment the email is confirmed |
| Re-upload after rejection | refused | under review | back to automatic approval |
| Owner adds a driver | refused, driver not created | created (owner reviews) | created (owner reviews) |
| Owner signup | recorded for the admin | recorded | recorded; the admin approves every owner |

The automatic approval round (`DriverAutoApprovalService`) now holds every driver whose `doc_check` is not
PASS, or whose form checks fail. Before this change it approved them anyway and only logged the problem.

## Duplicates

- Drivers: `aadhaar_number`, licence, mobile, email and plate are unique (existing).
- Owners: new `owners.aadhaar_hash` (HMAC, same scheme as `pan_hash`) with a unique index. A REJECTED
  application releases its hash so a misused card can't lock out the real holder.

## Sample documents

`sample document/` is git-ignored. `scripts/sample-docs.mjs` encrypts each file there (AES-256-GCM, with a
PBKDF2-SHA256 key from `SAMPLE_DOCS_KEY`) and deletes the original. `SampleDocumentsTest` decrypts the files
in memory and checks that the real cards pass the colour check and that their QR reads. It is skipped
without the key.

## Limits

- Without UIDAI's Secure QR certificate (`AADHAAR_QR_CERTIFICATES`), nothing is proven: every driver lands on
  REVIEW and waits for the admin. Configuring the certificate is required for automatic approval.
- Licence and RC are format-checked only (Parivahan has no free offline proof).
- The colour thresholds are low on purpose. Calibrate them with `SampleDocumentsTest` output.

## Update (same day): PDFs, current UIDAI key, decision A

- **PDF uploads** (`DocumentImages`): page 1 is rendered at 300 DPI for the colour check. The Secure QR is
  read from the PDF's own embedded QR image, because UIDAI's e-Aadhaar QR doesn't decode from a 300 DPI
  render. Storage receives a JPEG of the page.
- **Decision A:** an Aadhaar with no readable QR is refused. A locked e-Aadhaar is unlocked automatically.
  Its password is the first 4 letters of the name (as printed, in capitals) plus the birth year, so the
  typed name is tried with each year from now back to 1900 (about 2 ms per try). A name that doesn't open
  it is refused, with a message to type the name as printed. The password found is used only to render
  the page for storage. It is never stored or logged (`Result.toString` leaves it out).
- **UIDAI certificates:** the uidai.gov.in Developer page lists only the 2017/2018 keys (DS UIDAI 4 and 7).
  A 2026 e-Aadhaar is signed with "DS Unique Identification Authority of India 06" (Feb 2026 to Feb 2029,
  (n)Code Solutions Sub-CA under CCA India 2022). That certificate was taken from the e-Aadhaar PDF's own
  signature. Configure all three. When UIDAI rotates its key, take the new certificate from a fresh
  e-Aadhaar the same way.
- Verified on a real e-Aadhaar: QR VALID with the DS-06 key.
