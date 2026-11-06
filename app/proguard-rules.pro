# No logging in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
# libVLC uses JNI callbacks into these classes.
-keep class org.videolan.libvlc.** { *; }
# FFmpeg extension is loaded reflectively by DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.fitztv.**$$serializer { *; }
-keepclassmembers class com.fitztv.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
