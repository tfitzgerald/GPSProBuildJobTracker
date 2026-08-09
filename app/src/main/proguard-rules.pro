# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep,allowobfuscation @interface dagger.hilt.**
-keep class dagger.hilt.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ca.gpsprobuild.app.** {
    *** Companion;
}
-keepclasseswithmembers class ca.gpsprobuild.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ca.gpsprobuild.app.**$$serializer { *; }

# Compose
-dontwarn androidx.compose.**
