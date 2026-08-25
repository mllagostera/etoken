# kotlinx.serialization keeps the generated serializers off the entry-point
# graph, so R8 needs to be told they are reachable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.etoken.** {
    *** Companion;
}
-keepclasseswithmembers class com.etoken.** {
    kotlinx.serialization.KSerializer serializer(...);
}
