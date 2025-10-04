package app.grapekim.smartlotto.ui.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.model.LottoDrawData;
import app.grapekim.smartlotto.data.model.LottoStatistics;
import app.grapekim.smartlotto.data.model.NumberFrequency;
import app.grapekim.smartlotto.MainActivity;
import app.grapekim.smartlotto.data.service.CsvLottoDataService;
import app.grapekim.smartlotto.data.service.LottoStatisticsCalculator;
import app.grapekim.smartlotto.data.CsvUpdateManager;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FavoritesFragment extends Fragment implements CsvUpdateManager.DataUpdateListener {

    // UI 컴포넌트들
    private Spinner spinnerPeriod;
    private RecyclerView rvHotNumbers, rvColdNumbers, rvBonusHotNumbers, rvBonusColdNumbers;
    private TextView tvOddCount, tvOddPercent, tvEvenCount, tvEvenPercent;
    private TextView tvBonusOddCount, tvBonusOddPercent, tvBonusEvenCount, tvBonusEvenPercent;
    private TextView tvSection1, tvSection2, tvSection3, tvSection4, tvSection5;
    private TextView tvBonusSection1, tvBonusSection2, tvBonusSection3, tvBonusSection4, tvBonusSection5;
    private ProgressBar progressSection1, progressSection2, progressSection3, progressSection4, progressSection5;
    private ProgressBar progressBonusSection1, progressBonusSection2, progressBonusSection3, progressBonusSection4, progressBonusSection5;
    private TextView tvBonusStats;
    private CircularProgressIndicator progressLoading;

    // 데이터 서비스
    private CsvLottoDataService csvService;
    private CsvUpdateManager csvUpdateManager;

    // 어댑터
    private NumberStatisticsAdapter hotNumbersAdapter;
    private NumberStatisticsAdapter coldNumbersAdapter;
    private NumberStatisticsAdapter bonusHotNumbersAdapter;
    private NumberStatisticsAdapter bonusColdNumbersAdapter;

    // 현재 통계 데이터
    private LottoStatistics currentStatistics;

    // 기간 선택 옵션
    private final String[] periodOptions = {"전체 기간", "최근 1년", "최근 6개월", "최근 3개월"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeServices();
        initializeViews(view);
        setupSpinner();
        setupRecyclerViews();
        loadStatistics(0); // 기본: 전체 기간
    }

    private void initializeServices() {
        android.util.Log.d("FavoritesFragment", "=== FavoritesFragment 서비스 초기화 시작 ===");

        csvService = new CsvLottoDataService(requireContext());

        // MainActivity에서 공유 CsvUpdateManager 가져오기
        if (getActivity() instanceof MainActivity) {
            android.util.Log.d("FavoritesFragment", "MainActivity 확인됨, CsvUpdateManager 가져오는 중...");
            csvUpdateManager = ((MainActivity) getActivity()).getCsvUpdateManager();

            // CSV 업데이트 리스너로 등록
            if (csvUpdateManager != null) {
                android.util.Log.i("FavoritesFragment", "CsvUpdateManager 획득 성공! 리스너 등록 중...");
                csvUpdateManager.addUpdateListener(this);
                android.util.Log.i("FavoritesFragment", "FavoritesFragment가 데이터 업데이트 리스너로 등록 완료!");
            } else {
                android.util.Log.e("FavoritesFragment", "CsvUpdateManager가 null입니다!");
            }
        } else {
            android.util.Log.e("FavoritesFragment", "Activity가 MainActivity가 아닙니다: " +
                (getActivity() != null ? getActivity().getClass().getSimpleName() : "null"));
        }

        android.util.Log.d("FavoritesFragment", "=== FavoritesFragment 서비스 초기화 완료 ===");
    }

    private void initializeViews(@NonNull View view) {
        spinnerPeriod = view.findViewById(R.id.spinnerPeriod);

        // 메인 번호 RecyclerViews
        rvHotNumbers = view.findViewById(R.id.rvHotNumbers);
        rvColdNumbers = view.findViewById(R.id.rvColdNumbers);

        // 보너스 번호 RecyclerViews
        rvBonusHotNumbers = view.findViewById(R.id.rvBonusHotNumbers);
        rvBonusColdNumbers = view.findViewById(R.id.rvBonusColdNumbers);

        // 메인 번호 홀짝 통계
        tvOddCount = view.findViewById(R.id.tvOddCount);
        tvOddPercent = view.findViewById(R.id.tvOddPercent);
        tvEvenCount = view.findViewById(R.id.tvEvenCount);
        tvEvenPercent = view.findViewById(R.id.tvEvenPercent);

        // 보너스 번호 홀짝 통계
        tvBonusOddCount = view.findViewById(R.id.tvBonusOddCount);
        tvBonusOddPercent = view.findViewById(R.id.tvBonusOddPercent);
        tvBonusEvenCount = view.findViewById(R.id.tvBonusEvenCount);
        tvBonusEvenPercent = view.findViewById(R.id.tvBonusEvenPercent);

        // 메인 번호 구간별 통계
        tvSection1 = view.findViewById(R.id.tvSection1);
        tvSection2 = view.findViewById(R.id.tvSection2);
        tvSection3 = view.findViewById(R.id.tvSection3);
        tvSection4 = view.findViewById(R.id.tvSection4);
        tvSection5 = view.findViewById(R.id.tvSection5);

        progressSection1 = view.findViewById(R.id.progressSection1);
        progressSection2 = view.findViewById(R.id.progressSection2);
        progressSection3 = view.findViewById(R.id.progressSection3);
        progressSection4 = view.findViewById(R.id.progressSection4);
        progressSection5 = view.findViewById(R.id.progressSection5);

        // 보너스 번호 구간별 통계
        tvBonusSection1 = view.findViewById(R.id.tvBonusSection1);
        tvBonusSection2 = view.findViewById(R.id.tvBonusSection2);
        tvBonusSection3 = view.findViewById(R.id.tvBonusSection3);
        tvBonusSection4 = view.findViewById(R.id.tvBonusSection4);
        tvBonusSection5 = view.findViewById(R.id.tvBonusSection5);

        progressBonusSection1 = view.findViewById(R.id.progressBonusSection1);
        progressBonusSection2 = view.findViewById(R.id.progressBonusSection2);
        progressBonusSection3 = view.findViewById(R.id.progressBonusSection3);
        progressBonusSection4 = view.findViewById(R.id.progressBonusSection4);
        progressBonusSection5 = view.findViewById(R.id.progressBonusSection5);

        tvBonusStats = view.findViewById(R.id.tvBonusStats);
        progressLoading = view.findViewById(R.id.progressLoading);
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, periodOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(adapter);

        spinnerPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadStatistics(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupRecyclerViews() {
        // 메인 Hot Numbers RecyclerView
        hotNumbersAdapter = new NumberStatisticsAdapter(new ArrayList<>(), true);
        rvHotNumbers.setLayoutManager(new GridLayoutManager(requireContext(), 5));
        rvHotNumbers.setAdapter(hotNumbersAdapter);

        // 메인 Cold Numbers RecyclerView
        coldNumbersAdapter = new NumberStatisticsAdapter(new ArrayList<>(), false);
        rvColdNumbers.setLayoutManager(new GridLayoutManager(requireContext(), 5));
        rvColdNumbers.setAdapter(coldNumbersAdapter);

        // 보너스 Hot Numbers RecyclerView
        bonusHotNumbersAdapter = new NumberStatisticsAdapter(new ArrayList<>(), true);
        rvBonusHotNumbers.setLayoutManager(new GridLayoutManager(requireContext(), 5));
        rvBonusHotNumbers.setAdapter(bonusHotNumbersAdapter);

        // 보너스 Cold Numbers RecyclerView
        bonusColdNumbersAdapter = new NumberStatisticsAdapter(new ArrayList<>(), false);
        rvBonusColdNumbers.setLayoutManager(new GridLayoutManager(requireContext(), 5));
        rvBonusColdNumbers.setAdapter(bonusColdNumbersAdapter);
    }

    /**
     * 통계 데이터 로드 및 UI 업데이트
     * @param periodIndex 0=전체, 1=최근1년, 2=최근6개월, 3=최근3개월
     */
    private void loadStatistics(int periodIndex) {
        progressLoading.setVisibility(View.VISIBLE);

        // 백그라운드에서 CSV 데이터 로드 및 통계 계산
        CompletableFuture.supplyAsync(() -> {
            try {
                // CSV 데이터 로드
                List<LottoDrawData> drawData = csvService.loadDrawDataByPeriod(periodIndex);

                // 데이터 범위 로깅
                if (!drawData.isEmpty()) {
                    LottoDrawData latest = drawData.get(0);
                    LottoDrawData oldest = drawData.get(drawData.size() - 1);
                    android.util.Log.i("FavoritesFragment", String.format(
                        "통계 계산 - 데이터 범위: %d회차(%s) ~ %d회차(%s), 총 %d개 회차",
                        oldest.drawNo, oldest.date,
                        latest.drawNo, latest.date,
                        drawData.size()
                    ));
                } else {
                    android.util.Log.w("FavoritesFragment", "CSV 데이터가 비어있습니다!");
                }

                // 통계 계산
                String periodDescription = periodOptions[periodIndex];
                return LottoStatisticsCalculator.calculateStatistics(drawData, periodDescription);

            } catch (Exception e) {
                throw new RuntimeException("Failed to load statistics", e);
            }
        }).thenAccept(statistics -> {
            // UI 스레드에서 결과 업데이트
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    currentStatistics = statistics;
                    updateUI();
                    progressLoading.setVisibility(View.GONE);
                });
            }
        }).exceptionally(throwable -> {
            // 오류 처리
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            "통계 로드 실패: " + throwable.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
            return null;
        });
    }

    /**
     * 통계 데이터로 UI 업데이트
     */
    private void updateUI() {
        if (currentStatistics == null) {
            return;
        }

        updateMainHotColdNumbers();
        updateBonusHotColdNumbers();
        updateMainOddEvenRatio();
        updateBonusOddEvenRatio();
        updateMainSectionAnalysis();
        updateBonusSectionAnalysis();
        updateBonusStats();
    }

    private void updateMainHotColdNumbers() {
        // 메인 Hot Numbers 업데이트
        List<NumberStat> hotNumbers = new ArrayList<>();
        for (NumberFrequency freq : currentStatistics.hotNumbers) {
            hotNumbers.add(new NumberStat(freq.number, freq.frequency));
        }
        hotNumbersAdapter.updateData(hotNumbers);

        // 메인 Cold Numbers 업데이트
        List<NumberStat> coldNumbers = new ArrayList<>();
        for (NumberFrequency freq : currentStatistics.coldNumbers) {
            coldNumbers.add(new NumberStat(freq.number, freq.frequency));
        }
        coldNumbersAdapter.updateData(coldNumbers);
    }

    private void updateBonusHotColdNumbers() {
        // 보너스 Hot Numbers 업데이트
        List<NumberStat> bonusHotNumbers = new ArrayList<>();
        for (NumberFrequency freq : currentStatistics.bonusHotNumbers) {
            bonusHotNumbers.add(new NumberStat(freq.number, freq.frequency));
        }
        bonusHotNumbersAdapter.updateData(bonusHotNumbers);

        // 보너스 Cold Numbers 업데이트
        List<NumberStat> bonusColdNumbers = new ArrayList<>();
        for (NumberFrequency freq : currentStatistics.bonusColdNumbers) {
            bonusColdNumbers.add(new NumberStat(freq.number, freq.frequency));
        }
        bonusColdNumbersAdapter.updateData(bonusColdNumbers);
    }

    private void updateMainOddEvenRatio() {
        tvOddCount.setText(String.valueOf(currentStatistics.oddCount));
        tvEvenCount.setText(String.valueOf(currentStatistics.evenCount));
        tvOddPercent.setText(String.format("%.1f%%", currentStatistics.oddPercentage));
        tvEvenPercent.setText(String.format("%.1f%%", currentStatistics.evenPercentage));
    }

    private void updateBonusOddEvenRatio() {
        tvBonusOddCount.setText(String.valueOf(currentStatistics.bonusOddCount));
        tvBonusEvenCount.setText(String.valueOf(currentStatistics.bonusEvenCount));
        tvBonusOddPercent.setText(String.format("%.1f%%", currentStatistics.bonusOddPercentage));
        tvBonusEvenPercent.setText(String.format("%.1f%%", currentStatistics.bonusEvenPercentage));
    }

    private void updateMainSectionAnalysis() {
        TextView[] sectionTexts = {tvSection1, tvSection2, tvSection3, tvSection4, tvSection5};
        ProgressBar[] sectionProgress = {progressSection1, progressSection2, progressSection3, progressSection4, progressSection5};

        for (int i = 0; i < 5; i++) {
            double percentage = currentStatistics.getSectionPercentage(i);
            sectionTexts[i].setText(String.format("%.1f%%", percentage));
            sectionProgress[i].setProgress((int) percentage);
        }
    }

    private void updateBonusSectionAnalysis() {
        TextView[] bonusSectionTexts = {tvBonusSection1, tvBonusSection2, tvBonusSection3, tvBonusSection4, tvBonusSection5};
        ProgressBar[] bonusSectionProgress = {progressBonusSection1, progressBonusSection2, progressBonusSection3, progressBonusSection4, progressBonusSection5};

        for (int i = 0; i < 5; i++) {
            double percentage = currentStatistics.getBonusSectionPercentage(i);
            bonusSectionTexts[i].setText(String.format("%.1f%%", percentage));
            bonusSectionProgress[i].setProgress((int) percentage);
        }
    }

    private void updateBonusStats() {
        if (currentStatistics.mostFrequentBonus != null && currentStatistics.leastFrequentBonus != null) {
            String bonusStatsText = String.format(
                    "가장 많이 나온 보너스: %d번 (%d회)\n가장 적게 나온 보너스: %d번 (%d회)\n분석 기간: %s",
                    currentStatistics.mostFrequentBonus.number,
                    currentStatistics.mostFrequentBonus.frequency,
                    currentStatistics.leastFrequentBonus.number,
                    currentStatistics.leastFrequentBonus.frequency,
                    currentStatistics.periodDescription
            );
            tvBonusStats.setText(bonusStatsText);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // CSV 업데이트 리스너 해제
        if (csvUpdateManager != null) {
            csvUpdateManager.removeUpdateListener(this);
        }
    }

    /**
     * CSV 데이터 업데이트 완료 시 호출되는 콜백
     * @param success 업데이트 성공 여부
     */
    @Override
    public void onDataUpdated(boolean success) {
        android.util.Log.i("FavoritesFragment", "=== 자동 통계 업데이트 이벤트 수신! ===");
        android.util.Log.i("FavoritesFragment", "업데이트 성공: " + success);
        android.util.Log.i("FavoritesFragment", "Activity 존재: " + (getActivity() != null));

        if (success && getActivity() != null) {
            // 사용자에게 알림 토스트 표시
            getActivity().runOnUiThread(() -> {
                android.widget.Toast.makeText(requireContext(),
                    "통계가 자동으로 업데이트됩니다!",
                    android.widget.Toast.LENGTH_LONG).show();
            });

            // UI 스레드에서 통계 새로고침
            getActivity().runOnUiThread(() -> {
                android.util.Log.d("FavoritesFragment", "CSV 데이터 업데이트 완료 - 통계 새로고침 시작");

                // 현재 선택된 기간으로 통계 새로고침
                if (spinnerPeriod != null) {
                    int currentPeriod = spinnerPeriod.getSelectedItemPosition();
                    android.util.Log.d("FavoritesFragment", "현재 선택된 기간: " + currentPeriod + " (" + periodOptions[currentPeriod] + ")");
                    refreshStatistics(currentPeriod);
                } else {
                    // Spinner가 아직 초기화되지 않았다면 전체 기간으로 새로고침
                    android.util.Log.d("FavoritesFragment", "Spinner가 없어 전체 기간으로 새로고침");
                    refreshStatistics(0);
                }
            });
        } else if (!success) {
            android.util.Log.w("FavoritesFragment", "데이터 업데이트 실패로 통계 새로고침 생략");
        }
    }


    /**
     * 통계 새로고침 (캐시 무효화 포함)
     * @param periodIndex 기간 인덱스
     */
    private void refreshStatistics(int periodIndex) {
        android.util.Log.d("FavoritesFragment", "통계 새로고침 시작 - 기간: " + periodOptions[periodIndex]);

        // CSV 서비스 캐시 무효화
        if (csvService != null) {
            csvService.clearCache();
        }

        // 기존 loadStatistics 메서드로 새로고침
        loadStatistics(periodIndex);
    }

    // NumberStat 클래스 (어댑터와의 호환성을 위해 유지)
    public static class NumberStat {
        public int number;
        public int frequency;

        public NumberStat(int number, int frequency) {
            this.number = number;
            this.frequency = frequency;
        }
    }
}