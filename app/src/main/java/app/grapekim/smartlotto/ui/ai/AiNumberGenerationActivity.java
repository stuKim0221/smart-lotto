package app.grapekim.smartlotto.ui.ai;

import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.util.LottoNumberAnalyzer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import app.grapekim.smartlotto.BuildConfig;

// AdMob 리워드 광고 imports
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 번호 생성 액티비티 (Repository 통합 버전)
 * - Repository의 AI 생성 메서드 활용
 * - 리워드 광고 시청 완료 후 AI 번호 생성
 * - 결과를 팝업으로 표시
 * - 저장 후 메인화면으로 이동
 */
public class AiNumberGenerationActivity extends AppCompatActivity {

    private static final String TAG = "AiNumberGeneration";

    // AdMob 리워드 광고 ID (임시 테스트용에서 수정 완료)
    private static final String REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID;

    // Repository
    private LottoRepository lottoRepository;

    // UI 요소들
    private ImageButton btnBack;
    private ProgressBar progressBar;
    private TextView tvCurrentMode;
    private MaterialCardView cardPureStatisticsSettings;
    private MaterialCardView cardCombinationMode;
    private MaterialCardView cardPureStatisticsWarning;

    // 분석 모드 선택
    private RadioGroup rgAnalysisMode;
    private RadioButton rbCombinationMode;
    private RadioButton rbPureStatistics;

    // 조합 모드 전략 체크박스들
    private CheckBox cbPopular, cbNeglected, cbTrend, cbPair;
    private CheckBox cbOddEven, cbZone, cbConsecutive, cbLastDigit, cbVisualPattern;
    private CheckBox cbAvoidance, cbLucky;

    // 순수 통계 모드 전략 체크박스들
    private CheckBox cbPurePopular, cbPureNeglected, cbPureRecent;
    private CheckBox cbPureHighWeight, cbPureBalance;
    private CheckBox cbPureCyclic, cbPureCorrelation, cbPureRegression;

    // 생성 버튼
    private MaterialButton btnGenerate;

    // 결과 UI 요소들
    private LinearLayout layoutResult;
    private TextView tvGenerationResult;
    private TextView tvResultAnalysis;
    private MaterialButton btnSave;

    // 현재 생성된 번호들
    private List<Integer> currentGeneratedNumbers;

    // AdMob 리워드 광고
    private RewardedAd rewardedAd;
    private boolean isAdLoading = false;
    private boolean isGenerating = false;
    private int adLoadRetryCount = 0;
    private static final int MAX_AD_RETRY = 3; // 최대 3회 재시도

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_number_generation);

        initializeRepository();
        initializeViews();
        setupClickListeners();
        setupInitialState();
        initializeAdMob();
    }

    private void initializeRepository() {
        lottoRepository = new LottoRepositoryImpl(this);
    }

    private void initializeViews() {
        // 상단 바
        btnBack = findViewById(R.id.btnBack);

        // 분석 모드 관련 UI
        tvCurrentMode = findViewById(R.id.tvCurrentMode);
        cardPureStatisticsSettings = findViewById(R.id.cardPureStatisticsSettings);
        cardCombinationMode = findViewById(R.id.cardCombinationMode);
        cardPureStatisticsWarning = findViewById(R.id.cardPureStatisticsWarning);

        // 분석 모드 선택
        rgAnalysisMode = findViewById(R.id.rgAnalysisMode);
        rbCombinationMode = findViewById(R.id.rbCombinationMode);
        rbPureStatistics = findViewById(R.id.rbPureStatistics);

        // 조합 모드 전략 체크박스들
        cbPopular = findViewById(R.id.cbPopular);
        cbNeglected = findViewById(R.id.cbNeglected);
        cbTrend = findViewById(R.id.cbTrend);
        cbPair = findViewById(R.id.cbPair);
        cbOddEven = findViewById(R.id.cbOddEven);
        cbZone = findViewById(R.id.cbZone);
        cbConsecutive = findViewById(R.id.cbConsecutive);
        cbLastDigit = findViewById(R.id.cbLastDigit);
        cbVisualPattern = findViewById(R.id.cbVisualPattern);
        cbAvoidance = findViewById(R.id.cbAvoidance);
        cbLucky = findViewById(R.id.cbLucky);

        // 순수 통계 모드 전략 체크박스들
        cbPurePopular = findViewById(R.id.cbPurePopular);
        cbPureNeglected = findViewById(R.id.cbPureNeglected);
        cbPureRecent = findViewById(R.id.cbPureRecent);
        cbPureHighWeight = findViewById(R.id.cbPureHighWeight);
        cbPureBalance = findViewById(R.id.cbPureBalance);
        cbPureCyclic = findViewById(R.id.cbPureCyclic);
        cbPureCorrelation = findViewById(R.id.cbPureCorrelation);
        cbPureRegression = findViewById(R.id.cbPureRegression);

        // 생성 버튼
        btnGenerate = findViewById(R.id.btnGenerate);

        // 진행상황 표시
        progressBar = findViewById(R.id.progressBar);

        // 결과 UI 요소들
        layoutResult = findViewById(R.id.layoutResult);
        tvGenerationResult = findViewById(R.id.tvGenerationResult);
        tvResultAnalysis = findViewById(R.id.tvResultAnalysis);
        btnSave = findViewById(R.id.btnSave);

        // 현재 생성된 데이터 초기화
        currentGeneratedNumbers = new ArrayList<>();
    }

    private void setupClickListeners() {
        // 뒤로가기 버튼
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // AI 생성 버튼 (리워드 광고 시청 후 생성)
        if (btnGenerate != null) {
            btnGenerate.setOnClickListener(v -> showRewardedAdThenGenerate());
        }

        // 저장 버튼
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveNumbers());
        }

        // 분석 모드 라디오 그룹 상태 변화 감지
        if (rgAnalysisMode != null) {
            rgAnalysisMode.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == -1) {
                    // 아무것도 선택되지 않음
                    updateAnalysisModeState(false, false);
                } else if (checkedId == R.id.rbPureStatistics) {
                    // 순수 통계 모드 선택
                    updateAnalysisModeState(true, true);
                } else if (checkedId == R.id.rbCombinationMode) {
                    // 조합 모드 선택
                    updateAnalysisModeState(false, true);
                }
            });
        }
    }

    private void setupInitialState() {
        // 모든 전략 체크박스는 기본적으로 해제 상태
        // (체크박스는 기본값이 false이므로 별도 설정 불필요)

        // 결과 레이아웃 초기 숨김
        if (layoutResult != null) {
            layoutResult.setVisibility(View.GONE);
        }

        // 저장 버튼 초기 비활성화
        if (btnSave != null) {
            btnSave.setEnabled(false);
            btnSave.setText("조건을 모두 만족해주세요");
        }

        // 초기 상태에서는 모든 설정 카드 숨김 (모드 선택 전)
        if (cardPureStatisticsSettings != null) {
            cardPureStatisticsSettings.setVisibility(View.GONE);
        }
        if (cardPureStatisticsWarning != null) {
            cardPureStatisticsWarning.setVisibility(View.GONE);
        }
        if (cardCombinationMode != null) {
            cardCombinationMode.setVisibility(View.GONE);
        }

        // 초기 버튼 상태 설정
        updateButtonState();
    }

    // ==================== AdMob 리워드 광고 관련 메서드들 ====================

    /**
     * AdMob 초기화 및 리워드 광고 로드 - 개선된 로깅 추가
     */
    private void initializeAdMob() {
        Log.d(TAG, "AdMob 초기화 시작...");
        Log.d(TAG, "사용 중인 AdMob App ID: " + BuildConfig.ADMOB_APP_ID);
        Log.d(TAG, "사용 중인 Rewarded Ad ID: " + BuildConfig.ADMOB_REWARDED_ID);

        // AdMob 초기화
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "AdMob 초기화 완료");
            Log.d(TAG, "초기화 상태: " + initializationStatus.getAdapterStatusMap().toString());

            // 첫 번째 광고 로드 시도
            loadRewardedAd();
        });
    }

    /**
     * 리워드 광고 로드 - 네트워크 상태 확인 추가
     */
    private void loadRewardedAd() {
        if (isAdLoading) {
            Log.d(TAG, "광고 로딩 중이므로 건너뜀");
            return;
        }

        // 네트워크 연결 상태 확인
        if (!isNetworkAvailable()) {
            Log.e(TAG, "네트워크 연결 없음 - 광고 로드 불가능");
            // 네트워크 문제는 조용히 처리하고 사용자에게는 버튼 클릭 시에만 알림
            updateButtonState();
            return;
        }

        Log.d(TAG, "광고 로드 시작...");
        isAdLoading = true;
        updateButtonState();
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        isAdLoading = false;
                        adLoadRetryCount = 0; // 성공 시 재시도 카운트 초기화
                        Log.d(TAG, "리워드 광고 로드 성공");
                        setupRewardedAdCallbacks();
                        updateButtonState();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.e(TAG, "===== 리워드 광고 로드 실패 =====");
                        Log.e(TAG, "에러 코드: " + loadAdError.getCode());
                        Log.e(TAG, "에러 메시지: " + loadAdError.getMessage());
                        Log.e(TAG, "에러 도메인: " + loadAdError.getDomain());
                        Log.e(TAG, "에러 원인: " + loadAdError.getCause());
                        Log.e(TAG, "재시도 횟수: " + adLoadRetryCount + "/" + MAX_AD_RETRY);

                        // 일반적인 에러 코드 해석
                        String errorExplanation = getAdErrorExplanation(loadAdError.getCode());
                        Log.e(TAG, "에러 설명: " + errorExplanation);

                        rewardedAd = null;
                        isAdLoading = false;
                        updateButtonState();

                        // 최대 재시도 횟수 체크
                        if (adLoadRetryCount < MAX_AD_RETRY) {
                            adLoadRetryCount++;
                            // 광고 로드 실패 시 자동 재시도 (10초 후)
                            btnGenerate.postDelayed(() -> {
                                if (rewardedAd == null && !isAdLoading) {
                                    Log.d(TAG, "자동 재시도: 광고 다시 로드 시도 (" + adLoadRetryCount + "/" + MAX_AD_RETRY + ")");
                                    loadRewardedAd();
                                }
                            }, 10000);
                        } else {
                            Log.w(TAG, "최대 재시도 횟수 도달 - 광고 없이 진행 가능");
                        }
                    }
                });
    }

    /**
     * 리워드 광고 콜백 설정
     */
    private void setupRewardedAdCallbacks() {
        if (rewardedAd != null) {
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdClicked() {
                    Log.d(TAG, "리워드 광고 클릭됨");
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "리워드 광고 닫힘");
                    rewardedAd = null;
                    updateButtonState();
                    loadRewardedAd(); // 다음 광고 미리 로드
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    Log.e(TAG, "리워드 광고 표시 실패: " + adError.getMessage());
                    Log.e(TAG, "광고 표시 실패 코드: " + adError.getCode());
                    rewardedAd = null;
                    updateButtonState();
                    loadRewardedAd();
                    // 광고 표시 실패 시에만 사용자에게 알림 (이미 버튼을 클릭한 상태)
                    showToast("광고를 불러올 수 없습니다. 다시 시도해주세요.");
                }

                @Override
                public void onAdImpression() {
                    Log.d(TAG, "리워드 광고 노출됨");
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "리워드 광고 표시됨");
                }
            });
        }
    }

    /**
     * 버튼 상태 업데이트 - 항상 클릭 가능하게 유지
     */
    private void updateButtonState() {
        if (btnGenerate != null) {
            // 버튼은 항상 활성화 상태로 유지
            btnGenerate.setEnabled(true);

            if (isGenerating) {
                // AI 번호 생성 중
                btnGenerate.setText("🤖 AI 분석 중...");
            } else if (rewardedAd != null && !isAdLoading) {
                // 광고 로드 완료 상태
                btnGenerate.setText("🎁 광고 보고 AI 번호 받기");
            } else if (isAdLoading && adLoadRetryCount < MAX_AD_RETRY) {
                // 광고 로딩 중 상태 (재시도 중)
                btnGenerate.setText("📱 광고 준비중...");
            } else {
                // 광고 로드 실패 또는 최대 재시도 도달 - 광고 없이도 가능
                btnGenerate.setText("🎁 AI 번호 생성하기");
            }
        }
    }

    /**
     * 리워드 광고 시청 후 AI 번호 생성 - 개선된 버전
     */
    private void showRewardedAdThenGenerate() {
        // 이미 AI 번호 생성 중이면 사용자에게 알림
        if (isGenerating) {
            Log.d(TAG, "이미 AI 번호 생성 중");
            showToast("🤖 이미 AI 번호를 생성 중입니다. 잠시만 기다려주세요...");
            return;
        }

        if (rewardedAd != null && !isAdLoading) {
            Log.d(TAG, "리워드 광고 표시 시작");

            rewardedAd.show(this, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    // 사용자가 광고를 끝까지 시청했을 때만 보상 제공
                    Log.d(TAG, "사용자가 보상을 받았습니다: " + rewardItem.getAmount() + " " + rewardItem.getType());
                    showToast("🎉 광고 시청 완료! AI 번호를 생성합니다.");
                    generateAiNumbers();
                }
            });
        } else if (isAdLoading && adLoadRetryCount < MAX_AD_RETRY) {
            // 광고 로딩 중이면 사용자에게 알림
            Log.d(TAG, "광고 로딩 중 - 사용자에게 안내");
            showToast("📱 광고를 로딩 중입니다. 잠시만 기다려주세요...");
        } else {
            // 광고가 없거나 최대 재시도 도달 - 광고 없이 진행
            if (adLoadRetryCount >= MAX_AD_RETRY) {
                Log.w(TAG, "최대 재시도 도달 - 광고 없이 AI 번호 생성");
                showToast("광고 로드 실패로 광고 없이 진행합니다");
                generateAiNumbers();
            } else {
                // 광고가 없으면 즉시 재로드 시도
                Log.d(TAG, "리워드 광고 없음 - 즉시 재로드 시도");

                // 네트워크 상태 먼저 확인
                if (!isNetworkAvailable()) {
                    showToast("📶 인터넷 연결 없음 - 광고 없이 진행합니다");
                    generateAiNumbers();
                    return;
                }

                showToast("🔄 광고를 로드합니다. 잠시만 기다려주세요...");

                if (!isAdLoading && adLoadRetryCount < MAX_AD_RETRY) {
                    loadRewardedAd(); // 광고 즉시 재로드 시도
                }

                // 3초 후 다시 확인
                btnGenerate.postDelayed(() -> {
                    if (rewardedAd != null && !isAdLoading) {
                        Log.d(TAG, "광고 로드 완료 - 자동 재시도");
                        showToast("✅ 광고가 준비되었습니다! 다시 버튼을 눌러주세요.");
                    } else if (!isAdLoading) {
                        // 광고 로드 실패 - 광고 없이 진행
                        showToast("광고 없이 AI 번호를 생성합니다");
                        generateAiNumbers();
                    }
                }, 3000);
            }
        }
    }

    // ==================== AI 번호 생성 로직 (Repository 활용) ====================

    /**
     * Repository의 AI 생성 메서드를 활용한 번호 생성 (비동기)
     */
    private void generateAiNumbers() {
        isGenerating = true;
        showProgress(true);
        Log.d(TAG, "=== AI 번호 생성 시작 ===");

        // 선택된 전략 분석
        List<String> strategies = getSelectedStrategies();
        Log.d(TAG, "선택된 전략들: " + strategies);

        if (strategies.isEmpty()) {
            Log.d(TAG, "전략이 선택되지 않음 - 기본 AI 추천 사용");
            showToast("전략을 선택하지 않으면 기본 AI 추천을 사용합니다.");
            // 기본 전략으로 인기번호 + 트렌드 사용
            strategies.add("인기번호");
            strategies.add("트렌드");
        }

        // 백그라운드에서 AI 번호 생성 (비동기)
        new Thread(() -> {
            try {
                // Repository의 AI 번호 생성 메서드 호출
                List<List<Integer>> generatedNumbersList = lottoRepository.generateAiNumbers(strategies, 1);

                if (generatedNumbersList == null || generatedNumbersList.isEmpty()) {
                    throw new RuntimeException("Repository에서 번호 생성 실패");
                }

                List<Integer> generatedNumbers = generatedNumbersList.get(0);
                Log.d(TAG, "생성된 번호: " + generatedNumbers);

                // UI 스레드에서 결과 처리
                runOnUiThread(() -> {
                    try {
                        currentGeneratedNumbers.clear();
                        currentGeneratedNumbers.addAll(generatedNumbers);

                        // UI에 결과 표시
                        showResultInUI(currentGeneratedNumbers, strategies);

                        isGenerating = false;
                        showProgress(false);
                        Log.d(TAG, "=== AI 번호 생성 완료 ===");

                    } catch (Exception uiError) {
                        Log.e(TAG, "UI 업데이트 중 오류: " + uiError.getMessage(), uiError);
                        handleGenerationError(uiError, strategies);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "AI 번호 생성 중 오류: " + e.getMessage(), e);

                // UI 스레드에서 오류 처리
                runOnUiThread(() -> handleGenerationError(e, strategies));
            }
        }).start();
    }

    /**
     * 생성 오류 처리
     */
    private void handleGenerationError(Exception e, List<String> strategies) {
        isGenerating = false;
        showProgress(false);

        if (currentGeneratedNumbers.isEmpty()) {
            Log.d(TAG, "Fallback: 기본 번호 생성 시도");
            generateFallbackNumbers();
        } else {
            showToast("번호 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * Repository 실패 시 fallback 번호 생성
     */
    private void generateFallbackNumbers() {
        try {
            Log.d(TAG, "Fallback 번호 생성 시작");

            // 1~45 중에서 랜덤하게 6개 선택
            List<Integer> allNumbers = new ArrayList<>();
            for (int i = 1; i <= 45; i++) {
                allNumbers.add(i);
            }
            java.util.Collections.shuffle(allNumbers);

            currentGeneratedNumbers.clear();
            for (int i = 0; i < 6; i++) {
                currentGeneratedNumbers.add(allNumbers.get(i));
            }
            java.util.Collections.sort(currentGeneratedNumbers);

            Log.d(TAG, "Fallback 생성된 번호: " + currentGeneratedNumbers);

            List<String> fallbackStrategies = new ArrayList<>();
            fallbackStrategies.add("기본 AI 추천");

            showResultPopup(currentGeneratedNumbers, fallbackStrategies);
            showToast("기본 AI 추천으로 번호를 생성했습니다.");

        } catch (Exception fallbackError) {
            Log.e(TAG, "Fallback 번호 생성도 실패: " + fallbackError.getMessage());
            showToast("번호 생성에 실패했습니다. 다시 시도해주세요.");
        } finally {
            isGenerating = false;
        }
    }

    /**
     * 결과 표시 (다이얼로그 + UI 업데이트)
     */
    private void showResultInUI(List<Integer> numbers, List<String> strategies) {
        Log.d(TAG, "결과 표시: " + numbers);

        // 공통 분석기를 사용한 전체 분석
        LottoNumberAnalyzer.AnalysisResult analysis =
                LottoNumberAnalyzer.analyzeNumbers(numbers, strategies);

        if (analysis == null) {
            Log.e(TAG, "분석 결과가 null");
            showToast("분석 중 오류가 발생했습니다.");
            return;
        }

        // 1. 먼저 다이얼로그로 결과 표시 (조합 모드와 동일한 경험)
        showResultPopup(numbers, strategies);

        // 2. UI 레이아웃도 업데이트 (기존 기능 유지)
        updateResultLayout(analysis, strategies);

        showToast("🎉 AI 번호 생성이 완료되었습니다!");
    }

    /**
     * 결과 레이아웃 업데이트 (다이얼로그와 별도)
     */
    private void updateResultLayout(LottoNumberAnalyzer.AnalysisResult analysis, List<String> strategies) {
        // 결과 레이아웃 표시
        if (layoutResult != null) {
            layoutResult.setVisibility(View.VISIBLE);
        }

        // 생성된 번호 표시
        if (tvGenerationResult != null) {
            tvGenerationResult.setText(analysis.getFormattedNumbers());
        }

        // 분석 정보 표시
        if (tvResultAnalysis != null) {
            StringBuilder analysisText = new StringBuilder();

            // 품질 점수
            analysisText.append("🎯 품질 점수: ").append(analysis.qualityScore).append("/100 (")
                    .append(analysis.getQualityGrade()).append(")\n");

            // 홀짝 분석
            analysisText.append("⚖️ 홀짝 비율: ").append(analysis.oddEvenAnalysis.ratio).append("\n");

            // 구간 분포
            analysisText.append("📊 구간 분포: ");
            for (int i = 0; i < analysis.rangeAnalysis.rangeCounts.length; i++) {
                if (i > 0) analysisText.append(", ");
                analysisText.append(analysis.rangeAnalysis.rangeLabels[i]).append("(")
                        .append(analysis.rangeAnalysis.rangeCounts[i]).append(")");
            }
            analysisText.append("\n");

            // 연속 번호
            if (analysis.consecutiveAnalysis.consecutiveCount > 0) {
                analysisText.append("🔗 연속 번호: ").append(analysis.consecutiveAnalysis.consecutiveCount)
                        .append("쌍\n");
            } else {
                analysisText.append("✅ 연속 번호 없음\n");
            }

            // 적용된 전략
            if (!strategies.isEmpty()) {
                analysisText.append("📋 적용 전략: ").append(String.join(", ", strategies));
            } else {
                analysisText.append("✨ 적용 전략: 기본 AI 추천");
            }

            tvResultAnalysis.setText(analysisText.toString());
        }

        // 저장 버튼 활성화
        if (btnSave != null) {
            btnSave.setEnabled(true);
            btnSave.setText("💾 저장하기");
        }
    }

    /**
     * 결과를 팝업으로 표시 (통일된 분석 결과 사용)
     */
    private void showResultPopup(List<Integer> numbers, List<String> strategies) {
        Log.d(TAG, "결과 팝업 표시: " + numbers);

        // 공통 분석기를 사용한 전체 분석
        LottoNumberAnalyzer.AnalysisResult analysis =
                LottoNumberAnalyzer.analyzeNumbers(numbers, strategies);

        if (analysis == null) {
            Log.e(TAG, "분석 결과가 null");
            showToast("분석 중 오류가 발생했습니다.");
            return;
        }

        StringBuilder message = new StringBuilder();

        // 생성된 번호 표시
        message.append("🎯 AI 추천 번호\n\n");
        message.append(analysis.getFormattedNumbers()).append("\n\n");

        // 통일된 분석 결과 표시
        message.append("🤖 AI 분석 결과\n\n");

        // 품질 점수 (NumberAnalysisDialog와 동일한 방식)
        message.append("🎯 품질 점수: ").append(analysis.qualityScore).append("/100 (")
                .append(analysis.getQualityGrade()).append(")\n");

        // 홀짝 분석
        message.append("⚖️ 홀짝 비율: ").append(analysis.oddEvenAnalysis.ratio).append("\n");

        // 구간 분포
        message.append("📊 구간 분포: ");
        for (int i = 0; i < analysis.rangeAnalysis.rangeCounts.length; i++) {
            if (i > 0) message.append(", ");
            message.append(analysis.rangeAnalysis.rangeLabels[i]).append("(")
                    .append(analysis.rangeAnalysis.rangeCounts[i]).append(")");
        }
        message.append("\n");

        // 연속 번호
        if (analysis.consecutiveAnalysis.consecutiveCount > 0) {
            message.append("🔗 연속 번호: ").append(analysis.consecutiveAnalysis.consecutiveCount)
                    .append("쌍 (").append(String.join(", ", analysis.consecutiveAnalysis.consecutivePairs))
                    .append(")\n");
        } else {
            message.append("✅ 연속 번호 없음\n");
        }

        // 적용된 전략 및 모드별 설명
        if (!strategies.isEmpty()) {
            message.append("\n📋 적용된 전략: ").append(String.join(", ", strategies)).append("\n");

            // 순수 통계 모드인지 확인
            if (strategies.contains("순수통계")) {
                message.append("\n🎯 순수 통계 모드 정보:");
                message.append("\n• 100% 결정론적 생성");
                message.append("\n• 동일 조건시 동일 결과");
                message.append("\n• 랜덤 요소 완전 제거");
                message.append("\n• 출현빈도(40%) + 인기도(30%) + 트렌드(20%) + 기타(10%)");
            } else {
                message.append("\n📊 조합 모드 정보:");
                message.append("\n• 선택된 전략들을 조합하여 생성");
                message.append("\n• 적절한 랜덤성과 통계 분석 결합");
            }
        } else {
            message.append("\n✨ 적용 전략: 기본 AI 추천\n");
        }

        message.append("\n💡 데이터 기반: 실제 로또 통계 분석");
        message.append("\n🤖 Repository AI 엔진 사용");
        message.append("\n🎁 광고 시청으로 받은 특별 추천!");

        // 모드에 따른 제목 설정
        String dialogTitle;
        if (strategies.contains("순수통계")) {
            dialogTitle = "🎯 순수 통계 분석 완료";
        } else {
            dialogTitle = "🎁 AI 번호 생성 완료";
        }

        // AlertDialog 생성
        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setMessage(message.toString())
                .setNegativeButton("💾 저장하기", (dialog, which) -> saveNumbers())
                .setPositiveButton("❌ 저장하지 않고 나가기", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * 선택된 전략들을 리스트로 반환
     */
    private List<String> getSelectedStrategies() {
        List<String> strategies = new ArrayList<>();

        // 라디오 버튼으로 순수 통계 모드가 선택된 경우
        if (rbPureStatistics != null && rbPureStatistics.isChecked()) {
            strategies.add("순수통계");

            // 순수 통계 모드의 세부 전략들 추가
            if (cbPurePopular != null && cbPurePopular.isChecked()) strategies.add("순수고빈도");
            if (cbPureNeglected != null && cbPureNeglected.isChecked()) strategies.add("순수소외번호");
            if (cbPureRecent != null && cbPureRecent.isChecked()) strategies.add("순수최근추세");
            if (cbPureHighWeight != null && cbPureHighWeight.isChecked()) strategies.add("순수고가중치");
            if (cbPureBalance != null && cbPureBalance.isChecked()) strategies.add("순수균형가중치");
            if (cbPureCyclic != null && cbPureCyclic.isChecked()) strategies.add("순수주기성");
            if (cbPureCorrelation != null && cbPureCorrelation.isChecked()) strategies.add("순수상관관계");
            if (cbPureRegression != null && cbPureRegression.isChecked()) strategies.add("순수회귀분석");

            return strategies;
        }

        // 조합 모드에서 선택된 개별 전략들
        if (cbPopular != null && cbPopular.isChecked()) strategies.add("인기번호");
        if (cbNeglected != null && cbNeglected.isChecked()) strategies.add("소외번호");
        if (cbTrend != null && cbTrend.isChecked()) strategies.add("트렌드");
        if (cbPair != null && cbPair.isChecked()) strategies.add("페어분석");
        if (cbOddEven != null && cbOddEven.isChecked()) strategies.add("홀짝균형");
        if (cbZone != null && cbZone.isChecked()) strategies.add("구간분산");
        if (cbConsecutive != null && cbConsecutive.isChecked()) strategies.add("연속방지");
        if (cbLastDigit != null && cbLastDigit.isChecked()) strategies.add("끝자리다양성");
        if (cbVisualPattern != null && cbVisualPattern.isChecked()) strategies.add("시각패턴방지");
        if (cbAvoidance != null && cbAvoidance.isChecked()) strategies.add("대중기피");
        if (cbLucky != null && cbLucky.isChecked()) strategies.add("행운번호");

        return strategies;
    }

    /**
     * 번호 저장 (Repository 활용)
     */
    private void saveNumbers() {
        try {
            if (currentGeneratedNumbers.isEmpty()) {
                showToast("저장할 번호가 없습니다.");
                return;
            }

            List<String> strategies = getSelectedStrategies();
            Log.d(TAG, "번호 저장: " + currentGeneratedNumbers + ", 전략: " + strategies);

            // Repository의 saveAiGeneratedPick 메서드 사용
            // 이미 AI 생성 로그도 자동으로 저장됨
            long savedId = lottoRepository.saveAiGeneratedPick(currentGeneratedNumbers, strategies, 0);

            Log.d(TAG, "저장 완료, ID: " + savedId);
            showToast("🎉 AI 번호가 이력에 저장되었습니다!");

            // 메인화면으로 이동
            finishAndGoToMain();

        } catch (Exception e) {
            Log.e(TAG, "저장 중 오류: " + e.getMessage(), e);
            showToast("저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 메인화면으로 이동
     */
    private void finishAndGoToMain() {
        // 현재 액티비티 종료
        finish();
    }

    /**
     * 진행상황 표시/숨김 - 버튼은 항상 활성화 유지
     */
    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        if (btnGenerate != null) {
            // AI 분석 중에도 버튼을 비활성화하지 않고 상태만 표시
            if (show) {
                btnGenerate.setText("🤖 AI 분석 중...");
            } else {
                // 분석 완료 후 원래 상태로 복원
                updateButtonState();
            }
        }
    }

    /**
     * 분석 모드에 따라 UI 상태 업데이트 (카드 숨김/표시 포함)
     */
    private void updateAnalysisModeState(boolean isPureStatisticsMode, boolean modeSelected) {
        // 현재 모드 표시 업데이트
        if (tvCurrentMode != null) {
            if (!modeSelected) {
                tvCurrentMode.setText("📋 분석 모드를 선택해주세요");
                tvCurrentMode.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else if (isPureStatisticsMode) {
                tvCurrentMode.setText("현재: 🎯 순수 통계 모드 (결정론적)");
                tvCurrentMode.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else {
                tvCurrentMode.setText("현재: 📊 조합 모드 (개별 전략 선택)");
                tvCurrentMode.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
        }

        // 카드 표시/숨김 (모드가 선택되었을 때만 표시)
        if (cardPureStatisticsSettings != null) {
            cardPureStatisticsSettings.setVisibility((modeSelected && isPureStatisticsMode) ? View.VISIBLE : View.GONE);
        }

        // 순수 통계 주의사항 카드 표시/숨김
        if (cardPureStatisticsWarning != null) {
            cardPureStatisticsWarning.setVisibility((modeSelected && isPureStatisticsMode) ? View.VISIBLE : View.GONE);
        }

        // 조합 모드 카드 표시/숨김
        if (cardCombinationMode != null) {
            cardCombinationMode.setVisibility((modeSelected && !isPureStatisticsMode) ? View.VISIBLE : View.GONE);
        }

        // 모드 전환 시 상태 정리
        if (isPureStatisticsMode) {
            // 조합 모드 전략들 모두 체크 해제 (실제로는 보이지 않지만 내부 상태 정리)
            CheckBox[] combinationCheckboxes = {
                cbPopular, cbNeglected, cbTrend, cbPair, cbOddEven,
                cbZone, cbConsecutive, cbLastDigit, cbVisualPattern,
                cbAvoidance, cbLucky
            };

            for (CheckBox checkbox : combinationCheckboxes) {
                if (checkbox != null) {
                    checkbox.setChecked(false);
                }
            }
        } else {
            // 조합 모드로 전환 시 순수 통계 모드 전략들 정리는 필요 없음 (기본값 유지)
        }

        // 사용자에게 상태 변화 안내
        if (!modeSelected) {
            // 모드 선택이 해제된 경우는 토스트 표시하지 않음
        } else if (isPureStatisticsMode) {
            showToast("🎯 순수 통계 모드로 전환됨");
        } else {
            showToast("📊 조합 모드로 전환됨");
        }
    }

    /**
     * 토스트 메시지 표시
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * AdMob 에러 코드를 사용자 친화적인 메시지로 변환
     */
    private String getAdErrorExplanation(int errorCode) {
        switch (errorCode) {
            case 0: // ERROR_CODE_INTERNAL_ERROR
                return "내부 에러 - 잠시 후 다시 시도하세요";
            case 1: // ERROR_CODE_INVALID_REQUEST
                return "잘못된 광고 요청 - 설정을 확인하세요";
            case 2: // ERROR_CODE_NETWORK_ERROR
                return "네트워크 에러 - 인터넷 연결을 확인하세요";
            case 3: // ERROR_CODE_NO_FILL
                return "사용 가능한 광고가 없습니다 - 잠시 후 다시 시도하세요";
            case 4: // ERROR_CODE_INVALID_AD_SIZE
                return "잘못된 광고 크기";
            case 5: // ERROR_CODE_MEDIATION_NO_FILL
                return "중재 광고를 사용할 수 없습니다";
            case 6: // ERROR_CODE_NOT_READY
                return "광고가 아직 준비되지 않았습니다";
            case 7: // ERROR_CODE_APP_ID_MISSING
                return "AdMob App ID가 누락되었습니다";
            case 8: // ERROR_CODE_MEDIATION_ADAPTER_ERROR
                return "중재 어댑터 에러";
            case 9: // ERROR_CODE_REQUEST_ID_MISMATCH
                return "요청 ID 불일치";
            default:
                return "알 수 없는 에러 (코드: " + errorCode + ")";
        }
    }

    /**
     * 네트워크 연결 상태 확인
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            Log.d(TAG, "네트워크 연결 상태: " + (isConnected ? "연결됨" : "연결 안됨"));
            return isConnected;
        }

        Log.w(TAG, "ConnectivityManager를 가져올 수 없음");
        return false;
    }
}