# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class py.com.recargas.ussd.catalog.**$$serializer { *; }
-keepclassmembers class py.com.recargas.ussd.catalog.** { *** Companion; }
-keepclasseswithmembers class py.com.recargas.ussd.catalog.** { kotlinx.serialization.KSerializer serializer(...); }
