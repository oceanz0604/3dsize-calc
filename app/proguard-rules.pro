# Add project specific ProGuard rules here.

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep ARCore classes
-keep class com.google.ar.** { *; }
-dontwarn com.google.ar.**

# Keep SceneView classes
-keep class io.github.sceneview.** { *; }
-dontwarn io.github.sceneview.**

# Keep Filament classes
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Navigation classes
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# Keep Coil classes
-keep class coil.** { *; }
-dontwarn coil.**
