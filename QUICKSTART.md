# Quick Start Guide - Travel Booking Platform

## 🚀 Getting Started in 5 Minutes

### Step 1: Prerequisites Check
```bash
java -version    # Should be 17+
mvn -version     # Should be 3.6+
psql --version   # PostgreSQL 14+
```

### Step 2: Database Setup
```bash
# Login to PostgreSQL
psql -U postgres

# Run initialization script
\i database-init.sql

# Or manually:
CREATE DATABASE travel_booking_db;
```

### Step 3: Configure Application
Edit `src/main/resources/application.yml`:
- Update PostgreSQL username/password
- (Optional) Update JWT secret

### Step 4: Build & Run
```bash
# Build project
mvn clean install

# Run application
mvn spring-boot:run

# Or run JAR directly
java -jar target/travel-booking-platform-1.0.0.jar
```

### Step 5: Test APIs

#### Test User Signup
```bash
curl -X POST http://localhost:8080/api/auth/user/signup \
  -H "Content-Type: application/json" \
  -d '{"phone":"9876543210","name":"Test User"}'
```

#### Test Driver Login
```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9999999999","password":"driver@123"}'
```

#### Test Owner Login
```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@travelplatform.com","password":"owner@123"}'
```

---

## 📋 Default Test Accounts

### Owner Account
- **Email:** admin@travelplatform.com
- **Password:** owner@123

### Driver Account (Sample)
- **Mobile:** 9999999999
- **Password:** driver@123

### User Account
- Create via signup endpoint
- **Mock OTP:** 123456

---

## 🔧 Common Issues & Solutions

### Issue: Port 8080 already in use
**Solution:** Change port in application.yml
```yaml
server:
  port: 8081
```

### Issue: Database connection failed
**Solution:** Check PostgreSQL is running
```bash
# Windows
net start postgresql-x64-14

# Linux/Mac
sudo service postgresql start
```

### Issue: JWT token expired
**Solution:** Token expires in 24 hours. Login again to get new token.

---

## 📦 Project Structure Overview

```
Travel Booking Platform/
├── src/main/java/com/travelplatform/
│   ├── config/              # Security & JWT configuration
│   ├── controller/          # REST API endpoints
│   ├── service/             # Business logic
│   ├── repository/          # Database access
│   ├── entity/              # JPA entities
│   ├── dto/                 # Request/Response objects
│   └── exception/           # Error handling
├── src/main/resources/
│   └── application.yml      # Configuration
├── pom.xml                  # Maven dependencies
├── README.md                # Full documentation
└── database-init.sql        # Database setup script
```

---

## 🧪 Testing Workflow

1. **Start Application**
   ```bash
   mvn spring-boot:run
   ```

2. **Import Postman Collection**
   - Import `Travel-Booking-API.postman_collection.json`
   - Test all endpoints

3. **Verify Database**
   ```sql
   SELECT * FROM users;
   SELECT * FROM drivers;
   SELECT * FROM owners;
   ```

---

## 🔐 Authentication Flow

1. **Signup/Login** → Get JWT token
2. **Copy token** from response
3. **Add to headers** for protected endpoints:
   ```
   Authorization: Bearer <your_token_here>
   ```

---

## 📝 API Endpoints Summary

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/auth/user/signup` | POST | User registration | No |
| `/api/auth/user/login` | POST | User login | No |
| `/api/auth/driver/signup` | POST | Driver registration | No |
| `/api/auth/driver/login` | POST | Driver login | No |
| `/api/auth/owner/login` | POST | Owner login | No |

---

## 🎯 Next Steps (Phase 2)

- [ ] Vehicle management APIs
- [ ] Booking system
- [ ] Payment integration
- [ ] Trip tracking
- [ ] Rating & reviews
- [ ] Notifications

---

## 📞 Support

For issues or questions:
- Check README.md for detailed documentation
- Review application logs in console
- Verify database connections

---

## ✅ Checklist Before Deployment

- [ ] Update JWT secret in production
- [ ] Configure production database
- [ ] Enable HTTPS
- [ ] Set up proper logging
- [ ] Configure CORS if needed
- [ ] Add rate limiting
- [ ] Set up monitoring

---

**Happy Coding! 🚗💨**
