package app.grapekim.smartlotto.notify;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import app.grapekim.smartlotto.App;
import app.grapekim.smartlotto.MainActivity;
import app.grapekim.smartlotto.R;

public class DrawReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "DrawReminderReceiver";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "DrawReminderReceiver triggered");

        try {
            // 알림이 비활성화된 경우 처리하지 않음
            if (!ReminderScheduler.isEnabled(context)) {
                Log.d(TAG, "Reminder is disabled, skipping notification");
                return;
            }

            // 알림 표시
            boolean notificationShown = showDrawReminder(context);

            if (notificationShown) {
                Log.i(TAG, "Draw reminder notification shown successfully");

                // 다음 주 알림 예약 (주간 반복)
                scheduleNextReminder(context);
            } else {
                Log.e(TAG, "Failed to show notification");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in DrawReminderReceiver", e);
        }
    }

    private boolean showDrawReminder(Context context) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager is null");
                return false;
            }

            int minutes = ReminderScheduler.getMinutes(context);

            // 메인 액티비티로 이동하는 인텐트
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent contentIntent = PendingIntent.getActivity(
                    context,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 조용한 알림 빌드 (소리/진동 없이 알림 트레이에만 표시)
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, App.CHANNEL_DRAW_REMINDER_ID)
                    .setSmallIcon(R.mipmap.ic_launcher) // 기본 런처 아이콘 사용
                    .setContentTitle(context.getString(R.string.notif_draw_title, minutes))
                    .setContentText(context.getString(R.string.notif_draw_body))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 기본 우선순위
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSilent(true); // 조용한 알림 (Android 8.0+)

            // 알림 표시
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
            return false;
        }
    }

    private void scheduleNextReminder(Context context) {
        try {
            // 다음 주 알림 예약
            boolean scheduled = ReminderScheduler.scheduleNext(context);

            if (scheduled) {
                Log.i(TAG, "Next reminder scheduled successfully");
            } else {
                Log.w(TAG, "Failed to schedule next reminder");

                // 스케줄링 실패 시 사용자에게 알림 (선택사항)
                // 너무 자주 발생하면 사용자 경험에 방해가 될 수 있으므로 로그만 남김
            }

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling next reminder", e);
        }
    }
}