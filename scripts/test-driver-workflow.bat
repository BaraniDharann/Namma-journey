@echo off
REM Driver Creation Workflow - Test Script
REM This script tests the complete driver creation and verification workflow

echo ========================================
echo Driver Creation Workflow - Test Script
echo ========================================
echo.

set BASE_URL=http://localhost:8080
set OWNER_EMAIL=admin@travelplatform.com
set OWNER_PASSWORD=owner@123
set DRIVER_NAME=Test Driver
set DRIVER_MOBILE=9876543210
set DRIVER_EMAIL=testdriver@example.com
set DRIVER_LICENSE=DL1420110012345
set DRIVER_AADHAAR=123456789012

echo Step 1: Owner Login
echo -------------------
curl -X POST %BASE_URL%/api/auth/owner/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%OWNER_EMAIL%\",\"password\":\"%OWNER_PASSWORD%\"}" ^
  -o owner_login.json
echo.
echo Response saved to owner_login.json
echo Please copy the token and set it as OWNER_TOKEN variable
echo.
pause

set /p OWNER_TOKEN="Enter Owner Token: "
echo.

echo Step 2: Create Driver
echo ---------------------
curl -X POST %BASE_URL%/api/owner/drivers ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %OWNER_TOKEN%" ^
  -d "{\"name\":\"%DRIVER_NAME%\",\"mobile\":\"%DRIVER_MOBILE%\",\"email\":\"%DRIVER_EMAIL%\",\"licenseNumber\":\"%DRIVER_LICENSE%\",\"aadhaarNumber\":\"%DRIVER_AADHAAR%\",\"photo\":\"https://example.com/photo.jpg\",\"licensePhoto\":\"https://example.com/license.jpg\",\"aadhaarPhoto\":\"https://example.com/aadhaar.jpg\"}" ^
  -o create_driver.json
echo.
echo Response saved to create_driver.json
echo Check driver email for OTP
echo.
pause

set /p OTP="Enter OTP from driver email: "
echo.

echo Step 3: Verify Driver Email
echo ---------------------------
curl -X POST %BASE_URL%/api/owner/drivers/verify ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %OWNER_TOKEN%" ^
  -d "{\"email\":\"%DRIVER_EMAIL%\",\"otp\":\"%OTP%\"}" ^
  -o verify_driver.json
echo.
echo Response saved to verify_driver.json
echo Check driver email for login credentials
echo.
pause

set /p DRIVER_PASSWORD="Enter password from driver email: "
echo.

echo Step 4: Driver First Login
echo --------------------------
curl -X POST %BASE_URL%/api/auth/driver/login ^
  -H "Content-Type: application/json" ^
  -d "{\"mobile\":\"%DRIVER_MOBILE%\",\"password\":\"%DRIVER_PASSWORD%\"}" ^
  -o driver_login.json
echo.
echo Response saved to driver_login.json
echo Check firstLogin flag in response
echo.
pause

set /p DRIVER_TOKEN="Enter Driver Token: "
echo.

set /p NEW_PASSWORD="Enter new password for driver: "
echo.

echo Step 5: Change Password
echo -----------------------
curl -X POST %BASE_URL%/api/auth/driver/change-password ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %DRIVER_TOKEN%" ^
  -d "{\"oldPassword\":\"%DRIVER_PASSWORD%\",\"newPassword\":\"%NEW_PASSWORD%\"}" ^
  -o change_password.json
echo.
echo Response saved to change_password.json
echo.
pause

echo Step 6: Login with New Password
echo --------------------------------
curl -X POST %BASE_URL%/api/auth/driver/login ^
  -H "Content-Type: application/json" ^
  -d "{\"mobile\":\"%DRIVER_MOBILE%\",\"password\":\"%NEW_PASSWORD%\"}" ^
  -o driver_login_new.json
echo.
echo Response saved to driver_login_new.json
echo Check firstLogin flag should be false
echo.

echo ========================================
echo Test Complete!
echo ========================================
echo.
echo Response files created:
echo - owner_login.json
echo - create_driver.json
echo - verify_driver.json
echo - driver_login.json
echo - change_password.json
echo - driver_login_new.json
echo.
pause
