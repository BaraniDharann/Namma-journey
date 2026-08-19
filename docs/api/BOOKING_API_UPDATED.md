# Updated Travel Booking API

## Changes Made

### New Fields Added:
1. **fromDate** (LocalDate) - Travel start date
2. **toDate** (LocalDate) - Travel end date  
3. **travelMembers** (Integer) - Number of travelers (1-20)
4. **acType** (String) - Vehicle AC preference ("AC" or "NON_AC")

### Removed Fields:
- **travelDays** - Now auto-calculated from fromDate and toDate

### Auto-Calculation:
- **travelDays** = (toDate - fromDate) + 1 day

---

## Updated API Request

### Create Booking
**Endpoint:** `POST /api/user/{userId}/bookings`

**New Request Body:**
```json
{
  "userName": "Rajesh Kumar",
  "userPhone": "9000000000",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-03-15",
  "toDate": "2024-03-17",
  "travelMembers": 4,
  "acType": "AC"
}
```

**Response:**
```json
{
  "bookingId": "0ff04487-be30-4383-912c-36a498f76619",
  "userName": "Rajesh Kumar",
  "userPhone": "9000000000",
  "fromPlace": "Bangalore",
  "toPlace": "Tirupati",
  "fromDate": "2024-03-15",
  "toDate": "2024-03-17",
  "travelDays": 3,
  "travelMembers": 4,
  "acType": "AC",
  "distanceKm": 250.5,
  "estimatedTimeMinutes": 270,
  "estimatedTimeFormatted": "4 hours 30 minutes",
  "routeDetails": "Road route: 250.50 km, 270 minutes (via OSRM)",
  "bookingDate": "2024-02-12T21:40:39.875230",
  "status": "PENDING"
}
```

---

## cURL Example

```bash
curl --request POST \
  --url http://localhost:8080/api/user/dcfed925-b68c-4358-bbaa-af380e06acc8/bookings \
  --header 'authorization: Bearer YOUR_JWT_TOKEN' \
  --header 'content-type: application/json' \
  --data '{
    "userName": "Rajesh Kumar",
    "userPhone": "9000000000",
    "fromPlace": "Bangalore",
    "toPlace": "Tirupati",
    "fromDate": "2024-03-15",
    "toDate": "2024-03-17",
    "travelMembers": 4,
    "acType": "AC"
}'
```

---

## Field Validations

| Field | Type | Validation | Example |
|-------|------|------------|---------|
| userName | String | Required, Not blank | "Rajesh Kumar" |
| userPhone | String | Required, 10-digit Indian mobile (6-9 start) | "9000000000" |
| fromPlace | String | Required, Not blank | "Bangalore" |
| toPlace | String | Required, Not blank | "Tirupati" |
| fromDate | LocalDate | Required, ISO format (YYYY-MM-DD) | "2024-03-15" |
| toDate | LocalDate | Required, Must be >= fromDate | "2024-03-17" |
| travelMembers | Integer | Required, Min: 1, Max: 20 | 4 |
| acType | String | Required, Must be "AC" or "NON_AC" | "AC" |

---

## Error Responses

### Invalid Date Range
```json
{
  "error": "To date cannot be before from date"
}
```

### Invalid Travel Members
```json
{
  "travelMembers": "At least 1 travel member required"
}
```

### Invalid AC Type
```json
{
  "acType": "AC type must be either AC or NON_AC"
}
```

---

## Database Migration

The schema is owned by Flyway. These columns arrived in the versioned baseline under
`src/main/resources/db/migration`; Hibernate runs with `ddl-auto: validate` and never alters
tables itself.

New columns added to `travel_bookings` table:
- `from_date` (DATE, NOT NULL)
- `to_date` (DATE, NOT NULL)
- `travel_members` (INTEGER, NOT NULL)
- `ac_type` (VARCHAR, NOT NULL)

---

## Benefits

✅ **Auto-calculated travel days** - No manual calculation needed  
✅ **Date validation** - Ensures toDate >= fromDate  
✅ **Travel members tracking** - Know how many people are traveling  
✅ **AC preference** - Vehicle type selection for pricing  
✅ **Road distance calculation** - Uses OSRM API for accurate routing  

---

## Next Steps

1. Restart your Spring Boot application
2. Test with the new request format
3. Verify travelDays is auto-calculated correctly
4. Check that road distance is now accurate (not air distance)
