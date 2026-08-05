@echo off
echo ========================================
echo Rebuilding Travel Booking Platform
echo ========================================
echo.

echo Step 1: Cleaning project...
call mvn clean
if %errorlevel% neq 0 (
    echo ERROR: Maven clean failed!
    pause
    exit /b %errorlevel%
)
echo.

echo Step 2: Compiling and installing...
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Maven install failed!
    pause
    exit /b %errorlevel%
)
echo.

echo ========================================
echo Build completed successfully!
echo ========================================
echo.
echo Next steps:
echo 1. Refresh your IDE project (File ^> Reload Project)
echo 2. If errors persist, restart your IDE
echo.
pause
