@echo off
echo ========================================
echo Travel Booking Platform - Email OTP API Tests
echo ========================================
echo.

set BASE_URL=http://localhost:8080/api/auth

echo Test 1: Send OTP to Email
echo --------------------------
curl -X POST %BASE_URL%/otp/send ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\"}"
echo.
echo.

echo Test 2: User Signup with Email
echo --------------------------------
curl -X POST %BASE_URL%/user/signup ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"rajesh@example.com\",\"name\":\"Rajesh Kumar\"}"
echo.
echo.

echo Test 3: User Login with Email OTP
echo -----------------------------------
echo Note: Check your email for OTP or use the OTP from logs
curl -X POST %BASE_URL%/user/login ^
  -H "Content-Type: application/json" ^
  -d "{\"loginType\":\"EMAIL\",\"email\":\"rajesh@example.com\",\"otp\":\"123456\"}"
echo.
echo.

echo Test 4: User Login with Google
echo --------------------------------
curl -X POST %BASE_URL%/user/login ^
  -H "Content-Type: application/json" ^
  -d "{\"loginType\":\"GOOGLE\",\"token\":\"mock_google_token\"}"
echo.
echo.

echo Test 5: Driver Signup
echo ----------------------
curl -X POST %BASE_URL%/driver/signup ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Suresh Sharma\",\"mobile\":\"9123456789\",\"password\":\"driver@123\",\"licenseNumber\":\"DL1420110012345\",\"aadhaarNumber\":\"123456789012\"}"
echo.
echo.

echo Test 6: Driver Login
echo ---------------------
curl -X POST %BASE_URL%/driver/login ^
  -H "Content-Type: application/json" ^
  -d "{\"mobile\":\"9123456789\",\"password\":\"driver@123\"}"
echo.
echo.

echo ========================================
echo Tests Completed
echo ========================================
pause
