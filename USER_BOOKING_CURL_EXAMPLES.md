# User Booking API - cURL Examples

## Overview
All booking endpoints now use `userId` as a path variable for better RESTful design.

**Base URL:** `http://localhost:8080`

---

## 🔐 Authentication Flow

### 1. User Signup
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rajesh.kumar@example.com",
    "name": "Rajesh Kumar"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

**Save the `userId` and `token` for subsequent requests**

---

### 2. User Login (Email OTP)

#### Step 1: Send OTP
```bash
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{
    "email": "rajesh.kumar@example.com"
  }'
```

#### Step 2: Login with OTP
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "EMAIL",
    "email": "rajesh.kumar@example.com",
    "otp": "123456"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "message": "Authentication successful"
}
```

---

### 3. User Login (Google OAuth)
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "GOOGLE",
    "token": "google_oauth_token_here"
  }'
```

---

## 📋 Booking Operations

### 1. CREATE BOOKING
**Endpoint:** `POST /api/user/{userId}/bookings`

```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "travelDays": 2
  }'
```

**Response (201 Created):**
```json
{
  "bookingId": "456e7890-e89b-12d3-a456-426614174111",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "travelDays": 2,
  "distanceKm": 245.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "Route: 245.50 km, 300 minutes\nWaypoints: 150",
  "bookingDate": "2024-02-10T10:30:00",
  "status": "PENDING"
}
```

---

### 2. GET ALL BOOKINGS
**Endpoint:** `GET /api/user/{userId}/bookings`

```bash
curl -X GET http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

**Response (200 OK):**
```json
[
  {
    "bookingId": "456e7890-e89b-12d3-a456-426614174111",
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "travelDays": 2,
    "distanceKm": 245.5,
    "estimatedTimeMinutes": 300,
    "estimatedTimeFormatted": "5 hours 0 minutes",
    "routeDetails": "Route: 245.50 km, 300 minutes",
    "bookingDate": "2024-02-10T10:30:00",
    "status": "PENDING"
  },
  {
    "bookingId": "789e1234-e89b-12d3-a456-426614174222",
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Chennai",
    "toPlace": "Shirdi",
    "travelDays": 3,
    "distanceKm": 890.2,
    "estimatedTimeMinutes": 720,
    "estimatedTimeFormatted": "12 hours 0 minutes",
    "routeDetails": "Route: 890.20 km, 720 minutes",
    "bookingDate": "2024-02-11T08:00:00",
    "status": "CONFIRMED"
  }
]
```

---

### 3. GET SPECIFIC BOOKING
**Endpoint:** `GET /api/user/{userId}/bookings/{bookingId}`

```bash
curl -X GET http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

**Response (200 OK):**
```json
{
  "bookingId": "456e7890-e89b-12d3-a456-426614174111",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "travelDays": 2,
  "distanceKm": 245.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "Route: 245.50 km, 300 minutes",
  "bookingDate": "2024-02-10T10:30:00",
  "status": "PENDING"
}
```

---

### 4. UPDATE BOOKING
**Endpoint:** `PUT /api/user/{userId}/bookings/{bookingId}`

```bash
curl -X PUT http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Varanasi",
    "travelDays": 3
  }'
```

**Response (200 OK):**
```json
{
  "bookingId": "456e7890-e89b-12d3-a456-426614174111",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Varanasi",
  "travelDays": 3,
  "distanceKm": 1850.3,
  "estimatedTimeMinutes": 1200,
  "estimatedTimeFormatted": "20 hours 0 minutes",
  "routeDetails": "Route: 1850.30 km, 1200 minutes",
  "bookingDate": "2024-02-10T10:30:00",
  "status": "PENDING"
}
```

---

### 5. DELETE BOOKING
**Endpoint:** `DELETE /api/user/{userId}/bookings/{bookingId}`

```bash
curl -X DELETE http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

**Response (204 No Content):**
```
(Empty response body)
```

---

## 🎯 Complete Workflow Example

### Step 1: Signup
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "Test User"
  }'
```
**Output:** Save `userId` and `token`

### Step 2: Create First Booking
```bash
curl -X POST http://localhost:8080/api/user/YOUR_USER_ID/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "userName": "Test User",
    "userPhone": "9876543210",
    "fromPlace": "Delhi",
    "toPlace": "Haridwar",
    "travelDays": 1
  }'
```
**Output:** Save `bookingId`

### Step 3: Get All Bookings
```bash
curl -X GET http://localhost:8080/api/user/YOUR_USER_ID/bookings \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Step 4: Update Booking
```bash
curl -X PUT http://localhost:8080/api/user/YOUR_USER_ID/bookings/YOUR_BOOKING_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "userName": "Test User",
    "userPhone": "9876543210",
    "fromPlace": "Delhi",
    "toPlace": "Rishikesh",
    "travelDays": 2
  }'
```

### Step 5: Delete Booking
```bash
curl -X DELETE http://localhost:8080/api/user/YOUR_USER_ID/bookings/YOUR_BOOKING_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 📊 API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/user/{userId}/bookings` | Create new booking |
| GET | `/api/user/{userId}/bookings` | Get all user bookings |
| GET | `/api/user/{userId}/bookings/{bookingId}` | Get specific booking |
| PUT | `/api/user/{userId}/bookings/{bookingId}` | Update booking |
| DELETE | `/api/user/{userId}/bookings/{bookingId}` | Delete booking |

---

## 🔒 Security Notes

1. **All endpoints require JWT authentication** via `Authorization: Bearer {token}` header
2. **User can only access their own bookings** - userId in path must match authenticated user
3. **Token expires in 24 hours** - Re-login required after expiration
4. **Role required:** `ROLE_USER`

---

## ❌ Error Responses

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token"
}
```

### 403 Forbidden
```json
{
  "error": "Forbidden",
  "message": "Unauthorized access to booking"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Booking not found"
}
```

### 400 Bad Request
```json
{
  "userName": "User name is required",
  "userPhone": "Invalid Indian phone number",
  "fromPlace": "From place is required",
  "toPlace": "To place is required",
  "travelDays": "Travel days must be at least 1"
}
```

---

## 🧪 Testing with Postman

### Environment Variables
```
BASE_URL = http://localhost:8080
USER_ID = 123e4567-e89b-12d3-a456-426614174000
TOKEN = eyJhbGciOiJIUzI1NiJ9...
BOOKING_ID = 456e7890-e89b-12d3-a456-426614174111
```

### Headers (Global)
```
Content-Type: application/json
Authorization: Bearer {{TOKEN}}
```

---

## 📝 Request Body Validation

### TravelBookingRequest
```json
{
  "userName": "string (required, not blank)",
  "userPhone": "string (required, 10 digits, starts with 6-9)",
  "fromPlace": "string (required, not blank)",
  "toPlace": "string (required, not blank)",
  "travelDays": "integer (required, min: 1, max: 30)"
}
```

---

## 🚀 Quick Reference

### Create Booking
```bash
POST /api/user/{userId}/bookings
```

### Get All Bookings
```bash
GET /api/user/{userId}/bookings
```

### Get Specific Booking
```bash
GET /api/user/{userId}/bookings/{bookingId}
```

### Update Booking
```bash
PUT /api/user/{userId}/bookings/{bookingId}
```

### Delete Booking
```bash
DELETE /api/user/{userId}/bookings/{bookingId}
```

---

## 💡 Tips

1. **Save userId after signup/login** - You'll need it for all booking operations
2. **Use environment variables** - Store userId and token for easy testing
3. **Check token expiration** - Tokens expire after 24 hours
4. **Validate phone numbers** - Must be 10-digit Indian numbers starting with 6-9
5. **Travel days range** - Between 1 and 30 days

---

## 🎉 Success!

Your Travel Booking Platform API is now ready with full CRUD operations using userId in path variables!
