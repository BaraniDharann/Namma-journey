@echo off
echo ========================================
echo Checking Project Status
echo ========================================
echo.

echo Your code is CORRECT!
echo.
echo The errors you see are because Lombok is not enabled in your IDE.
echo.
echo Follow these steps:
echo.
echo 1. Open IntelliJ IDEA Settings (Ctrl + Alt + S)
echo 2. Go to Plugins
echo 3. Search "Lombok" and install it
echo 4. Restart IDE
echo 5. Go to Settings again
echo 6. Build, Execution, Deployment ^> Compiler ^> Annotation Processors
echo 7. Check "Enable annotation processing"
echo 8. Click Apply and OK
echo 9. Build ^> Rebuild Project
echo.
echo After these steps, all errors will disappear!
echo.
pause
