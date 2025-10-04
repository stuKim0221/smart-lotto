package app.grapekim.smartlotto.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";
    private static final String PREFS = "settings";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_MINUTES = "reminder_minutes";
    private static final int REQ_CODE = 1001;

    // 로또 추첨 시간 상수
    private static final int DRAW_HOUR = 20;    // 오후 8시
    private static final int DRAW_MINUTE = 35;  // 35분
    private static final DayOfWeek DRAW_DAY = DayOfWeek.SATURDAY;  // 토요일

    private ReminderScheduler() {}

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.d(TAG, "Reminder enabled set to: " + enabled);
    }

    public static int getMinutes(Context ctx) {
        return prefs(ctx).getInt(KEY_MINUTES, 5); // 기본 5분 전
    }

    public static void setMinutes(Context ctx, int minutes) {
        prefs(ctx).edit().putInt(KEY_MINUTES, minutes).apply();
        Log.d(TAG, "Reminder minutes set to: " + minutes);
    }

    /**
     * 다음 알림 한 건 예약
     */
    public static boolean scheduleNext(Context ctx) {
        if (!isEnabled(ctx)) {
            Log.d(TAG, "Reminder is disabled, skipping schedule");
            return false;
        }

        try {
            long triggerAt = computeNextTrigger(ctx);
            long currentTime = System.currentTimeMillis();

            // 최소 5초 여유를 둠 (기존 1초에서 증가)
            if (triggerAt <= currentTime + 5000L) {
                Log.w(TAG, "Trigger time is too close or in the past. Trigger: " + triggerAt + ", Current: " + currentTime);
                return false;
            }

            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                Log.e(TAG, "AlarmManager is null");
                return false;
            }

            PendingIntent pi = pending(ctx);

            // Android 12+ 정확한 알람 권한 체크 및 처리
            boolean scheduled = scheduleAlarmWithPermissionCheck(am, triggerAt, pi);

            if (scheduled) {
                Log.i(TAG, "Alarm scheduled for: " + new java.util.Date(triggerAt));
            }

            return scheduled;

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling reminder", e);
            return false;
        }
    }

    /**
     * Android 12+ 권한을 고려한 알람 스케줄링
     */
    private static boolean scheduleAlarmWithPermissionCheck(AlarmManager am, long triggerAt, PendingIntent pi) {
        boolean canExact = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            canExact = am.canScheduleExactAlarms();
            Log.d(TAG, "Can schedule exact alarms: " + canExact);
        }

        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.d(TAG, "Scheduled exact alarm");
            } else {
                // 권한이 없으면 부정확 알람으로 대체
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                Log.w(TAG, "Scheduled inexact alarm due to missing permission");
            }
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception while scheduling alarm", e);
            return false;
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(pending(ctx));
                Log.d(TAG, "Alarm cancelled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling alarm", e);
        }
    }

    public static long peekNextTrigger(Context ctx) {
        try {
            return computeNextTrigger(ctx);
        } catch (Exception e) {
            Log.e(TAG, "Error computing next trigger", e);
            return 0L;
        }
    }

    /**
     * 다음 로또 추첨 알림 시간 계산
     * 기준: 매주 토요일 20:35 (사용자 로컬 타임존)
     * 사용자가 선택한 N분 전으로 앞당겨 예약
     */
    private static long computeNextTrigger(Context ctx) {
        int reminderMinutes = getMinutes(ctx);

        // 사용자의 로컬 타임존 사용
        ZoneId localZone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(localZone);

        Log.d(TAG, "Computing next trigger. Current time: " + now + ", Reminder minutes: " + reminderMinutes);

        // 이번 주 토요일 20:35 계산
        ZonedDateTime thisWeekDraw = now.with(TemporalAdjusters.nextOrSame(DRAW_DAY))
                .withHour(DRAW_HOUR)
                .withMinute(DRAW_MINUTE)
                .withSecond(0)
                .withNano(0);

        ZonedDateTime targetDraw;

        // 현재 시간이 이번 주 추첨 시간을 지났는지 확인
        if (now.isAfter(thisWeekDraw)) {
            // 다음 주 토요일로 설정
            targetDraw = thisWeekDraw.plusWeeks(1);
            Log.d(TAG, "This week's draw time passed, using next week");
        } else {
            // 이번 주 토요일 사용
            targetDraw = thisWeekDraw;
            Log.d(TAG, "Using this week's draw time");
        }

        // N분 전으로 알림 시간 설정
        ZonedDateTime reminderTime = targetDraw.minusMinutes(reminderMinutes);

        Log.d(TAG, "Next draw time: " + targetDraw + ", Reminder time: " + reminderTime);

        return reminderTime.toInstant().toEpochMilli();
    }

    /**
     * Android 12+ 정확한 알람 권한이 있는지 확인
     */
    public static boolean hasExactAlarmPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true; // Android 12 미만에서는 항상 true
    }

    /**
     * 정확한 알람 권한 요청을 위한 Intent 생성
     */
    public static Intent createExactAlarmPermissionIntent(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(android.net.Uri.parse("package:" + ctx.getPackageName()));
            return intent;
        }
        return null;
    }

    private static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, DrawReminderReceiver.class);
        return PendingIntent.getBroadcast(
                ctx, REQ_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}