# Add project specific ProGuard rules here.

# Keep MediaPipe generated code.
-keep class com.google.mediapipe.** { *; }

# Room
-keep class androidx.room.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
