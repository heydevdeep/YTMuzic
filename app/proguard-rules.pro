# YoutubeDL-Android
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }

# Apache Commons Compress (used by youtubedl-common ZipUtils to unpack Python/FFmpeg)
-keep class org.apache.commons.compress.** { *; }
-keep interface org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Jackson (used by youtubedl-android to parse yt-dlp JSON output)
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-keep class * extends com.fasterxml.jackson.databind.JsonDeserializer { *; }
-keep class * extends com.fasterxml.jackson.databind.JsonSerializer { *; }

# Keep fields annotated by Jackson (JsonProperty etc.) on model classes used by youtubedl-android
-keepclassmembers class com.yausername.** {
    <fields>;
    <init>(...);
}

# Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$Companion {
    *;
}

# Suppress warnings from optional Jackson modules
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn java.beans.**
