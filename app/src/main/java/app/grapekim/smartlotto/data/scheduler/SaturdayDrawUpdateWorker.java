package app.grapekim.smartlotto.data.scheduler;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;

import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.util.LottoDrawCalculator;
import app.grapekim.smartlotto.data.notification.UpdateNotificationManager;

/**
 * 매주 토요일 9시에 실행되는 자동 업데이트 워커
 * 로또 발표 후 최신 데이터를 자동으로 가져와서 업데이트
 */
public class SaturdayDrawUpdateWorker extends Worker {
    private static final String TAG = "SaturdayDrawUpdateWorker";

    public SaturdayDrawUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "🕘 토요일 9시 자동 업데이트 시작!");

        try {
            Context context = getApplicationContext();

            // 1단계: 현재 상황 확인
            logCurrentStatus();

            // 2단계: 최신 회차 확인
            int latestDraw = checkLatestDraw();

            // 3단계: CSV 데이터 업데이트 실행
            boolean updateSuccess = performDataUpdate(context);

            // 4단계: 업데이트 결과 알림
            notifyUpdateResult(context, updateSuccess, latestDraw);

            // 5단계: 결과 반환
            if (updateSuccess) {
                Log.i(TAG, "✅ 토요일 자동 업데이트 성공!");
                return Result.success();
            } else {
                Log.w(TAG, "⚠️ 토요일 자동 업데이트 부분 실패 (재시도 예정)");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 토요일 자동 업데이트 실패", e);
            return Result.failure();
        }
    }

    /**
     * 현재 상황 로깅
     */
    private void logCurrentStatus() {
        try {
            Calendar now = Calendar.getInstance();
            String currentTime = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH),
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    now.get(Calendar.SECOND));

            Log.i(TAG, "=== 토요일 자동 업데이트 상황 ===");
            Log.i(TAG, "현재 시간: " + currentTime);
            Log.i(TAG, "요일: " + getDayOfWeekString(now.get(Calendar.DAY_OF_WEEK)));

            boolean isBroadcastTime = DrawResultScheduler.isCurrentlyBroadcastTime();
            Log.i(TAG, "발표 시간대: " + (isBroadcastTime ? "✅ 맞음" : "❌ 아님"));

        } catch (Exception e) {
            Log.w(TAG, "현재 상황 로깅 실패", e);
        }
    }

    /**
     * 최신 회차 확인
     * @return 최신 회차 번호
     */
    private int checkLatestDraw() {
        try {
            int expectedDraw = LottoDrawCalculator.getCurrentExpectedDrawNumber();
            int availableDraw = LottoDrawCalculator.getLatestAvailableDrawNumber();

            Log.i(TAG, "=== 최신 회차 확인 ===");
            Log.i(TAG, String.format("예상 회차: %d회차", expectedDraw));
            Log.i(TAG, String.format("실제 발표된 회차: %d회차", availableDraw));

            if (availableDraw < expectedDraw) {
                Log.w(TAG, String.format("⏰ %d회차가 아직 발표되지 않았습니다", expectedDraw));
            } else {
                Log.i(TAG, "✅ 최신 회차가 정상적으로 발표되었습니다");
            }

            return availableDraw;

        } catch (Exception e) {
            Log.e(TAG, "최신 회차 확인 실패", e);
            return 0;
        }
    }

    /**
     * 데이터 업데이트 실행
     * @param context 앱 컨텍스트
     * @return 업데이트 성공 여부
     */
    private boolean performDataUpdate(Context context) {
        try {
            Log.i(TAG, "=== 데이터 업데이트 실행 ===");

            CsvUpdateManager updateManager = new CsvUpdateManager(context);

            // 강제 업데이트 실행
            boolean success = updateManager.forceUpdateCsvFile();

            if (success) {
                Log.i(TAG, "✅ CSV 데이터 업데이트 성공");
            } else {
                Log.w(TAG, "⚠️ CSV 데이터 업데이트 실패");
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "데이터 업데이트 실행 실패", e);
            return false;
        }
    }

    /**
     * 업데이트 결과 알림
     * @param context 앱 컨텍스트
     * @param success 업데이트 성공 여부
     * @param latestDraw 최신 회차 번호
     */
    private void notifyUpdateResult(Context context, boolean success, int latestDraw) {
        try {
            UpdateNotificationManager notificationManager =
                new UpdateNotificationManager(context);

            if (success) {
                String message = String.format("🎉 %d회차 결과가 자동으로 업데이트되었습니다!", latestDraw);
                notificationManager.showUpdateSuccessNotification(message);
                Log.i(TAG, "성공 알림 전송: " + message);
            } else {
                String message = "⚠️ 자동 업데이트에 실패했습니다. 나중에 다시 시도됩니다.";
                notificationManager.showUpdateFailureNotification(message);
                Log.w(TAG, "실패 알림 전송: " + message);
            }

        } catch (Exception e) {
            Log.e(TAG, "업데이트 결과 알림 실패", e);
        }
    }

    /**
     * 요일을 문자열로 변환
     * @param dayOfWeek Calendar.DAY_OF_WEEK 값
     * @return 요일 문자열
     */
    private String getDayOfWeekString(int dayOfWeek) {
        String[] days = {"", "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        return (dayOfWeek >= 1 && dayOfWeek <= 7) ? days[dayOfWeek] : "알 수 없음";
    }
}