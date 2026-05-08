# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\anilp\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# MapLibre Specific Rules
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Keep generic security related classes
-keep class com.geovault.security.** { *; }

# Kotlin Serialization or other libraries might need rules if added later.
