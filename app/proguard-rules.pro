# Proguard rules for FriendTalk
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Three Junks calls: org.webrtc is called from native code via JNI
-keep class org.webrtc.** { *; }
-keep class com.threejunks.sdk.** { *; }
