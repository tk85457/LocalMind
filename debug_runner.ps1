# Local Mind Debug Runner
# This script builds, installs, and starts live logging for the app.

$PACKAGE_NAME = "com.localmind.app"
$MAIN_ACTIVITY = "com.localmind.app.MainActivity"

Write-Host "--- Step 1: Building Debug APK ---" -ForegroundColor Cyan
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed!"; exit }

Write-Host "--- Step 2: Installing APK ---" -ForegroundColor Cyan
.\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) { Write-Error "Installation failed!"; exit }

Write-Host "--- Step 3: Starting App ---" -ForegroundColor Cyan
adb shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"
if ($LASTEXITCODE -ne 0) { Write-Warning "Could not start app automatically. Please open it manually." }

Write-Host "--- Step 4: Catching Live Logs (Press Ctrl+C to stop) ---" -ForegroundColor Green
Write-Host "Filtering for: $PACKAGE_NAME" -ForegroundColor Yellow

# Clear old logs first
adb logcat -c

# Run logcat and filter for the package name
adb logcat | Select-String $PACKAGE_NAME
