# ==============================================================================
# Attract Attendance Tracker - Production ProGuard / R8 Rules (PR-02)
# ==============================================================================

# --- Room Database ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.RoomDatabase$Callback
-keep class com.attract.attendance.data.local.** { *; }

# --- TensorFlow Lite & Face Biometrics ---
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

# --- Google API Client & Google Drive ---
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Google ML Kit ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# --- Firebase AI & App Check ---
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.appcheck.playintegrity.** { *; }
-dontwarn com.google.firebase.**

# --- App Domain & Export Models ---
-keep class com.attract.attendance.core.model.** { *; }
-keep class com.attract.attendance.data.importexport.** { *; }

# --- Logging Stripping (Release Hardening) ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

-assumenosideeffects class com.attract.attendance.util.AppLog {
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}
