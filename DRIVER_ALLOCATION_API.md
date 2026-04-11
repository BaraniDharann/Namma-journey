# Automatic Driver Allocation - API Documentation

## Overview
When a user creates a booking, the system automatically assigns an available driver. The driver can then accept or reject the booking.

## Workflow

1. **User Creates Booking** → System finds available driver → Driver automatically assigned
2. **Driver Logs In** → Views assigned bookings
3. **Driver Takes Action** → Accepts or Rejects booking
4. **User Views Booking** → Sees driver details (name, photo, mobile, license)

---

## Database Changes

Run the migration:
```bash
psql -U postgres -d travel_booking_db -f database-migration-driver-allocation.sql
```

Or manually:
```sql
ALTER TABLE travel_bookings ADD COLUMN driver_id BIGINT;
ALTER TABLE travel_bookings ADD CONSTRAINT fk_driver FOREIGN KEY (driver_id) REFERENCES drivers(id);
CREATE INDEX idx_driver_bookings ON travel_bookings(driver_id);
CREATE INDEX idx_booking_dates ON travel_bookings(from_date, to_date);
```

---

## API Endpoints

### 1. USER: Create Booking (Auto-assigns Driver)
**Endpoint:** `POST /api/user/{userId}/bookings`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body:**
```json
{
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-02-01",
  "toDate": "2024-02-03",
  "travelMembers": 4,
  "acType": "AC"
}
```

**Response (201 Created):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-02-01",
  "toDate": "2024-02-03",
  "travelDays": 3,
  "travelMembers": 4,
  "acType": "AC",
  "distanceKm": 250.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "...",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "PENDING",
  "driver": {
    "driverId": 1,
    "name": "Suresh Sharma",
    "mobile": "9123456789",
    "photo": "https://example.com/photos/driver.jpg",
    "licenseNumber": "DL1420110012345"
  }
}
```

**Note:** Driver is automatically assigned if available for the date range.

---

### 2. USER: Get Booking Details (with Driver Info)
**Endpoint:** `GET /api/user/{userId}/bookings/{bookingId}`  
**Authorization:** Bearer token with `ROLE_USER`

**Response (200 OK):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-02-01",
  "toDate": "2024-02-03",
  "travelDays": 3,
  "travelMembers": 4,
  "acType": "AC",
  "distanceKm": 250.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "...",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "CONFIRMED",
  "driver": {
    "driverId": 1,
    "name": "Suresh Sharma",
    "mobile": "9123456789",
    "photo": "https://example.com/photos/driver.jpg",
    "licenseNumber": "DL1420110012345"
  }
}
```

---

### 3. DRIVER: Get Assigned Bookings
**Endpoint:** `GET /api/driver/{driverId}/bookings`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Response (200 OK):**
```json
[
  {
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "fromDate": "2024-02-01",
    "toDate": "2024-02-03",
    "travelDays": 3,
    "travelMembers": 4,
    "acType": "AC",
    "distanceKm": 250.5,
    "estimatedTimeMinutes": 300,
    "estimatedTimeFormatted": "5 hours 0 minutes",
    "routeDetails": "...",
    "bookingDate": "2024-01-15T10:30:00",
    "status": "PENDING",
    "driver": {
      "driverId": 1,
      "name": "Suresh Sharma",
      "mobile": "9123456789",
      "photo": "https://example.com/photos/driver.jpg",
      "licenseNumber": "DL1420110012345"
    }
  }
]
```

---

### 4. DRIVER: Accept Booking
**Endpoint:** `POST /api/driver/{driverId}/bookings/{bookingId}/action`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "action": "ACCEPT"
}
```

**Response (200 OK):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-02-01",
  "toDate": "2024-02-03",
  "travelDays": 3,
  "travelMembers": 4,
  "acType": "AC",
  "distanceKm": 250.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "...",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "CONFIRMED",
  "driver": {
    "driverId": 1,
    "name": "Suresh Sharma",
    "mobile": "9123456789",
    "photo": "https://example.com/photos/driver.jpg",
    "licenseNumber": "DL1420110012345"
  }
}
```

---

### 5. DRIVER: Reject Booking
**Endpoint:** `POST /api/driver/{driverId}/bookings/{bookingId}/action`  
**Authorization:** Bearer token with `ROLE_DRIVER`

**Request Body:**
```json
{
  "action": "REJECT"
}
```

**Response (200 OK):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-02-01",
  "toDate": "2024-02-03",
  "travelDays": 3,
  "travelMembers": 4,
  "acType": "AC",
  "distanceKm": 250.5,
  "estimatedTimeMinutes": 300,
  "estimatedTimeFormatted": "5 hours 0 minutes",
  "routeDetails": "...",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "PENDING",
  "driver": null
}
```

**Note:** When rejected, driver is unassigned and booking returns to PENDING status.

---

## Driver Availability Logic

The system finds available drivers using this logic:
- Driver status must be `ACTIVE`
- Driver must NOT have any bookings with overlapping dates
- Only considers bookings with status `PENDING` or `CONFIRMED`
- First available driver is automatically assigned

**SQL Query:**
```sql
SELECT d FROM Driver d 
WHERE d.status = 'ACTIVE' 
AND d.id NOT IN (
  SELECT tb.driverId FROM TravelBooking tb 
  WHERE tb.driverId IS NOT NULL 
  AND ((tb.fromDate <= :toDate AND tb.toDate >= :fromDate) 
  AND tb.status IN ('PENDING', 'CONFIRMED'))
)
```

---

## Booking Status Flow

```
PENDING → Driver assigned automatically
   ↓
PENDING → Driver accepts → CONFIRMED
   ↓
PENDING → Driver rejects → PENDING (driver unassigned)
   ↓
CONFIRMED → Trip completed → COMPLETED
   ↓
CANCELLED → User/Owner cancels
```

---

## Testing with cURL

### User Creates Booking
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings \
  -H "Authorization: Bearer USER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "fromDate": "2024-02-01",
    "toDate": "2024-02-03",
    "travelMembers": 4,
    "acType": "AC"
  }'
```

### Driver Views Assigned Bookings
```bash
curl -X GET http://localhost:8080/api/driver/1/bookings \
  -H "Authorization: Bearer DRIVER_JWT_TOKEN"
```

### Driver Accepts Booking
```bash
curl -X POST http://localhost:8080/api/driver/1/bookings/123e4567-e89b-12d3-a456-426614174000/action \
  -H "Authorization: Bearer DRIVER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action": "ACCEPT"}'
```

### Driver Rejects Booking
```bash
curl -X POST http://localhost:8080/api/driver/1/bookings/123e4567-e89b-12d3-a456-426614174000/action \
  -H "Authorization: Bearer DRIVER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action": "REJECT"}'
```

### User Views Booking with Driver Details
```bash
curl -X GET http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/123e4567-e89b-12d3-a456-426614174000 \
  -H "Authorization: Bearer USER_JWT_TOKEN"
```

---

## Key Features

✅ Automatic driver allocation on booking creation  
✅ Driver availability check based on date ranges  
✅ Driver can accept/reject bookings  
✅ User sees complete driver details (name, photo, mobile, license)  
✅ Rejected bookings return to pool for reassignment  
✅ Prevents double-booking of drivers  
✅ Real-time status updates  

---

## Error Scenarios

### No Available Driver
If no driver is available for the requested dates, booking is created with `driver: null` and status `PENDING`. Owner can manually assign later.

### Driver Rejects Booking
Booking returns to `PENDING` status with `driver: null`. System can reassign to another available driver.

### Invalid Action
```json
{
  "error": "Invalid action. Use ACCEPT or REJECT"
}
```

### Unauthorized Access
```json
{
  "error": "This booking is not assigned to you"
}
```

---

## Next Steps

1. Run database migration
2. Test driver allocation flow
3. Implement manual driver assignment by Owner
4. Add notifications (email/SMS) when driver is assigned
5. Add driver rating system after trip completion
