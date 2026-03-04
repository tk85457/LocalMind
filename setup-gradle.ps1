# Setup Gradle Wrapper
# This script downloads and sets up the Gradle wrapper for the project

Write-Host "Setting up Gradle wrapper..." -ForegroundColor Cyan

# Create gradle wrapper directory
$wrapperDir = "gradle\wrapper"
if (!(Test-Path $wrapperDir)) {
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
}

# Download gradle-wrapper.jar
$jarUrl = "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar"
$jarPath = "$wrapperDir\gradle-wrapper.jar"

Write-Host "Downloading gradle-wrapper.jar..." -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri $jarUrl -OutFile $jarPath -UseBasicParsing
    Write-Host "✓ gradle-wrapper.jar downloaded successfully" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to download gradle-wrapper.jar: $_" -ForegroundColor Red
    exit 1
}

# Verify the jar file exists
if (Test-Path $jarPath) {
    $fileSize = (Get-Item $jarPath).Length
    Write-Host "✓ Wrapper jar size: $fileSize bytes" -ForegroundColor Green
} else {
    Write-Host "✗ Wrapper jar not found!" -ForegroundColor Red
    exit 1
}

Write-Host "`n✓ Gradle wrapper setup complete!" -ForegroundColor Green
Write-Host "You can now run: .\gradlew.bat assembleDebug" -ForegroundColor Cyan
