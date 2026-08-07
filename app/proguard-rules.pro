# AndroidLibXrayLite (gomobile generated)
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class go.seq.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
