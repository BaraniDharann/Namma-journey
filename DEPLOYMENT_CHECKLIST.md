# Deployment Checklist - Driver Management Update

## Pre-Deployment

### Code Review
- [ ] All new files created successfully
- [ ] All modified files updated correctly
- [ ] No compilation errors
- [ ] All imports resolved

### Database
- [ ] Backup existing database
- [ ] Test database migration on staging
- [ ] New Flyway migration added under `src/main/resources/db/migration` for any schema change
- [ ] `JPA_DDL_AUTO` is `validate` — Hibernate must never alter tables itself

### Environment Variables
- [ ] `MAIL_USERNAME` configured (Gmail SMTP account)
- [ ] `MAIL_PASSWORD` configured (Gmail app password, not the account password)
- [ ] `MAIL_FROM_NAME` configured
- [ ] `DB_URL` configured
- [ ] `DB_USERNAME` configured
- [ ] `DB_PASSWORD` configured
- [ ] `JWT_SECRET` configured
- [ ] `JWT_EXPIRATION` configured
- [ ] `OWNER_UPI_ID` configured — the app refuses to start without it
- [ ] `CORS_ALLOWED_ORIGINS` lists the real frontend origin
- [ ] `OTP_TEST_MODE` is `false`
- [ ] `OWNER_BOOTSTRAP_SECRET` cleared again after the first owner was created

### Dependencies
- [ ] Spring Security dependency in pom.xml
- [ ] JWT dependencies in pom.xml
- [ ] Run `mvn clean install` successfully

---

## Deployment Steps

### 1. Build Application
```bash
cd "c:\react project\Travel Booking Platform"
mvn clean package -DskipTests
```

### 2. Stop Current Application
```bash
# If running as service
sudo systemctl stop travel-platform

# If running manually
# Press Ctrl+C or kill process
```

### 3. Backup Current JAR
```bash
cp target/travel-platform.jar target/travel-platform.jar.backup
```

### 4. Deploy New JAR
```bash
# Copy new JAR to deployment location
cp target/travel-platform-0.0.1-SNAPSHOT.jar /opt/travel-platform/travel-platform.jar
```

### 5. Start Application
```bash
# If running as service
sudo systemctl start travel-platform

# If running manually
java -jar target/travel-platform-0.0.1-SNAPSHOT.jar
```

### 6. Verify Application Started
```bash
# Check logs
tail -f logs/application.log

# Check health
curl http://localhost:8080/actuator/health
```

---

## Post-Deployment Verification

### 1. Database Schema
```sql
-- Verify new columns exist
DESCRIBE drivers;

-- Should see:
-- email, photo, license_photo, aadhaar_photo
```

### 2. API Endpoints
```bash
# Test owner login
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{"email":"YOUR_OWNER_EMAIL","password":"YOUR_OWNER_PASSWORD"}'

# Test driver creation endpoint exists
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json"
# Should return 401 (not 404)

# Test old driver signup removed
curl -X POST http://localhost:8080/api/auth/driver/signup \
  -H "Content-Type: application/json"
# Should return 404
```

### 3. Email Service
```bash
# Create test driver and verify email sent
# Check the backend log for mail send failures
```

### 4. Security
```bash
# Test unauthorized access
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Content-Type: application/json" \
  -d '{...}'
# Should return 401

# Test with USER token
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer <USER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{...}'
# Should return 403
```

---

## Rollback Plan

### If Issues Occur:

1. **Stop Application**
```bash
sudo systemctl stop travel-platform
```

2. **Restore Backup**
```bash
cp target/travel-platform.jar.backup /opt/travel-platform/travel-platform.jar
```

3. **Restart Application**
```bash
sudo systemctl start travel-platform
```

4. **Verify Rollback**
```bash
curl http://localhost:8080/actuator/health
```

### Database Rollback (if needed)
```sql
-- Remove new columns (only if necessary)
ALTER TABLE drivers 
DROP COLUMN email,
DROP COLUMN photo,
DROP COLUMN license_photo,
DROP COLUMN aadhaar_photo;
```

---

## Monitoring

### Application Logs
```bash
# Monitor for errors
tail -f logs/application.log | grep ERROR

# Monitor driver creation
tail -f logs/application.log | grep "Driver created"

# Monitor email sending
tail -f logs/application.log | grep "email sent"
```

### Database Monitoring
```sql
-- Check driver creation rate
SELECT COUNT(*), DATE(created_at) 
FROM drivers 
GROUP BY DATE(created_at) 
ORDER BY DATE(created_at) DESC;

-- Check email field population
SELECT COUNT(*) as total,
       COUNT(email) as with_email,
       COUNT(*) - COUNT(email) as without_email
FROM drivers;
```

### Mail delivery
- Monitor email delivery rate
- Check bounce rate
- Verify no spam complaints

---

## Communication Plan

### Notify Stakeholders

**Before Deployment:**
```
Subject: System Update - Driver Registration Process Change

Dear Team,

We will be deploying an update to the driver registration process:

Date: [DATE]
Time: [TIME]
Duration: ~15 minutes
Impact: Driver signup temporarily unavailable

Changes:
- Driver accounts will now be created by admin only
- Drivers will receive login credentials via email
- Old driver signup endpoint will be removed

Action Required:
- Admins: Learn new driver creation process
- Support: Update documentation and FAQs

Thank you,
Tech Team
```

**After Deployment:**
```
Subject: System Update Complete - New Driver Registration Live

Dear Team,

The driver registration update has been deployed successfully.

New Process:
1. Admin logs in to owner portal
2. Admin creates driver account with details
3. Driver receives email with credentials
4. Driver logs in using received credentials

Documentation:
- API Guide: DRIVER_MANAGEMENT_API.md
- Testing Guide: TESTING_GUIDE.md

Support: support@travelplatform.com

Thank you,
Tech Team
```

---

## Training Materials

### For Admins
- [ ] Share DRIVER_MANAGEMENT_API.md
- [ ] Share TESTING_GUIDE.md
- [ ] Conduct training session on new process
- [ ] Provide Postman collection

### For Support Team
- [ ] Update FAQs
- [ ] Update help documentation
- [ ] Train on troubleshooting common issues
- [ ] Provide escalation process

### For Drivers
- [ ] Email template explaining new process
- [ ] Instructions on what to expect
- [ ] Contact information for support

---

## Success Metrics

### Week 1 Post-Deployment
- [ ] Number of drivers created by admin
- [ ] Email delivery success rate
- [ ] Driver login success rate
- [ ] Support tickets related to new process
- [ ] System errors/bugs reported

### Week 2-4 Post-Deployment
- [ ] Process efficiency improvements
- [ ] User feedback collection
- [ ] Performance metrics
- [ ] Security audit results

---

## Known Issues & Workarounds

### Issue 1: Email Delivery Delay
**Workaround**: Check the backend log for the send failure; share credentials by hand if needed

### Issue 2: Existing Drivers Without Email
**Workaround**: Email field is optional, existing drivers unaffected

### Issue 3: Photo Upload Not Implemented
**Workaround**: Accept URLs or Base64 strings for now

---

## Future Enhancements

- [ ] File upload for photos
- [ ] Driver password reset
- [ ] Admin dashboard UI
- [ ] Bulk driver import
- [ ] Driver document verification
- [ ] SMS notification option
- [ ] Multi-language email templates

---

## Support Contacts

- **Technical Issues**: tech@travelplatform.com
- **Mail delivery issues**: check MAIL_USERNAME / MAIL_PASSWORD and the Gmail app-password setup
- **Database Issues**: dba@travelplatform.com
- **Emergency**: +91-XXXXXXXXXX

---

## Sign-Off

- [ ] Development Team Lead: _________________ Date: _______
- [ ] QA Team Lead: _________________ Date: _______
- [ ] DevOps Lead: _________________ Date: _______
- [ ] Product Manager: _________________ Date: _______

---

**Deployment Status**: ⏳ Pending / ✅ Complete / ❌ Failed

**Deployment Date**: __________________

**Deployed By**: __________________

**Verified By**: __________________
