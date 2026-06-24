# JBullet 物理引擎
-keep class com.bulletphysics.** { *; }
-dontwarn com.bulletphysics.**

# OpenGL 相关
-keep class javax.vecmath.** { *; }
-dontwarn javax.vecmath.**

# Kotlin 序列化
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# 保持 R8 完整模式
-keep class com.drivingsim.** { *; }
