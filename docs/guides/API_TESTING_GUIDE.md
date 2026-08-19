# Live API Testing Guide - OTP & Google OAuth

## ✅ Prerequisites Checklist

1. **PostgreSQL Running**: Database `namma_journey` exists
2. **Spring Boot Running**: `mvn spring-boot:run`
3. **Environment Variables**: `.env` file loaded with:
   - ✅ Gmail SMTP API Key configured
   - ✅ Google OAuth credentials configured
   - ✅ Database credentials set

---

## 🔥 LIVE OTP EMAIL TESTING

### Step 1: Send OTP to Real Email

**Test with YOUR real email address:**

```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"YOUR_REAL_EMAIL@gmail.com\"}"
```

**Expected Response:**
```json
{
  "message": "OTP sent successfully to email",
  "success": true
}
```

### Step 2: Check Your Email Inbox

1. Open your email inbox (check spam/junk folder too)
2. Look for email from: **Namma Journey** (demo.user@example.com)
3. Subject: **"Your OTP for Namma Journey"**
4. Copy the 6-digit OTP code

**Email will look like:**
```
Your OTP Code
Your OTP for Namma Journey is: 123456
This OTP is valid for 5 minutes.
```

### Step 3: User Signup with Email

```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"YOUR_REAL_EMAIL@gmail.com\",\"name\":\"Your Name\"}"
```

**Save the token from response!**

### Step 4: Login with OTP (Use OTP from Email)

```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"EMAIL\",\"email\":\"YOUR_REAL_EMAIL@gmail.com\",\"otp\":\"PASTE_OTP_HERE\"}"
```

**Replace `PASTE_OTP_HERE` with actual OTP from your email!**

---

## 🔐 GOOGLE OAUTH TESTING

### Method 1: Using Google OAuth Playground (Easiest)

#### Step 1: Get Google Token

1. Go to: https://developers.google.com/oauthplayground/
2. Click **Settings** (gear icon) → Check "Use your own OAuth credentials"
3. Enter your credentials:
   - **OAuth Client ID**: `448523730319-0n5p4iua6lscrgqrrrupkvj585iulge4.apps.googleusercontent.com`
   - **OAuth Client Secret**: `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`
4. In left panel, select: **Google OAuth2 API v2**
   - Check: `https://www.googleapis.com/auth/userinfo.email`
   - Check: `https://www.googleapis.com/auth/userinfo.profile`
5. Click **"Authorize APIs"**
6. Sign in with your Google account
7. Click **"Exchange authorization code for tokens"**
8. Copy the **`id_token`** value (long JWT string)

#### Step 2: Test Google Login

```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"GOOGLE\",\"token\":\"PASTE_GOOGLE_ID_TOKEN_HERE\"}"
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "uuid-here",
  "message": "Authentication successful"
}
```

---

### Method 2: Using Postman

1. **Install Postman** (if not installed)
2. Create new request:
   - Method: `POST`
   - URL: `http://localhost:8080/api/auth/user/login`
   - Headers: `Content-Type: application/json`
   - Body (raw JSON):
   ```json
   {
     "loginType": "GOOGLE",
     "token": "YOUR_GOOGLE_TOKEN"
   }
   ```
3. Click **Send**

---

### Method 3: Frontend HTML Test Page

Create `test-google-login.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Google OAuth Test</title>
    <script src="https://accounts.google.com/gsi/client" async defer></script>
</head>
<body>
    <h1>Test Google Login</h1>
    
    <div id="g_id_onload"
         data-client_id="448523730319-0n5p4iua6lscrgqrrrupkvj585iulge4.apps.googleusercontent.com"
         data-callback="handleCredentialResponse">
    </div>
    
    <div class="g_id_signin" data-type="standard"></div>
    
    <div id="result"></div>

    <script>
        function handleCredentialResponse(response) {
            console.log("Google Token:", response.credential);
            
            // Test your API
            fetch('http://localhost:8080/api/auth/user/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    loginType: 'GOOGLE',
                    token: response.credential
                })
            })
            .then(res => res.json())
            .then(data => {
                document.getElementById('result').innerHTML = 
                    '<h3>Success!</h3><pre>' + JSON.stringify(data, null, 2) + '</pre>';
            })
            .catch(error => {
                document.getElementById('result').innerHTML = 
                    '<h3>Error!</h3><pre>' + error + '</pre>';
            });
        }
    </script>
</body>
</html>
```

**Open in browser**: `file:///path/to/test-google-login.html`

---

## 🧪 Complete Testing Workflow

### Test 1: OTP Email Flow (5 minutes)

```bash
# 1. Send OTP
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"email":"youremail@gmail.com"}'

# 2. Check email and get OTP

# 3. Signup
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"youremail@gmail.com","name":"Test User"}'

# 4. Login with OTP
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"EMAIL","email":"youremail@gmail.com","otp":"123456"}'
```

### Test 2: Google OAuth Flow (2 minutes)

```bash
# 1. Get token from OAuth Playground (see above)

# 2. Login with Google
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"GOOGLE","token":"YOUR_GOOGLE_TOKEN"}'
```

---

## 🔍 Troubleshooting

### OTP Email Not Received?

**Check 1: Gmail SMTP API Key Valid?**
```bash
curl --request POST \
  --url https://api.Gmail SMTP.com/v3/mail/send \
  --header "Authorization: Bearer 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" \
  --header 'Content-Type: application/json' \
  --data '{"personalizations":[{"to":[{"email":"test@example.com"}]}],"from":{"email":"demo.user@example.com"},"subject":"Test","content":[{"type":"text/plain","value":"Test"}]}'
```

**Check 2: Verify Gmail SMTP Sender**
- Go to: https://app.Gmail SMTP.com/settings/sender_auth
- Ensure `demo.user@example.com` is verified

**Check 3: Check Application Logs**
```bash
# Look for errors in console
grep -i "Gmail SMTP\|otp" logs/spring.log
```

**Check 4: Database OTP Entry**
```sql
SELECT * FROM otps ORDER BY created_at DESC LIMIT 5;
```

### Google OAuth Not Working?

**Check 1: Valid Client ID?**
- Go to: https://console.cloud.google.com/apis/credentials
- Verify Client ID matches `.env` file

**Check 2: Authorized Redirect URIs**
- Add: `http://localhost:8080`
- Add: `http://localhost:3000` (if using React)

**Check 3: OAuth Consent Screen**
- Must be configured in Google Cloud Console
- Add test users if in "Testing" mode

**Check 4: Token Expiry**
- Google tokens expire in 1 hour
- Get fresh token from OAuth Playground

---

## 📊 Verify Configuration

### Check Environment Variables Loaded

```bash
# Windows
echo %MAIL_USERNAME%
echo %GOOGLE_CLIENT_ID%

# Or check in application
curl http://localhost:8080/actuator/env | grep -i Gmail SMTP
```

### Check Database Connection

```sql
-- Connect to PostgreSQL
psql -U postgres -d namma_journey

-- Check tables
\dt

-- Check OTP table
SELECT * FROM otps;

-- Check users
SELECT * FROM users;
```

---

## ✅ Success Indicators

### OTP Email Working ✓
- [ ] API returns `"success": true`
- [ ] Email received in inbox within 30 seconds
- [ ] OTP is 6 digits
- [ ] OTP works for login
- [ ] Database has OTP entry

### Google OAuth Working ✓
- [ ] Token obtained from OAuth Playground
- [ ] API returns JWT token
- [ ] User created/logged in successfully
- [ ] Database has user with `login_type = 'GOOGLE'`

---

## 🚀 Quick Test Commands

**Test OTP (Replace with your email):**
```bash
EMAIL="your.email@gmail.com"

curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}"

# Check email, then:
OTP="123456"  # Replace with actual OTP

curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"name\":\"Test User\"}"

curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"EMAIL\",\"email\":\"$EMAIL\",\"otp\":\"$OTP\"}"
```

**Test Google (Get token first):**
```bash
GOOGLE_TOKEN="paste_token_here"

curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"GOOGLE\",\"token\":\"$GOOGLE_TOKEN\"}"
```

---

## 📝 Notes

1. **OTP expires in 5 minutes** - Test quickly after receiving email
2. **Google tokens expire in 1 hour** - Get fresh token if expired
3. **Gmail SMTP free tier**: 100 emails/day limit
4. **Check spam folder** if email not in inbox
5. **Use real email addresses** for testing (not temp emails)

---

## 🎯 Expected Results

### Successful OTP Flow:
1. Send OTP → `200 OK` + "success": true
2. Email arrives → Within 30 seconds
3. Signup → `201 Created` + JWT token
4. Login with OTP → `200 OK` + JWT token

### Successful Google OAuth:
1. Get token → From OAuth Playground
2. Login → `200 OK` + JWT token
3. User auto-created if new
4. Subsequent logins work with same Google account
