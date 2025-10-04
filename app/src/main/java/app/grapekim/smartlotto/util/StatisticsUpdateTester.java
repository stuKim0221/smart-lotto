package app.grapekim.smartlotto.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.grapekim.smartlotto.data.CsvUpdateManager;

/**
 * 자동 통계 업데이트 시스템을 테스트하는 유틸리티 클래스
 */
public class StatisticsUpdateTester {
    private static final String TAG = "StatisticsUpdateTester";

    /**
     * 자동 업데이트 시스템의 전체적인 동작을 테스트
     */
    public static void runComprehensiveTest(Context context) {
        Log.i(TAG, "========== 통계 자동 업데이트 시스템 종합 테스트 시작 ==========");

        CsvUpdateManager csvUpdateManager = new CsvUpdateManager(context);

        // 테스트용 리스너 생성
        TestUpdateListener testListener = new TestUpdateListener();

        // 1단계: 리스너 등록 테스트
        Log.i(TAG, "1단계: 리스너 등록 테스트");
        csvUpdateManager.addUpdateListener(testListener);
        Log.i(TAG, "테스트 리스너 등록 완료");

        // 2단계: 즉시 업데이트 이벤트 테스트
        Log.i(TAG, "2단계: 즉시 업데이트 이벤트 테스트");
        csvUpdateManager.notifyUpdateListeners(true);

        // 3단계: 지연 업데이트 이벤트 테스트 (실제 앱 시나리오 시뮬레이션)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "3단계: 지연 업데이트 이벤트 테스트 (3초 후)");
            csvUpdateManager.notifyUpdateListeners(true);

            // 4단계: 실패 시나리오 테스트
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.i(TAG, "4단계: 실패 시나리오 테스트 (6초 후)");
                csvUpdateManager.notifyUpdateListeners(false);

                // 5단계: 리스너 제거 테스트
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.i(TAG, "5단계: 리스너 제거 테스트 (9초 후)");
                    csvUpdateManager.removeUpdateListener(testListener);
                    Log.i(TAG, "테스트 리스너 제거 완료");

                    // 6단계: 제거 후 이벤트 테스트 (리스너가 호출되면 안 됨)
                    Log.i(TAG, "6단계: 리스너 제거 후 이벤트 테스트 (호출되면 안 됨)");
                    csvUpdateManager.notifyUpdateListeners(true);

                    Log.i(TAG, "========== 통계 자동 업데이트 시스템 종합 테스트 완료 ==========");
                }, 3000);
            }, 3000);
        }, 3000);
    }

    /**
     * CSV 업데이트 매니저의 상태 정보를 출력
     */
    public static void logCsvUpdateManagerStatus(CsvUpdateManager csvUpdateManager) {
        if (csvUpdateManager == null) {
            Log.w(TAG, "CsvUpdateManager가 null입니다");
            return;
        }

        Log.i(TAG, "=== CsvUpdateManager 상태 정보 ===");

        try {
            boolean needsUpdate = csvUpdateManager.needsUpdate();
            long lastUpdateTime = csvUpdateManager.getLastAutoUpdateTime();
            int daysUntilNext = csvUpdateManager.getDaysUntilNextUpdate();

            Log.i(TAG, "업데이트 필요 여부: " + needsUpdate);
            Log.i(TAG, "마지막 자동 업데이트: " +
                  (lastUpdateTime > 0 ? new java.util.Date(lastUpdateTime).toString() : "없음"));
            Log.i(TAG, "다음 업데이트까지 일수: " + daysUntilNext + "일");

            // CSV 파일 정보도 로그
            csvUpdateManager.logCsvFileInfo();

        } catch (Exception e) {
            Log.e(TAG, "CsvUpdateManager 상태 확인 중 오류", e);
        }

        Log.i(TAG, "================================");
    }

    /**
     * 실제 CSV 업데이트를 시뮬레이션하는 테스트
     */
    public static void simulateRealUpdate(Context context) {
        Log.i(TAG, "=== 실제 CSV 업데이트 시뮬레이션 시작 ===");

        CsvUpdateManager csvUpdateManager = new CsvUpdateManager(context);

        // 실제 FavoritesFragment와 같은 동작을 하는 테스트 리스너
        CsvUpdateManager.DataUpdateListener simulationListener = new CsvUpdateManager.DataUpdateListener() {
            @Override
            public void onDataUpdated(boolean success) {
                Log.i(TAG, "시뮬레이션 리스너 호출됨 - 성공: " + success);

                if (success) {
                    // 실제 FavoritesFragment가 하는 일들을 시뮬레이션
                    Log.i(TAG, "통계 새로고침 시뮬레이션 중...");

                    // 캐시 무효화 시뮬레이션
                    Log.d(TAG, "CSV 서비스 캐시 무효화 시뮬레이션");

                    // 통계 재계산 시뮬레이션
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Log.i(TAG, "통계 재계산 완료 시뮬레이션");
                        Log.i(TAG, "UI 업데이트 완료 시뮬레이션");
                    }, 1000);
                }
            }
        };

        // 리스너 등록
        csvUpdateManager.addUpdateListener(simulationListener);

        // 실제 업데이트 시나리오 시뮬레이션
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 2초 대기

                Log.i(TAG, "백그라운드 CSV 업데이트 시작 시뮬레이션...");

                // 실제 업데이트는 시간이 오래 걸리므로 시뮬레이션
                Thread.sleep(3000); // 3초 대기 (실제 네트워크 요청 시뮬레이션)

                // UI 스레드에서 리스너 통지
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.i(TAG, "CSV 업데이트 완료, 리스너들에게 통지");
                    csvUpdateManager.notifyUpdateListeners(true);

                    // 정리
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        csvUpdateManager.removeUpdateListener(simulationListener);
                        Log.i(TAG, "=== 실제 CSV 업데이트 시뮬레이션 완료 ===");
                    }, 2000);
                });

            } catch (InterruptedException e) {
                Log.e(TAG, "시뮬레이션 스레드 중단", e);
            }
        }).start();
    }

    /**
     * 테스트용 데이터 업데이트 리스너
     */
    private static class TestUpdateListener implements CsvUpdateManager.DataUpdateListener {
        private int callCount = 0;

        @Override
        public void onDataUpdated(boolean success) {
            callCount++;
            Log.i(TAG, "테스트 리스너 호출 #" + callCount + " - 성공: " + success);
            Log.i(TAG, "호출 시간: " + new java.util.Date());

            if (success) {
                Log.d(TAG, "성공적인 업데이트 처리 시뮬레이션");
            } else {
                Log.w(TAG, "실패한 업데이트 처리 시뮬레이션");
            }
        }
    }
}