package app.grapekim.smartlotto.ui.mode;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.ui.period.PeriodBottomSheet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 수동 번호 생성 모드 Fragment (기간별 통계 기반)
 */
public class ManualModeFragment extends Fragment implements PeriodBottomSheet.PeriodSelectionListener {

    private MaterialButton btnSelectPeriod;
    private MaterialButton btnManualGenerate;
    private MaterialButton btnManualSave;
    private MaterialTextView tvPeriodChoice;
    private MaterialCardView cardManualResult;
    private LinearLayout rowManualBalls;

    private LottoRepository repository;
    private ExecutorService backgroundExecutor;
    private Handler mainHandler;

    private List<Integer> currentNumbers;
    private String selectedPeriod = "없음";
    private int selectedPeriodValue = -1; // -1: 선택안함, 0: 랜덤, 3,5,10... : 해당 회차수

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context appContext = requireContext().getApplicationContext();
        repository = new LottoRepositoryImpl(appContext);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manual_mode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
        updatePeriodDisplay();
        hideResultCard();
    }

    private void initViews(View view) {
        btnSelectPeriod = view.findViewById(R.id.btnSelectPeriod);
        btnManualGenerate = view.findViewById(R.id.btnManualGenerate);
        btnManualSave = view.findViewById(R.id.btnManualSave);
        tvPeriodChoice = view.findViewById(R.id.tvPeriodChoice);
        cardManualResult = view.findViewById(R.id.cardManualResult);
        rowManualBalls = view.findViewById(R.id.rowManualBalls);
    }

    private void setupClickListeners() {
        btnSelectPeriod.setOnClickListener(v -> showPeriodBottomSheet());
        btnManualGenerate.setOnClickListener(v -> generateNumbers());
        btnManualSave.setOnClickListener(v -> saveNumbers());
    }

    private void showPeriodBottomSheet() {
        PeriodBottomSheet bottomSheet = new PeriodBottomSheet();
        bottomSheet.setPeriodSelectionListener(this);
        bottomSheet.show(getChildFragmentManager(), "period_bottom_sheet");
    }

    @Override
    public void onPeriodSelected(String periodText, int periodValue) {
        selectedPeriod = periodText;
        selectedPeriodValue = periodValue;
        updatePeriodDisplay();
    }

    private void updatePeriodDisplay() {
        tvPeriodChoice.setText("현재 선택: " + selectedPeriod);
    }

    private void generateNumbers() {
        if (selectedPeriodValue == -1) {
            Toast.makeText(requireContext(), "먼저 기간을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 실제로는 여기서 선택된 기간에 따른 통계 기반 번호 생성
        // 현재는 미니멀 구현으로 랜덤 생성
        currentNumbers = generateNumbersByPeriod(selectedPeriodValue);
        displayNumbers(currentNumbers);
        showResultCard();
    }

    private List<Integer> generateNumbersByPeriod(int periodValue) {
        // TODO: 실제 통계 기반 로직 구현
        // 현재는 단순 랜덤으로 구현
        List<Integer> pool = new ArrayList<>(45);
        for (int i = 1; i <= 45; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, new Random());
        List<Integer> selected = new ArrayList<>(pool.subList(0, 6));
        Collections.sort(selected);
        return selected;
    }

    private void displayNumbers(List<Integer> numbers) {
        rowManualBalls.removeAllViews();

        for (int number : numbers) {
            TextView ballView = createBallView(number);
            rowManualBalls.addView(ballView);
        }
    }

    private TextView createBallView(int number) {
        TextView ball = new TextView(requireContext());
        ball.setText(String.valueOf(number));
        ball.setGravity(Gravity.CENTER);
        ball.setTextSize(16);
        ball.setMinWidth(dpToPx(48));
        ball.setMinHeight(dpToPx(48));

        // 번호별 색상 적용
        int backgroundColor = getColorForNumber(number);
        ball.setBackgroundResource(R.drawable.bg_lotto_ball);
        ball.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        ball.setTextColor(getTextColorForBackground(backgroundColor));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dpToPx(6), 0, dpToPx(6), 0);
        ball.setLayoutParams(params);

        return ball;
    }

    private void saveNumbers() {
        if (currentNumbers == null || currentNumbers.isEmpty()) {
            Toast.makeText(requireContext(), "먼저 번호를 생성해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 백그라운드에서 저장
        backgroundExecutor.execute(() -> {
            try {
                repository.saveManualPick(new ArrayList<>(currentNumbers), null, null);

                // UI 스레드에서 토스트 표시
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show();
                    hideResultCard();
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(requireContext(), "저장 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showResultCard() {
        cardManualResult.setVisibility(View.VISIBLE);
    }

    private void hideResultCard() {
        cardManualResult.setVisibility(View.GONE);
        currentNumbers = null;
    }

    private int getColorForNumber(int number) {
        if (number >= 1 && number <= 10) return Color.parseColor("#FBC02D");   // 노랑
        if (number >= 11 && number <= 20) return Color.parseColor("#1976D2");  // 파랑
        if (number >= 21 && number <= 30) return Color.parseColor("#D32F2F");  // 빨강
        if (number >= 31 && number <= 40) return Color.parseColor("#424242");  // 회색
        return Color.parseColor("#388E3C"); // 초록 (41-45)
    }

    private int getTextColorForBackground(int backgroundColor) {
        // 노랑 배경에는 검은 글씨, 나머지는 흰 글씨
        if (backgroundColor == Color.parseColor("#FBC02D")) {
            return Color.BLACK;
        }
        return Color.WHITE;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 메모리 누수 방지: ExecutorService 안전하게 종료
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
            try {
                if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    backgroundExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                backgroundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}