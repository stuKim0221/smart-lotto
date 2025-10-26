package app.grapekim.smartlotto.ui.qr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.google.zxing.ResultPoint;

import java.util.List;

import app.grapekim.smartlotto.R;

/**
 * ZXing 기반 QR 스캐너 (16KB 페이지 크기 대응)
 *
 * 목적:
 * - Android 15+ 16KB 기기에서 ML Kit 대체
 * - 100% 자바 기반으로 네이티브 라이브러리 이슈 없음
 *
 * 사용 시나리오:
 * - DevicePageSizeDetector.is16KbPageSize() == true
 * - ML Kit 초기화 실패 시 폴백
 */
public class ZxingScanActivity extends AppCompatActivity {

    private static final String TAG = "ZxingScanActivity";
    public static final String EXTRA_QR_TEXT = "qr_text";
    public static final String EXTRA_SCAN_MODE = "SCAN_MODE";

    // 스캔 모드 상수 (QrScanActivity와 동일)
    public static final String SCAN_MODE_ADD_NUMBERS = "ADD_NUMBERS";
    public static final String SCAN_MODE_WINNING_CHECK = "WINNING_CHECK";

    private DecoratedBarcodeView barcodeView;
    private TextView tvGuide;
    private String scanMode;
    private boolean isScanning = true;

    // 카메라 권한 요청 런처
    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    initializeScanner();
                } else {
                    Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_zxing_scan);

        // Edge-to-Edge 설정 (setContentView 이후)
        setupEdgeToEdge();

        // Intent에서 스캔 모드 가져오기
        scanMode = getIntent().getStringExtra(EXTRA_SCAN_MODE);
        if (scanMode == null) {
            scanMode = SCAN_MODE_ADD_NUMBERS;
        }

        Log.i(TAG, "ZXing 스캐너 시작 - 모드: " + scanMode);

        // UI 초기화
        initializeViews();

        // 카메라 권한 체크 후 스캔 시작
        checkCameraPermissionAndStart();
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initializeScanner();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void initializeScanner() {
        // 스캔 시작
        startScanning();
    }

    private void setupEdgeToEdge() {
        Window window = getWindow();
        if (window == null) return;

        // Android 15(API 35) 미만에서만 색상 설정 (deprecated API 회피)
        if (Build.VERSION.SDK_INT < 35) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);

                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }

                // Android 15 미만에서만 색상 설정
                window.setStatusBarColor(Color.TRANSPARENT);
                window.setNavigationBarColor(Color.TRANSPARENT);
            } else {
                // Android R 미만
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                );
                getWindow().setStatusBarColor(Color.TRANSPARENT);
            }
        } else {
            // Android 15+에서는 EdgeToEdge.enable() 사용 권장
            // 또는 시스템이 자동으로 처리하도록 둠
            androidx.activity.EdgeToEdge.enable(this);
        }
    }

    private void initializeViews() {
        barcodeView = findViewById(R.id.barcode_scanner);
        tvGuide = findViewById(R.id.tv_guide);

        // 스캔 모드에 따른 가이드 텍스트 설정
        if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
            tvGuide.setText("로또 용지의 QR 코드를 스캔하세요\n(당첨 확인 모드)");
        } else {
            tvGuide.setText("로또 용지의 QR 코드를 스캔하세요");
        }
    }

    private void startScanning() {
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!isScanning) {
                    return;
                }

                if (result.getText() != null && !result.getText().isEmpty()) {
                    handleScanSuccess(result.getText());
                }
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
                // 스캔 진행 중 시각적 피드백 (선택사항)
            }
        });
    }

    private void handleScanSuccess(String qrText) {
        // 중복 호출 방지
        if (!isScanning) {
            return;
        }

        isScanning = false;
        barcodeView.pause();

        Log.i(TAG, "✅ ZXing QR 스캔 성공: " + qrText.substring(0, Math.min(50, qrText.length())));

        // 진동 피드백
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                            100,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    ));
                } else {
                    vibrator.vibrate(100);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "진동 실패", e);
        }

        // 스캔 모드에 따라 처리
        if (SCAN_MODE_WINNING_CHECK.equals(scanMode)) {
            // 당첨 확인 모드: QrResultActivity로 이동
            Intent intent = new Intent(this, app.grapekim.smartlotto.ui.qr.QrResultActivity.class);
            intent.putExtra(app.grapekim.smartlotto.ui.qr.QrResultActivity.EXTRA_QR_RAW_DATA, qrText);
            intent.putExtra("IS_WINNING_CHECK", true);  // 당첨 확인 모드 플래그
            startActivity(intent);
            finish();
        } else {
            // 번호 추가 모드: QrResultActivity로 이동 (당첨 확인 비활성화)
            Intent intent = new Intent(this, app.grapekim.smartlotto.ui.qr.QrResultActivity.class);
            intent.putExtra(app.grapekim.smartlotto.ui.qr.QrResultActivity.EXTRA_QR_RAW_DATA, qrText);
            intent.putExtra("IS_WINNING_CHECK", false);  // 일반 모드

            Toast.makeText(this, "QR 코드 스캔 완료!", Toast.LENGTH_SHORT).show();

            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null && isScanning) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pauseAndWait();  // pause 대신 pauseAndWait 사용 (더 안전)
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isScanning = false;
        if (barcodeView != null) {
            barcodeView.pause();
        }
        Log.d(TAG, "ZXing 스캐너 종료");
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "사용자가 스캔 취소");
        super.onBackPressed();
    }
}
