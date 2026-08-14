# Review & Rating System API Documentation

## Overview
Users can rate their completed journeys and provide feedback about driver attitude. Owners can view all customer reviews to improve business operations.

---

## API Endpoints

### 1. SUBMIT REVIEW (User Only)
**Endpoint:** `POST /api/user/{userId}/bookings/{bookingId}/reviews`  
**Authorization:** Bearer token with `ROLE_USER`

**Request Body:**
```json
{
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous. The journey was smooth and comfortable."
}
```

**Validations:**
- `rating`: Required, must be between 1 and 5
- `feedback`: Optional, maximum 1000 characters

**Response (201 Created):**
```json
{
  "id": "987e6543-e21b-12d3-a456-426614174999",
  "bookingId": "123e4567-e89b-12d3-a456-426614174000",
  "userName": "Rajesh Kumar",
  "driverName": "Suresh Sharma",
  "rating": 5,
  "feedback": "Excellent service! Driver was very professional and courteous. The journey was smooth and comfortable.",
  "createdAt": "2024-01-15T10:30:00"
}
```

**Error Responses:**

**400 Bad Request - Invalid Rating:**
```json
{
  "rating": "Rating must be between 1 and 5"
}
```

**400 Bad Request - Booking Not Completed:**
```json
{
  "error": "Can only review completed bookings"
}
```

**400 Bad Request - Already Reviewed:**
```json
{
  "error": "Review already submitted for this booking"
}
```

**401 Unauthorized:**
```json
{
  "error": "Unauthorized to review this booking"
}
```

**404 Not Found:**
```json
{
  "error": "Booking not found"
}
```

---

### 2. VIEW ALL REVIEWS (Owner Only)
**Endpoint:** `GET /api/owner/reviews`  
**Authorization:** Bearer token with `ROLE_OWNER`

**Response (200 OK):**
```json
[
  {
    "id": "987e6543-e21b-12d3-a456-426614174999",
    "bookingId": "123e4567-e89b-12d3-a456-426614174000",
    "userName": "Rajesh Kumar",
    "driverName": "Suresh Sharma",
    "rating": 5,
    "feedback": "Excellent service! Driver was very professional and courteous.",
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

**Note:** Reviews are sorted by creation date (newest first)

---

## Business Rules

### Review Submission Rules:
1. ✅ Only users who booked the journey can submit reviews
2. ✅ Reviews can only be submitted for COMPLETED bookings
3. ✅ One review per booking (no duplicate reviews)
4. ✅ Rating must be between 1-5 stars
5. ✅ Feedback is optional but limited to 1000 characters

### Owner Access:
1. ✅ Owners can view all reviews from all customers
2. ✅ Reviews are sorted by date (newest first)
3. ✅ Includes customer name, driver name, rating, and feedback

---

## Testing with cURL

### Submit Review (User)
```bash
curl -X POST http://localhost:8080/api/user/123e4567-e89b-12d3-a456-426614174000/bookings/123e4567-e89b-12d3-a456-426614174000/reviews \
  -H "Authorization: Bearer YOUR_USER_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "rating": 5,
    "feedback": "Excellent service! Driver was very professional."
  }'
```

### View All Reviews (Owner)
```bash
curl -X GET http://localhost:8080/api/owner/reviews \
  -H "Authorization: Bearer YOUR_OWNER_JWT_TOKEN"
```

---

## Database Schema

### Reviews Table
```sql
CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    driver_id BIGINT NOT NULL,
    driver_name VARCHAR(255) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    feedback VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (booking_id) REFERENCES travel_bookings(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
);
```

---

## Complete Workflow

### User Journey:
1. User books a travel journey
2. Driver completes the journey
3. Booking status changes to COMPLETED
4. User submits rating (1-5 stars) and feedback
5. Review is stored in the system

### Owner Journey:
1. Owner logs into the system
2. Owner views all customer reviews
3. Owner analyzes ratings and feedback
4. Owner identifies areas for improvement
5. Owner takes action to improve service quality

---

## Rating Guidelines

**5 Stars ⭐⭐⭐⭐⭐** - Excellent service, professional driver, smooth journey  
**4 Stars ⭐⭐⭐⭐** - Good service with minor issues  
**3 Stars ⭐⭐⭐** - Average service, met basic expectations  
**2 Stars ⭐⭐** - Below average, multiple issues  
**1 Star ⭐** - Poor service, major problems  

---

## Benefits for Business

### For Owners:
- 📊 Track customer satisfaction
- 🎯 Identify top-performing drivers
- ⚠️ Detect service quality issues
- 📈 Make data-driven improvements
- 💡 Understand customer expectations

### For Users:
- 🗣️ Share their experience
- 👍 Appreciate good service
- 📝 Provide constructive feedback
- 🤝 Help improve service quality

---

## Setup Instructions

### 1. Run Database Migration
```bash
psql -U postgres -d travel_booking_db -f database-migration-reviews.sql
```

### 2. Restart Application
```bash
mvn spring-boot:run
```

### 3. Test the API
Use the provided cURL commands or Postman collection

---

## Security Features

✅ JWT-based authentication  
✅ Role-based access control  
✅ User authorization validation  
✅ Booking ownership verification  
✅ Input validation  
✅ SQL injection prevention  

---

## Future Enhancements (Not Implemented)

- Driver response to reviews
- Review moderation system
- Average rating calculation per driver
- Review analytics dashboard
- Email notifications for low ratings
- Review filtering and search
- Export reviews to CSV/PDF

---

## Support

For issues or questions, contact: support@travelplatform.com
