# Keep Gson models used by the shared core.
#
# Gson builds these objects with reflection, so every field name and the
# generic type information has to survive R8's shrinking and renaming. Without
# the rules below a minified release build parses JSON into objects whose
# fields are all null, which is why the release APK used to show empty screens.
-keep class com.watchall.core.data.** { *; }
-keep class com.watchall.core.net.** { *; }
-keep class com.watchall.core.prefs.** { *; }

# Gson reads field names, @SerializedName values and generic signatures.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson's TypeToken relies on the generic superclass of the anonymous subclass.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
