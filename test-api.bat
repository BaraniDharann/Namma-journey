@echo off
REM Quick API Testing Script for Travel Booking Platform (Windows)

set BASE_URL=http://localhost:8080/api/auth

echo ==================================
echo Travel Booking Platform - API Tests
echo ==================================

REM Test 1: Send OTP
echo.
echo 1. Sending OTP...
curl -X POST %BASE_URL%/otp/send -H "Content-Type: application/json" -d "{\"phone\":\"9876543210\"}"

echo.
echo Check console for OTP in MOCK mode
set /p OTP="Enter OTP from console: "

REM Test 2: User Login with OTP
echo.
echo 2. User Login with OTP...
curl -X POST %BASE_URL%/user/login -H "Content-Type: application/json" -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"%OTP%\"}"

REM Test 3: Driver Signup
echo.
echo 3. Driver Signup...
curl -X POST %BASE_URL%/driver/signup -H "Content-Type: application/json" -d "{\"name\":\"Suresh Sharma\",\"mobile\":\"9123456789\",\"password\":\"driver@123\",\"licenseNumber\":\"DL1420110012345\",\"aadhaarNumber\":\"123456789012\"}"

REM Test 4: Driver Login
echo.
echo 4. Driver Login...
curl -X POST %BASE_URL%/driver/login -H "Content-Type: application/json" -d "{\"mobile\":\"9123456789\",\"password\":\"driver@123\"}"

echo.
echo All tests completed!
pause
