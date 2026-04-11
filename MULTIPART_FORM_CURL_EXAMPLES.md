# Driver Creation with Multipart Form Data - cURL Examples

## 📋 Prerequisites
- Have 3 image files ready: driver_photo.jpg, license.jpg, aadhaar.jpg
- Owner token from login

---

## 1️⃣ Owner Login (Get Token)
```bash
curl -X POST http://localhost:8080/api/auth/owner/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@travelplatform.com","password":"owner@123"}'
```

**Save the token from response**

---

## 2️⃣ Create Driver with Image Files (Linux/Mac)

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -F "name=Suresh Sharma" \
  -F "mobile=9123456789" \
  -F "email=tlokeshthiru123@gmail.com" \
  -F "licenseNumber=DL1420110012345" \
  -F "aadhaarNumber=123456789012" \
  -F "photo=@/path/to/driver_photo.jpg" \
  -F "licensePhoto=@/path/to/license.jpg" \
  -F "aadhaarPhoto=@/path/to/aadhaar.jpg"
```

---

## 2️⃣ Create Driver with Image Files (Windows CMD)

```cmd
curl -X POST http://localhost:8080/api/owner/drivers ^
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" ^
  -F "name=Suresh Sharma" ^
  -F "mobile=9123456789" ^
  -F "email=tlokeshthiru123@gmail.com" ^
  -F "licenseNumber=DL1420110012345" ^
  -F "aadhaarNumber=123456789012" ^
  -F "photo=@C:\path\to\driver_photo.jpg" ^
  -F "licensePhoto=@C:\path\to\license.jpg" ^
  -F "aadhaarPhoto=@C:\path\to\aadhaar.jpg"
```

---

## 2️⃣ Create Driver WITHOUT Images (Optional)

```bash
curl -X POST http://localhost:8080/api/owner/drivers \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -F "name=Suresh Sharma" \
  -F "mobile=9123456789" \
  -F "email=tlokeshthiru123@gmail.com" \
  -F "licenseNumber=DL1420110012345" \
  -F "aadhaarNumber=123456789012"
```

---

## 3️⃣ Verify Driver Email (JSON - No Change)

```bash
curl -X POST http://localhost:8080/api/owner/drivers/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_OWNER_TOKEN" \
  -d '{"email":"tlokeshthiru123@gmail.com","otp":"123456"}'
```

---

## 4️⃣ Driver Login (JSON - No Change)

```bash
curl -X POST http://localhost:8080/api/auth/driver/login \
  -H "Content-Type: application/json" \
  -d '{"mobile":"9123456789","password":"PASSWORD_FROM_EMAIL"}'
```

---

## 5️⃣ Change Password (JSON - No Change)

```bash
curl -X POST http://localhost:8080/api/auth/driver/change-password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_DRIVER_TOKEN" \
  -d '{"oldPassword":"PASSWORD_FROM_EMAIL","newPassword":"MyNewPassword@123"}'
```

---

## 📝 Postman Setup for Multipart Form

### In Postman:
1. **Method:** POST
2. **URL:** `http://localhost:8080/api/owner/drivers`
3. **Headers:**
   - `Authorization: Bearer YOUR_OWNER_TOKEN`
4. **Body:** Select `form-data`
5. **Add fields:**

| Key | Type | Value |
|-----|------|-------|
| name | Text | Suresh Sharma |
| mobile | Text | 9123456789 |
| email | Text | tlokeshthiru123@gmail.com |
| licenseNumber | Text | DL1420110012345 |
| aadhaarNumber | Text | 123456789012 |
| photo | File | Select image file |
| licensePhoto | File | Select image file |
| aadhaarPhoto | File | Select image file |

---

## 🖼️ Image Requirements

- **Format:** JPG, JPEG, PNG
- **Max Size:** 5MB per file
- **Max Total:** 20MB per request
- **Fields:** All image fields are optional

---

## 📂 File Storage

Images are stored in: `uploads/drivers/`

File naming format: `{type}_{uuid}.{extension}`

Examples:
- `photo_a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg`
- `license_b2c3d4e5-f6a7-8901-bcde-f12345678901.jpg`
- `aadhaar_c3d4e5f6-a7b8-9012-cdef-123456789012.jpg`

---

## ✅ Response Example

```json
{
  "id": 1,
  "name": "Suresh Sharma",
  "email": "tlokeshthiru123@gmail.com",
  "mobile": "9123456789",
  "message": "Driver created successfully. OTP sent to driver's email for verification."
}
```

---

## ⚠️ Common Errors

### File Too Large
```json
{
  "error": "Maximum upload size exceeded"
}
```
**Solution:** Reduce image size to under 5MB

### Invalid File Type
```json
{
  "error": "Failed to store file"
}
```
**Solution:** Use JPG, JPEG, or PNG format

### Missing Required Fields
```json
{
  "name": "must not be blank",
  "mobile": "must match pattern"
}
```
**Solution:** Provide all required fields

---

## 🔧 Testing Tips

1. **Test without images first** to verify basic functionality
2. **Use small test images** (< 1MB) for faster testing
3. **Check uploads folder** to verify files are saved
4. **Verify file paths** in database after creation

---

## 📱 Frontend Integration Example

### JavaScript Fetch API
```javascript
const formData = new FormData();
formData.append('name', 'Suresh Sharma');
formData.append('mobile', '9123456789');
formData.append('email', 'driver@example.com');
formData.append('licenseNumber', 'DL1420110012345');
formData.append('aadhaarNumber', '123456789012');
formData.append('photo', photoFile); // File object
formData.append('licensePhoto', licenseFile);
formData.append('aadhaarPhoto', aadhaarFile);

fetch('http://localhost:8080/api/owner/drivers', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer ' + ownerToken
  },
  body: formData
})
.then(response => response.json())
.then(data => console.log(data));
```

### React Example
```jsx
const handleSubmit = async (e) => {
  e.preventDefault();
  
  const formData = new FormData();
  formData.append('name', name);
  formData.append('mobile', mobile);
  formData.append('email', email);
  formData.append('licenseNumber', licenseNumber);
  formData.append('aadhaarNumber', aadhaarNumber);
  formData.append('photo', photoFile);
  formData.append('licensePhoto', licensePhotoFile);
  formData.append('aadhaarPhoto', aadhaarPhotoFile);
  
  const response = await fetch('/api/owner/drivers', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${ownerToken}`
    },
    body: formData
  });
  
  const data = await response.json();
  console.log(data);
};
```

---

## 📄 Notes

- **Content-Type header is NOT needed** - Browser/cURL sets it automatically with boundary
- **Images are optional** - Can create driver without uploading images
- **File validation** - Only image files are accepted
- **Storage location** - Files stored in `uploads/drivers/` directory
- **Database** - Only file paths are stored, not the actual files

---

**Updated:** 2024  
**Version:** 2.0 (Multipart Form Support)
