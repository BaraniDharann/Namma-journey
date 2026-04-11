# Updated API Endpoints - Quick Reference

## ✅ Changes Made

### UserController.java
- ✅ Added `userId` as path variable in all endpoints
- ✅ Removed `Authentication` parameter dependency
- ✅ Added `PUT` endpoint for updating bookings
- ✅ Added `DELETE` endpoint for deleting bookings

### UserService.java
- ✅ Added `updateBooking()` method
- ✅ Added `deleteBooking()` method
- ✅ Both methods validate user ownership before operations

---

## 🎯 New Endpoint Structure

### Before (Old)
```
POST   /api/user/bookings
GET    /api/user/bookings
GET    /api/user/bookings/{bookingId}
```

### After (New)
```
POST   /api/user/{userId}/bookings
GET    /api/user/{userId}/bookings
GET    /api/user/{userId}/bookings/{bookingId}
PUT    /api/user/{userId}/bookings/{bookingId}
DELETE /api/user/{userId}/bookings/{bookingId}
```

---

## 📋 Complete CRUD Operations

### 1. CREATE - Post New Booking
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "travelDays": 2
  }'
```

### 2. READ - Get All Bookings
```bash
curl -X GET http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 3. READ - Get Specific Booking
```bash
curl -X GET http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 4. UPDATE - Update Booking
```bash
curl -X PUT http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Varanasi",
    "travelDays": 3
  }'
```

### 5. DELETE - Delete Booking
```bash
curl -X DELETE http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/456e7890-e89b-12d3-a456-426614174111 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

## 🔑 How to Get userId

### From Signup Response
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User"}'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",  ← Use this
  "message": "Authentication successful"
}
```

### From Login Response
```bash
curl -X POST http://localhost:8080/api/auth/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "loginType": "EMAIL",
    "email": "test@example.com",
    "otp": "123456"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "role": "ROLE_USER",
  "userId": "123e4567-e89b-12d3-a456-426614174000",  ← Use this
  "message": "Authentication successful"
}
```

---

## 📊 Files Modified

1. **UserController.java**
   - Changed all endpoints to use `@PathVariable UUID userId`
   - Added PUT and DELETE endpoints
   - Removed Authentication parameter

2. **UserService.java**
   - Added `updateBooking(UUID userId, UUID bookingId, TravelBookingRequest request)`
   - Added `deleteBooking(UUID userId, UUID bookingId)`
   - Both methods validate user ownership

---

## 🎯 Benefits of Path Variable Approach

✅ **RESTful Design** - Follows REST conventions  
✅ **Clear Resource Hierarchy** - `/user/{userId}/bookings/{bookingId}`  
✅ **Explicit User Context** - userId visible in URL  
✅ **Better API Documentation** - Self-documenting endpoints  
✅ **Frontend Friendly** - Easy to construct URLs dynamically  

---

## 🔒 Security

- All endpoints still require JWT authentication
- UserService validates that userId matches booking owner
- Prevents unauthorized access to other users' bookings

---

## 📚 Documentation Files

1. **USER_BOOKING_CURL_EXAMPLES.md** - Complete cURL examples
2. **USER_BOOKING_WORKFLOW.md** - Full workflow documentation
3. **QUICK_REFERENCE.md** - This file

---

## ✨ Ready to Use!

Your API now supports full CRUD operations with userId in path variables!
