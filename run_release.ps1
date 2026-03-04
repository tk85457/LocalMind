$ErrorActionPreference = "Stop"

Write-Host "Waiting for device to be connected..." -ForegroundColor Cyan
adb wait-for-device

Write-Host "Installing Release APK..." -ForegroundColor Cyan
$apkPath = "app\build\outputs\apk\release\app-release.apk"

$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$installOutput = & adb install -r $apkPath 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -ne 0) {
    $installText = ($installOutput | Out-String)
    if ($installText -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "Signature mismatch detected. Uninstalling existing app and retrying..." -ForegroundColor Yellow
        & adb uninstall com.localmind.app | Out-Host
        & adb install $apkPath | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Release APK install failed even after uninstall."
        }
    } else {
        $installOutput | Out-Host
        throw "Release APK install failed."
    }
} else {
    $installOutput | Out-Host
}

Write-Host "Clearing previous logs..." -ForegroundColor Cyan
adb logcat -c

Write-Host "Launching App..." -ForegroundColor Cyan
adb shell am start -n com.localmind.app/com.localmind.app.MainActivity

Write-Host "Attaching to Logcat..." -ForegroundColor Cyan
# Loop to wait until the app process starts and we can get its PID
$appPid = ""
while ([string]::IsNullOrWhiteSpace($appPid)) {
    Start-Sleep -Milliseconds 500
    $appPid = (adb shell pidof -s com.localmind.app).Trim()
}

Write-Host "Streaming logs for PID: $appPid" -ForegroundColor Green
adb logcat --pid=$appPid
