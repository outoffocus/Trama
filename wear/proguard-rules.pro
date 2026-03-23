# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mydiary.**$$serializer { *; }
-keepclassmembers class com.mydiary.** { *** Companion; }
-keepclasseswithmembers class com.mydiary.** { kotlinx.serialization.KSerializer serializer(...); }
