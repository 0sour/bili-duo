-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Retrofit reads each service method's generic return type (ApiResponse<T>) at
# runtime via reflection. If R8 strips the interface method signature the app
# crashes with "Class cannot be cast to ParameterizedType" only in release.
-keep interface cn.xfangfang.biliduo.data.api.** { *; }

# Suspend service methods carry the real response type in Continuation<? super T>.
# Keep its generic signature so Retrofit can unwrap ApiResponse<T> in release.
-keep class kotlin.coroutines.Continuation { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# DownloadManager persists List<DownloadInfo> through an anonymous
# TypeToken<List<DownloadInfo>>. R8 strips the anonymous subclass' generic
# superclass in release, causing "Missing type parameter" and an empty
# download list; keep TypeToken and all of its subclasses.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

-keep class cn.xfangfang.biliduo.data.model.** { *; }

-keep class io.noties.markwon.** { *; }
-keep class dev.jeziellago.compose.markdowntext.** { *; }
