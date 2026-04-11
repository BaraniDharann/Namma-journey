# How to Set Java 17 in IntelliJ IDEA

## Option 1: Download JDK 17 from IntelliJ (Easiest)

1. **Open Project Structure**
   - Press `Ctrl + Alt + Shift + S`
   - OR: File → Project Structure

2. **Download JDK 17**
   - Go to "Project" tab (left sidebar)
   - Click "SDK" dropdown
   - Click "Add SDK" → "Download JDK..."
   - Select:
     - Version: **17**
     - Vendor: **Oracle OpenJDK** or **Amazon Corretto**
   - Click "Download"
   - Wait for download to complete

3. **Set Project SDK**
   - In "Project" tab, set SDK to the downloaded JDK 17
   - Set Language Level to **17**
   - Click "Apply" → "OK"

4. **Set Module SDK**
   - Go to "Modules" tab
   - Select your module
   - Set Language Level to **17**
   - Click "Apply" → "OK"

5. **Rebuild Project**
   - Build → Rebuild Project

---

## Option 2: Use Existing JDK 17 (You already have it!)

You already have JDK 17 installed at: `C:\Program Files\Java\jdk-17`

1. **Open Project Structure**
   - Press `Ctrl + Alt + Shift + S`

2. **Add Existing JDK**
   - Go to "SDKs" tab (under Platform Settings)
   - Click "+" → "Add JDK..."
   - Navigate to: `C:\Program Files\Java\jdk-17`
   - Click "OK"

3. **Set Project SDK**
   - Go to "Project" tab
   - Set SDK to **17 (java version "17.0.12")**
   - Set Language Level to **17**
   - Click "Apply"

4. **Set Module SDK**
   - Go to "Modules" tab
   - Select your module
   - Set Language Level to **17**
   - Click "Apply" → "OK"

5. **Configure Maven**
   - File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
   - Set JRE to **Use Project JDK (17)**
   - Click "Apply" → "OK"

6. **Reload Maven Project**
   - Right-click on `pom.xml`
   - Maven → Reload Project

7. **Rebuild**
   - Build → Rebuild Project

---

## Quick Fix (If still failing)

1. **Invalidate Caches**
   - File → Invalidate Caches...
   - Check "Clear file system cache and Local History"
   - Click "Invalidate and Restart"

2. **After Restart**
   - Build → Rebuild Project

---

## Verify Java Version

Open Terminal in IntelliJ and run:
```bash
java -version
```

Should show: `java version "17.0.12"`

---

## Still Having Issues?

Delete these folders and reimport:
- `.idea` folder
- `target` folder

Then: File → Open → Select project folder
