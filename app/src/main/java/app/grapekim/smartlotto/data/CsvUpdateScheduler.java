package app.grapekim.smartlotto.data;

import android.content.Context;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class CsvUpdateScheduler {
    private static final String TAG = "CsvUpdateScheduler";
    private static final String WORK_NAME = "csv_saturday_update_work";

    public static void scheduleWeeklySaturdayUpdate(Context context) {
        Log.d(TAG, "토요일 10시 CSV 업데이트 스케줄링...");

        // 네트워크 연결 조건 설정
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // 인터넷 연결 필요
                .setRequiresBatteryNotLow(true)                 // 배터리 부족하지 않을 때
                .build();

        // 매주 토요일 오후 10시 업데이트 (7일 주기)
        PeriodicWorkRequest weeklyWork =
                new PeriodicWorkRequest.Builder(CsvUpdateWorker.class, 7, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .addTag("csv_saturday_update")
                        .setInitialDelay(calculateDelayUntilNextSaturday10PM(), TimeUnit.MILLISECONDS)
                        .build();

        // 작업 스케줄링 (기존 작업이 있으면 교체)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,  // 기존 작업 교체
                weeklyWork
        );

        Log.d(TAG, "✅ 매주 토요일 10시 CSV 업데이트 스케줄링 완료");
    }

    /**
     * 다음 토요일 오후 10시까지의 지연 시간 계산
     */
    private static long calculateDelayUntilNextSaturday10PM() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar nextSaturday = java.util.Calendar.getInstance();

        // 이번 주 토요일 오후 10시로 설정
        nextSaturday.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SATURDAY);
        nextSaturday.set(java.util.Calendar.HOUR_OF_DAY, 22);  // 오후 10시
        nextSaturday.set(java.util.Calendar.MINUTE, 0);
        nextSaturday.set(java.util.Calendar.SECOND, 0);
        nextSaturday.set(java.util.Calendar.MILLISECOND, 0);

        // 이미 이번 주 토요일 10시가 지났다면 다음 주 토요일로
        if (nextSaturday.before(now)) {
            nextSaturday.add(java.util.Calendar.WEEK_OF_YEAR, 1);
        }

        long delay = nextSaturday.getTimeInMillis() - now.getTimeInMillis();

        Log.d(TAG, String.format("다음 토요일 10시까지: %.1f시간 후", delay / (1000.0 * 60 * 60)));

        return delay;
    }

    // 즉시 업데이트 (사용자가 수동으로 업데이트할 때)
    public static void scheduleImmediateUpdate(Context context) {
        Log.d(TAG, "Scheduling immediate CSV update...");

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest immediateWork =
                new PeriodicWorkRequest.Builder(CsvUpdateWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag("csv_immediate_update")
                        .build();

        WorkManager.getInstance(context).enqueue(immediateWork);
    }

    // 업데이트 작업 취소
    public static void cancelPeriodicUpdate(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "Periodic CSV update cancelled");
    }
}