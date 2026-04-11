# Environment Verification Checklist

## ✅ Before Testing - Verify Your Setup

### 1. Check .env File Configuration

Your current `.env` file has:
```
✅ DB_URL=jdbc:postgresql://localhost:5432/namma_journey
✅ DB_USERNAME=postgres
✅ DB_PASSWORD=Mypassword
✅ JWT_SECRET=mySecretKeyForJWTTokenGenerationAndValidation12345678901234567890
✅ JWT_EXPIRATION=86400000
✅ GOOGLE_CLIENT_ID=448523730319-0n5p4iua6lscrgqrrrupkvj585iulge4.apps.googleusercontent.com
✅ GOOGLE_CLIENT_SECRET=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
✅ SENDGRID_API_KEY=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
✅ SENDGRID_FROM_EMAIL=tlokeshthiru123@gmail.com
✅ SENDGRID_FROM_NAME=Namma Journey
✅ OTP_EXPIRY_MINUTES=5
```

---

## 🔍 Step-by-Step Verification

### Step 1: Verify SendGrid Configuration

**A. Check SendGrid API Key is Active**

Open PowerShell and run:
```powershell
$headers = @{
    "Authorization" = "Bearer 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"
    "Content-Type" = "application/json"
}

$body = @{
    personalizations = @(
        @{
            to = @(
                @{
                    email = "tlokeshthiru123@gmail.com"
                }
            )
        }
    )
    from = @{
        email = "tlokeshthiru123@gmail.com"
        name = "Namma Journey"
    }
    subject = "SendGrid Test"
    content = @(
        @{
            type = "text/plain"
            value = "This is a test email from SendGrid"
        }
    )
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "https://api.sendgrid.com/v3/mail/send" -Method Post -Headers $headers -Body $body
```

**Expected Result:** No error = API key is valid ✅

**B. Verify Sender Email**
1. Go to: https://app.sendgrid.com/settings/sender_auth
2. Login with your SendGrid account
3. Check if `tlokeshthiru123@gmail.com` is verified
4. If not verified, click "Verify" and check your Gmail

---

### Step 2: Verify Google OAuth Configuration

**A. Check Google Cloud Console**

1. Go to: https://console.cloud.google.com/apis/credentials
2. Find your OAuth 2.0 Client ID: `448523730319-0n5p4iua6lscrgqrrrupkvj585iulge4`
3. Click on it to edit
4. Verify these settings:

**Authorized JavaScript origins:**
```
http://localhost:8080
http://localhost:3000
http://localhost
```

**Authorized redirect URIs:**
```
http://localhost:8080/login/oauth2/code/google
http://localhost:3000
```

**B. Check OAuth Consent Screen**
1. Go to: https://console.cloud.google.com/apis/credentials/consent
2. Ensure status is "Testing" or "Published"
3. If "Testing", add your test email addresses

---

### Step 3: Verify Database Connection

**Open Command Prompt and run:**
```cmd
psql -U postgres -d namma_journey
```

**If connected successfully, run:**
```sql
-- Check if tables exist
\dt

-- Expected tables:
-- users, drivers, owners, otps

-- Check OTP table structure
\d otps

-- Check if you can insert test data
SELECT NOW();
```

**Exit:** `\q`

---

### Step 4: Verify Spring Boot Application

**A. Check if application is running:**
```cmd
curl http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{"status":"UP"}
```

**B. Check application logs for errors:**
Look for these in console:
```
✅ Started TravelPlatformApplication
✅ Tomcat started on port(s): 8080
❌ No errors about SendGrid
❌ No errors about database connection
```

---

## 🧪 Quick Live Tests

### Test 1: Live OTP Email (2 minutes)

**Replace with YOUR email:**
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"YOUR_EMAIL@gmail.com\"}"
```

**Expected:**
1. Response: `{"message":"OTP sent successfully to email","success":true}`
2. Email arrives in 10-30 seconds
3. Check inbox AND spam folder

**If email NOT received:**
- Check SendGrid dashboard: https://app.sendgrid.com/email_activity
- Look for your email in activity log
- Check if email was blocked/bounced

---

### Test 2: Google OAuth Token (3 minutes)

**Get test token:**
1. Go to: https://developers.google.com/oauthplayground/
2. Click ⚙️ Settings
3. Check "Use your own OAuth credentials"
4. Enter:
   - Client ID: `448523730319-0n5p4iua6lscrgqrrrupkvj585iulge4.apps.googleusercontent.com`
   - Client Secret: `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`
5. Select: `Google OAuth2 API v2` → `userinfo.email` + `userinfo.profile`
6. Click "Authorize APIs"
7. Sign in with Google
8. Click "Exchange authorization code for tokens"
9. Copy the `id_token`

**Test the token:**
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"GOOGLE\",\"token\":\"PASTE_TOKEN_HERE\"}"
```

**Expected:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "...",
  "message": "Authentication successful"
}
```

---

## 🚨 Common Issues & Solutions

### Issue 1: OTP Email Not Received

**Solution A: Check SendGrid Activity**
```
1. Go to: https://app.sendgrid.com/email_activity
2. Search for your email
3. Check status: Delivered/Bounced/Dropped
```

**Solution B: Verify Sender**
```
1. Go to: https://app.sendgrid.com/settings/sender_auth
2. Verify tlokeshthiru123@gmail.com
3. Check Gmail for verification email
```

**Solution C: Check API Key Permissions**
```
1. Go to: https://app.sendgrid.com/settings/api_keys
2. Find your API key
3. Ensure "Mail Send" permission is enabled
```

---

### Issue 2: Google OAuth Fails

**Solution A: Token Expired**
```
Google tokens expire in 1 hour
Get fresh token from OAuth Playground
```

**Solution B: Invalid Client ID**
```
Verify in Google Cloud Console:
https://console.cloud.google.com/apis/credentials
```

**Solution C: OAuth Consent Screen**
```
1. Go to: https://console.cloud.google.com/apis/credentials/consent
2. Add your email as test user
3. Ensure app is not suspended
```

---

### Issue 3: Database Connection Failed

**Solution:**
```cmd
# Check PostgreSQL is running
sc query postgresql-x64-14

# If not running, start it
net start postgresql-x64-14

# Test connection
psql -U postgres -d namma_journey
```

---

## 📋 Final Checklist Before Testing

- [ ] PostgreSQL service is running
- [ ] Database `namma_journey` exists
- [ ] Spring Boot application is running on port 8080
- [ ] No errors in application console
- [ ] `.env` file is in project root
- [ ] SendGrid sender email is verified
- [ ] Google OAuth credentials are valid
- [ ] You have a real email address for testing
- [ ] You can access Google OAuth Playground

---

## 🎯 Success Criteria

### OTP Email Working ✓
```bash
# Send OTP
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email":"test@gmail.com"}'

# Response: {"message":"OTP sent successfully to email","success":true}
# Email received within 30 seconds ✓
# OTP is 6 digits ✓
```

### Google OAuth Working ✓
```bash
# Login with Google
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"GOOGLE","token":"valid_token"}'

# Response: {"token":"jwt_token","role":"ROLE_USER",...} ✓
```

---

## 📞 Need Help?

**Check Application Logs:**
```
Look for errors in Spring Boot console
Search for: "SendGrid", "OTP", "Google", "OAuth"
```

**Check Database:**
```sql
-- Check OTP entries
SELECT * FROM otps ORDER BY created_at DESC LIMIT 5;

-- Check users
SELECT * FROM users ORDER BY created_at DESC LIMIT 5;
```

**Test Individual Components:**
1. Database: `psql -U postgres -d namma_journey`
2. SendGrid: Use PowerShell script above
3. Google OAuth: Use OAuth Playground
4. Application: `curl http://localhost:8080/actuator/health`
