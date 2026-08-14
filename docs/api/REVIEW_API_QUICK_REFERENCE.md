# Review & Rating API - Quick Reference

## Base URL
```
http://localhost:8080
```

---

## 1. Submit Review (User)

### Endpoint
```
POST /api/user/{userId}/bookings/{bookingId}/reviews
```

### Headers
```
Authorization: Bearer YOUR_USER_JWT_TOKEN
Content-Type: application/json
```

### Request Body
```json
{
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous."
}
```

### Success Response (201 Created)
```json
{
  "id": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "driverName": "Suresh Sharma",
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous.",
  "createdAt": "2024-01-15T10:30:00"
}
```

### cURL Example
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-f12b-34c5-d678-901234567890/reviews \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 5,
    "feedback": "Excellent service! Driver was very professional."
  }'
```

### Postman Setup
1. **Method:** POST
2. **URL:** `http://localhost:8080/api/user/{{userId}}/bookings/{{bookingId}}/reviews`
3. **Headers:**
   - Key: `Authorization` | Value: `Bearer {{userToken}}`
   - Key: `Content-Type` | Value: `application/json`
4. **Body (raw JSON):**
```json
{
  "rating": 5,
  "feedback": "Excellent service!"
}
```

---

## 2. View All Reviews (Owner)

### Endpoint
```
GET /api/owner/reviews
```

### Headers
```
Authorization: Bearer YOUR_OWNER_JWT_TOKEN
```

### Success Response (200 OK)
```json
[
  {
    "id": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "userName": "Rajesh Kumar",
    "driverName": "Suresh Sharma",
    "rating": 5,
    "feedback": "Excellent service! Driver was very professional.",
    "createdAt": "2024-01-15T10:30:00"
  },
  {
    "id": "876e5432-d10a-11d2-9345-325513063888",
    "bookingId": "234f5678-f90c-23e4-b567-537725285111",
    "userName": "Priya Sharma",
    "driverName": "Ramesh Kumar",
    "rating": 4,
    "feedback": "Good service, but driver was slightly late.",
    "createdAt": "2024-01-14T15:45:00"
  }
]
```

### cURL Example
```bash
curl -X GET http://localhost:8080/api/owner/reviews \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### Postman Setup
1. **Method:** GET
2. **URL:** `http://localhost:8080/api/owner/reviews`
3. **Headers:**
   - Key: `Authorization` | Value: `Bearer {{ownerToken}}`

---

## Error Responses

### 400 Bad Request - Invalid Rating
```json
{
  "rating": "Rating must be between 1 and 5"
}
```

### 400 Bad Request - Feedback Too Long
```json
{
  "feedback": "Feedback cannot exceed 1000 characters"
}
```

### 400 Bad Request - Not Completed
```json
{
  "error": "Can only review completed bookings"
}
```

### 400 Bad Request - Already Reviewed
```json
{
  "error": "Review already submitted for this booking"
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized to review this booking"
}
```

### 404 Not Found
```json
{
  "error": "Booking not found"
}
```

---

## Complete Testing Flow

### Step 1: User Login
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "EMAIL",
    "email": "user@example.com",
    "password": "MyPassword@123"
  }'
```

**Save the token and userId from response**

### Step 2: Create Booking
```bash
curl -X POST http://localhost:8080/api/user/{userId}/bookings \
  -H "Authorization: Bearer {userToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "fromDate": "2024-02-01",
    "toDate": "2024-02-03",
    "travelMembers": 4,
    "acType": "AC"
  }'
```

**Save the bookingId from response**

### Step 3: Owner Completes Booking
(Owner marks booking as COMPLETED)

### Step 4: Submit Review
```bash
curl -X POST http://localhost:8080/api/user/{userId}/bookings/{bookingId}/reviews \
  -H "Authorization: Bearer {userToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 5,
    "feedback": "Excellent service!"
  }'
```

### Step 5: Owner Views Reviews
```bash
curl -X GET http://localhost:8080/api/owner/reviews \
  -H "Authorization: Bearer {ownerToken}"
```

---

## Validation Rules

| Field | Required | Type | Constraints |
|-------|----------|------|-------------|
| rating | Yes | Integer | 1-5 |
| feedback | No | String | Max 1000 chars |

---

## Business Rules

✅ Only COMPLETED bookings can be reviewed  
✅ One review per booking  
✅ Only booking owner can submit review  
✅ Reviews sorted by date (newest first)  
✅ Owner can view all reviews  

---

## Quick Test (Windows)

Save as `test-review.bat`:
```batch
@echo off
set USER_TOKEN=YOUR_USER_TOKEN
set OWNER_TOKEN=YOUR_OWNER_TOKEN
set USER_ID=YOUR_USER_ID
set BOOKING_ID=YOUR_BOOKING_ID

curl -X POST http://localhost:8080/api/user/%USER_ID%/bookings/%BOOKING_ID%/reviews ^
  -H "Authorization: Bearer %USER_TOKEN%" ^
  -H "Content-Type: application/json" ^
  -d "{\"rating\":5,\"feedback\":\"Great service!\"}"

curl -X GET http://localhost:8080/api/owner/reviews ^
  -H "Authorization: Bearer %OWNER_TOKEN%"
```

---

## Postman Collection Variables

```json
{
  "userToken": "YOUR_USER_JWT_TOKEN",
  "ownerToken": "YOUR_OWNER_JWT_TOKEN",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "bookingId": "456e7890-f12b-34c5-d678-901234567890"
}
```
