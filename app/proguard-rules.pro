# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\anilp\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt

# --- MapLibre GL Specific Rules ---
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# --- Security & Crypto Rules ---
# Keep critical security singletons intact for reliable preference access
-keep class com.aitoyz.mapplock.security.SecureManager { *; }
-keep class com.aitoyz.mapplock.security.CryptoManager { *; }
-keep class com.aitoyz.mapplock.security.LockerRepository { *; }
-keep class com.aitoyz.mapplock.security.LocaleManager { *; }

# Initializers for Jetpack Startup must be kept
-keep class com.aitoyz.mapplock.startup.** { *; }

# --- Jetpack Compose Rules ---
# Using more specific rules to avoid overly broad keep rules
-keep class androidx.compose.runtime.Recomposer { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
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
