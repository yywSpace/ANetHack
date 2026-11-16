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

# 游戏数据通过 Gson 反射读取，字段名不能混淆。
-keep class com.yywspace.anethack.gamedata.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# JNI 方法需要保留名称。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Gson 需要保留泛型签名，否则 List<CommandEntry> 会解析成 LinkedHashMap。
-keepattributes Signature
-keep class com.yywspace.anethack.keybord.CommandEntry { *; }
-keep class com.yywspace.anethack.keybord.CommandRow { *; }
-keep class com.yywspace.anethack.keybord.CommandConfig { *; }

# 原生层通过 GetMethodID 按名字调用这些方法，不能被 R8 重命名。
-keep class com.yywspace.anethack.NetHack { *; }
