# Keep Capacitor WebView bridge
-keep class com.getcapacitor.** { *; }
-keep class * extends com.getcapacitor.Plugin { *; }
-keep class * extends com.getcapacitor.annotation.CapacitorPlugin { *; }

# Keep JavaScript interface methods accessible from WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep plugin classes and their methods (reflection via JS bridge)
-keep class com.detetive.app.** { *; }
-keepclassmembers class * extends com.getcapacitor.Plugin {
    @com.getcapacitor.annotation.PluginMethod <methods>;
}

# Keep AndroidX / Material components
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep R8 rules for web assets
-keep,allowobfuscation class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Prevent obfuscation of WebView plugin method names
-keepclassmembers class * {
    @android.webkit.JavascriptInterface public *;
}
