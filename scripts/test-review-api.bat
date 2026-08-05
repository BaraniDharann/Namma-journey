@echo off
echo ========================================
echo Testing Review & Rating System API
echo ========================================
echo.

REM Set your tokens here
set USER_TOKEN=YOUR_USER_JWT_TOKEN
set OWNER_TOKEN=YOUR_OWNER_JWT_TOKEN
set USER_ID=YOUR_USER_UUID
set BOOKING_ID=YOUR_BOOKING_UUID

echo 1. Submit Review (User)
echo ----------------------------------------
curl -X POST http://localhost:8080/api/user/%USER_ID%/bookings/%BOOKING_ID%/reviews ^
  -H "Authorization: Bearer %USER_TOKEN%" ^
  -H "Content-Type: application/json" ^
  -d "{\"rating\":5,\"feedback\":\"Excellent service! Driver was very professional and courteous.\"}"
echo.
echo.

echo 2. View All Reviews (Owner)
echo ----------------------------------------
curl -X GET http://localhost:8080/api/owner/reviews ^
  -H "Authorization: Bearer %OWNER_TOKEN%"
echo.
echo.

echo ========================================
echo Testing Complete!
echo ========================================
pause
