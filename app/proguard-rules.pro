# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Preserve Oboe Audio Engine JNI callback
-keep class com.example.audio.OboeAudioEngine {
    @androidx.annotation.Keep <methods>;
    void onNativeAudioBuffer(float[], int);
}

-keep class com.example.** { *; }
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }

