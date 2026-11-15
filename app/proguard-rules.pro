# Ben's Sandbox optimizer rules.
# Goal: let R8 speed up Jetpack Compose and the other libraries, while leaving the
# sandbox engine exactly as it is (it relies heavily on reflection).

# Never rename anything (keeps reflection and crash logs readable)
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

# --- Sandbox engine (NewBlackbox) and its helpers: keep everything ---
-keep class top.niunaijun.** { *; }
-keep interface top.niunaijun.** { *; }
-keep class black.** { *; }
-keep interface black.** { *; }
-keep class mirror.** { *; }
-keep class android.** { *; }
-keep interface android.** { *; }
-keep class com.android.** { *; }
-keep class me.weishu.** { *; }
-keep class com.moandjiezana.toml.** { *; }
-keep class com.google.gson.** { *; }
-keep class * implements android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }
-keep class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep @top.niunaijun.blackreflection.annotation.BClass class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassName class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassNameNotProcess class * { *; }

# --- Ben's Sandbox's own code: small, keep it all ---
-keep class com.example.** { *; }

# Native methods anywhere
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn **
