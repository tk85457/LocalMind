# Build Instructions for LocalMind

## Prerequisites

1. **Android Studio** (Hedgehog or later)
2. **JDK 17** or later
3. **Android SDK** with:
   - Android 14 (API 34)
   - Android 8.0 (API 26) minimum
   - NDK 26.1.10909125
   - CMake 3.22.1+

## Quick Start (Android Studio)

1. Open Android Studio
2. Click "Open" and select the `Local Mind` folder
3. Wait for Gradle sync to complete
4. Click "Build" → "Make Project" or press `Ctrl+F9`
5. Run on device/emulator

## Command Line Build

### Windows

```powershell
# Navigate to project
cd "C:\Users\tk854\Desktop\New folder\Local Mind"

# Build debug APK
.\gradlew assembleDebug

# Install on connected device
.\gradlew installDebug

# Build release AAB (for Play Store)
.\gradlew bundleRelease
```

### Output Locations

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release AAB**: `app/build/outputs/bundle/release/app-release.aab`

## Important Notes

### llama.cpp Integration Required

The app currently has a **placeholder** native layer. For actual LLM inference:

1. Add llama.cpp as submodule:
   ```bash
   cd app/src/main/cpp
   git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
   ```

2. Update `app/src/main/cpp/CMakeLists.txt`:
   - Uncomment line: `add_subdirectory(llama.cpp)`
   - Uncomment line: `# llama` in `target_link_libraries`

3. Update `app/src/main/cpp/jni_bridge.cpp`:
   - Include llama.cpp headers
   - Replace placeholder code with actual llama.cpp API calls

### First Build

The first build will:
- Download Gradle dependencies (~500MB)
- Download Android SDK components
- Download NDK if not present
- Take 5-10 minutes

Subsequent builds are much faster.

### Common Issues

**Issue**: "SDK location not found"
**Fix**: Create `local.properties` with:
```
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

**Issue**: "NDK not found"
**Fix**: Android Studio will prompt to download. Accept and retry.

**Issue**: "CMake not found"
**Fix**: Install CMake via SDK Manager in Android Studio.

## Testing

### Run on Emulator

1. Create AVD with:
   - System Image: Android 14 (API 34)
   - RAM: 8GB
   - Storage: 8GB

2. Run: `.\gradlew installDebug`

### Run on Physical Device

1. Enable Developer Options
2. Enable USB Debugging
3. Connect device
4. Run: `.\gradlew installDebug`

## Build Variants

- **debug**: Development build with debugging enabled
- **release**: Production build (requires signing)

## Signing (for Release)

Create `keystore.properties` in project root:
```
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=your_key_alias
storeFile=path/to/keystore.jks
```

Then build: `.\gradlew bundleRelease`

## Clean Build

```powershell
.\gradlew clean
.\gradlew assembleDebug
```

## Troubleshooting

1. **Sync Issues**: File → Invalidate Caches → Restart
2. **Build Fails**: Check `build` output in Android Studio
3. **Native Errors**: Verify NDK and CMake are installed
4. **Memory Issues**: Increase heap in `gradle.properties`:
   ```
   org.gradle.jvmargs=-Xmx4g
   ```
