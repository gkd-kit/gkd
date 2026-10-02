# JNA's JNI implementation uses its core classes; platform wrappers remain shrinkable.
-keep class com.sun.jna.* { *; }
# JNA reads structure fields/constructors and callback methods through reflection.
-keepclassmembers class * extends com.sun.jna.Structure {
    public <fields>;
    public <init>(...);
}
-keepclassmembers class * implements com.sun.jna.Callback {
    public <methods>;
}

# META-INF/services providers are instantiated reflectively, not through direct calls.
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    public <init>();
}

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-dontwarn **
