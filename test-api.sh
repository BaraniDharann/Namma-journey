#!/bin/bash
# Quick API Testing Script for Travel Booking Platform

BASE_URL="http://localhost:8080/api/auth"

echo "=================================="
echo "Travel Booking Platform - API Tests"
echo "=================================="

# Test 1: Send OTP
echo -e "\n1️⃣  Sending OTP..."
curl -X POST $BASE_URL/otp/send \
  -H "Content-Type: application/json" \
  -d '{"phone":"9876543210"}' \
  -w "\n"

echo -e "\n⏳ Check console for OTP in MOCK mode"
read -p "Enter OTP from console: " OTP

# Test 2: User Login with OTP
echo -e "\n2️⃣  User Login with OTP..."
curl -X POST $BASE_URL/user/login \
  -H "Content-Type: application/json" \
  -d "{\"loginType\":\"PHONE\",\"phone\":\"9876543210\",\"otp\":\"$OTP\"}" \
  -w "\n"

# Test 3: Driver Signup
echo -e "\n3️⃣  Driver Signup..."
curl -X POST $BASE_URL/driver/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Suresh Sharma","mobile":"9123456789","password":"driver@123","licenseNumber":"DL1420110012345","aadhaarNumber":"123456789012"}' \
  -w "\n"

# Test 4: Driver Login
echo -e "\n4️⃣  Driver Login..."
curl -X POST $BASE_URL/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9123456789","password":"driver@123"}' \
  -w "\n"

echo -e "\n✅ All tests completed!"
