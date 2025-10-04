package app.grapekim.smartlotto.data.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import app.grapekim.smartlotto.MainActivity;
import app.grapekim.smartlotto.R;

/**
 * 업데이트 관련 알림을 관리하는 클래스
 */
public class UpdateNotificationManager {
    private static final String TAG = "UpdateNotificationManager";

    // 알림 채널 ID들
    private static final String CHANNEL_UPDATE_SUCCESS = "update_success";
    private static final String CHANNEL_UPDATE_FAILURE = "update_failure";

    // 알림 ID들
    private static final int NOTIFICATION_UPDATE_SUCCESS = 1001;
    private static final int NOTIFICATION_UPDATE_FAILURE = 1002;

    private final Context context;
    private final NotificationManager notificationManager;

    public UpdateNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // 성공 알림 채널
                NotificationChannel successChannel = new NotificationChannel(
                        CHANNEL_UPDATE_SUCCESS,
                        "업데이트 성공",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                successChannel.setDescription("로또 데이터 업데이트 성공 알림");
                successChannel.setShowBadge(true);
                successChannel.enableVibration(true);

                // 실패 알림 채널
                NotificationChannel failureChannel = new NotificationChannel(
                        CHANNEL_UPDATE_FAILURE,
                        "업데이트 실패",
                        NotificationManager.IMPORTANCE_HIGH
                );
                failureChannel.setDescription("로또 데이터 업데이트 실패 알림");
                failureChannel.setShowBadge(true);
                failureChannel.enableVibration(true);

                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(successChannel);
                    notificationManager.createNotificationChannel(failureChannel);
                    Log.d(TAG, "업데이트 알림 채널 생성 완료");
                }

            } catch (Exception e) {
                Log.e(TAG, "알림 채널 생성 실패", e);
            }
        }
    }

    /**
     * 업데이트 성공 알림 표시
     * @param message 알림 메시지
     */
    public void showUpdateSuccessNotification(String message) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_UPDATE_SUCCESS)
                    .setSmallIcon(R.drawable.ic_refresh)
                    .setContentTitle("Smart Lotto")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setShowWhen(true);

            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_UPDATE_SUCCESS, builder.build());
                Log.i(TAG, "업데이트 성공 알림 표시: " + message);
            }

        } catch (Exception e) {
            Log.e(TAG, "업데이트 성공 알림 표시 실패", e);
        }
    }

    /**
     * 업데이트 실패 알림 표시
     * @param message 알림 메시지
     */
    public void showUpdateFailureNotification(String message) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_UPDATE_FAILURE)
                    .setSmallIcon(R.drawable.ic_refresh)
                    .setContentTitle("Smart Lotto")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setShowWhen(true);

            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_UPDATE_FAILURE, builder.build());
                Log.i(TAG, "업데이트 실패 알림 표시: " + message);
            }

        } catch (Exception e) {
            Log.e(TAG, "업데이트 실패 알림 표시 실패", e);
        }
    }

    /**
     * 수동 업데이트 시작 알림
     */
    public void showManualUpdateStartNotification() {
        showUpdateSuccessNotification("🔄 수동 업데이트를 시작합니다...");
    }

    /**
     * 모든 업데이트 관련 알림 삭제
     */
    public void clearAllUpdateNotifications() {
        try {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_UPDATE_SUCCESS);
                notificationManager.cancel(NOTIFICATION_UPDATE_FAILURE);
                Log.d(TAG, "모든 업데이트 알림 삭제됨");
            }
        } catch (Exception e) {
            Log.e(TAG, "알림 삭제 실패", e);
        }
    }
}