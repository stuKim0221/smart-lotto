package app.grapekim.smartlotto.ui.qr;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * QR 스캔을 위한 오버레이 뷰
 * - 반투명 배경으로 카메라 프리뷰 위에 표시
 * - 중앙에 투명한 스캔 영역
 * - 모서리 가이드라인과 애니메이션 효과
 */
public class QrScanOverlayView extends View {

    // 색상 설정
    private static final int OVERLAY_COLOR = 0x88000000; // 반투명 검정
    private static final int GUIDE_COLOR = 0xFFFFFFFF;   // 흰색 가이드라인
    private static final int CORNER_COLOR = 0xFF00FF00;  // 초록색 모서리

    // 크기 설정 (dp 단위로 정의하고 픽셀로 변환)
    private static final int SCAN_AREA_SIZE_DP = 250;    // 스캔 영역 크기
    private static final int CORNER_LENGTH_DP = 20;      // 모서리 길이
    private static final int CORNER_WIDTH_DP = 3;        // 모서리 두께
    private static final int GUIDE_STROKE_WIDTH_DP = 1;  // 가이드라인 두께

    // 애니메이션 설정
    private static final long ANIMATION_DURATION = 2000; // 2초
    private static final long ANIMATION_DELAY = 500;     // 0.5초 지연

    // Paint 객체들
    private Paint overlayPaint;
    private Paint guidePaint;
    private Paint cornerPaint;

    // 크기 및 위치
    private RectF scanArea;
    private float scanAreaSize;
    private float cornerLength;
    private float cornerWidth;

    // 애니메이션
    private ValueAnimator scanLineAnimator;
    private float scanLineY = 0f;

    public QrScanOverlayView(Context context) {
        super(context);
        init();
    }

    public QrScanOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QrScanOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // dp를 픽셀로 변환
        float density = getContext().getResources().getDisplayMetrics().density;
        scanAreaSize = SCAN_AREA_SIZE_DP * density;
        cornerLength = CORNER_LENGTH_DP * density;
        cornerWidth = CORNER_WIDTH_DP * density;

        // Paint 초기화
        setupPaints();

        // 스캔 영역 초기화
        scanArea = new RectF();

        // 애니메이션 설정
        setupAnimation();
    }

    private void setupPaints() {
        // 오버레이 Paint (반투명 배경)
        overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlayPaint.setColor(OVERLAY_COLOR);
        overlayPaint.setStyle(Paint.Style.FILL);

        // 가이드라인 Paint
        guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setColor(GUIDE_COLOR);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(GUIDE_STROKE_WIDTH_DP * getContext().getResources().getDisplayMetrics().density);
        guidePaint.setAlpha(100); // 약간 투명하게

        // 모서리 Paint
        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setColor(CORNER_COLOR);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(cornerWidth);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void setupAnimation() {
        // 스캔 라인 애니메이션
        scanLineAnimator = ValueAnimator.ofFloat(0f, 1f);
        scanLineAnimator.setDuration(ANIMATION_DURATION);
        scanLineAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scanLineAnimator.setRepeatMode(ValueAnimator.REVERSE);
        scanLineAnimator.setStartDelay(ANIMATION_DELAY);

        scanLineAnimator.addUpdateListener(animation -> {
            scanLineY = (Float) animation.getAnimatedValue();
            invalidate(); // 화면 다시 그리기
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 스캔 영역을 화면 중앙에 배치
        float centerX = w / 2f;
        float centerY = h / 2f;
        float halfSize = scanAreaSize / 2f;

        scanArea.set(
                centerX - halfSize,
                centerY - halfSize,
                centerX + halfSize,
                centerY + halfSize
        );

        // 애니메이션 시작
        if (!scanLineAnimator.isStarted()) {
            scanLineAnimator.start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (scanArea.isEmpty()) {
            return;
        }

        // 1. 반투명 배경 그리기 (스캔 영역 제외)
        drawOverlayBackground(canvas);

        // 2. 스캔 영역 가이드라인 그리기
        drawScanAreaGuide(canvas);

        // 3. 모서리 가이드라인 그리기
        drawCornerGuides(canvas);

        // 4. 스캔 라인 애니메이션 그리기
        drawScanLine(canvas);
    }

    /**
     * 반투명 배경 그리기 (스캔 영역만 투명하게)
     */
    private void drawOverlayBackground(Canvas canvas) {
        // 전체 화면을 반투명으로 채움
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);

        // 스캔 영역만 투명하게 만들기 (PorterDuff 사용)
        Paint clearPaint = new Paint();
        clearPaint.setColor(Color.TRANSPARENT);
        clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(scanArea, 12f, 12f, clearPaint);
    }

    /**
     * 스캔 영역 가이드라인 그리기
     */
    private void drawScanAreaGuide(Canvas canvas) {
        // 둥근 모서리 사각형으로 스캔 영역 외곽선
        canvas.drawRoundRect(scanArea, 12f, 12f, guidePaint);
    }

    /**
     * 모서리 가이드라인 그리기 (4개 모서리)
     */
    private void drawCornerGuides(Canvas canvas) {
        float left = scanArea.left;
        float top = scanArea.top;
        float right = scanArea.right;
        float bottom = scanArea.bottom;

        // 좌상단 모서리
        canvas.drawLine(left, top + cornerLength, left, top, cornerPaint);
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint);

        // 우상단 모서리
        canvas.drawLine(right - cornerLength, top, right, top, cornerPaint);
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint);

        // 우하단 모서리
        canvas.drawLine(right, bottom - cornerLength, right, bottom, cornerPaint);
        canvas.drawLine(right, bottom, right - cornerLength, bottom, cornerPaint);

        // 좌하단 모서리
        canvas.drawLine(left + cornerLength, bottom, left, bottom, cornerPaint);
        canvas.drawLine(left, bottom, left, bottom - cornerLength, cornerPaint);
    }

    /**
     * 스캔 라인 애니메이션 그리기
     */
    private void drawScanLine(Canvas canvas) {
        if (scanLineAnimator.isRunning()) {
            // 스캔 라인 위치 계산
            float lineY = scanArea.top + (scanArea.height() * scanLineY);

            // 그라디언트 효과를 위한 Paint
            Paint scanLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            scanLinePaint.setColor(CORNER_COLOR);
            scanLinePaint.setAlpha(150);
            scanLinePaint.setStrokeWidth(3f);

            // 수평선 그리기
            canvas.drawLine(scanArea.left + 10, lineY, scanArea.right - 10, lineY, scanLinePaint);
        }
    }

    /**
     * 스캔 성공 시 애니메이션 효과
     */
    public void showScanSuccess() {
        // 기존 애니메이션 중단
        if (scanLineAnimator.isRunning()) {
            scanLineAnimator.cancel();
        }

        // 성공 효과 (모서리 색상 변경)
        cornerPaint.setColor(0xFF00FF00); // 밝은 초록색

        // 약간의 깜빡임 효과
        ValueAnimator successAnimator = ValueAnimator.ofFloat(1f, 0.3f, 1f);
        successAnimator.setDuration(300);
        successAnimator.addUpdateListener(animation -> {
            float alpha = (Float) animation.getAnimatedValue();
            cornerPaint.setAlpha((int) (255 * alpha));
            invalidate();
        });
        successAnimator.start();
    }

    /**
     * 스캔 실패 시 애니메이션 효과
     */
    public void showScanError() {
        // 오류 효과 (모서리 색상을 빨간색으로)
        cornerPaint.setColor(0xFFFF0000); // 빨간색

        ValueAnimator errorAnimator = ValueAnimator.ofFloat(1f, 0.5f, 1f, 0.5f, 1f);
        errorAnimator.setDuration(500);
        errorAnimator.addUpdateListener(animation -> {
            float alpha = (Float) animation.getAnimatedValue();
            cornerPaint.setAlpha((int) (255 * alpha));
            invalidate();
        });
        errorAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // 원래 색상으로 복원
                cornerPaint.setColor(CORNER_COLOR);
                cornerPaint.setAlpha(255);
                // 스캔 라인 애니메이션 재시작
                if (!scanLineAnimator.isRunning()) {
                    scanLineAnimator.start();
                }
            }
        });
        errorAnimator.start();
    }

    /**
     * 애니메이션 정리
     */
    public void cleanup() {
        if (scanLineAnimator != null && scanLineAnimator.isRunning()) {
            scanLineAnimator.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }
}