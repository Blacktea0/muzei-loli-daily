# Keep Muzei API
-keep class com.google.android.apps.muzei.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable data classes
-keep,includedescriptorclasses class me.eroi.lolidaily.muzei.model.**$$serializer { *; }
-keepclassmembers class me.eroi.lolidaily.muzei.model.** {
    *** Companion;
}
-keepclasseswithmembers class me.eroi.lolidaily.muzei.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
