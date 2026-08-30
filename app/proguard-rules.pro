# Keep Attributes for Annotations & Reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Google ARCore Keep Rules
-keep class com.google.ar.core.** { *; }
-keep class com.google.ar.core.annotations.** { *; }
-dontwarn com.google.ar.core.**

# Google Filament & Sceneview Keep Rules
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.android.filament.**
-dontwarn io.github.sceneview.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# 3D Math & Models
-keep class com.example.math3d.** { *; }
-keep class com.example.engine.** { *; }
