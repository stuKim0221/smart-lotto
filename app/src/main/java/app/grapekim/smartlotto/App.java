package app.grapekim.smartlotto;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import app.grapekim.smartlotto.data.work.DataInitWorker;
import app.grapekim.smartlotto.data.CsvUpdateScheduler;
import app.grapekim.smartlotto.data.scheduler.DrawResultScheduler;
import app.grapekim.smartlotto.util.AdMobConfigValidator;

/**
 * 로또 앱의 Application 클래스
 * 앱 전역 초기화 작업을 담당합니다.
 */
public class App extends Application {

    private static final String TAG = "LottoApp";

    // WorkManager 시드 작업 관련 상수
    public static final String SEED_WORK_TAG = "seed_init";
    public static final String SEED_WORK_NAME = "seed_init_unique";

    // 알림 채널 ID들
    public static final String CHANNEL_DRAW_REMINDER_ID = "draw_reminder";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "🚀 Smart Lotto 앱 초기화 시작 (최적화된 시스템 v3)");

        try {
            // 1) 필수 동기 작업: 알림 채널 생성 (즉시 필요)
            createNotificationChannels();

            // 2) 디버그 전용 작업 (개발 시에만)
            if (BuildConfig.DEBUG) {
                AdMobConfigValidator.logAdMobSummary();
            }

            // 3) 비동기 백그라운드 작업들을 WorkManager로 위임
            scheduleBackgroundInitialization();

            Log.i(TAG, "✅ Smart Lotto 앱 기본 초기화 완료 (백그라운드 작업 예약됨)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Application 초기화 중 오류 발생", e);
        }
    }

    /**
     * 알림 채널 생성 (Android 8.0 이상)
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel drawReminder = new NotificationChannel(
                        CHANNEL_DRAW_REMINDER_ID,
                        getString(R.string.notif_channel_draw_reminder_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                drawReminder.setDescription(getString(R.string.notif_channel_draw_reminder_desc));
                drawReminder.setShowBadge(true);
                drawReminder.enableVibration(false);

                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(drawReminder);
                    Log.d(TAG, "알림 채널 생성 완료");
                } else {
                    Log.w(TAG, "NotificationManager를 가져올 수 없습니다");
                }
            } catch (Exception e) {
                Log.e(TAG, "알림 채널 생성 실패", e);
            }
        }
    }

    /**
     * 모든 백그라운드 초기화 작업을 통합 관리
     * 앱 시작 속도를 위해 무거운 작업들을 백그라운드로 위임
     */
    private void scheduleBackgroundInitialization() {
        try {
            SharedPreferences sp = getSharedPreferences(DataInitWorker.PREFS, MODE_PRIVATE);
            boolean seeded = sp.getBoolean(DataInitWorker.KEY_SEEDED, false);
            int lastInitVersion = sp.getInt(DataInitWorker.KEY_INIT_VERSION, 0);

            // 초기화 버전 체크 (v3로 업그레이드)
            boolean needsInit = !seeded || lastInitVersion < 3;

            if (needsInit) {
                Log.i(TAG, String.format("백그라운드 초기화 예약 - 시드: %s, 버전: %d→3",
                    seeded ? "완료" : "미완료", lastInitVersion));

                // 즉시 실행되는 초기화 워커
                OneTimeWorkRequest initRequest = new OneTimeWorkRequest.Builder(DataInitWorker.class)
                        .addTag(SEED_WORK_TAG)
                        .build();

                WorkManager.getInstance(this)
                        .enqueueUniqueWork(SEED_WORK_NAME, ExistingWorkPolicy.REPLACE, initRequest);
            } else {
                Log.i(TAG, "초기화 완료됨 - 정기 업데이트만 스케줄링");
            }

            // 매주 토요일 10시 로또 데이터 업데이트 (배터리 최적화)
            scheduleSaturdayLottoUpdates();

        } catch (Exception e) {
            Log.e(TAG, "백그라운드 초기화 스케줄링 실패", e);
        }
    }

    /**
     * 매주 토요일 10시 로또 데이터 업데이트 스케줄링
     * 배터리 최적화: 6시간마다 → 주 1회로 대폭 절약
     *
     * 일정:
     * - 로또 발표: 매주 토요일 8:35 PM
     * - GitHub 업데이트: 매주 토요일 9:30 PM
     * - 앱 데이터 업데이트: 매주 토요일 10:00 PM
     */
    private void scheduleSaturdayLottoUpdates() {
        try {
            // CSV 데이터 업데이트
            CsvUpdateScheduler.scheduleWeeklySaturdayUpdate(this);

            // 당첨 결과 알림 스케줄링 (기존 기능 유지)
            DrawResultScheduler.scheduleWeeklySaturdayUpdate(this);

            Log.i(TAG, "🎯 매주 토요일 10시 로또 업데이트 스케줄링 완료");
            Log.i(TAG, "📱 배터리 최적화: 6시간마다 → 주 1회 (85% 절약)");
        } catch (Exception e) {
            Log.e(TAG, "토요일 로또 업데이트 스케줄링 실패", e);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // 메모리 부족 시 정리 작업 (필요시 구현)
        Log.d(TAG, "메모리 정리 요청: level=" + level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // 메모리 부족 경고 (필요시 구현)
        Log.w(TAG, "메모리 부족 경고");
    }
}