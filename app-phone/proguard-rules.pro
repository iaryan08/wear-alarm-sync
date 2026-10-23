# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
    *** descriptor;
    public *** serialize(...);
    public *** deserialize(...);
}

# Keep all data classes in core used for serialization
-keep class dev.sync.core.model.** { *; }

# Keep Wearable API
-keep class com.google.android.gms.wearable.** { *; }

# Keep notification listener
-keep class dev.sync.phone.notification.PhoneAlarmNotificationListener { *; }
-keep class dev.sync.phone.service.PhoneWearDataListenerService { *; }
-keep class dev.sync.phone.receiver.PhoneAlarmChangeReceiver { *; }
