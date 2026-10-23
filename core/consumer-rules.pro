# Keep serialization models for kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep class dev.sync.core.model.** { *; }
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
    *** descriptor;
    public *** serialize(...);
    public *** deserialize(...);
}
