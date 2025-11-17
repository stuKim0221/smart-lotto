plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.grapekim.smartlotto"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            // 키스토어 정보 - local.properties에서 읽어옴
            val keystorePath = project.findProperty("MYAPP_RELEASE_STORE_FILE") as String?
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = project.findProperty("MYAPP_RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("MYAPP_RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("MYAPP_RELEASE_KEY_PASSWORD") as String?
            } else {
                // 키스토어 파일이 없으면 디버그 키로 서명 (개발용)
                // 프로덕션 릴리즈 시에는 local.properties에 키스토어 정보 필수
                logger.warn("⚠️  릴리즈 키스토어를 찾을 수 없습니다. 디버그 키로 서명합니다.")
            }
        }
    }

    defaultConfig {
        applicationId = "app.grapekim.smartlotto"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "1.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        debug {
            // 모든 테스트용 AdMob ID들 (Google 제공 테스트 ID)
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            manifestPlaceholders["admobAppId"] ="ca-app-pub-3940256099942544~3347511713"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 릴리즈 빌드에 키스토어 적용 (키스토어가 설정된 경우에만)
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            } else {
                // 키스토어가 없으면 디버그 서명 사용
                signingConfig = signingConfigs.getByName("debug")
            }

            // 실제 프로덕션 AdMob ID들
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-5416988082526174/7410355538\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-5416988082526174/9714188894\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-5416988082526174~7214365675\"")
            manifestPlaceholders["admobAppId"] ="ca-app-pub-5416988082526174~7214365675"
        }
    }

    // ==================== 패키징 설정 ====================
    // ZXing은 순수 자바 라이브러리이므로 16KB 페이지 크기 문제 없음
    packaging {
        resources {
            excludes.addAll(listOf("/META-INF/{AL2.0,LGPL2.1}"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation("com.google.android.material:material:1.12.0") // Material 3
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // ===== ViewModel & Fragment (최신 버전) =====
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.common.java8)

    // ===== RecyclerView & CardView =====
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    // ===== ViewPager2 for Carousel =====
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // ===== Room 데이터베이스 =====
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.work.runtime)

    // --- 네트워크 ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // --- CameraX 제거 ---
    // ZXing 스캐너가 자체 카메라 구현 제공하므로 불필요
    // implementation(libs.camera.core)
    // implementation(libs.camera.camera2)
    // implementation(libs.camera.lifecycle)
    // implementation(libs.camera.view)

    // --- ZXing: QR 코드 스캐너 ---
    // 순수 자바 기반 (네이티브 라이브러리 없음)
    // Android 15+ 16KB 페이지 크기 완벽 지원
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // --- Google Play Services ---
    implementation(libs.play.services.ads)

    // --- 기타 라이브러리 ---
    implementation(libs.guava)
    implementation(libs.opencsv)
}