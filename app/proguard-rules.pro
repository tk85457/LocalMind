# Add project specific ProGuard rules here.

# ─────────────────────────────────────────────────────────────────────────────
# SECURITY: ProGuard hardening rules (OWASP MASVS MSTG-RESILIENCE-9)
# ─────────────────────────────────────────────────────────────────────────────

# Remove all debug log calls in release (MSTG-RESILIENCE-2)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}

# Keep Android Keystore and Crypto classes
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class androidx.security.crypto.** { *; }

# Keep SecureStorageManager
-keep class com.localmind.app.core.security.SecureStorageManager { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep LlamaCppBridge (fixed package name typo)
-keep class com.localmind.app.llm.nativelib.LlamaCppBridge { *; }

# Keep native callback and models for JNI Reflection
-keep class com.localmind.app.llm.nativelib.GenerationCallback { *; }
-keep class com.localmind.app.core.engine.PerfMetrics { *; }
-keep class com.localmind.app.core.engine.ModelMetadata { *; }

# Keep Room entities
-keep class com.localmind.app.data.local.entity.** { *; }

# Keep data models
-keep class com.localmind.app.data.model.** { *; }
-keep class com.localmind.app.domain.model.** { *; }
-keep class com.localmind.app.data.remote.** { *; }

# Hilt
-dontwarn com.google.errorprone.annotations.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# PDFBox optional JPX decoder dependency is not bundled
-dontwarn com.gemalto.jp2.JP2Decoder

# Keep Parcelables
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
