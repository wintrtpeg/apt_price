# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# TikXml generated type adapters
-keep class **$$TypeAdapter { *; }
-keep @com.tickaroo.tikxml.annotation.Xml class * { *; }

# 국토교통부 API 응답 DTO
-keep class com.aptprice.tracker.data.remote.dto.** { *; }
