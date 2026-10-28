# Immich API models are serialized with kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class org.himstedt.immichslideshow.data.** {
    *** Companion;
}
-keepclasseswithmembers class org.himstedt.immichslideshow.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.himstedt.immichslideshow.data.**$$serializer { *; }

# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-dontwarn okio.**
