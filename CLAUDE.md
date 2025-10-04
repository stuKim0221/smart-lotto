# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고할 가이드를 제공합니다.

## 개발 환경 개요

Java와 Android SDK로 구축된 안드로이드 로또 애플리케이션("Smart Lotto")입니다. 이 앱은 로또 데이터를 분석하고, 번호 예측을 생성하며, 로또 용지의 QR 코드 스캔 기능을 포함합니다.

### 프로젝트 구조

- **app.grapekim.smartlotto**: 모든 애플리케이션 코드를 포함하는 메인 패키지
- **data/**: Room 데이터베이스, 저장소, 네트워크 서비스, CSV 데이터 관리를 포함한 데이터 계층
- **ui/**: 기능별로 구성된 UI 계층 (home, settings, qr, analysis 등)
- **util/**: 번호 분석, 파싱, 헬퍼 함수를 위한 유틸리티 클래스
- **notify/**: 알림 및 리마인더 스케줄링 시스템

## 일반적인 개발 명령어

### 안드로이드 프로젝트 명령어
```bash
# 프로젝트 빌드 (디버그 버전)
./gradlew assembleDebug

# 릴리즈 APK 빌드
./gradlew assembleRelease

# 유닛 테스트 실행
./gradlew test

# 계측 테스트 실행
./gradlew connectedAndroidTest

# 빌드 아티팩트 정리
./gradlew clean

# 연결된 기기에 디버그 APK 설치
./gradlew installDebug
```

### 개발 작업
```bash
# 테스트용 빌드 및 설치
./gradlew clean assembleDebug installDebug

# 서명된 릴리즈 빌드 생성 (키스토어 설정 필요)
./gradlew assembleRelease

# 특정 테스트 클래스 실행
./gradlew test --tests "ClassName"
```

## 기술 아키텍처

### 핵심 아키텍처
- **MVVM 패턴**: ViewModel이 비즈니스 로직을 처리하고, Fragment/Activity가 UI를 처리
- **Repository 패턴**: LottoRepository와 LottoRepositoryImpl을 통한 데이터 계층 추상화
- **Room 데이터베이스**: 엔티티, DAO, 데이터베이스 관리를 통한 로컬 데이터 지속성
- **Navigation Component**: BottomNavigationView를 사용한 Fragment 기반 네비게이션
- **WorkManager**: 데이터 업데이트 및 초기화를 위한 백그라운드 작업

### 주요 컴포넌트
- **MainActivity**: 하단 네비게이션과 엣지 투 엣지 디스플레이가 있는 네비게이션 호스트
- **LottoRepository**: 로컬 Room 데이터베이스와 CSV 데이터를 결합한 중앙 데이터 관리
- **LottoNumberAnalyzer**: 통계 분석 및 번호 예측 알고리즘
- **QrScanActivity**: 로또 용지를 위한 CameraX 기반 QR 코드 스캐닝
- **CsvUpdateManager**: CSV 소스에서 주기적인 로또 데이터 업데이트 관리

### 데이터 관리
- **Room 데이터베이스** (AppDatabase): 로또 추첨 이력, 통계, 사용자 설정 저장
- **CSV 데이터 로딩**: raw 리소스에서 초기 데이터 시딩 및 주기적 업데이트
- **네트워크 계층**: 안전한 OkHttp 클라이언트 설정과 함께 Retrofit 기반 API 호출
- **AdMob 통합**: 테스트/프로덕션 설정이 있는 전면 및 보상형 광고

## 주요 의존성

### Android 프레임워크
- **compileSdk/targetSdk**: 35, **minSdk**: 26
- **Java 버전**: 11 (소스/타겟 호환성)
- **Android Gradle Plugin**: 8.9.0
- **Material Design**: UI 컴포넌트 및 테마

### 주요 라이브러리
- **AndroidX Navigation**: 2.7.7 (Fragment 네비게이션)
- **Room Database**: 2.6.1 (로컬 데이터 지속성)
- **CameraX**: 1.3.4 (QR 코드 스캐닝)
- **ML Kit Barcode**: 17.3.0 (QR 코드 감지)
- **Retrofit**: 2.11.0 (네트워크 요청)
- **WorkManager**: 2.9.0 (백그라운드 작업)
- **Google Play Services**: 지도, 위치, 광고 통합

### 개발 라이브러리
- **Lifecycle**: 2.8.4 (ViewModel, LiveData)
- **Guava**: 31.1-android (유틸리티 함수)
- **OpenCSV**: 5.8 (CSV 데이터 처리)

## 코드 관례
- **Java**: 명확한 패키지 구성을 가진 표준 Android Java 관례
- **데이터베이스**: 적절한 관계와 외래 키를 가진 Room 엔티티
- **네트워킹**: 인증서 피닝이 있는 보안 HTTPS 전용 설정
- **UI**: 적절한 생명주기 관리를 가진 Fragment 기반 아키텍처
- **테스팅**: 유닛 테스트용 JUnit, 계측 테스트용 Espresso

## 빌드 설정
- **디버그 빌드**: 테스트 AdMob ID, 디버깅 활성화, 최적화 없음
- **릴리즈 빌드**: 프로덕션 AdMob ID, ProGuard 최적화, 최적화 활성화
- **패키징**: 16KB 라이브러리 최적화를 위한 레거시 JNI 패키징 (Android 15 호환성)
- **ABI 필터**: armeabi-v7a, arm64-v8a, x86, x86_64 지원

## 개발 참고사항
- **엣지 투 엣지 디스플레이**: 적절한 인셋 처리와 가이드라인 기반 레이아웃으로 구현
- **알림 시스템**: 적절한 채널 관리가 있는 예정된 로또 추첨 리마인더
- **보안**: HTTPS 강제 적용이 있는 보안 네트워크 설정
- **성능**: 부드러운 UI 경험을 위한 최적화된 CSV 로딩 및 데이터베이스 작업