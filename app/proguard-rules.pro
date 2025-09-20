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

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 1. Keep no-arg constructors and fields for all Firestore models
-keep class com.kouloundissa.twinstracker.data.** {
    public <init>();
    <fields>;
}

# 2. Preserve all Kotlin metadata & annotations needed by serialization
-keepattributes InnerClasses,EnclosingMethod,Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# 3. Keep Firebase & Gson classes
-keep class com.google.firebase.** { *; }
-keepattributes *Annotation*

# 4. (Optional) Prevent obfuscation of your data-model package
-keepnames class com.kouloundissa.twinstracker.data.** { *; }