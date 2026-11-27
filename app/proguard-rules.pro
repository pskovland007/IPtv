# Keep app classes cleanly for reflection, JNI callbacks, and stacktraces
-keep class com.example.miboxiptv.** { *; }

# Keep LibVLC classes and native JNI hooks
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# Keep Media3 and Jellyfin FFmpeg decoder classes loaded via reflection
-keep class androidx.media3.** { *; }
-keep class org.jellyfin.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn org.jellyfin.media3.**

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keepattributes SourceFile,LineNumberTable
