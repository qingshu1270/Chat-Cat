# keep kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# security-crypto (Tink) 引用了仅编译期的 errorprone 注解，运行时不需要
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Room / Compose / OkHttp 反射与 native 方法
-keep class com.chatcat.data.db.** { *; }
-dontwarn okhttp3.internal.platform.**
