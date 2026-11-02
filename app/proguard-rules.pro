# ============================================================================
# Attract - Release Hardening ProGuard / R8 Rules (PR-02)
# ============================================================================

# 1. Strip logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
    public static int println(...);
}
-assumenosideeffects class com.attract.attendance.util.AppLog {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# 2. Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter { *; }
-keep class * extends androidx.room.EntityInsertionAdapter { *; }
-keep class com.attract.attendance.data.local.** { *; }

# 3. TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# 4. Google API Client / Drive Model Classes
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**
-dontwarn com.google.common.**

# 5. Gson & Backup Data Classes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-dontwarn com.google.gson.**
# Keep backup snapshot models serialized to/from JSON
-keep class com.attract.attendance.data.importexport.** { *; }
-keep class com.attract.attendance.core.model.** { *; }

# 6. Firebase AI & App Check
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.**

# 8. Kotlin Coroutines & AndroidX
-dontwarn kotlinx.coroutines.**
-keep class androidx.biometric.** { *; }

# 9. MediaPipe Tasks Vision & AutoValue
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**
