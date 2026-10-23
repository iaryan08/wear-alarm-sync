# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep class dev.sync.core.model.** { *; }
-keepclasseswithmembers class **$$serializer { *** INSTANCE; *** descriptor; public *** serialize(...); public *** deserialize(...); }

# Keep Wearable / GMS
-keep class com.google.android.gms.wearable.** { *; }

# Keep Wear Tiles
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }

# Keep all declared AndroidManifest components
-keep class dev.sync.wear.** { *; }
