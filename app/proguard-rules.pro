# ============================================================
# SentinalChat ProGuard / R8 Rules
# ============================================================

# -------- Crypto & Protocol (never strip) --------
-keep class com.hacksecure.p2p.crypto.** { *; }
-keep class com.hacksecure.p2p.Protocol.** { *; }
-keep class com.hacksecure.p2p.security.** { *; }
-keep class com.hacksecure.p2p.identity.** { *; }

# -------- Serialization models (field names matter) --------
-keep class com.hacksecure.p2p.messaging.models.** { *; }
-keep class com.hacksecure.p2p.session.** { *; }
-keep class com.hacksecure.p2p.storage.** { *; }

# -------- ZXing (QR code library) --------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# -------- Gson (if used for JSON serialization) --------
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# -------- CameraX --------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# -------- General Android --------
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Don't strip exception info (needed for meaningful error logs)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
