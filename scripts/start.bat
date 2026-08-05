@echo off
echo ========================================
echo Travel Booking Platform - Backend Setup
echo ========================================
echo.
echo PREREQUISITES:
echo 1. PostgreSQL must be installed and running
echo 2. Database 'travel_booking_db' must exist
echo 3. Update credentials in .env file if needed
echo.
echo ========================================
echo.

REM Check if PostgreSQL is accessible
echo Checking PostgreSQL connection...
psql -U postgres -c "SELECT version();" 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] PostgreSQL is not accessible!
    echo.
    echo Please:
    echo 1. Install PostgreSQL from https://www.postgresql.org/download/
    echo 2. Start PostgreSQL service
    echo 3. Create database: CREATE DATABASE travel_booking_db;
    echo 4. Update .env file with correct credentials
    echo.
    pause
    exit /b 1
)

echo [OK] PostgreSQL is running
echo.

REM Create database if it doesn't exist
echo Creating database if not exists...
psql -U postgres -c "CREATE DATABASE travel_booking_db;" 2>nul
echo.

REM Set environment variables
echo Loading environment variables...
for /f "tokens=1,2 delims==" %%a in (.env) do set %%a=%%b

REM Set JAVA_HOME
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting application...
echo.
call apache-maven-3.9.6\bin\mvn.cmd spring-boot:run

pause
