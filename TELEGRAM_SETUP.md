# Telegram Driver Dispatch — Setup Guide

## What this solves

A driver at the wheel cannot read an email or open the web app. Their phone rings, they can't
answer, and the ride goes to somebody else. This channel puts the trip in front of them where
their phone already alerts loudly and reliably — Telegram — with an **Accept** button they can
press with one thumb, without opening the website at all.

```
Owner assigns a driver
        │
        ├─→ in-app notification   (record — unchanged)
        ├─→ email                 (record — unchanged)
        └─→ Telegram card         ← new: buzzes the phone, one-tap Accept
                │
        Driver taps ✅ Accept  →  webhook  →  booking becomes CONFIRMED
                                              passenger is notified
```

Telegram is used **only for drivers**, never for passengers. Drivers are a small set the owner
onboards in person, so asking them to install one app is realistic. Passengers are the open
public and stay on the website.

---

## Before you start

- The backend must be reachable from the public internet over **HTTPS**. Telegram will not
  deliver to plain HTTP, and will not deliver to `localhost`. For local development, use
  `ngrok` (Step 5a).
- Everything below is optional. Leave `TELEGRAM_ENABLED=false` and the platform behaves
  exactly as it did before.

---

## Step 1 — Create the bot

1. Open Telegram and search for **@BotFather**.
2. Send `/newbot`.
3. Give it a display name, e.g. `Namma Journey Driver`.
4. Give it a username ending in `bot`, e.g. `NammaJourneyDriver_bot`. This must be globally
   unique — if it's taken, try another.
5. BotFather replies with a token like `8123456789:AAH1a2B3c4D5e6F7g8H9i0J1k2L3m4N5o6P`.

**Copy that token somewhere safe and never commit it.** Anyone holding it can post as your bot
and read every message sent to it. If it leaks, send `/revoke` to BotFather immediately.

Optional polish while you're in BotFather:

```
/setdescription  → Get your assigned trips instantly. Accept with one tap.
/setabouttext    → Namma Journey driver alerts
/setuserpic      → upload your logo
```

---

## Step 2 — Generate a webhook secret

This is what proves an incoming request really came from Telegram and not from someone who
guessed your URL. Generate a long random value:

```bash
openssl rand -hex 32
```

No `openssl`? On Windows PowerShell:

```powershell
-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
```

---

## Step 3 — Configure the backend

Add to your `.env` (never to `.env.example`, and never to git):

```bash
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=8123456789:AAH1a2B3c4D5e6F7g8H9i0J1k2L3m4N5o6P
TELEGRAM_BOT_USERNAME=NammaJourneyDriver_bot
TELEGRAM_WEBHOOK_SECRET=<the value from Step 2>
```

`TELEGRAM_BOT_USERNAME` has no `@` — a leading `@` is stripped automatically, but the plain
form is clearer.

---

## Step 4 — Apply the database migration

Nothing to run by hand. Flyway applies `V2__driver_telegram_link.sql` on the next start:

```bash
mvn clean package
java -jar target/*.jar
```

Confirm it applied:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
-- 2 | driver telegram link | t
```

If startup fails with a Flyway validation error, your database has drifted from the migration
files. Do **not** delete the history table — investigate the mismatch first.

---

## Step 5 — Make the webhook reachable

### 5a. Local development (ngrok)

```bash
ngrok http 8080
```

Copy the `https://` forwarding URL, e.g. `https://a1b2-49-207-x-x.ngrok-free.app`.

> The free ngrok URL changes every restart. Each time it does, re-run Step 6.

### 5b. Production

Point your domain at the server and terminate TLS with Caddy or Nginx + Let's Encrypt. Your
webhook URL is then `https://api.yourdomain.in/api/telegram/webhook`.

Minimal Caddy config, which obtains and renews the certificate for you:

```
api.yourdomain.in {
    reverse_proxy localhost:8080
}
```

---

## Step 6 — Register the webhook with Telegram

Tell Telegram where to deliver updates, and give it the secret from Step 2:

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://YOUR_DOMAIN/api/telegram/webhook",
    "secret_token": "<YOUR_WEBHOOK_SECRET>",
    "allowed_updates": ["message", "callback_query"]
  }'
```

Expected: `{"ok":true,"result":true,"description":"Webhook was set"}`

Verify it stuck:

```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

Look at `pending_update_count` (should be 0) and `last_error_message` (should be absent).
That endpoint is your first stop for any delivery problem.

---

## Step 7 — Connect a driver

1. Sign in as **Owner** → **Drivers** → open the driver.
2. Under **Telegram Alerts**, click **Create connect link**.
3. Send the link to that driver — or better, show it as a QR code and have them scan it in
   person during onboarding.
4. The driver taps the link, Telegram opens the bot, they press **Start**.
5. The bot replies *"Linked successfully"*, and the driver's record flips to **✓ Connected**.

**About that link:** it is a one-time credential valid for 24 hours. Whoever opens it can
accept and reject that driver's trips, so treat it like a password — send it to one person,
not a group. Creating a new link revokes the previous one. It is deliberately random rather
than derived from the driver ID, so nobody can guess another driver's link.

The driver can send `/status` to the bot at any time to check they're still connected.

---

## Step 8 — Test the whole flow

1. Book a trip as a passenger.
2. As owner, assign it to the connected driver.
3. The driver's phone should buzz within a second or two with the trip card.
4. Tap **✅ Accept**.
5. The card rewrites itself to *"Trip accepted"*, the booking becomes `CONFIRMED`, and the
   passenger is notified.

Also test the unhappy path, because it's the one that bites in production: assign a trip,
then reassign it to someone else **before** the first driver taps Accept. Their card should
say *"This trip is no longer available"* rather than erroring.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No card arrives | Driver shows **Not connected** — they never redeemed the link |
| No card, driver is connected | `TELEGRAM_ENABLED=false`, or the token is wrong. Check backend logs for `Telegram sendMessage failed` |
| Telegram reports 403 in `getWebhookInfo` | `secret_token` in Step 6 does not match `TELEGRAM_WEBHOOK_SECRET` |
| Telegram reports 404 | `TELEGRAM_ENABLED=false` — the endpoint hides itself when the integration is off |
| Button spins forever | Backend unreachable or erroring. Check logs and `getWebhookInfo` |
| "This link is not valid or has already been used" | Links are single-use. Create a new one |
| Works locally, breaks after restart | ngrok issued a new URL — re-run Step 6 |

---

## Security notes

- **The bot token and webhook secret are credentials.** They live in `.env`, which is
  gitignored. Rotate both if they ever appear in a commit, a screenshot, or a log.
- **The webhook fails closed.** With no `TELEGRAM_WEBHOOK_SECRET` configured, the endpoint
  rejects every request rather than trusting unverified callers.
- **Identity never comes from the payload.** The acting driver is resolved from the Telegram
  user ID on the update and matched against a stored link; the existing accept/reject logic
  then independently checks the booking actually belongs to them. A forged payload cannot act
  as another driver.
- **Passenger text is escaped.** Place names and passenger names are user input rendered as
  HTML in the card, so they are escaped before sending.
- **Telegram is an accelerator, not the record.** In-app notifications and email still fire.
  If Telegram is down, blocked, or the driver never linked, bookings still work — the driver
  just doesn't get the fast alert.

---

## Turning it off

Set `TELEGRAM_ENABLED=false` and restart. All sends become no-ops and the webhook returns 404.
Stored links stay in the database, so re-enabling does not require re-onboarding drivers.

To stop Telegram calling you entirely:

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/deleteWebhook"
```

---

## Known limits

- **One Telegram account per driver.** A shared phone cannot serve two driver accounts.
- **Rate limits.** Telegram allows ~30 messages/second overall and 1/second per chat. Far
  above this deployment's needs, but relevant if you later broadcast to all drivers at once.
- **Assignment is still manual.** The owner picks the driver; this channel only makes the
  hand-off fast. Automatic driver matching is the natural next step.
- **No fallback if the driver misses it.** If nobody accepts, nothing escalates yet. A useful
  addition: flag un-accepted trips on the owner dashboard after 60 seconds so a human can
  phone the driver.
