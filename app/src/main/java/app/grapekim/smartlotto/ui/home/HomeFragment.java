package app.grapekim.smartlotto.ui.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import app.grapekim.smartlotto.ui.ai.AiNumberGenerationActivity;
import app.grapekim.smartlotto.ui.base.BaseFragment;
import app.grapekim.smartlotto.ui.manual.ManualInputActivity;
import app.grapekim.smartlotto.ui.qr.ZxingScanActivity;
import app.grapekim.smartlotto.util.SafeUtils;
import com.google.android.material.card.MaterialCardView;

/**
 * 홈 화면 Fragment (MVVM 패턴 적용 + 고정 구슬 크기)
 */
public class HomeFragment extends BaseFragment<HomeViewModel> {

    // UI 요소들
    private TextView tvLastDrawTitle;
    private View progressLast;
    private TextView lastNum1, lastNum2, lastNum3, lastNum4, lastNum5, lastNum6, lastBonus;

    // ViewPager2와 인디케이터
    private ViewPager2 viewPagerButtons;
    private LinearLayout dotsIndicator;
    private ImageView[] dots;

    // 고정 크기 상수들
    private static final int BALL_SIZE_DP = 32;
    private static final int TEXT_SIZE_SP = 10;
    private static final int MARGIN_DP = 3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 기존 번호 생성 관련 코드 제거됨
    }

    @Override
    protected void initializeViewModel() {
        viewModel = createViewModel();
    }

    @Override
    protected void initializeViews(@NonNull View view) {
        // 지난 회차 뷰
        tvLastDrawTitle = view.findViewById(R.id.tvLastDrawTitle);
        progressLast = view.findViewById(R.id.progressLast);
        lastNum1 = view.findViewById(R.id.lastNum1);
        lastNum2 = view.findViewById(R.id.lastNum2);
        lastNum3 = view.findViewById(R.id.lastNum3);
        lastNum4 = view.findViewById(R.id.lastNum4);
        lastNum5 = view.findViewById(R.id.lastNum5);
        lastNum6 = view.findViewById(R.id.lastNum6);
        lastBonus = view.findViewById(R.id.lastBonusNum);

        // ViewPager2와 인디케이터 초기화
        viewPagerButtons = view.findViewById(R.id.viewPagerButtons);
        dotsIndicator = view.findViewById(R.id.dotsIndicator);

        // ViewPager2 설정
        setupViewPager();

        // 구슬들에 고정 크기 적용
        applyFixedSizesToBalls();
    }

    @Override
    protected void setupClickListeners() {
        // ViewPager의 어댑터에서 클릭 이벤트를 처리합니다
    }

    /**
     * ViewPager2 설정
     */
    private void setupViewPager() {
        MainButtonsAdapter adapter = new MainButtonsAdapter(position -> {
            SafeUtils.safeRun(() -> {
                try {
                    Intent intent;
                    switch (position) {
                        case 0: // QR 당첨 확인
                            intent = new Intent(requireContext(), ZxingScanActivity.class);
                            intent.putExtra("SCAN_MODE", ZxingScanActivity.SCAN_MODE_WINNING_CHECK);
                            startActivity(intent);
                            break;
                        case 1: // 수동 선택
                            intent = new Intent(requireContext(), ManualInputActivity.class);
                            startActivity(intent);
                            break;
                        case 2: // AI 기반 번호 생성
                            intent = new Intent(requireContext(), AiNumberGenerationActivity.class);
                            startActivity(intent);
                            break;
                    }
                } catch (Exception e) {
                    showToast("화면을 열 수 없습니다.");
                }
            });
        });

        viewPagerButtons.setAdapter(adapter);

        // 페이지 인디케이터 설정
        setupIndicator(3);
        setCurrentIndicator(0);

        // 페이지 변경 리스너
        viewPagerButtons.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
            }
        });
    }

    /**
     * 페이지 인디케이터 dots 설정
     */
    private void setupIndicator(int count) {
        dots = new ImageView[count];
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);

        for (int i = 0; i < count; i++) {
            dots[i] = new ImageView(requireContext());
            dots[i].setImageResource(R.drawable.dot_indicator_inactive);
            dots[i].setLayoutParams(params);
            dotsIndicator.addView(dots[i]);
        }
    }

    /**
     * 현재 페이지 인디케이터 업데이트
     */
    private void setCurrentIndicator(int position) {
        if (dots == null) return;
        for (int i = 0; i < dots.length; i++) {
            int drawableId = (i == position) ?
                    R.drawable.dot_indicator_active :
                    R.drawable.dot_indicator_inactive;
            dots[i].setImageResource(drawableId);
        }
    }

    @Override
    protected void observeViewModel() {
        if (viewModel == null) return;

        // 최신 회차 데이터 관찰
        viewModel.getLatestDraw().observe(getViewLifecycleOwner(), this::applyLastDraw);

        // 로딩 상태 관찰
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), this::showLoading);

        // 에러 메시지 관찰
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                showToast(errorMessage);
                viewModel.clearError();
            }
        });

        // 생성된 번호 관찰 제거됨 (새 기능 대기 중)
        // 저장 성공 상태 관찰 제거됨 (새 기능 대기 중)
    }

    @Override
    protected Class<HomeViewModel> getViewModelClass() {
        return HomeViewModel.class;
    }

    // ========================= Fixed Size Methods =========================

    /**
     * 모든 구슬에 고정 크기 적용
     */
    private void applyFixedSizesToBalls() {
        TextView[] balls = {lastNum1, lastNum2, lastNum3, lastNum4, lastNum5, lastNum6, lastBonus};
        for (TextView ball : balls) {
            if (ball != null) {
                applyFixedSizeToBall(ball);
            }
        }
    }

    /**
     * 개별 구슬에 고정 크기 적용
     */
    private void applyFixedSizeToBall(TextView ball) {
        if (ball == null) return;

        try {
            int ballSizePx = dpToPx(BALL_SIZE_DP);
            int marginPx = dpToPx(MARGIN_DP);

            // 크기 설정
            ViewGroup.LayoutParams params = ball.getLayoutParams();
            params.width = ballSizePx;
            params.height = ballSizePx;
            ball.setLayoutParams(params);

            // 텍스트 설정
            ball.setTextSize(TEXT_SIZE_SP);
            ball.setIncludeFontPadding(false);

            // 내부 패딩 (구슬 크기의 1/8)
            int innerPadding = ballSizePx / 8;
            ball.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);

            // 여백 설정
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams linearParams = (LinearLayout.LayoutParams) params;
                linearParams.setMargins(marginPx, 0, marginPx, 0);
            }

        } catch (Exception e) {
            android.util.Log.e("FixedBallSize", "구슬 크기 적용 실패", e);
        }
    }

    // ========================= Private Methods =========================

    /**
     * 로딩 상태 표시
     */
    private void showLoading(Boolean isLoading) {
        if (progressLast != null && isLoading != null) {
            progressLast.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 최신 회차 정보 UI에 적용
     */
    private void applyLastDraw(LottoDrawDto drawDto) {
        if (drawDto == null) return;

        try {
            if (drawDto.drwNo != null && tvLastDrawTitle != null) {
                String date = SafeUtils.safeString(drawDto.date);
                String meta = date.isEmpty() ?
                        "제 " + drawDto.drwNo + "회" :
                        "제 " + drawDto.drwNo + "회 • " + date;
                tvLastDrawTitle.setText(meta);
            }

            setBall(lastNum1, drawDto.n1, false);
            setBall(lastNum2, drawDto.n2, false);
            setBall(lastNum3, drawDto.n3, false);
            setBall(lastNum4, drawDto.n4, false);
            setBall(lastNum5, drawDto.n5, false);
            setBall(lastNum6, drawDto.n6, false);
            setBall(lastBonus, drawDto.bonus, true);
        } catch (Exception e) {
            handleError("번호 표시 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 로또 번호 볼 설정 (고정 크기)
     */
    private void setBall(TextView tv, Integer num, boolean bonus) {
        if (tv == null || num == null) return;

        // 안전한 번호 유효성 검사
        if (!SafeUtils.isValidLottoNumber(num)) {
            tv.setText("?");
            return;
        }

        tv.setText(String.valueOf(num));
        tv.setBackgroundResource(bonus ? R.drawable.bg_lotto_ball_bonus : R.drawable.bg_lotto_ball);

        // 고정 크기 적용
        applyFixedSizeToBall(tv);

        try {
            int bg = getColorForNumber(num);
            tv.setBackgroundTintList(ColorStateList.valueOf(bg));
            tv.setTextColor(getTextColorForBackground(bg));
        } catch (Exception e) {
            // 색상 적용 실패 시 기본 색상 사용
            tv.setBackgroundTintList(null);
            tv.setTextColor(Color.WHITE);
        }
    }

    // 자동 생성 번호 다이얼로그 관련 메서드들 제거됨 (새 기능 대기 중)

    /**
     * 번호별 색상 반환
     */
    private int getColorForNumber(int number) {
        if (SafeUtils.isInRange(number, 1, 10)) return Color.parseColor("#FBC02D");   // 노랑
        if (SafeUtils.isInRange(number, 11, 20)) return Color.parseColor("#1976D2");  // 파랑
        if (SafeUtils.isInRange(number, 21, 30)) return Color.parseColor("#D32F2F");  // 빨강
        if (SafeUtils.isInRange(number, 31, 40)) return Color.parseColor("#424242");  // 회색
        if (SafeUtils.isInRange(number, 41, 45)) return Color.parseColor("#388E3C");  // 초록
        return Color.parseColor("#9E9E9E"); // 기본 회색
    }

    /**
     * 배경색에 따른 텍스트 색상 반환
     */
    private int getTextColorForBackground(int backgroundColor) {
        if (backgroundColor == Color.parseColor("#FBC02D")) {
            return Color.BLACK; // 노랑 배경에는 검은 글씨
        }
        return Color.WHITE;
    }

    /**
     * DP를 PX로 변환
     */
    private int dpToPx(int dp) {
        try {
            float density = getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        } catch (Exception e) {
            return dp * 3; // 대략적인 기본값
        }
    }
}