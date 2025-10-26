package app.grapekim.smartlotto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.activity.EdgeToEdge;  // 🚨 새로 추가된 import
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import app.grapekim.smartlotto.data.csv.LottoDataLoader;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.util.RoundCache;
import app.grapekim.smartlotto.util.StatisticsUpdateTester;
import app.grapekim.smartlotto.util.AdMobConfigValidator;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 메인 액티비티
 * Navigation Component와 BottomNavigationView를 사용한 주요 화면 관리
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private BottomNavigationView bottomNav;
    private LottoRepository lottoRepository;
    private CsvUpdateManager csvUpdateManager;

    // 안전 영역 처리용 가이드라인
    private Guideline guidelineTopSafe;
    private Guideline guidelineBottomSafe;

    // AI 데이터 정리 완료 체크용 키
    private static final String PREF_AI_DATA_FIXED = "ai_data_fixed_v1";

    // 뒤로가기 두 번 종료 관련 변수
    private static final long BACK_PRESS_INTERVAL = 2000; // 2초
    private long lastBackPressTime = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 🚨 Android 15 권장사항: EdgeToEdge 활성화
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // setContentView 후에 시스템 바 설정 (DecorView 준비 완료 후)
        setupSystemBars();

        // 안전 영역 가이드라인 초기화
        initializeSafeAreaGuidelines();

        // 시스템 UI 인셋 처리
        setupSystemUIInsets();

        try {
            initializeRepository();
            initializeCsvUpdateManager(); // CSV 업데이트 매니저 초기화
            initializeRoundCache(); // 회차 캐시 초기화
            fixAiDataOnce(); // AI 데이터 정리 (한 번만 실행)

            // AdMob 설정 검증 (개발 중에만 실행)
            if (android.util.Log.isLoggable("MainActivity", android.util.Log.DEBUG)) {
                AdMobConfigValidator.validateAdMobConfig(this);
            } else {
                AdMobConfigValidator.logAdMobSummary();
            }

            // 자동 업데이트 체크 (앱 시작 시) - 강제 실행으로 변경
            forceAutoUpdateOnStartup();

            // CSV 데이터 자동 로딩 (필요시 업데이트 포함)
            initializeCsvDataIfNeeded();

            initializeNavigation();
            setupBottomNavigation();
            setupBackPressHandler(); // 뒤로가기 핸들러 설정
            handleIntentExtras(); // Intent extra 처리
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "초기화 실패", e);
            // 네비게이션 초기화 실패 시 앱 종료
            finish();
        }
    }

    /**
     * CSV 업데이트 매니저 초기화
     */
    private void initializeCsvUpdateManager() {
        csvUpdateManager = new CsvUpdateManager(this);
        android.util.Log.d("MainActivity", "CSV 업데이트 매니저 초기화 완료");
    }

    /**
     * 앱 시작 시 주간 자동 업데이트 체크
     */
    private void checkAutoUpdateOnStartup() {
        android.util.Log.d("MainActivity", "=== 주간 자동 업데이트 체크 시작 ===");

        long lastAutoUpdate = csvUpdateManager.getLastAutoUpdateTime();
        int daysUntilNext = csvUpdateManager.getDaysUntilNextUpdate();

        if (lastAutoUpdate > 0) {
            android.util.Log.d("MainActivity", "마지막 자동 업데이트: " + new java.util.Date(lastAutoUpdate));
            android.util.Log.d("MainActivity", "다음 업데이트까지: " + daysUntilNext + "일");
        } else {
            android.util.Log.d("MainActivity", "첫 실행 - 자동 업데이트 이력 없음");
        }

        // 백그라운드에서 체크 및 필요시 업데이트
        csvUpdateManager.checkAndUpdateIfNeeded();
    }

    /**
     * 앱 시작 시 강제 자동 업데이트 실행 (테스트용)
     */
    private void forceAutoUpdateOnStartup() {
        android.util.Log.i("MainActivity", "=== 강제 자동 업데이트 실행 ===");

        long lastAutoUpdate = csvUpdateManager.getLastAutoUpdateTime();

        if (lastAutoUpdate > 0) {
            android.util.Log.i("MainActivity", "마지막 자동 업데이트: " + new java.util.Date(lastAutoUpdate));
        } else {
            android.util.Log.i("MainActivity", "첫 실행 - 자동 업데이트 이력 없음");
        }

        // 항상 업데이트 시도 (강제)
        android.util.Log.i("MainActivity", "최신 데이터 확인을 위해 강제 업데이트 시작...");
        csvUpdateManager.checkAndUpdateIfNeeded();

        // 5초 후 추가로 강제 업데이트 (확실히 하기 위해)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.i("MainActivity", "=== 추가 강제 업데이트 실행 ===");

            new Thread(() -> {
                try {
                    boolean success = csvUpdateManager.forceUpdateCsvFile();
                    android.util.Log.i("MainActivity", "추가 업데이트 결과: " + (success ? "성공" : "실패"));
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "추가 업데이트 오류", e);
                }
            }).start();
        }, 5000);
    }

    /**
     * 시스템 바 설정 (상태바, 네비게이션바)
     * Android 15+는 EdgeToEdge.enable()이 처리하므로 조건부 적용
     */
    private void setupSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        // Android 15(API 35) 미만에서만 수동 설정 (중단된 API 회피)
        if (Build.VERSION.SDK_INT < 35) {
            // minSdk가 26이므로 LOLLIPOP 체크 불필요 (항상 true)
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);

            // 에지 투 에지 모드 활성화
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            } else {
                View decorView = window.getDecorView();
                // decorView null 체크는 실제로 항상 false이므로 제거
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }
        }
        // Android 15+는 EdgeToEdge.enable()이 모든 것을 처리

        // 상태바 텍스트 색상 설정
        updateSystemBarAppearance();
    }

    /**
     * 안전 영역 가이드라인 초기화
     */
    private void initializeSafeAreaGuidelines() {
        guidelineTopSafe = findViewById(R.id.guideline_top_safe);
        guidelineBottomSafe = findViewById(R.id.guideline_bottom_safe);
    }

    /**
     * 시스템 UI 인셋 처리
     */
    private void setupSystemUIInsets() {
        View rootView = findViewById(android.R.id.content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsets.Type.systemBars());
                Insets displayCutout = windowInsets.getInsets(WindowInsets.Type.displayCutout());

                // 상단 안전 영역 계산 (상태바 + 노치)
                int topInset = Math.max(systemBars.top, displayCutout.top);
                // 하단 안전 영역 계산 (네비게이션바 + 제스처바)
                int bottomInset = Math.max(systemBars.bottom, displayCutout.bottom);

                // 가이드라인 위치 조정
                updateGuidelines(topInset, bottomInset);

                // BottomNavigationView에 하단 패딩 적용
                if (bottomNav != null) {
                    bottomNav.setPadding(
                        bottomNav.getPaddingLeft(),
                        bottomNav.getPaddingTop(),
                        bottomNav.getPaddingRight(),
                        bottomInset
                    );
                }

                return WindowInsets.CONSUMED;
            });
        } else {
            // Android 10 이하 호환성
            adjustLayoutForSystemUI();
        }
    }

    /**
     * 가이드라인 위치 업데이트
     */
    private void updateGuidelines(int topInset, int bottomInset) {
        if (guidelineTopSafe != null) {
            ConstraintLayout.LayoutParams topParams =
                    (ConstraintLayout.LayoutParams) guidelineTopSafe.getLayoutParams();
            topParams.guideBegin = topInset;
            guidelineTopSafe.setLayoutParams(topParams);
        }

        if (guidelineBottomSafe != null) {
            ConstraintLayout.LayoutParams bottomParams =
                    (ConstraintLayout.LayoutParams) guidelineBottomSafe.getLayoutParams();
            bottomParams.guideEnd = bottomInset;
            guidelineBottomSafe.setLayoutParams(bottomParams);
        }
    }

    /**
     * 시스템 바 텍스트 색상 업데이트 (다크모드 대응)
     */
    private void updateSystemBarAppearance() {
        Window window = getWindow();
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (isDarkMode()) {
                    // 다크모드: 상태바/네비바 텍스트를 밝게
                    controller.setSystemBarsAppearance(0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                } else {
                    // 라이트모드: 상태바/네비바 텍스트를 어둡게
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            }
        } else {
            // Android R 미만에서만 deprecated API 사용
            updateSystemBarAppearanceOld();
        }
    }

    /**
     * Android R 미만에서 시스템 바 외관 업데이트 (deprecated API 사용)
     */
    @SuppressWarnings("deprecation")
    private void updateSystemBarAppearanceOld() {
        // Android 15+ 에서는 중단된 API이므로 사용하지 않음
        if (Build.VERSION.SDK_INT >= 35) {
            return;
        }

        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();
        // decorView null 체크는 실제로 항상 false이므로 제거

        int flags = decorView.getSystemUiVisibility();

        if (isDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            // minSdk가 26이므로 O(API 26) 체크 불필요 (항상 true)
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }

        decorView.setSystemUiVisibility(flags);
    }

    /**
     * Android 10 이하 시스템 UI 조정
     */
    private void adjustLayoutForSystemUI() {
        View rootView = findViewById(android.R.id.content);

        // ViewCompat을 사용한 안전한 인셋 처리
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView,
                (view, windowInsets) -> {
                    androidx.core.graphics.Insets systemBars = windowInsets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars());

                    updateGuidelines(systemBars.top, systemBars.bottom);

                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });
    }

    /**
     * 다크모드 확인
     */
    private boolean isDarkMode() {
        int currentMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Repository 초기화
     */
    private void initializeRepository() {
        lottoRepository = new LottoRepositoryImpl(this);
    }

    /**
     * 회차 캐시 초기화 (백그라운드에서 최신 회차 정보 로드)
     */
    private void initializeRoundCache() {
        try {
            RoundCache.getInstance().initialize(this);
            android.util.Log.d("MainActivity", "회차 캐시 초기화 완료: " +
                    RoundCache.getInstance().getCacheInfo());
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "회차 캐시 초기화 실패", e);
        }
    }

    /**
     * CSV 데이터 자동 로딩 (3개월 자동 업데이트 고려)
     */
    private void initializeCsvDataIfNeeded() {
        new Thread(() -> {
            try {
                android.util.Log.d("CSV_INIT", "CSV 데이터 초기화 시작...");

                // 3개월 주기가 아닌 경우에만 필요시 최신 데이터 확인
                boolean shouldCheckGitHub = true;

                // 최근에 자동 업데이트가 있었다면 GitHub 체크 생략
                long lastAutoUpdate = csvUpdateManager.getLastAutoUpdateTime();
                if (lastAutoUpdate > 0) {
                    long timeSinceUpdate = System.currentTimeMillis() - lastAutoUpdate;
                    long daysSinceUpdate = timeSinceUpdate / (24 * 60 * 60 * 1000L);

                    if (daysSinceUpdate < 7) { // 7일 이내 자동 업데이트가 있었다면
                        shouldCheckGitHub = false;
                        android.util.Log.d("CSV_INIT", "최근 자동 업데이트 있음 (" + daysSinceUpdate + "일 전) - GitHub 체크 생략");
                    }
                }

                boolean updated = false;
                if (shouldCheckGitHub) {
                    // GitHub에서 최신 데이터 확인
                    android.util.Log.d("CSV_INIT", "GitHub에서 최신 CSV 데이터 확인 시작...");
                    updated = csvUpdateManager.updateCsvFile();

                    if (updated) {
                        android.util.Log.d("CSV_INIT", "GitHub에서 최신 데이터 업데이트 완료");
                    } else {
                        android.util.Log.d("CSV_INIT", "GitHub 업데이트 실패 또는 불필요");
                    }
                }

                // DB 상태 확인
                int dbTotalCount = lottoRepository.getTotalDrawCount();
                Integer dbLatestRound = lottoRepository.getLatestDrawNumber();

                // CSV 최신 회차 확인 (업데이트된 파일에서)
                int csvLatestRound = getCsvLatestRound();

                android.util.Log.d("CSV_INIT", "DB 최신 회차: " + dbLatestRound + ", CSV 최신 회차: " + csvLatestRound);

                boolean needsUpdate = false;
                String updateReason = "";

                if (dbTotalCount == 0) {
                    needsUpdate = true;
                    updateReason = "DB가 비어있음";
                } else if (dbLatestRound == null || csvLatestRound > dbLatestRound) {
                    needsUpdate = true;
                    updateReason = "CSV에 더 최신 데이터 있음 (" + dbLatestRound + " → " + csvLatestRound + ")";
                }

                if (needsUpdate) {
                    android.util.Log.d("CSV_INIT", "CSV 로딩 시작: " + updateReason);

                    // 기존 데이터 삭제 후 전체 재로딩
                    android.util.Log.d("CSV_INIT", "기존 AI 데이터 삭제 중...");
                    lottoRepository.clearDrawHistory();
                    lottoRepository.clearNumberStatistics();
                    lottoRepository.clearNumberPairs();

                    // CSV 데이터 로딩
                    LottoDataLoader loader = new LottoDataLoader(this, lottoRepository);
                    boolean success = loader.loadLottoDataSync();

                    if (success) {
                        android.util.Log.d("CSV_INIT", "CSV 로딩 완료! 새로운 분석 데이터 준비됨");

                        // UI 스레드에서 완료 처리 및 리스너 통지
                        runOnUiThread(() -> {
                            android.util.Log.d("CSV_INIT", "분석 탭 데이터 업데이트 완료!");

                            // CSV 업데이트 매니저를 통해 리스너들에게 통지
                            if (csvUpdateManager != null) {
                                // 약간의 지연 후 통지 (Fragment들이 완전히 초기화된 후)
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    android.util.Log.i("MainActivity", "=== 자동 통계 업데이트 테스트 시작 ===");
                                    android.util.Log.i("MainActivity", "앱 시작 후 CSV 데이터 로딩 완료, 리스너들에게 통지");
                                    csvUpdateManager.notifyUpdateListeners(true);

                                    // 5초 후 추가 테스트 (사용자가 다른 탭으로 이동했을 수도 있음)
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                        android.util.Log.i("MainActivity", "=== 추가 자동 업데이트 테스트 ===");
                                        csvUpdateManager.notifyUpdateListeners(true);

                                        // 종합 테스트 실행 (10초 후)
                                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                            android.util.Log.i("MainActivity", "=== 통계 업데이트 시스템 종합 테스트 시작 ===");
                                            StatisticsUpdateTester.logCsvUpdateManagerStatus(csvUpdateManager);
                                            StatisticsUpdateTester.runComprehensiveTest(MainActivity.this);

                                            // 강제 CSV 업데이트 실행 (15초 후)
                                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                                android.util.Log.i("MainActivity", "=== 강제 CSV 업데이트 실행 ===");
                                                android.util.Log.i("MainActivity", "GitHub에서 최신 데이터 가져오는 중...");

                                                new Thread(() -> {
                                                    try {
                                                        boolean updateSuccess = csvUpdateManager.forceUpdateCsvFile();

                                                        runOnUiThread(() -> {
                                                            if (updateSuccess) {
                                                                android.util.Log.i("MainActivity", "✅ CSV 업데이트 성공! 통계가 자동으로 갱신됩니다.");
                                                            } else {
                                                                android.util.Log.e("MainActivity", "❌ CSV 업데이트 실패");
                                                            }
                                                        });
                                                    } catch (Exception e) {
                                                        android.util.Log.e("MainActivity", "CSV 업데이트 중 오류", e);
                                                    }
                                                }).start();
                                            }, 5000);
                                        }, 5000);
                                    }, 5000);
                                }, 1000); // 1초 지연
                            }
                        });
                    } else {
                        android.util.Log.e("CSV_INIT", "CSV 로딩 실패");
                    }

                    loader.shutdown();
                } else {
                    android.util.Log.d("CSV_INIT", "최신 상태 유지 - 업데이트 불필요");
                }

            } catch (Exception e) {
                android.util.Log.e("CSV_INIT", "CSV 초기화 실패", e);
            }
        }).start();
    }

    /**
     * CSV 파일의 최신 회차 번호 확인 (GitHub 업데이트된 파일에서)
     */
    private int getCsvLatestRound() {
        try {
            // GitHub에서 업데이트된 파일 사용
            File csvFile = csvUpdateManager.getCsvFile();
            FileInputStream fis = new FileInputStream(csvFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));

            int maxRound = 0;
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false; // 헤더 스킵
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue; // 빈 줄 스킵
                }

                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    try {
                        int round = Integer.parseInt(parts[1].trim());
                        if (round > maxRound) {
                            maxRound = round;
                        }
                    } catch (NumberFormatException e) {
                        // 파싱 실패한 라인은 무시
                    }
                }
            }

            reader.close();
            fis.close();

            android.util.Log.d("CSV_INIT", "CSV 최신 회차 확인 완료: " + maxRound);
            return maxRound;

        } catch (Exception e) {
            android.util.Log.e("CSV_INIT", "CSV 최신 회차 확인 실패", e);

            // 실패 시 assets에서 확인 (fallback)
            try {
                android.content.res.AssetManager assets = getAssets();
                java.io.InputStream is = assets.open("draw_kor.csv");
                java.io.BufferedReader assetReader = new java.io.BufferedReader(new java.io.InputStreamReader(is));

                int maxRound = 0;
                String line;
                boolean isFirstLine = true;

                while ((line = assetReader.readLine()) != null) {
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        try {
                            int round = Integer.parseInt(parts[1].trim());
                            if (round > maxRound) {
                                maxRound = round;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                assetReader.close();
                is.close();

                android.util.Log.d("CSV_INIT", "Assets에서 CSV 최신 회차 확인: " + maxRound);
                return maxRound;

            } catch (Exception fallbackError) {
                android.util.Log.e("CSV_INIT", "Assets CSV 최신 회차 확인도 실패", fallbackError);
                return 0;
            }
        }
    }

    /**
     * AI 데이터 정리 (한 번만 실행)
     * "[수정]" -> "[AI]" 변경 및 "합계" 형식을 "회차" 형식으로 변경
     */
    private void fixAiDataOnce() {
        SharedPreferences prefs = getSharedPreferences("lotto_app_prefs", MODE_PRIVATE);
        boolean isAlreadyFixed = prefs.getBoolean(PREF_AI_DATA_FIXED, false);

        if (!isAlreadyFixed) {
            try {
                // AI 데이터 정리 실행
                lottoRepository.updateAiMethodLabels();

                // 완료 표시
                prefs.edit().putBoolean(PREF_AI_DATA_FIXED, true).apply();

                // 로그 출력 (옵션)
                android.util.Log.d("MainActivity", "AI 데이터 정리 완료: [수정] -> [AI], 합계 -> 회차 형식 변경");

            } catch (Exception e) {
                // 에러 발생 시 로그만 출력하고 앱은 계속 실행
                android.util.Log.e("MainActivity", "AI 데이터 정리 실패", e);
            }
        }
    }

    /**
     * Navigation Component 초기화
     */
    private void initializeNavigation() {
        // NavHost 안전 참조
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);

        if (host == null) {
            // 드물게 복원 과정에서 null일 수 있으므로 예외 발생
            throw new IllegalStateException("NavHostFragment not found");
        }

        navController = host.getNavController();
    }

    /**
     * BottomNavigationView 설정
     */
    private void setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_nav);

        if (bottomNav == null) {
            throw new IllegalStateException("BottomNavigationView not found");
        }

        // Navigation과 BottomNav 연결
        NavigationUI.setupWithNavController(bottomNav, navController);

        // 같은 탭 재선택 시 스택 리셋/중복 내비게이션 방지 (람다 표현식 간소화)
        bottomNav.setOnItemReselectedListener(item -> {
            // 현재 화면 유지 - 필요시 Fragment 스택 리셋 로직 추가 가능
        });
    }

    /**
     * Intent extra 처리
     * QR 결과 화면에서 저장 후 특정 탭으로 이동하는 기능 지원
     */
    private void handleIntentExtras() {
        Intent intent = getIntent();
        if (intent == null) return;

        // "tab" extra로 특정 탭 지정 가능
        String targetTab = intent.getStringExtra("tab");
        if (targetTab != null) {
            navigateToTab(targetTab);
        }
    }

    /**
     * 특정 탭으로 이동
     * @param tabName 탭 이름 ("history", "home", "generator" 등)
     */
    private void navigateToTab(String tabName) {
        if (navController == null || bottomNav == null) return;

        try {
            int destinationId = getDestinationIdForTab(tabName);
            if (destinationId != -1) {
                // NavController로 해당 destination으로 이동
                navController.navigate(destinationId);

                // BottomNavigationView의 선택 상태도 업데이트
                bottomNav.setSelectedItemId(getMenuItemIdForTab(tabName));
            }
        } catch (Exception e) {
            // 네비게이션 실패 시 조용히 무시 (기본 탭 유지)
        }
    }

    /**
     * 탭 이름에 따른 Navigation destination ID 반환
     * @param tabName 탭 이름
     * @return destination ID
     */
    private int getDestinationIdForTab(String tabName) {
        switch (tabName.toLowerCase()) {
            case "history":
                return R.id.historyFragment;
            case "home":
                return R.id.homeFragment;
            case "favorites":
                return R.id.favoritesFragment;
            case "settings":
                return R.id.settingsFragment;
            default:
                return -1; // 알 수 없는 탭
        }
    }

    /**
     * 탭 이름에 따른 BottomNavigationView 메뉴 아이템 ID 반환
     * @param tabName 탭 이름
     * @return 메뉴 아이템 ID
     */
    private int getMenuItemIdForTab(String tabName) {
        switch (tabName.toLowerCase()) {
            case "history":
                return R.id.historyFragment;
            case "home":
                return R.id.homeFragment;
            case "favorites":
                return R.id.favoritesFragment;
            case "settings":
                return R.id.settingsFragment;
            default:
                return -1; // 알 수 없는 탭
        }
    }

    /**
     * 새 Intent 처리 (앱이 이미 실행 중일 때)
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 새 Intent로 업데이트
        handleIntentExtras(); // 새 Intent의 extra 처리
    }

    /**
     * 공개 메서드: 외부에서 탭 전환 요청 시 사용
     * 사용 예: MainActivity.switchToTab("history")
     * @param tabName 이동할 탭 이름
     */
    @SuppressWarnings("unused")
    public void switchToTab(String tabName) {
        navigateToTab(tabName);
    }

    /**
     * 히스토리 탭으로 직접 이동하는 편의 메서드
     * 사용 예: QR 스캔 후 결과를 히스토리에 저장하고 해당 탭으로 이동
     */
    @SuppressWarnings("unused")
    public void switchToHistoryTab() {
        navigateToTab("history");
    }

    /**
     * 공유 CSV 업데이트 매니저 반환
     * Fragment들이 같은 인스턴스를 사용하도록 보장
     */
    public CsvUpdateManager getCsvUpdateManager() {
        return csvUpdateManager;
    }

    @Override
    public boolean onSupportNavigateUp() {
        // 상단 AppBar의 뒤로가기 버튼 처리
        // 현재는 ActionBar를 사용하지 않으므로 기본 동작 유지
        return navController != null && navController.navigateUp() || super.onSupportNavigateUp();
    }

    /**
     * 뒤로가기 버튼 핸들러 설정 (Android 13+ 대응)
     */
    private void setupBackPressHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // NavController가 백스택을 처리할 수 있는지 확인
                if (navController != null && navController.getCurrentDestination() != null) {
                    int currentDestId = navController.getCurrentDestination().getId();

                    // 홈 화면(시작 destination)에서만 두 번 뒤로가기로 종료
                    if (currentDestId == R.id.homeFragment) {
                        long currentTime = System.currentTimeMillis();

                        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
                            // 2초 이내에 다시 눌렀으면 앱 종료
                            android.util.Log.d("MainActivity", "두 번째 뒤로가기 - 앱 종료");
                            finish();
                        } else {
                            // 첫 번째 뒤로가기 - 토스트 메시지 표시
                            lastBackPressTime = currentTime;
                            android.util.Log.d("MainActivity", "첫 번째 뒤로가기 - 토스트 표시");
                            android.widget.Toast.makeText(MainActivity.this,
                                    "뒤로가기 버튼을 한 번 더 누르면 종료됩니다.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // 다른 화면에서는 네비게이션 백스택 처리
                        android.util.Log.d("MainActivity", "다른 화면에서 뒤로가기 - 네비게이션 처리");
                        if (navController.popBackStack()) {
                            // 성공적으로 백스택 팝
                        } else {
                            // 더 이상 백스택이 없으면 앱 종료
                            finish();
                        }
                    }
                } else {
                    // NavController가 없으면 앱 종료
                    finish();
                }
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 명시적인 정리 작업
        navController = null;
        bottomNav = null;
        lottoRepository = null;
        guidelineTopSafe = null;
        guidelineBottomSafe = null;
        csvUpdateManager = null;
    }
}