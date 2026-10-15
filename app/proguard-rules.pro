# Add project specific ProGuard rules here.

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# SECURITY: ProGuard hardening rules (OWASP MASVS MSTG-RESILIENCE-9)
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

# Remove verbose and debug log calls in release (keep warning and error for diagnostics)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Keep Android Keystore and Crypto classes
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class androidx.security.crypto.** { *; }

# Keep SecureStorageManager
-keep class com.tk854.localmind.core.security.SecureStorageManager { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep LlamaCppBridge (fixed package name typo)
-keep class com.tk854.localmind.llm.nativelib.LlamaCppBridge { *; }

# Keep native callback and models for JNI Reflection
-keep class com.tk854.localmind.llm.nativelib.GenerationCallback { *; }
-keep class com.tk854.localmind.core.engine.PerfMetrics { *; }
-keep class com.tk854.localmind.core.engine.ModelMetadata { *; }

# Keep Room entities
-keep class com.tk854.localmind.data.local.entity.** { *; }

# Keep data models
-keep class com.tk854.localmind.data.model.** { *; }
-keep class com.tk854.localmind.domain.model.** { *; }
-keep class com.tk854.localmind.data.remote.** { *; }

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
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Keep Parcelables
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Markwon & Prism4j
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**
-keep class io.noties.prism4j.** { *; }
-dontwarn io.noties.prism4j.**

# Lottie
-keep class com.airbnb.lottie.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
