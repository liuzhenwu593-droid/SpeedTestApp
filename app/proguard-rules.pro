# Add project specific ProGuard rules here.
-keep class com.shadiao.nb.** { *; }
-keep class libv2ray.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
