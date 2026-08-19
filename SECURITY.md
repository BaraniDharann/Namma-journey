# Security Policy

## Supported versions

This project is pre-1.0. Security fixes land on `main`; there are no maintained release branches yet.

| Version | Supported |
|---|---|
| `main` | ✅ |
| Tagged releases below 1.0 | ❌ |

## Reporting a vulnerability

**Please do not open a public GitHub issue for a security vulnerability.**

Report it privately, either by:

- Using GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability)
  (the **Security** tab → *Report a vulnerability*), or
- Emailing **help@ciyex.org**

Please include:

- What the vulnerability is and roughly how severe you think it is
- Steps to reproduce it, or a proof of concept
- Which component is affected (backend, frontend, deployment configuration)
- Any suggested fix, if you have one

### What to expect

- **Acknowledgement** within 72 hours.
- **An initial assessment** — confirmed or not, and a rough severity — within 7 days.
- **Progress updates** at least every 14 days while we work on a fix.
- **Credit** in the release notes when the fix ships, unless you'd rather stay anonymous.

Please give us a reasonable window to ship a fix before disclosing publicly. We'd rather work with
you on the timing than be surprised by it.

## Scope

In scope: authentication and authorization flaws, injection, exposure of another user's data,
privilege escalation between the USER / DRIVER / OWNER roles, payment-flow manipulation, and secrets
leaking through the API or logs.

Out of scope: vulnerabilities requiring physical access, social engineering, denial of service by
volume alone, and findings in third-party dependencies that have no exploitable path in this
codebase (report those upstream).

## For deployers

A few things this project relies on you to get right — they aren't defaults we can enforce:

- **`JWT_SECRET` has no fallback value.** Generate a unique one per environment
  (`openssl rand -hex 64`). The application will not start without it.
- **Never commit `.env`.** It's gitignored, and CI scans history for secrets.
- **Set `RATELIMIT_TRUSTED_PROXIES`** to your actual proxy addresses. Rate limiting keys on client
  IP, and `X-Forwarded-For` is only believed from a trusted peer — leave it unset behind a load
  balancer and every request looks like it came from the balancer.
- **Rotate `TELEGRAM_WEBHOOK_SECRET`** if it is ever exposed; it's what proves a webhook call really
  came from Telegram.
- Serve everything over TLS. JWTs in transit over plain HTTP are compromised JWTs.

## Known dependency advisories

`npm audit` on the frontend reports two findings that are deliberately not fixed. Both are
recorded here rather than silently carried, so a deployer can make their own call.

**`xlsx` (SheetJS) — prototype pollution + ReDoS, no patched version on npm.**
Both advisories are in the *parsing* path: they need the library to read an attacker-supplied
spreadsheet. This application only ever writes them — `frontend/src/pages/owner/OwnerRevenue.jsx`
calls `aoa_to_sheet`/`book_new`/`writeFile` to generate an owner revenue export, and no code path
anywhere reads a workbook. There is therefore no reachable exploit here. The package is also
dynamically imported, so it stays out of the main bundle. If a future change ever parses an
uploaded spreadsheet, this stops being true and the dependency must be replaced first —
`exceljs` is the usual substitute.

**`react-router` — moderate, fixed only in the v7 major.**
Upgrading is a breaking change across every route in the application. It is worth doing, but it
is a deliberate piece of work rather than something to fold into an unrelated change.

The critical `websocket-driver` advisories that previously appeared here (reached through
`sockjs-client`) are resolved — the lockfile now pins `0.7.5`.
