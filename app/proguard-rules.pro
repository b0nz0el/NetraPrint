# PrinterJTM ProGuard / R8 Rules

# Preserve Line Numbers and Source File Attributes for accurate crash debugging
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Keep Application components required by OS & reflection
-keep class com.david.printerjtm.PrinterApplication { *; }
-keep class com.david.printerjtm.PrinterJtmPrintService { *; }
-keepclassmembers enum com.david.printerjtm.** { *; }

# Google ML Kit Barcode Scanning & Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.**

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Lottie Animation
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
# Tink's consumer protobuf.pro rule is applied from tink-android automatically.
# It keeps GeneratedMessageLite fields used by its reflective protobuf parser.
# Crypto and ESC/POS implementations are directly referenced; avoid blanket keeps.

# Google Play In-App Review
-keep class com.google.android.play.core.review.** { *; }
-keep class com.google.android.play.core.common.** { *; }
-dontwarn com.google.android.play.core.**

# AndroidX WebKit
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
