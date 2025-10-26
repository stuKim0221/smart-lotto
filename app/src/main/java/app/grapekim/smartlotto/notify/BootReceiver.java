package app.grapekim.smartlotto.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import app.grapekim.smartlotto.data.scheduler.QuickDataCheckReceiver;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "BootReceiver triggered with action: " + action);

        // 처리할 액션들 확인
        if (!isValidBootAction(action)) {
            Log.d(TAG, "Ignoring action: " + action);
            return;
        }

        try {
            // 1. 알림이 활성화되어 있는지 확인
            if (!ReminderScheduler.isEnabled(context)) {
                Log.d(TAG, "Reminder is disabled, skipping reschedule");
            } else {
                // 알림 재스케줄링
                boolean scheduled = ReminderScheduler.scheduleNext(context);

                if (scheduled) {
                    Log.i(TAG, "Reminder successfully rescheduled after " + action);
                } else {
                    Log.w(TAG, "Failed to reschedule reminder after " + action);
                }
            }

            // 2. 토요일 빠른 체크 재스케줄링 (부팅 후 알람이 사라지므로 재설정 필요)
            QuickDataCheckReceiver.scheduleSaturdayQuickCheck(context);
            Log.i(TAG, "QuickDataCheck rescheduled after " + action);

        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling tasks after " + action, e);
        }
    }

    /**
     * 처리할 유효한 부팅/업데이트 액션인지 확인
     */
    private boolean isValidBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action);
    }
}