# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep all security-related classes
-keep class androidx.security.crypto.** { *; }
-keep class net.zetetic.database.** { *; }
-keep class com.lambdapioneer.argon2kt.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Preserve all native method names and the names of their classes.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep generic signatures for Room
-keepattributes Signature
-keepattributes *Annotation*

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ============================================
# ML Kit Text Recognition (OCR Feature)
# ============================================
-keep class com.google.mlkit.vision.text.** { *; }
-keep interface com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.vision.text.**

# ML Kit common classes
-keep class com.google.mlkit.common.** { *; }
-dontwarn com.google.mlkit.common.**

# ============================================
# CameraX (OCR Feature)
# ============================================
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.**

# ============================================
# TrustVault OCR Security Components
# ============================================
# Keep security-critical classes for debugging/analysis
-keep class com.trustvault.android.security.ocr.** { *; }

# CRITICAL: Keep OcrResult clear() method (must not be optimized away)
-keepclassmembers class com.trustvault.android.security.ocr.OcrResult {
    public void clear();
    private void secureWipe(char[]);
}

# Keep the Argon2 engine implementation loaded reflectively by PasswordHasher
-keep class com.trustvault.android.security.PasswordHasherRealEngine {
    <init>();
    *;
}

# ============================================
# Logging Removal (Release Builds)
# ============================================
# Remove all debug and verbose logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
