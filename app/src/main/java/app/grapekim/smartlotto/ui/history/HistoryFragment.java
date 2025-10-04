package app.grapekim.smartlotto.ui.history;

import app.grapekim.smartlotto.ui.qr.QrResultActivity;
import app.grapekim.smartlotto.util.ExecutorUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.grapekim.smartlotto.BuildConfig;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;
import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;
import app.grapekim.smartlotto.data.remote.NetworkProvider;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.ui.qr.QrScanActivity;
import app.grapekim.smartlotto.ui.analysis.NumberAnalysisDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

// AdMob imports
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

@OptIn(markerClass = ExperimentalGetImage.class)
public class HistoryFragment extends Fragment {

    private static final String TAG = "HistoryFragment";

    private static final String AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID;

    private MaterialSwitch swOnlyFav;
    private MaterialButtonToggleGroup tgSort;
    private TextView tvEmpty;
    private RecyclerView rv;
    private FloatingActionButton fabQrScan;
    private MaterialButton btnClearAll;
    private HistoryAdapter adapter;

    private LottoRepository repo;

    private boolean onlyFav = false;
    private boolean newestFirst = true;

    private LiveData<List<GeneratedPickEntity>> liveData;
    private ActivityResultLauncher<Intent> qrScanLauncher;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // AdMob 전면광고
    private InterstitialAd mInterstitialAd;
    private GeneratedPickEntity pendingAnalysisRow; // 분석 대기 중인 데이터

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        initializeRepository();
        initializeViews(v);
        setupQrScanLauncher();
        setupRecyclerView();
        setupClickListeners();
        setupChangeListeners();
        initializeAdMob();
        subscribe();
    }

    private void initializeRepository() {
        repo = new LottoRepositoryImpl(requireContext().getApplicationContext());
    }

    private void initializeViews(@NonNull View v) {
        swOnlyFav = v.findViewById(R.id.swOnlyFav);
        tgSort = v.findViewById(R.id.tgSort);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        rv = v.findViewById(R.id.rvHistory);
        fabQrScan = v.findViewById(R.id.fabQrScan);
        btnClearAll = v.findViewById(R.id.btnClearAll);
    }

    // ==================== AdMob 관련 메서드들 ====================

    /**
     * AdMob 초기화 및 전면광고 로드
     */
    private void initializeAdMob() {
        // Fragment가 attach되지 않았으면 초기화하지 않음
        if (!isAdded() || getContext() == null) {
            return;
        }

        // AdMob 초기화
        MobileAds.initialize(getContext(), new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(@NonNull InitializationStatus initializationStatus) {
                Log.d(TAG, "AdMob 초기화 완료");
                // 콜백 실행 시점에도 Fragment 상태 확인
                if (isAdded() && getContext() != null) {
                    loadInterstitialAd();
                }
            }
        });
    }

    /**
     * 전면광고 로드
     */
    private void loadInterstitialAd() {
        try {
            AdRequest adRequest = new AdRequest.Builder().build();

            InterstitialAd.load(requireContext(), AD_UNIT_ID, adRequest,
                    new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                            mInterstitialAd = interstitialAd;
                            Log.d(TAG, "전면광고 로드 성공");
                            setupAdCallbacks();
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            Log.e(TAG, "전면광고 로드 실패: " + loadAdError.getMessage());
                            Log.e(TAG, "오류 코드: " + loadAdError.getCode());
                            mInterstitialAd = null;
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "광고 로드 과정에서 오류", e);
        }
    }

    /**
     * 전면광고 콜백 설정
     */
    private void setupAdCallbacks() {
        if (mInterstitialAd != null) {
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    // 광고가 닫혔을 때 - 번호 분석 다이얼로그 표시
                    Log.d(TAG, "전면광고 닫힘 - 번호 분석 시작");
                    mInterstitialAd = null;
                    loadInterstitialAd(); // 다음 광고 미리 로드
                    showNumberAnalysisDialog();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    // 광고 표시 실패 시에도 분석 다이얼로그 표시
                    Log.e(TAG, "전면광고 표시 실패: " + adError.getMessage());
                    mInterstitialAd = null;
                    loadInterstitialAd();
                    showNumberAnalysisDialog();
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "전면광고 표시됨");
                }
            });
        }
    }

    /**
     * QR 스캔 결과 처리 런처 설정
     */
    private void setupQrScanLauncher() {
        qrScanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String qrText = result.getData().getStringExtra(QrScanActivity.EXTRA_QR_TEXT);
                        handleQrScanResult(qrText);
                    }
                }
        );
    }

    private void setupRecyclerView() {
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(
                this::handleFavoriteClick,  // 즐겨찾기
                this::handleDeleteClick,    // 삭제
                this::onCheckResultClicked, // 결과 확인 (하이브리드 방식으로 수정됨)
                this::onAnalyzeClicked      // 번호 분석 (광고 연동)
        );
        rv.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // QR 스캔 버튼 클릭 리스너
        fabQrScan.setOnClickListener(view -> startQrScan());

        // 모두 지우기 버튼 클릭 리스너
        btnClearAll.setOnClickListener(view -> showClearAllConfirmDialog());
    }

    private void setupChangeListeners() {
        swOnlyFav.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            onlyFav = isChecked;
            subscribe();
        });

        tgSort.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            newestFirst = (checkedId == R.id.btnSortNewest);
            subscribe();
        });
    }

    private void postError(AlertDialog dialog) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() -> dialog.setMessage(getString(R.string.result_error)));
        }
    }

    // ==================== 클릭 핸들러들 (람다 오류 방지) ====================

    /**
     * 즐겨찾기 클릭 처리 (개별 항목만 처리)
     */
    private void handleFavoriteClick(GeneratedPickEntity row) {
        io.execute(() -> {
            try {
                // 모든 경우에 개별 항목만 즐겨찾기 설정 (그룹 전체가 아닌)
                repo.setFavorite(row.id, !row.favorite);
            } catch (Exception e) {
                // 오류 처리 (필요시)
                Log.e(TAG, "즐겨찾기 설정 오류", e);
            }
        });
    }

    /**
     * 삭제 클릭 처리 (즐겨찾기 보호 기능 포함)
     */
    private void handleDeleteClick(GeneratedPickEntity row) {
        // 즐겨찾기 확인 후 삭제 여부 결정
        if (row.favorite) {
            // 즐겨찾기 항목은 확인 다이얼로그 표시
            if (!isAdded() || getContext() == null) return;

            requireActivity().runOnUiThread(() -> {
                if (isAdded() && getContext() != null) {
                    new AlertDialog.Builder(getContext())
                            .setTitle("즐겨찾기 항목 삭제")
                            .setMessage("즐겨찾기로 설정된 번호입니다.\n정말 삭제하시겠습니까?")
                            .setPositiveButton("삭제", (dialog, which) -> {
                                // 강제 삭제 실행
                                io.execute(() -> {
                                    try {
                                        if (row.isQrMultiGame()) {
                                            repo.deleteQrGroup(row.qrGroupId);
                                        } else {
                                            repo.deletePick(row.id);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "즐겨찾기 항목 삭제 오류", e);
                                    }
                                });
                            })
                            .setNegativeButton("취소", null)
                            .show();
                }
            });
        } else {
            // 일반 항목은 바로 삭제
            io.execute(() -> {
                try {
                    if (row.isQrMultiGame()) {
                        repo.deleteQrGroupIfNotFavorite(row.qrGroupId);
                    } else {
                        repo.deletePickIfNotFavorite(row.id);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "항목 삭제 오류", e);
                }
            });
        }
    }

    /**
     * 모든 이력 삭제 확인 대화상자 (즐겨찾기 보호 기능 포함)
     */
    private void showClearAllConfirmDialog() {
        if (!isAdded() || getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("모든 이력 삭제")
                .setMessage("즐겨찾기를 제외한 모든 로또 번호를 삭제하시겠습니까?\n즐겨찾기한 번호는 보호됩니다.")
                .setPositiveButton("삭제", (dialog, which) -> clearAllHistory())
                .setNeutralButton("즐겨찾기 포함 전체 삭제", (dialog, which) -> clearAllHistoryIncludingFavorites())
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 즐겨찾기 제외 모든 이력 삭제 실행
     */
    private void clearAllHistory() {
        io.execute(() -> {
            try {
                // Repository의 clearAllExceptFavorites() 호출
                repo.clearAllExceptFavorites();

                // UI 스레드에서 완료 메시지 표시
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "즐겨찾기를 제외한 모든 이력이 삭제되었습니다", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                // 오류 발생시 UI 스레드에서 오류 메시지 표시
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "삭제 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * 즐겨찾기 포함 모든 이력 삭제 실행
     */
    private void clearAllHistoryIncludingFavorites() {
        if (!isAdded() || getContext() == null) return;

        // 한 번 더 확인
        new AlertDialog.Builder(getContext())
                .setTitle("⚠️ 경고")
                .setMessage("즐겨찾기한 번호를 포함하여 모든 번호가 삭제됩니다.\n정말로 진행하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("전체 삭제", (dialog, which) -> {
                    io.execute(() -> {
                        try {
                            // Repository의 clearAll() 호출
                            repo.clearAll();

                            // UI 스레드에서 완료 메시지 표시
                            if (isAdded() && getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    if (isAdded() && getContext() != null) {
                                        Toast.makeText(getContext(), "모든 이력이 삭제되었습니다", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // 오류 발생시 UI 스레드에서 오류 메시지 표시
                            if (isAdded() && getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    if (isAdded() && getContext() != null) {
                                        Toast.makeText(getContext(), "삭제 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * QR 스캔 시작 (기존 유지)
     */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startQrScan() {
        if (!isAdded() || getContext() == null) return;

        try {
            Intent intent = new Intent(getContext(), QrScanActivity.class);
            qrScanLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(),
                    "QR 스캔을 시작할 수 없습니다: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * QR 스캔 결과 처리 (기존 방식으로 단순화)
     */
    private void handleQrScanResult(String qrText) {
        if (!isAdded() || getContext() == null) return;

        if (qrText == null || qrText.trim().isEmpty()) {
            Toast.makeText(getContext(),
                    "QR 코드를 읽을 수 없습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // QrResultActivity로 원시 데이터 전달
        Intent intent = new Intent(getContext(), QrResultActivity.class);
        intent.putExtra(QrResultActivity.EXTRA_QR_RAW_DATA, qrText);
        startActivity(intent);
    }

    // ==================== 기존 기능들 모두 유지 ====================

    private void subscribe() {
        if (liveData != null) liveData.removeObservers(getViewLifecycleOwner());
        liveData = repo.observeHistory(onlyFav, newestFirst);
        liveData.observe(getViewLifecycleOwner(), list -> {
            tvEmpty.setVisibility((list == null || list.isEmpty()) ? View.VISIBLE : View.GONE);
            adapter.submit(list);

            // ▼ 하이브리드 방식으로 '발표 전' 항목들을 미리 표시
            if (list != null && !list.isEmpty()) {
                io.execute(() -> precomputePreAnnounce(list));
            } else {
                adapter.setPreAnnounce(null);
            }
        });
    }

    /**
     * 하이브리드 방식 발표 전 판단 (로컬 DB 우선, 네트워크 API 보조)
     */
    private void precomputePreAnnounce(List<GeneratedPickEntity> list) {
        Set<Long> preIds = new HashSet<>();

        try {
            for (GeneratedPickEntity row : list) {
                try {
                    int targetRound = determineTargetRound(row);

                    // 하이브리드 방식으로 발표 여부 확인
                    if (!isDrawAnnounced(targetRound)) {
                        preIds.add(row.id);
                        Log.d(TAG, "발표 전 판정: ID=" + row.id + ", 목표회차=" + targetRound);
                    } else {
                        Log.d(TAG, "발표 완료 판정: ID=" + row.id + ", 목표회차=" + targetRound);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "회차 계산 오류, 안전하게 발표 전으로 처리: ID=" + row.id, e);
                    preIds.add(row.id);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "발표 전 판단 전체 오류, 안전하게 모두 발표 전으로 처리", e);
            // 오류 시 안전하게 모든 항목을 발표 전으로 처리
            for (GeneratedPickEntity row : list) {
                preIds.add(row.id);
            }
        }

        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() -> adapter.setPreAnnounce(preIds));
        }
    }

    /**
     * 🔧 수정된 발표 여부 확인 - 네트워크 우선, DB 보조
     */
    private boolean isDrawAnnounced(int targetRound) {
        try {
            // 1. 로컬 DB 우선 확인 (빠른 응답)
            LottoDrawHistoryEntity localDraw = repo.getDrawHistory(targetRound);
            if (localDraw != null) {
                Log.d(TAG, "로컬 DB에서 " + targetRound + "회 데이터 발견 - 발표됨");
                return true;
            }

            // 2. 네트워크 API로 실시간 확인 (신뢰도 높음)
            try {
                Response<LottoDrawDto> response = NetworkProvider.api().getDraw(targetRound).execute();
                boolean apiAvailable = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                Log.d(TAG, "네트워크 API " + targetRound + "회 확인 결과: " + apiAvailable);

                // 네트워크에서 데이터를 찾으면 즉시 로컬 DB에 저장
                if (apiAvailable) {
                    try {
                        saveDrawDataToLocalDB(response.body(), targetRound);
                    } catch (Exception saveError) {
                        Log.w(TAG, "DB 저장 실패하지만 당첨확인은 계속: " + saveError.getMessage());
                    }
                }

                return apiAvailable;
            } catch (Exception e) {
                Log.w(TAG, "네트워크 API 확인 실패: " + e.getMessage());

                // 3. 네트워크 실패 시에만 DB 기반 추정 (최후 수단)
                Integer actualLatestRound = repo.getLatestDrawNumber();
                if (actualLatestRound != null && targetRound > actualLatestRound + 2) {
                    // +2를 추가하여 더 관대하게 처리 (2회차 정도는 DB 업데이트 지연 허용)
                    Log.d(TAG, targetRound + "회는 최신 회차(" + actualLatestRound + ")보다 훨씬 미래 - 발표 전");
                    return false;
                } else {
                    Log.d(TAG, "네트워크 실패하지만 " + targetRound + "회는 가능한 범위 - 발표됨으로 추정");
                    return true; // 의심스러우면 당첨확인 허용
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "발표 여부 확인 오류: " + e.getMessage() + " - 당첨확인 허용");
            return true; // 오류 시에도 당첨확인 허용 (사용자 편의성 우선)
        }
    }

    /**
     * 네트워크에서 가져온 당첨번호 데이터를 로컬 DB에 저장
     */
    private void saveDrawDataToLocalDB(LottoDrawDto drawDto, int targetRound) {
        if (drawDto == null || !drawDto.isSuccess()) {
            return;
        }

        try {
            // DTO를 Entity로 변환
            LottoDrawHistoryEntity entity = new LottoDrawHistoryEntity();
            entity.drawNumber = targetRound;
            entity.drawDate = drawDto.date != null ? drawDto.date : "";

            // 당첨번호 설정 (올바른 필드명 사용)
            entity.number1 = drawDto.n1 != null ? drawDto.n1 : 0;
            entity.number2 = drawDto.n2 != null ? drawDto.n2 : 0;
            entity.number3 = drawDto.n3 != null ? drawDto.n3 : 0;
            entity.number4 = drawDto.n4 != null ? drawDto.n4 : 0;
            entity.number5 = drawDto.n5 != null ? drawDto.n5 : 0;
            entity.number6 = drawDto.n6 != null ? drawDto.n6 : 0;
            entity.bonusNumber = drawDto.bonus != null ? drawDto.bonus : 0;

            // 백그라운드에서 DB에 저장
            io.execute(() -> {
                try {
                    // 단일 항목을 리스트로 변환하여 저장
                    java.util.List<LottoDrawHistoryEntity> entities = java.util.Arrays.asList(entity);
                    repo.saveLottoDrawHistories(entities);
                    Log.d(TAG, "네트워크 데이터 로컬 DB 저장 완료: " + targetRound + "회");
                } catch (Exception e) {
                    Log.e(TAG, "DB 저장 실패: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "DTO→Entity 변환 실패: " + e.getMessage());
        }
    }

    /**
     * 대상 회차 결정 (QR 데이터 우선 사용)
     */
    private int determineTargetRound(GeneratedPickEntity row) {
        // 1. QR에서 파싱된 회차 정보가 있으면 우선 사용
        if (row.parsedRound != null && row.parsedRound > 0) {
            Log.d(TAG, "QR 파싱 회차 사용: ID=" + row.id + ", 회차=" + row.parsedRound);
            return row.parsedRound;
        }

        // 2. targetRound 정보가 있으면 사용
        if (row.targetRound != null && row.targetRound > 0) {
            Log.d(TAG, "타겟 회차 사용: ID=" + row.id + ", 회차=" + row.targetRound);
            return row.targetRound;
        }

        // 3. 기존 로직: 생성 시각 기준 계산
        LocalDate createdDate = Instant.ofEpochMilli(row.createdAt)
                .atZone(SEOUL).toLocalDate();
        LocalDate targetSat = createdDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        // 1회차 기준일부터 주차 계산 (2002-12-07이 1회차)
        LocalDate firstDrawDate = LocalDate.of(2002, 12, 7);
        long weeks = ChronoUnit.WEEKS.between(firstDrawDate, targetSat);
        int calculatedRound = (int) (weeks + 1);

        Log.d(TAG, "계산된 회차 사용: ID=" + row.id + ", 회차=" + calculatedRound);
        return calculatedRound;
    }

    /**
     * 현재 날짜 기준으로 이론적 최신 회차 계산
     * (실제 발표와 무관하게 시간만으로 계산) - 참고용으로 보존
     */
    private int calculateCurrentRoundByDate() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate lastSaturday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));

        // 토요일 오후 9시 이전이면 이전 주 토요일 기준
        LocalDateTime now = LocalDateTime.now(SEOUL);
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY && now.getHour() < 21) {
            lastSaturday = lastSaturday.minusWeeks(1);
        }

        LocalDate firstDrawDate = LocalDate.of(2002, 12, 7); // 1회차
        long weeks = ChronoUnit.WEEKS.between(firstDrawDate, lastSaturday);
        return (int) (weeks + 1);
    }

    // ====================== 번호 분석 로직 (광고 연동) ======================

    /**
     * 번호 분석 버튼 클릭 시 - 광고 시청 후 분석 다이얼로그 표시 (i 버튼)
     */
    private void onAnalyzeClicked(GeneratedPickEntity row) {
        // 분석할 데이터 저장
        pendingAnalysisRow = row;

        // 광고가 로드되어 있으면 표시, 없으면 바로 분석 다이얼로그 표시
        if (mInterstitialAd != null) {
            Log.d(TAG, "전면광고 표시 시작");
            try {
                mInterstitialAd.show(requireActivity());
            } catch (Exception e) {
                Log.e(TAG, "광고 표시 실패", e);
                showNumberAnalysisDialog();
            }
        } else {
            Log.d(TAG, "광고 없음 - 바로 번호 분석 시작");
            showNumberAnalysisDialog();
        }
    }

    /**
     * 번호 분석 다이얼로그 표시
     */
    private void showNumberAnalysisDialog() {
        if (pendingAnalysisRow == null) {
            Log.e(TAG, "분석할 데이터가 없습니다");
            return;
        }

        try {
            NumberAnalysisDialog dialog = NumberAnalysisDialog.newInstance(pendingAnalysisRow);
            dialog.show(getChildFragmentManager(), "NumberAnalysisDialog");

            // 분석 완료 후 데이터 초기화
            pendingAnalysisRow = null;

        } catch (Exception e) {
            Log.e(TAG, "번호 분석 다이얼로그 표시 실패", e);
            Toast.makeText(requireContext(),
                    "번호 분석을 표시할 수 없습니다: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            pendingAnalysisRow = null;
        }
    }


    /**
     * 결과 확인 버튼 클릭 시 - 하이브리드 방식 당첨 결과 확인 (완전히 수정됨)
     */
    private void onCheckResultClicked(GeneratedPickEntity row, View buttonView) {
        if (!isAdded() || getContext() == null) return;

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.result_title))
                .setMessage(getString(R.string.checking))
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();

        io.execute(() -> {
            try {
                int targetRound = determineTargetRound(row);
                Log.d(TAG, "결과 확인 시작: ID=" + row.id + ", 목표회차=" + targetRound);

                // 1단계: 로컬 DB에서 당첨번호 조회 시도
                LottoDrawHistoryEntity localDraw = repo.getDrawHistory(targetRound);
                if (localDraw != null) {
                    Log.i(TAG, "로컬 DB에서 " + targetRound + "회 당첨번호 발견 - 로컬 데이터 사용");
                    evaluateWithLocalData(row, localDraw, dialog, targetRound);
                    return;
                }

                Log.d(TAG, "로컬 DB에 " + targetRound + "회 데이터 없음 - 네트워크 API 시도");

                // 2단계: 발표 여부 확인
                if (!isDrawAnnounced(targetRound)) {
                    Log.i(TAG, targetRound + "회는 아직 발표되지 않음");
                    if (isAdded() && getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                dialog.setMessage(getString(R.string.not_announced));
                            }
                        });
                    }
                    return;
                }

                // 3단계: 네트워크에서 당첨번호 조회
                Log.d(TAG, "네트워크 API에서 " + targetRound + "회 당첨번호 조회");
                Response<LottoDrawDto> roundRes = NetworkProvider.api().getDraw(targetRound).execute();
                if (!roundRes.isSuccessful() || roundRes.body() == null || !roundRes.body().isSuccess()) {
                    Log.e(TAG, "네트워크 API에서 " + targetRound + "회 조회 실패");
                    postError(dialog);
                    return;
                }

                LottoDrawDto networkDraw = roundRes.body();
                Log.i(TAG, "네트워크 API에서 " + targetRound + "회 당첨번호 조회 성공");
                evaluateWithNetworkData(row, networkDraw, dialog, targetRound);

            } catch (Exception e) {
                Log.e(TAG, "결과 확인 중 오류 발생", e);
                postError(dialog);
            }
        });
    }

    /**
     * 로컬 DB 데이터로 결과 평가
     */
    private void evaluateWithLocalData(GeneratedPickEntity row, LottoDrawHistoryEntity localDraw, AlertDialog dialog, int targetRound) {
        try {
            Log.d(TAG, "로컬 데이터로 결과 평가: " + targetRound + "회");

            // 로컬 데이터를 네트워크 형식으로 변환
            LottoDrawDto convertedDraw = new LottoDrawDto();
            convertedDraw.drwNo = localDraw.drawNumber;
            convertedDraw.date = localDraw.drawDate;
            convertedDraw.n1 = localDraw.number1;
            convertedDraw.n2 = localDraw.number2;
            convertedDraw.n3 = localDraw.number3;
            convertedDraw.n4 = localDraw.number4;
            convertedDraw.n5 = localDraw.number5;
            convertedDraw.n6 = localDraw.number6;
            convertedDraw.bonus = localDraw.bonusNumber;

            // 공통 평가 로직 사용
            evaluateWithNetworkData(row, convertedDraw, dialog, targetRound);

        } catch (Exception e) {
            Log.e(TAG, "로컬 데이터 평가 중 오류", e);
            postError(dialog);
        }
    }

    /**
     * 네트워크 데이터로 결과 평가 (로컬/네트워크 공통 사용)
     */
    private void evaluateWithNetworkData(GeneratedPickEntity row, LottoDrawDto drawData, AlertDialog dialog, int targetRound) {
        try {
            Result result = evaluate(row, drawData);

            // 결과를 데이터베이스에 저장
            int finalRank = (result.rank == 0) ? -1 : result.rank; // 0등(낙첨)을 -1로 변환
            repo.updateResult(row.id, finalRank, result.matchCount, targetRound);

            String resultMessage = getString(R.string.result_round_date, drawData.drwNo, drawData.date)
                    + "\n\n"
                    + getString(R.string.result_rank, result.matchCount, rankLabel(result.rank));

            Log.i(TAG, targetRound + "회 결과: " + result.matchCount + "개 맞춤, " + rankLabel(result.rank));

            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        dialog.setMessage(resultMessage);
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "결과 평가 중 오류", e);
            postError(dialog);
        }
    }

    // ====================== 기존 결과 확인 로직들 (참고용으로 보존) ======================

    /** 최신 회차 찾기: 간단 상향 탐색 (참고용 보존) */
    private LottoDrawDto findLatestRound() {
        int cur = 1000;
        LottoDrawDto lastOk = null;

        int step = 128;
        while (step > 0) {
            LottoDrawDto ok = probe(cur);
            if (ok != null) {
                lastOk = ok;
                cur += step;
            } else {
                cur -= step;
                step /= 2;
            }
        }
        for (int i = 0; i < 16; i++) {
            LottoDrawDto ok = probe(cur + i);
            if (ok == null) break;
            lastOk = ok;
        }
        return lastOk;
    }

    @Nullable
    private LottoDrawDto probe(int round) {
        try {
            Response<LottoDrawDto> res = NetworkProvider.api().getDraw(round).execute();
            if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) return res.body();
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 생성 시각 기준 "다음 또는 같은 토요일"을 목표로 하여,
     * 그 토요일이 최신 토요일보다 미래이면 발표 전. (참고용 보존)
     */
    private RoundCalc computeRoundForTimestampNextOrSame(long createdAtMillis, LottoDrawDto latest) {
        LocalDate latestDate = LocalDate.parse(latest.date); // yyyy-MM-dd
        LocalDate latestSat  = latestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));

        LocalDate createdDate = Instant.ofEpochMilli(createdAtMillis).atZone(SEOUL).toLocalDate();
        LocalDate targetSat   = createdDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        if (targetSat.isAfter(latestSat)) {
            return new RoundCalc(true, -1);
        }
        long weeks = ChronoUnit.WEEKS.between(targetSat, latestSat);
        int round = (latest.drwNo != null) ? (int) (latest.drwNo - weeks) : -1;
        return new RoundCalc(false, round);
    }

    private static class RoundCalc {
        final boolean preAnnounce;
        final int roundNo;
        RoundCalc(boolean pre, int no) { preAnnounce = pre; roundNo = no; }
    }

    /** 등수 평가 (공통 사용) */
    private Result evaluate(GeneratedPickEntity row, LottoDrawDto off) {
        Set<Integer> mine = parseNumbers(row.numbersCsv);
        Set<Integer> official = new HashSet<>(Arrays.asList(off.n1, off.n2, off.n3, off.n4, off.n5, off.n6));
        int match = 0;
        for (Integer n : mine) if (official.contains(n)) match++;

        int rank;
        if (match == 6) {
            rank = 1;
        } else if (match == 5 && mine.contains(off.bonus)) {
            rank = 2;
        } else if (match == 5) {
            rank = 3;
        } else if (match == 4) {
            rank = 4;
        } else if (match == 3) {
            rank = 5;
        } else {
            rank = 0;
        }
        return new Result(match, rank);
    }

    private String rankLabel(int rank) {
        switch (rank) {
            case 1: return getString(R.string.rank_1);
            case 2: return getString(R.string.rank_2);
            case 3: return getString(R.string.rank_3);
            case 4: return getString(R.string.rank_4);
            case 5: return getString(R.string.rank_5);
            default: return getString(R.string.rank_miss);
        }
    }

    private Set<Integer> parseNumbers(String csv) {
        Set<Integer> s = new HashSet<>();
        if (csv == null) return s;
        for (String token : csv.split(",")) {
            String t = token.trim();
            if (TextUtils.isEmpty(t)) continue;
            try { s.add(Integer.parseInt(t)); } catch (NumberFormatException ignore) {}
        }
        return s;
    }

    private static class Result {
        final int matchCount;
        final int rank;
        Result(int m, int r) { matchCount = m; rank = r; }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // ExecutorService 안전하게 종료 (메모리 누수 방지)
        ExecutorUtils.shutdownSafely(io);
    }
}