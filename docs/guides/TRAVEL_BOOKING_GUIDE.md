# Travel Booking System - User Service

## Overview
Complete travel booking system with OSM-based routing, distance calculation, and time estimation for multi-day journeys across India.

---

## Features

✅ Book travel from source to destination  
✅ Calculate distance using OSM data (india-latest.osm.pbf)  
✅ Calculate route and estimated time  
✅ Support for 1-30 day journeys  
✅ View all bookings  
✅ View individual booking details  

---

## Setup Instructions

### 1. Download India OSM Data

Download the latest India OSM file:
```bash
wget https://download.geofabrik.de/asia/india-latest.osm.pbf
```

Place `india-latest.osm.pbf` in your project root directory.

### 2. Database Migration

Run the migration script:
```bash
psql -U postgres -d travel_booking_db -f database-migration-travel-bookings.sql
```

### 3. Configuration

The system will automatically create a GraphHopper cache on first run (may take 5-10 minutes for India data).

---

## API Endpoints

### 1. CREATE TRAVEL BOOKING
**Endpoint:** `POST /api/user/bookings`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body:**
```json
{
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Delhi",
  "toPlace": "Jaipur",
  "travelDays": 2
}
```

**Response (201 Created):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Delhi",
  "toPlace": "Jaipur",
  "travelDays": 2,
  "distanceKm": 280.5,
  "estimatedTimeMinutes": 320,
  "estimatedTimeFormatted": "5 hours 20 minutes",
  "routeDetails": "Route: 280.50 km, 320 minutes\nWaypoints: 1250",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "PENDING"
}
```

---

### 2. GET ALL BOOKINGS
**Endpoint:** `GET /api/user/bookings`  
**Authorization:** Bearer token with `ROLE_USER`

**Response (200 OK):**
```json
[
  {
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Delhi",
    "toPlace": "Jaipur",
    "travelDays": 2,
    "distanceKm": 280.5,
    "estimatedTimeMinutes": 320,
    "estimatedTimeFormatted": "5 hours 20 minutes",
    "routeDetails": "Route: 280.50 km, 320 minutes\nWaypoints: 1250",
    "bookingDate": "2024-01-15T10:30:00",
    "status": "PENDING"
  }
]
```

---

### 3. GET BOOKING BY ID
**Endpoint:** `GET /api/user/bookings/{bookingId}`  
**Authorization:** Bearer token with `ROLE_USER`

**Response (200 OK):**
```json
{
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "userPhone": "9876543210",
  "fromPlace": "Delhi",
  "toPlace": "Jaipur",
  "travelDays": 2,
  "distanceKm": 280.5,
  "estimatedTimeMinutes": 320,
  "estimatedTimeFormatted": "5 hours 20 minutes",
  "routeDetails": "Route: 280.50 km, 320 minutes\nWaypoints: 1250",
  "bookingDate": "2024-01-15T10:30:00",
  "status": "PENDING"
}
```

---

## Supported Cities

The system currently supports major Indian cities and pilgrimage destinations:

- **Metro Cities:** Delhi, Mumbai, Bangalore, Chennai, Kolkata, Hyderabad, Pune, Ahmedabad
- **Tourist Cities:** Jaipur, Lucknow
- **Pilgrimage Sites:** Tirupati, Shirdi, Varanasi, Haridwar, Rishikesh

**Note:** To add more locations, update the `geocode()` method in `RoutingService.java` or integrate with Nominatim API for dynamic geocoding.

---

## Testing with cURL

### Create Booking
```bash
curl -X POST http://localhost:8080/api/user/bookings \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userName": "Rajesh Kumar",
    "userPhone": "9876543210",
    "fromPlace": "Delhi",
    "toPlace": "Jaipur",
    "travelDays": 2
  }'
```

### Get All Bookings
```bash
curl -X GET http://localhost:8080/api/user/bookings \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Get Booking by ID
```bash
curl -X GET http://localhost:8080/api/user/bookings/123e4567-e89b-12d3-a456-426614174000 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## How It Works

### 1. Route Calculation
- Uses **GraphHopper** library with OSM data
- Calculates optimal car route between locations
- Provides distance in kilometers and time in minutes

### 2. Multi-Day Journey Support
- Users can book 1-30 day journeys
- System calculates base travel time
- Suitable for temple visits, tours, and long-distance travel

### 3. Data Storage
- All bookings stored in PostgreSQL
- User can view booking history
- Booking status tracking (PENDING, CONFIRMED, COMPLETED, CANCELLED)

---

## Technical Details

### Dependencies
- **GraphHopper 8.0** - OSM routing engine
- **Spring Data JPA** - Database operations
- **PostgreSQL** - Data persistence
- **Lombok** - Boilerplate reduction

### File Structure
```
com.travelplatform
├── controller
│   └── UserController.java          # Travel booking endpoints
├── service
│   ├── UserService.java              # Booking business logic
│   └── RoutingService.java           # OSM routing with GraphHopper
├── repository
│   └── TravelBookingRepository.java  # Database access
├── entity
│   └── TravelBooking.java            # Booking entity
└── dto
    ├── TravelBookingRequest.java     # Booking request DTO
    ├── TravelBookingResponse.java    # Booking response DTO
    └── RouteInfo.java                # Route calculation DTO
```

---

## Performance Notes

### First Run
- GraphHopper will process `india-latest.osm.pbf` (takes 5-10 minutes)
- Creates cache in `graphhopper-cache` directory
- Subsequent runs are instant (uses cache)

### Cache Size
- India OSM data: ~1.2 GB
- GraphHopper cache: ~2-3 GB
- Ensure sufficient disk space

---

## Future Enhancements

- [ ] Dynamic geocoding with Nominatim API
- [ ] Multiple route options (fastest, shortest, scenic)
- [ ] Toll calculation
- [ ] Fuel cost estimation
- [ ] Driver assignment
- [ ] Real-time tracking
- [ ] Payment integration

---

## Error Handling

### Location Not Found (400)
```json
{
  "error": "Location not found: InvalidCity"
}
```

### Routing Error (500)
```json
{
  "error": "Failed to calculate route: No route found"
}
```

### Unauthorized Access (403)
```json
{
  "error": "Unauthorized access to booking"
}
```

---

## Support

For issues or questions, contact: support@travelplatform.com
