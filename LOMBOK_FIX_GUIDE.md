# Fix Guide: Resolving "Cannot resolve symbol" Errors

## Problem
Your IDE is showing errors like:
- Cannot resolve symbol 'Slf4j'
- Cannot resolve method 'getEmail' in 'UserSignupRequest'
- Cannot resolve method 'setName' in 'User'

## Root Cause
These errors occur because **Lombok annotation processing is not enabled** in your IDE. Lombok generates getters, setters, constructors, and logging at compile time, but your IDE doesn't recognize them yet.

---

## Solution Steps

### Step 1: Enable Lombok Plugin (IntelliJ IDEA)

1. Go to **File → Settings** (or **Ctrl+Alt+S**)
2. Navigate to **Plugins**
3. Search for **"Lombok"**
4. Install the **Lombok Plugin** if not already installed
5. Restart IntelliJ IDEA

### Step 2: Enable Annotation Processing

1. Go to **File → Settings** (or **Ctrl+Alt+S**)
2. Navigate to **Build, Execution, Deployment → Compiler → Annotation Processors**
3. Check **"Enable annotation processing"**
4. Click **Apply** and **OK**

### Step 3: Rebuild Maven Project

**Option A: Using the provided script**
```bash
cd "c:\react project\Travel Booking Platform"
rebuild-project.bat
```

**Option B: Manual Maven commands**
```bash
cd "c:\react project\Travel Booking Platform"
mvn clean install -DskipTests
```

**Option C: Using IntelliJ Maven tool**
1. Open **Maven** tool window (View → Tool Windows → Maven)
2. Click **Clean** (under Lifecycle)
3. Click **Install** (under Lifecycle)

### Step 4: Refresh IDE

1. **File → Invalidate Caches / Restart**
2. Select **"Invalidate and Restart"**
3. Wait for IDE to restart and re-index

---

## Verification

After completing the steps above, verify that:

✅ No red underlines in service files  
✅ Methods like `getEmail()`, `setName()` are recognized  
✅ `@Slf4j` annotation works (log variable available)  
✅ Project compiles without errors  

---

## Alternative: If Errors Persist

### For Eclipse IDE:
1. Install **Lombok** from https://projectlombok.org/download
2. Run the downloaded `lombok.jar`
3. Point it to your Eclipse installation
4. Restart Eclipse

### For VS Code:
1. Install **"Language Support for Java"** extension
2. Install **"Lombok Annotations Support"** extension
3. Reload VS Code

---

## What Was Fixed in Code

### UserAuthService.java
✅ Added proper imports for Jackson:
```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
```

✅ Cleaned up fully qualified class names to use imports

---

## Files Status

| File | Status | Notes |
|------|--------|-------|
| UserAuthService.java | ✅ Fixed | Added Jackson imports |
| DriverAuthService.java | ✅ OK | No code changes needed |
| OwnerAuthService.java | ✅ OK | No code changes needed |
| EmailService.java | ✅ OK | No code changes needed |
| OtpService.java | ✅ OK | No code changes needed |
| FileStorageService.java | ✅ OK | No code changes needed |
| AdminDriverService.java | ✅ OK | No code changes needed |

---

## Why This Happens

Lombok uses **annotation processing** to generate code at compile time:

- `@Data` → Generates getters, setters, toString, equals, hashCode
- `@Slf4j` → Generates `private static final Logger log`
- `@NoArgsConstructor` → Generates no-args constructor
- `@AllArgsConstructor` → Generates all-args constructor

Your IDE needs to be configured to recognize these generated methods.

---

## Quick Test

After fixing, test with this code:
```java
User user = new User();
user.setName("Test");  // Should work now
String name = user.getName();  // Should work now
log.info("User created: {}", name);  // Should work now
```

---

## Need Help?

If errors persist after following all steps:
1. Check Maven output for compilation errors
2. Ensure Java 17 is configured in IDE
3. Verify pom.xml has Lombok dependency
4. Try deleting `.idea` folder and reimporting project

---

**Last Updated:** 2024
**Status:** All service files fixed ✅
