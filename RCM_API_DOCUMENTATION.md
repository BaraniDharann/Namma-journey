# Revenue Cycle Management (RCM) API Documentation

## Overview
Complete revenue management system with automatic pricing calculation based on distance.

---

## Database Migration

Run this SQL first:
```bash
psql -U postgres -d travel_booking_db -f database-migration-pricing.sql
```

---

## Owner APIs

### 1. Set Price Per Km
**Endpoint:** `POST /api/owner/pricing/set`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X POST "http://localhost:8080/api/owner/pricing/set?pricePerKm=15.5&ownerId=1" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "message": "Price per km updated successfully",
  "pricePerKm": 15.5,
  "updatedAt": "2024-01-15T10:30:00"
}
```

---

### 2. Get Current Price
**Endpoint:** `GET /api/owner/pricing/current`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X GET "http://localhost:8080/api/owner/pricing/current" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "pricePerKm": 15.5
}
```

---

### 3. Daily Revenue
**Endpoint:** `GET /api/owner/revenue/daily?date=YYYY-MM-DD`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X GET "http://localhost:8080/api/owner/revenue/daily?date=2024-01-15" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "period": "Daily",
  "periodValue": "2024-01-15",
  "totalTrips": 5,
  "totalDistance": 1250.5,
  "totalRevenue": 19382.75,
  "totalTravelDays": 12,
  "trips": [
    {
      "id": "uuid-here",
      "fromPlace": "Bangalore",
      "toPlace": "Tirupati",
      "distanceKm": 250.5,
      "travelDays": 2,
      "totalAmount": 3882.75,
      "bookingDate": "2024-01-15T10:30:00"
    }
  ]
}
```

---

### 4. Monthly Revenue
**Endpoint:** `GET /api/owner/revenue/monthly?year=2024&month=1`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X GET "http://localhost:8080/api/owner/revenue/monthly?year=2024&month=1" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "period": "Monthly",
  "periodValue": "2024-01",
  "totalTrips": 45,
  "totalDistance": 12500.5,
  "totalRevenue": 193757.75,
  "totalTravelDays": 120,
  "trips": [...]
}
```

---

### 5. Yearly Revenue
**Endpoint:** `GET /api/owner/revenue/yearly?year=2024`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X GET "http://localhost:8080/api/owner/revenue/yearly?year=2024" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "period": "Yearly",
  "periodValue": "2024",
  "totalTrips": 520,
  "totalDistance": 145000.5,
  "totalRevenue": 2247507.75,
  "totalTravelDays": 1450,
  "trips": [...]
}
```

---

### 6. Trip-wise Revenue
**Endpoint:** `GET /api/owner/revenue/trip/{tripId}`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Request:**
```bash
curl -X GET "http://localhost:8080/api/owner/revenue/trip/uuid-here" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN"
```

**Response:**
```json
{
  "tripId": "uuid-here",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "distanceKm": 250.5,
  "travelDays": 2,
  "totalAmount": 3882.75,
  "status": "COMPLETED",
  "bookingDate": "2024-01-15T10:30:00"
}
```

---

## User Booking with Auto-Calculated Amount

### Create Booking
**Endpoint:** `POST /api/user/bookings`  
**Authorization:** Bearer token with `ROLE_USER`

**Request:**
```bash
curl -X POST "http://localhost:8080/api/user/bookings" \
  -H "Authorization: Bearer YOUR_USER_TOKEN" \
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

**Response:**
```json
{
  "bookingId": "uuid-here",
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
  "totalAmount": 3882.75,
  "routeDetails": "Route via NH44...",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "PENDING",
  "driver": null
}
```

---

## How It Works

1. **Owner sets price per km** → Stored in `pricing_config` table
2. **User creates booking** → System calculates:
   - Distance using GraphHopper routing
   - `totalAmount = distanceKm × pricePerKm`
3. **Amount shown immediately** → User sees total cost at booking time
4. **RCM tracks revenue** → Owner can view:
   - Daily revenue
   - Monthly revenue
   - Yearly revenue
   - Individual trip revenue

---

## Revenue Calculation Logic

- Only **COMPLETED** trips are counted in revenue reports
- Formula: `totalAmount = distanceKm × pricePerKm`
- Price per km can be updated anytime by owner
- New bookings use the latest price per km
- Existing bookings retain their original amount

---

## Testing Workflow

### Step 1: Owner sets price
```bash
# Set price to 15 rupees per km
curl -X POST "http://localhost:8080/api/owner/pricing/set?pricePerKm=15&ownerId=1" \
  -H "Authorization: Bearer OWNER_TOKEN"
```

### Step 2: User creates booking
```bash
# Booking automatically calculates amount
curl -X POST "http://localhost:8080/api/user/bookings" \
  -H "Authorization: Bearer USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...booking details...}'
```

### Step 3: Owner views revenue
```bash
# Check monthly revenue
curl -X GET "http://localhost:8080/api/owner/revenue/monthly?year=2024&month=1" \
  -H "Authorization: Bearer OWNER_TOKEN"
```

---

## Database Schema

### pricing_config
```sql
id              BIGSERIAL PRIMARY KEY
price_per_km    DOUBLE PRECISION NOT NULL
updated_at      TIMESTAMP NOT NULL
updated_by      BIGINT NOT NULL
```

### travel_bookings (updated)
```sql
...existing columns...
total_amount    DOUBLE PRECISION NOT NULL
```

---

## Notes

✅ Automatic amount calculation  
✅ Real-time pricing updates  
✅ Complete revenue tracking  
✅ Day/Month/Year analytics  
✅ Trip-wise revenue details  
✅ Only completed trips in revenue  
✅ Default price: 10 rupees/km  

---
