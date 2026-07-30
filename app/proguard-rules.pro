# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\anilp\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt

# --- MapLibre GL Specific Rules ---
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# --- Security & Crypto Rules ---
# Keep EncryptedSharedPreferences and KeyStore related classes
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn androidx.biometric.**

# Keep our security logic intact
-keep class com.aitoyz.mapplock.security.** { *; }

# --- Jetpack Compose Rules ---
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keepattributes *Annotation*

# --- Coroutines & Kotlin ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# --- Coil & Media3 ---
-dontwarn coil.**
-dontwarn androidx.media3.**

# --- PostHog Analytics ---
-keep class com.posthog.** { *; }
-dontwarn com.posthog.**

# --- Strip Debug Logs ---
# This rule removes Log.d and Log.v calls from the release build.
# We keep Log.i/w/e for production audit and troubleshooting.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
