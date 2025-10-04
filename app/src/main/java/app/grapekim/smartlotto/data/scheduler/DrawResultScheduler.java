package app.grapekim.smartlotto.data.scheduler;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.work.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * 로또 발표 시간(토요일 8:35 PM)을 고려한 정확한 업데이트 스케줄러
 * 발표 후 충분한 시간(9:00 PM)에 자동 업데이트 실행
 */
public class DrawResultScheduler {
    private static final String TAG = "DrawResultScheduler";

    // 작업 태그와 고유 이름
    private static final String SATURDAY_UPDATE_TAG = "saturday_draw_update";
    private static final String SATURDAY_UPDATE_NAME = "saturday_draw_update_unique";

    // 발표 관련 시간
    private static final int BROADCAST_START_HOUR = 20; // 8 PM
    private static final int BROADCAST_START_MINUTE = 35; // 8:35 PM
    private static final int AUTO_UPDATE_HOUR = 21; // 9:00 PM (발표 후 25분)
    private static final int AUTO_UPDATE_MINUTE = 0;

    /**
     * 매주 토요일 9시에 자동 업데이트되도록 스케줄링
     * @param context 앱 컨텍스트
     */
    public static void scheduleWeeklySaturdayUpdate(Context context) {
        try {
            Log.i(TAG, "토요일 9시 자동 업데이트 스케줄링 시작...");

            // 다음 토요일 9시까지의 시간 계산
            long delayMillis = calculateNextSaturdayUpdateDelay();

            // 주기적 작업 설정 (매주 반복)
            PeriodicWorkRequest weeklyUpdate = new PeriodicWorkRequest.Builder(
                    SaturdayDrawUpdateWorker.class, 7, TimeUnit.DAYS)
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .addTag(SATURDAY_UPDATE_TAG)
                    .setConstraints(getUpdateConstraints())
                    .build();

            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                            SATURDAY_UPDATE_NAME,
                            ExistingPeriodicWorkPolicy.REPLACE,
                            weeklyUpdate
                    );

            logSchedulingInfo(delayMillis);

        } catch (Exception e) {
            Log.e(TAG, "토요일 자동 업데이트 스케줄링 실패", e);
        }
    }

    /**
     * 다음 토요일 9시까지의 지연 시간 계산
     * @return 밀리초 단위 지연 시간
     */
    private static long calculateNextSaturdayUpdateDelay() {
        Calendar now = Calendar.getInstance();
        Calendar nextSaturday9PM = Calendar.getInstance();

        // 다음 토요일 찾기
        int daysUntilSaturday = (Calendar.SATURDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        if (daysUntilSaturday == 0) {
            // 오늘이 토요일인 경우
            if (now.get(Calendar.HOUR_OF_DAY) < AUTO_UPDATE_HOUR ||
                (now.get(Calendar.HOUR_OF_DAY) == AUTO_UPDATE_HOUR &&
                 now.get(Calendar.MINUTE) < AUTO_UPDATE_MINUTE)) {
                // 아직 9시 이전이면 오늘
                daysUntilSaturday = 0;
            } else {
                // 9시 이후면 다음 토요일
                daysUntilSaturday = 7;
            }
        }

        nextSaturday9PM.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);
        nextSaturday9PM.set(Calendar.HOUR_OF_DAY, AUTO_UPDATE_HOUR);
        nextSaturday9PM.set(Calendar.MINUTE, AUTO_UPDATE_MINUTE);
        nextSaturday9PM.set(Calendar.SECOND, 0);
        nextSaturday9PM.set(Calendar.MILLISECOND, 0);

        return nextSaturday9PM.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * 업데이트 작업의 제약 조건 설정
     * @return 작업 제약 조건
     */
    private static Constraints getUpdateConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // 중요한 작업이므로 배터리 제한 없음
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();
    }

    /**
     * 스케줄링 정보 로깅
     * @param delayMillis 지연 시간 (밀리초)
     */
    private static void logSchedulingInfo(long delayMillis) {
        long delayHours = delayMillis / (1000 * 60 * 60);
        long delayMinutes = (delayMillis / (1000 * 60)) % 60;

        Calendar nextUpdate = Calendar.getInstance();
        nextUpdate.setTimeInMillis(System.currentTimeMillis() + delayMillis);

        Log.i(TAG, "📅 토요일 9시 자동 업데이트 스케줄링 완료");
        Log.i(TAG, String.format("⏰ 다음 자동 업데이트: %04d-%02d-%02d %02d:%02d (약 %d시간 %d분 후)",
                nextUpdate.get(Calendar.YEAR),
                nextUpdate.get(Calendar.MONTH) + 1,
                nextUpdate.get(Calendar.DAY_OF_MONTH),
                nextUpdate.get(Calendar.HOUR_OF_DAY),
                nextUpdate.get(Calendar.MINUTE),
                delayHours, delayMinutes));
        Log.i(TAG, "🎯 발표 시간: 매주 토요일 8:35 PM → 자동 업데이트: 9:00 PM");
    }

    /**
     * 수동으로 즉시 업데이트 실행
     * @param context 앱 컨텍스트
     */
    public static void triggerManualUpdate(Context context) {
        try {
            Log.i(TAG, "🔄 수동 업데이트 즉시 실행 요청");

            OneTimeWorkRequest manualUpdate = new OneTimeWorkRequest.Builder(SaturdayDrawUpdateWorker.class)
                    .addTag("manual_update")
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();

            WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                            "manual_update_" + System.currentTimeMillis(),
                            ExistingWorkPolicy.REPLACE,
                            manualUpdate
                    );

            Log.i(TAG, "✅ 수동 업데이트 작업 예약 완료");

        } catch (Exception e) {
            Log.e(TAG, "수동 업데이트 실행 실패", e);
        }
    }

    /**
     * 현재 토요일 발표 시간인지 확인
     * @return 토요일 8:35 PM ~ 9:00 PM 사이면 true
     */
    public static boolean isCurrentlyBroadcastTime() {
        Calendar now = Calendar.getInstance();

        // 토요일인지 확인
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            return false;
        }

        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);

        // 8:35 PM ~ 9:00 PM 사이인지 확인
        if (currentHour == BROADCAST_START_HOUR && currentMinute >= BROADCAST_START_MINUTE) {
            return true; // 8:35 PM ~ 8:59 PM
        } else if (currentHour == AUTO_UPDATE_HOUR && currentMinute == AUTO_UPDATE_MINUTE) {
            return true; // 9:00 PM 정확히
        }

        return false;
    }

    /**
     * 스케줄링된 작업 취소
     * @param context 앱 컨텍스트
     */
    public static void cancelScheduledUpdates(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(SATURDAY_UPDATE_NAME);
            Log.i(TAG, "토요일 자동 업데이트 스케줄링 취소됨");
        } catch (Exception e) {
            Log.e(TAG, "스케줄링 취소 실패", e);
        }
    }
}