# Add project specific ProGuard rules here.
-keepattributes *Annotation*
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
# Hilt
-keep class dagger.** { *; }
# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
# jbcrypt
-keep class org.mindrot.jbcrypt.** { *; }
