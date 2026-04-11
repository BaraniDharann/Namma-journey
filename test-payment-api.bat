@echo off
echo ========================================
echo Payment System API Testing
echo ========================================
echo.

REM Set your tokens here
set USER_TOKEN=your_user_token_here
set DRIVER_TOKEN=your_driver_token_here
set OWNER_TOKEN=your_owner_token_here

REM Set IDs
set USER_ID=your_user_id_here
set BOOKING_ID=your_booking_id_here
set DRIVER_ID=your_driver_id_here
set PAYMENT_ID=your_payment_id_here

echo 1. Testing User Initiates UPI Payment
echo ========================================
curl -X POST http://localhost:8080/api/user/%USER_ID%/bookings/%BOOKING_ID%/payment ^
  -H "Authorization: Bearer %USER_TOKEN%" ^
  -H "Content-Type: application/json" ^
  -d "{\"paymentMethod\":\"UPI\"}"
echo.
echo.

echo 2. Testing User Initiates Cash Payment
echo ========================================
curl -X POST http://localhost:8080/api/user/%USER_ID%/bookings/%BOOKING_ID%/payment ^
  -H "Authorization: Bearer %USER_TOKEN%" ^
  -H "Content-Type: application/json" ^
  -d "{\"paymentMethod\":\"CASH\"}"
echo.
echo.

echo 3. Testing Driver Marks Cash Received
echo ========================================
curl -X POST http://localhost:8080/api/driver/%DRIVER_ID%/bookings/%BOOKING_ID%/cash-payment ^
  -H "Authorization: Bearer %DRIVER_TOKEN%" ^
  -H "Content-Type: application/json" ^
  -d "{\"amountReceived\":2500.00}"
echo.
echo.

echo 4. Testing Owner Views Pending Payments
echo ========================================
curl -X GET http://localhost:8080/api/owner/payments/pending ^
  -H "Authorization: Bearer %OWNER_TOKEN%"
echo.
echo.

echo 5. Testing Owner Verifies Payment
echo ========================================
curl -X POST http://localhost:8080/api/owner/payments/%PAYMENT_ID%/verify ^
  -H "Authorization: Bearer %OWNER_TOKEN%"
echo.
echo.

echo 6. Testing User Views Payment History
echo ========================================
curl -X GET http://localhost:8080/api/user/%USER_ID%/payments ^
  -H "Authorization: Bearer %USER_TOKEN%"
echo.
echo.

echo ========================================
echo Testing Complete!
echo ========================================
pause
