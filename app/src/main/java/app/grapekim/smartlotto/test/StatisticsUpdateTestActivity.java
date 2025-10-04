package app.grapekim.smartlotto.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.data.service.CsvLottoDataService;
import app.grapekim.smartlotto.data.service.LottoStatisticsCalculator;
import app.grapekim.smartlotto.data.model.LottoStatistics;

import java.util.concurrent.CompletableFuture;

/**
 * 통계 자동 업데이트 시스템을 테스트하기 위한 간단한 테스트 액티비티
 */
public class StatisticsUpdateTestActivity extends Activity implements CsvUpdateManager.DataUpdateListener {

    private static final String TAG = "StatisticsUpdateTest";

    private CsvUpdateManager csvUpdateManager;
    private CsvLottoDataService csvLottoDataService;

    private TextView tvStatus;
    private TextView tvDataCount;
    private TextView tvLastUpdate;
    private Button btnTriggerUpdate;
    private Button btnClearCache;
    private Button btnTestListener;

    private int updateCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 간단한 레이아웃 생성
        createTestLayout();

        // 서비스 초기화
        initializeServices();

        // UI 초기화
        updateStatus("테스트 초기화 완료");
        loadInitialData();
    }

    private void createTestLayout() {
        // 코드로 간단한 레이아웃 생성
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 제목
        TextView title = new TextView(this);
        title.setText("통계 자동 업데이트 테스트");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);

        // 상태 표시
        tvStatus = new TextView(this);
        tvStatus.setText("상태: 초기화 중...");
        tvStatus.setPadding(0, 10, 0, 10);
        layout.addView(tvStatus);

        // 데이터 개수 표시
        tvDataCount = new TextView(this);
        tvDataCount.setText("데이터 개수: 로딩 중...");
        tvDataCount.setPadding(0, 10, 0, 10);
        layout.addView(tvDataCount);

        // 마지막 업데이트 시간
        tvLastUpdate = new TextView(this);
        tvLastUpdate.setText("마지막 업데이트: 확인 중...");
        tvLastUpdate.setPadding(0, 10, 0, 20);
        layout.addView(tvLastUpdate);

        // 업데이트 트리거 버튼
        btnTriggerUpdate = new Button(this);
        btnTriggerUpdate.setText("수동 업데이트 테스트");
        btnTriggerUpdate.setOnClickListener(this::onTriggerUpdateClick);
        layout.addView(btnTriggerUpdate);

        // 캐시 클리어 버튼
        btnClearCache = new Button(this);
        btnClearCache.setText("캐시 클리어 테스트");
        btnClearCache.setOnClickListener(this::onClearCacheClick);
        layout.addView(btnClearCache);

        // 리스너 테스트 버튼
        btnTestListener = new Button(this);
        btnTestListener.setText("리스너 직접 테스트");
        btnTestListener.setOnClickListener(this::onTestListenerClick);
        layout.addView(btnTestListener);

        setContentView(layout);
    }

    private void initializeServices() {
        csvUpdateManager = new CsvUpdateManager(this);
        csvLottoDataService = new CsvLottoDataService(this);

        // 리스너 등록
        csvUpdateManager.addUpdateListener(this);

        Log.d(TAG, "서비스 초기화 완료 - 리스너 등록됨");
    }

    private void loadInitialData() {
        CompletableFuture.supplyAsync(() -> {
            try {
                int dataCount = csvLottoDataService.getDataCount();
                long lastUpdateTime = csvUpdateManager.getLastAutoUpdateTime();
                return new Object[]{dataCount, lastUpdateTime};
            } catch (Exception e) {
                Log.e(TAG, "초기 데이터 로딩 실패", e);
                return new Object[]{0, 0L};
            }
        }).thenAccept(result -> {
            runOnUiThread(() -> {
                int dataCount = (Integer) result[0];
                long lastUpdateTime = (Long) result[1];

                updateDataCount(dataCount);
                updateLastUpdateTime(lastUpdateTime);
                updateStatus("초기 데이터 로딩 완료");
            });
        });
    }

    private void onTriggerUpdateClick(View view) {
        updateStatus("수동 업데이트 시작...");
        btnTriggerUpdate.setEnabled(false);

        new Thread(() -> {
            try {
                boolean success = csvUpdateManager.forceUpdateCsvFile();

                runOnUiThread(() -> {
                    btnTriggerUpdate.setEnabled(true);
                    if (success) {
                        updateStatus("수동 업데이트 완료! 리스너 알림 대기 중...");
                        Toast.makeText(this, "업데이트 성공!", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus("수동 업데이트 실패");
                        Toast.makeText(this, "업데이트 실패", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "수동 업데이트 오류", e);
                runOnUiThread(() -> {
                    btnTriggerUpdate.setEnabled(true);
                    updateStatus("수동 업데이트 오류: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onClearCacheClick(View view) {
        updateStatus("캐시 클리어 중...");

        csvLottoDataService.clearCache();

        // 데이터 다시 로딩
        CompletableFuture.supplyAsync(() -> {
            return csvLottoDataService.getDataCount();
        }).thenAccept(dataCount -> {
            runOnUiThread(() -> {
                updateDataCount(dataCount);
                updateStatus("캐시 클리어 및 데이터 재로딩 완료");
                Toast.makeText(this, "캐시가 클리어되었습니다", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void onTestListenerClick(View view) {
        updateStatus("리스너 직접 테스트 중...");

        // 직접 리스너 이벤트 트리거
        csvUpdateManager.notifyUpdateListeners(true);

        Toast.makeText(this, "리스너 이벤트 트리거됨", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDataUpdated(boolean success) {
        updateCount++;

        Log.d(TAG, "데이터 업데이트 알림 수신! 횟수: " + updateCount + ", 성공: " + success);

        runOnUiThread(() -> {
            String message = "자동 업데이트 알림 #" + updateCount + " (성공: " + success + ")";
            updateStatus(message);

            if (success) {
                // 데이터 개수 다시 확인
                CompletableFuture.supplyAsync(() -> {
                    csvLottoDataService.clearCache(); // 최신 데이터를 위해 캐시 클리어
                    return csvLottoDataService.getDataCount();
                }).thenAccept(dataCount -> {
                    runOnUiThread(() -> {
                        updateDataCount(dataCount);
                        updateLastUpdateTime(System.currentTimeMillis());
                    });
                });

                Toast.makeText(this, "통계 자동 업데이트 완료!", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateStatus(String status) {
        if (tvStatus != null) {
            tvStatus.setText("상태: " + status);
        }
    }

    private void updateDataCount(int count) {
        if (tvDataCount != null) {
            tvDataCount.setText("데이터 개수: " + count + "개");
        }
    }

    private void updateLastUpdateTime(long timestamp) {
        if (tvLastUpdate != null) {
            String timeStr = timestamp > 0 ?
                new java.util.Date(timestamp).toString() : "없음";
            tvLastUpdate.setText("마지막 업데이트: " + timeStr);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 리스너 해제
        if (csvUpdateManager != null) {
            csvUpdateManager.removeUpdateListener(this);
            Log.d(TAG, "리스너 해제 완료");
        }
    }
}