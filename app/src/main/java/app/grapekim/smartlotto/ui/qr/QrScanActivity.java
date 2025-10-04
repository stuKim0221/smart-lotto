package app.grapekim.smartlotto.ui.qr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;

import app.grapekim.smartlotto.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ExperimentalGetImage
public class QrScanActivity extends AppCompatActivity {

    private static final String TAG = "QrScanActivity";
    public static final String EXTRA_QR_TEXT = "qr_text";
    public static final String EXTRA_SCAN_MODE = "SCAN_MODE";

    // 스캔 모드 상수
    public static final String SCAN_MODE_ADD_NUMBERS = "ADD_NUMBERS";
    public static final String SCAN_MODE_WINNING_CHECK = "WINNING_CHECK";

    // 상수 정의
    private static final long SCAN_SUCCESS_DELAY_MS = 500L;
    private static final int CAMERA_INIT_RETRY_COUNT = 3;
    private static final long CAMERA_INIT_RETRY_DELAY_MS = 1000L;

    // 스캔 모드
    private String scanMode;

    // UI 요소들
    private PreviewView previewView;
    private TextView tvGuide;
    private QrScanOverlayView scanOverlay;

    // 안전 영역 가이드라인
    private Guideline guidelineTopSafe;
    private Guideline guidelineBottomSafe;

    // 카메라 관련
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private BarcodeScanner scanner;

    // 스캔 상태 관리 (Thread-safe)
    private final AtomicBoolean isScanning = new AtomicBoolean(true);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isScanCompleted = new AtomicBoolean(false);

    // 핸들러와 재시도 관련
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int cameraInitRetryCount = 0;

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    initializeCameraWithRetry();
                } else {
                    handleCameraPermissionDenied();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Intent에서 스캔 모드 받아오기
        scanMode = getIntent().getStringExtra(EXTRA_SCAN_MODE);
        if (scanMode == null) {
            scanMode = SCAN_MODE_ADD_NUMBERS; // 기본값: 번호 추가 모드
        }

        setContentView(R.layout.activity_qr_scan);

        // setContentView 후에 시스템 바 설정 (DecorView 준비 완료 후)
        setupSystemBars();

        // 안전 영역 처리
        initializeSafeAreaGuidelines();
        setupSystemUIInsets();

        initializeViews();
        setupClickListeners();
        initializeScanner();
        checkCameraPermission();
    }

    /**
     * 시스템 바 설정 (MainActivity와 동일)
     */
    private void setupSystemBars() {
        Window window = getWindow();
        if (window == null) return;

        // 상태바와 네비게이션바 투명하게
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        // 에지 투 에지 모드 활성화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decorView = window.getDecorView();
            if (decorView != null) {
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
            }
        }

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
     * 시스템 UI 인셋 처리 (MainActivity와 동일)
     */
    private void setupSystemUIInsets() {
        View rootView = findViewById(android.R.id.content);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                android.graphics.Insets systemBars = windowInsets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets displayCutout = windowInsets.getInsets(WindowInsets.Type.displayCutout());

                int topInset = Math.max(systemBars.top, displayCutout.top);
                int bottomInset = Math.max(systemBars.bottom, displayCutout.bottom);

                updateGuidelines(topInset, bottomInset);

                return WindowInsets.CONSUMED;
            });
        } else {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView,
                    (view, windowInsets) -> {
                        androidx.core.graphics.Insets systemBars = windowInsets.getInsets(
                                androidx.core.view.WindowInsetsCompat.Type.systemBars());

                        updateGuidelines(systemBars.top, systemBars.bottom);

                        return androidx.core.view.WindowInsetsCompat.CONSUMED;
                    });
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
                    controller.setSystemBarsAppearance(0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                } else {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateSystemBarAppearanceOld();
        }
    }

    /**
     * Android M 이상에서 시스템 바 외관 업데이트
     */
    private void updateSystemBarAppearanceOld() {
        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();
        if (decorView == null) return;

        int flags = decorView.getSystemUiVisibility();

        if (isDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }

        decorView.setSystemUiVisibility(flags);
    }

    /**
     * 다크모드 확인
     */
    private boolean isDarkMode() {
        int currentMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        tvGuide = findViewById(R.id.tvGuide);
        scanOverlay = findViewById(R.id.scanOverlay);

        // 헤더 텍스트 설정
        TextView tvHeader = findViewById(R.id.tvHeader);
        if (tvHeader != null) {
            if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
                tvHeader.setText("QR 당첨 확인");
            } else {
                tvHeader.setText("QR 코드 스캔");
            }
        }

        // 초기 상태 설정
        if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
            updateGuideText("로또 용지의 QR 코드를 스캔하여 당첨을 확인하세요");
        } else {
            updateGuideText("QR 코드를 가이드라인 안에 맞춰주세요");
        }
    }

    private void setupClickListeners() {
        // 프리뷰 터치로 재시작 (스캔 실패 후)
        if (previewView != null) {
            previewView.setOnClickListener(v -> {
                if (!isScanning.get() && !isScanCompleted.get()) {
                    restartScanning();
                }
            });
        }
    }

    private void initializeScanner() {
        cameraExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CameraThread");
            t.setDaemon(true);
            return t;
        });

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        scanner = BarcodeScanning.getClient(options);
        Log.d(TAG, "QR 스캐너 초기화 완료");
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "카메라 권한 요청");
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        } else {
            Log.d(TAG, "카메라 권한 이미 허용됨");
            initializeCameraWithRetry();
        }
    }

    private void handleCameraPermissionDenied() {
        runOnMainThread(() -> {
            showToast("카메라 권한이 필요합니다");
            updateGuideText("카메라 권한을 허용해주세요");
        });

        // 3초 후 액티비티 종료
        mainHandler.postDelayed(() -> finishWithResult(RESULT_CANCELED, null), 3000);
    }

    private void initializeCameraWithRetry() {
        cameraInitRetryCount++;
        startCamera();
    }

    private void startCamera() {
        if (isDestroyed() || isFinishing()) {
            return;
        }

        runOnMainThread(() -> {
            updateGuideText("카메라를 시작하는 중...");
        });

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                if (isDestroyed() || isFinishing()) {
                    return;
                }

                cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);

                runOnMainThread(() -> {
                    updateGuideText("QR 코드를 가이드라인 안에 맞춰주세요");
                });

                Log.d(TAG, "카메라 시작 성공");
                cameraInitRetryCount = 0; // 성공시 재시도 카운트 리셋

            } catch (Exception e) {
                Log.e(TAG, "카메라 초기화 실패 (시도: " + cameraInitRetryCount + ")", e);
                handleCameraInitFailure();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleCameraInitFailure() {
        if (cameraInitRetryCount < CAMERA_INIT_RETRY_COUNT && !isDestroyed() && !isFinishing()) {
            Log.d(TAG, "카메라 초기화 재시도 예정 (" + cameraInitRetryCount + "/" + CAMERA_INIT_RETRY_COUNT + ")");

            runOnMainThread(() -> {
                updateGuideText("카메라 초기화 재시도 중... (" + cameraInitRetryCount + "/" + CAMERA_INIT_RETRY_COUNT + ")");
            });

            mainHandler.postDelayed(this::initializeCameraWithRetry, CAMERA_INIT_RETRY_DELAY_MS);
        } else {
            runOnMainThread(() -> {
                showToast("카메라 초기화에 실패했습니다");
                updateGuideText("카메라 초기화에 실패했습니다. 앱을 재시작해보세요.");
            });

            mainHandler.postDelayed(() -> finishWithResult(RESULT_CANCELED, null), 2000);
        }
    }

    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        try {
            // 기존 바인딩 해제
            cameraProvider.unbindAll();

            // Preview 설정
            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(1280, 720))
                    .build();

            if (previewView != null) {
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
            }

            // ImageAnalysis 설정
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

            // 카메라 선택 (후면 카메라)
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

            // 카메라 바인딩
            cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);

            Log.d(TAG, "카메라 바인딩 성공");

        } catch (Exception e) {
            Log.e(TAG, "카메라 바인딩 실패", e);
            throw e; // 상위로 예외 전파
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        try {
            // 스캔이 중단되었거나 이미 완료되었거나 처리 중이면 건너뛰기
            if (!isScanning.get() || isScanCompleted.get() || isProcessing.get()) {
                imageProxy.close(); // 조건 체크 후 바로 닫기
                return;
            }

            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            // 처리 중 플래그 설정
            if (!isProcessing.compareAndSet(false, true)) {
                imageProxy.close(); // 이미 처리 중이면 바로 닫기
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            scanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        try {
                            if (barcodes != null && !barcodes.isEmpty() &&
                                    isScanning.get() && !isScanCompleted.get()) {

                                // 첫 번째 QR 코드 처리
                                Barcode firstBarcode = barcodes.get(0);
                                String rawValue = firstBarcode.getRawValue();

                                if (rawValue != null && !rawValue.trim().isEmpty()) {
                                    Log.d(TAG, "QR 코드 스캔 성공: " + rawValue.length() + " 글자");
                                    onScanSuccess(rawValue);
                                } else {
                                    Log.w(TAG, "QR 코드 내용이 비어있음");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "스캔 성공 처리 중 오류", e);
                        } finally {
                            isProcessing.set(false);
                            imageProxy.close(); // 성공 처리 후 이미지 닫기
                        }
                    })
                    .addOnFailureListener(exception -> {
                        Log.w(TAG, "QR 스캔 실패", exception);
                        isProcessing.set(false);
                        imageProxy.close(); // 실패 처리 후 이미지 닫기
                    });

        } catch (Exception e) {
            Log.e(TAG, "이미지 분석 중 오류", e);
            isProcessing.set(false);
            imageProxy.close(); // 예외 발생 시 이미지 닫기
        }
        // finally 블록 제거 - 각 경로에서 개별적으로 처리
    }

    /**
     * QR 스캔 성공 처리
     */
    private void onScanSuccess(String qrData) {
        // 중복 처리 방지
        if (!isScanCompleted.compareAndSet(false, true)) {
            return; // 이미 처리됨
        }

        // 스캔 중단
        isScanning.set(false);

        // 메인 스레드에서 UI 업데이트
        runOnMainThread(() -> {
            try {
                // UI 업데이트
                if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
                    updateGuideText("당첨 결과 확인 중...");
                } else {
                    updateGuideText("QR 코드 인식 완료!");
                }

                // 약간의 지연 후 결과 처리
                mainHandler.postDelayed(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        Log.d(TAG, "QR 스캔 완료, 결과 반환: " + qrData.substring(0, Math.min(qrData.length(), 50)) + "...");

                        if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
                            // 당첨 확인 모드: QrResultActivity로 이동하되 당첨 확인 모드 전달
                            navigateToResultActivity(qrData, true);
                        } else {
                            // 기본 모드: 결과만 반환
                            finishWithResult(RESULT_OK, qrData);
                        }
                    }
                }, SCAN_SUCCESS_DELAY_MS);

            } catch (Exception e) {
                Log.e(TAG, "스캔 성공 처리 중 오류", e);
                handleScanError("결과 처리 중 오류가 발생했습니다");
            }
        });
    }

    /**
     * 스캔 오류 처리
     */
    private void handleScanError(String message) {
        runOnMainThread(() -> {
            showToast(message);
            updateGuideText("화면을 터치하여 다시 시도");
            isScanning.set(false);
        });
    }

    /**
     * 스캔 재시작
     */
    private void restartScanning() {
        if (isScanCompleted.get()) {
            return; // 이미 완료된 경우 재시작 불가
        }

        Log.d(TAG, "스캔 재시작");
        isScanning.set(true);
        isProcessing.set(false);
        runOnMainThread(() -> {
            updateGuideText("QR 코드를 가이드라인 안에 맞춰주세요");
        });
    }

    /**
     * QrResultActivity로 이동
     */
    private void navigateToResultActivity(String qrData, boolean isWinningCheck) {
        try {
            Intent intent = new Intent(this, app.grapekim.smartlotto.ui.qr.QrResultActivity.class);
            intent.putExtra("qr_raw_data", qrData);  // QrResultActivity의 EXTRA_QR_RAW_DATA와 일치하는 키 사용
            intent.putExtra("IS_WINNING_CHECK", isWinningCheck);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "QrResultActivity 이동 중 오류", e);
            handleScanError("결과 화면을 열 수 없습니다");
        }
    }

    /**
     * 결과와 함께 액티비티 종료
     */
    private void finishWithResult(int resultCode, String qrData) {
        try {
            Intent resultIntent = new Intent();
            if (qrData != null) {
                resultIntent.putExtra(EXTRA_QR_TEXT, qrData);
            }
            setResult(resultCode, resultIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "결과 설정 중 오류", e);
            finish();
        }
    }

    /**
     * 메인 스레드에서 안전하게 실행
     */
    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!isDestroyed() && !isFinishing()) {
                runnable.run();
            }
        } else {
            mainHandler.post(() -> {
                if (!isDestroyed() && !isFinishing()) {
                    runnable.run();
                }
            });
        }
    }

    /**
     * 가이드 텍스트 업데이트
     */
    private void updateGuideText(String text) {
        if (tvGuide != null && text != null) {
            tvGuide.setText(text);
        }
    }

    /**
     * 토스트 메시지 표시
     */
    private void showToast(String message) {
        if (message != null && !isDestroyed() && !isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 액티비티가 다시 활성화되면 스캔 재시작
        if (!isScanning.get() && !isScanCompleted.get() && cameraProvider != null) {
            restartScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 액티비티가 비활성화되면 스캔 중단 (완료된 경우 제외)
        if (!isScanCompleted.get()) {
            isScanning.set(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "액티비티 종료, 리소스 정리 시작");

        // 스캔 중단
        isScanning.set(false);
        isProcessing.set(false);
        isScanCompleted.set(true);

        // Handler 콜백 제거
        mainHandler.removeCallbacksAndMessages(null);

        // 카메라 바인딩 해제
        releaseCameraProvider();

        // Scanner 리소스 해제
        releaseScanner();

        // ExecutorService 안전하게 종료
        shutdownExecutor();

        Log.d(TAG, "리소스 정리 완료");
    }

    private void releaseCameraProvider() {
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
                cameraProvider = null;
                Log.d(TAG, "카메라 바인딩 해제 완료");
            } catch (Exception e) {
                Log.w(TAG, "카메라 바인딩 해제 중 오류", e);
            }
        }
    }

    private void releaseScanner() {
        if (scanner != null) {
            try {
                scanner.close();
                scanner = null;
                Log.d(TAG, "스캐너 리소스 해제 완료");
            } catch (Exception e) {
                Log.w(TAG, "스캐너 리소스 해제 중 오류", e);
            }
        }
    }

    private void shutdownExecutor() {
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            try {
                cameraExecutor.shutdown();
                if (!cameraExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    cameraExecutor.shutdownNow();
                    if (!cameraExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "ExecutorService가 정상적으로 종료되지 않음");
                    }
                }
                Log.d(TAG, "ExecutorService 종료 완료");
            } catch (InterruptedException e) {
                cameraExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                Log.w(TAG, "ExecutorService 종료 중 인터럽트 발생", e);
            } catch (Exception e) {
                Log.w(TAG, "ExecutorService 종료 중 오류", e);
            } finally {
                cameraExecutor = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "뒤로가기 버튼 누름");
        super.onBackPressed();
        finishWithResult(RESULT_CANCELED, null);
    }
}