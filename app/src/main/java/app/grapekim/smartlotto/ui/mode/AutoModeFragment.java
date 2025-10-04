package app.grapekim.smartlotto.ui.mode;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 자동 번호 생성 모드 Fragment
 */
public class AutoModeFragment extends Fragment {

    private MaterialButton btnAutoGenerate;
    private MaterialCardView cardAutoResult;
    private LinearLayout rowAutoBalls;
    private MaterialButton btnRegenerate;
    private MaterialButton btnSave;
    private ImageButton btnClose;

    private LottoRepository repository;
    private ExecutorService backgroundExecutor;
    private Handler mainHandler;

    private List<Integer> currentNumbers;
    private final List<Runnable> pendingAnimations = new ArrayList<>();

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
        return inflater.inflate(R.layout.fragment_auto_mode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
        hideResultCard();
    }

    private void initViews(View view) {
        btnAutoGenerate = view.findViewById(R.id.btnAutoGenerate);
        cardAutoResult = view.findViewById(R.id.cardAutoResult);
        rowAutoBalls = view.findViewById(R.id.rowAutoBalls);
        btnRegenerate = view.findViewById(R.id.btnRegenerate);
        btnSave = view.findViewById(R.id.btnSave);
        btnClose = view.findViewById(R.id.btnClose);
    }

    private void setupClickListeners() {
        btnAutoGenerate.setOnClickListener(v -> generateNumbers());
        btnRegenerate.setOnClickListener(v -> regenerateNumbers());
        btnSave.setOnClickListener(v -> saveNumbers());
        btnClose.setOnClickListener(v -> hideResultCard());
    }

    private void generateNumbers() {
        cancelPendingAnimations();
        currentNumbers = generateRandomNumbers();
        showResultCardThenAnimate();
    }

    private void regenerateNumbers() {
        cancelPendingAnimations();
        currentNumbers = generateRandomNumbers();
        displayNumbersWithAnimation(currentNumbers);
    }

    private void showResultCardThenAnimate() {
        showResultCard();

        // 카드 표시 후 약간의 지연을 두고 애니메이션 시작
        mainHandler.postDelayed(() -> {
            if (currentNumbers != null && isAdded()) {
                displayNumbersWithAnimation(currentNumbers);
            }
        }, 100); // 100ms 지연
    }

    private List<Integer> generateRandomNumbers() {
        List<Integer> pool = new ArrayList<>(45);
        for (int i = 1; i <= 45; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, new Random());
        List<Integer> selected = new ArrayList<>(pool.subList(0, 6));
        Collections.sort(selected);
        return selected;
    }

    private void displayNumbersWithAnimation(List<Integer> numbers) {
        rowAutoBalls.removeAllViews();

        // 모든 번호 뷰를 먼저 생성하고 초기 상태 설정
        List<TextView> ballViews = new ArrayList<>();
        for (int number : numbers) {
            TextView ballView = createBallView(number);
            // 초기 상태: 보이지만 투명하고 작게
            ballView.setAlpha(0f);
            ballView.setScaleX(0.3f);
            ballView.setScaleY(0.3f);
            ballView.setVisibility(View.VISIBLE);
            rowAutoBalls.addView(ballView);
            ballViews.add(ballView);
        }

        // 각 번호를 순차적으로 애니메이션
        for (int i = 0; i < ballViews.size(); i++) {
            final TextView ballView = ballViews.get(i);

            Runnable animationRunnable = () -> {
                if (isAdded() && ballView.getParent() != null) {
                    animateNumberAppearance(ballView);
                }
            };

            mainHandler.postDelayed(animationRunnable, i * 150);
            pendingAnimations.add(animationRunnable);
        }
    }

    private void animateNumberAppearance(TextView ballView) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ballView, "scaleX", 0.3f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ballView, "scaleY", 0.3f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ballView, "alpha", 0f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(new android.view.animation.DecelerateInterpolator()); // new 추가
        animatorSet.start();
    }

    private void cancelPendingAnimations() {
        for (Runnable runnable : pendingAnimations) {
            mainHandler.removeCallbacks(runnable);
        }
        pendingAnimations.clear();
    }

    private TextView createBallView(int number) {
        TextView ball = new TextView(requireContext());
        ball.setText(String.valueOf(number));
        ball.setGravity(Gravity.CENTER);
        ball.setTextSize(16);
        ball.setMinWidth(dpToPx(48));
        ball.setMinHeight(dpToPx(48));

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

        backgroundExecutor.execute(() -> {
            try {
                repository.saveAutoPick(new ArrayList<>(currentNumbers));

                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show();
                        hideResultCard();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "저장 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showResultCard() {
        if (cardAutoResult != null) {
            cardAutoResult.setVisibility(View.VISIBLE);
        }
    }

    private void hideResultCard() {
        if (cardAutoResult != null) {
            cardAutoResult.setVisibility(View.GONE);
        }
        currentNumbers = null;
        cancelPendingAnimations();
    }

    private int getColorForNumber(int number) {
        if (number >= 1 && number <= 10) return Color.parseColor("#FBC02D");
        if (number >= 11 && number <= 20) return Color.parseColor("#1976D2");
        if (number >= 21 && number <= 30) return Color.parseColor("#D32F2F");
        if (number >= 31 && number <= 40) return Color.parseColor("#424242");
        return Color.parseColor("#388E3C");
    }

    private int getTextColorForBackground(int backgroundColor) {
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

        cancelPendingAnimations();

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