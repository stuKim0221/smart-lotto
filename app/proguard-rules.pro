# SmartLotto 앱 ProGuard 규칙

# 디버깅 정보 유지
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ======================== Retrofit & Gson ========================
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# API DTO 클래스
-keep class app.grapekim.smartlotto.data.remote.dto.** { *; }

# ======================== Room Database ========================
-keep class app.grapekim.smartlotto.data.local.room.entity.** { *; }
-keep class app.grapekim.smartlotto.data.local.room.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ======================== AdMob ========================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ======================== CameraX & ML Kit ========================
-keep class androidx.camera.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# ======================== Material Design ========================
-keep class com.google.android.material.** { *; }

# ======================== Android 컴포넌트 ========================
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity

-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ======================== 기본 Java/Android ========================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ======================== 외부 라이브러리 ========================
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep class com.opencsv.** { *; }
-dontwarn com.google.common.**
-keep class com.google.common.** { *; }

# ======================== 앱 특화 클래스 ========================
-keep class app.grapekim.smartlotto.BuildConfig { *; }
-keep class app.grapekim.smartlotto.notify.** { *; }
-keep class app.grapekim.smartlotto.util.QrLottoParser { *; }

# ======================== 경고 억제 ========================
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*