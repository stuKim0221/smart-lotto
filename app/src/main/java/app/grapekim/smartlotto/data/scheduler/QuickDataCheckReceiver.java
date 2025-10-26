package app.grapekim.smartlotto.data.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.data.notification.UpdateNotificationManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 토요일 20:35~21:10에 1분 간격으로 GitHub CSV를 체크하는 리시버
 */
public class QuickDataCheckReceiver extends BroadcastReceiver {
    private static final String TAG = "QuickDataCheckReceiver";
    private static final String CSV_URL = "https://raw.githubusercontent.com/stuKim0221/smart-lotto/refs/heads/main/app/src/main/assets/draw_kor.csv";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "=== 1분 간격 빠른 체크 시작 ===");

        // 백그라운드 스레드에서 실행
        new Thread(() -> {
            try {
                // 현재 시간 확인
                Calendar now = Calendar.getInstance();
                int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                int hour = now.get(Calendar.HOUR_OF_DAY);
                int minute = now.get(Calendar.MINUTE);

                // 토요일 20:35~21:10이 아니면 다음 알람만 설정하고 종료
                if (dayOfWeek != Calendar.SATURDAY ||
                    (hour == 20 && minute < 35) ||
                    (hour == 21 && minute > 10) ||
                    hour < 20 || hour > 21) {
                    Log.d(TAG, "발표 시간대가 아님 - 다음 알람 설정 후 종료");
                    scheduleNext1MinuteCheck(context);
                    return;
                }

                Log.i(TAG, String.format("🎯 발표 시간대 체크 (%02d:%02d)", hour, minute));

                // 로컬 DB 최신 회차 확인
                LottoRepository repository = new LottoRepositoryImpl(context);
                Integer localLatestRound = repository.getLatestDrawNumber();
                Log.i(TAG, "로컬 DB 최신 회차: " + (localLatestRound != null ? localLatestRound : "없음"));

                // GitHub CSV 최신 회차 확인
                Integer githubLatestRound = getLatestRoundFromGitHub();
                Log.i(TAG, "GitHub CSV 최신 회차: " + (githubLatestRound != null ? githubLatestRound : "확인 실패"));

                if (githubLatestRound == null) {
                    Log.w(TAG, "GitHub CSV 회차 확인 실패");
                    scheduleNext1MinuteCheck(context);
                    return;
                }

                // 새로운 데이터가 있는지 확인
                if (localLatestRound == null || githubLatestRound > localLatestRound) {
                    int newRounds = githubLatestRound - (localLatestRound != null ? localLatestRound : 0);
                    Log.i(TAG, String.format("🎉 새로운 데이터 발견! %d → %d (%d개 회차)",
                            localLatestRound, githubLatestRound, newRounds));

                    // 데이터 업데이트 실행
                    CsvUpdateManager updateManager = new CsvUpdateManager(context);
                    boolean success = updateManager.updateCsvFile();

                    if (success) {
                        // 알림 발송: QR 당첨확인 기능 사용 가능 안내
                        UpdateNotificationManager notificationManager = new UpdateNotificationManager(context);
                        String message = String.format("🎉 %d회차 로또 당첨 확인이 가능합니다!", githubLatestRound);
                        notificationManager.showUpdateSuccessNotification(message);
                        Log.i(TAG, "✅ 자동 업데이트 성공! 알림 발송 완료");
                    } else {
                        Log.w(TAG, "⚠️ 업데이트 실패");
                    }
                } else {
                    Log.d(TAG, "새로운 데이터 없음 (GitHub: " + githubLatestRound + ", Local: " + localLatestRound + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "체크 중 오류 발생", e);
            } finally {
                // 다음 1분 후 알람 설정
                scheduleNext1MinuteCheck(context);
            }
        }).start();
    }

    /**
     * GitHub CSV에서 최신 회차 번호 가져오기
     */
    private Integer getLatestRoundFromGitHub() {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            String urlWithTimestamp = CSV_URL + "?t=" + System.currentTimeMillis();
            URL url = new URL(urlWithTimestamp);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Cache-Control", "no-cache");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP 응답 코드: " + responseCode);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 헤더 스킵
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // 첫 번째 데이터 라인에서 회차 추출
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String drawNoStr = parts[1].trim();
                    if (!drawNoStr.isEmpty()) {
                        return Integer.parseInt(drawNoStr);
                    }
                }
                break;
            }

        } catch (Exception e) {
            Log.e(TAG, "GitHub CSV 회차 확인 실패", e);
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "리소스 정리 실패", e);
            }
        }

        return null;
    }

    /**
     * 1분 후 다음 체크 스케줄링
     */
    private void scheduleNext1MinuteCheck(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, QuickDataCheckReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerTime = System.currentTimeMillis() + 60000; // 1분 후
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);

            Log.d(TAG, "다음 1분 후 체크 스케줄링 완료");
        } catch (Exception e) {
            Log.e(TAG, "다음 알람 스케줄링 실패", e);
        }
    }

    /**
     * 토요일 20:35에 1분 간격 체크 시작
     */
    public static void scheduleSaturdayQuickCheck(Context context) {
        try {
            Calendar now = Calendar.getInstance();
            Calendar targetTime = Calendar.getInstance();

            // 다음 토요일 20:35 계산
            int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            int daysUntilSaturday = (Calendar.SATURDAY - currentDayOfWeek + 7) % 7;

            // 이미 토요일이고 20:35 이전이면 오늘, 아니면 다음 주
            if (daysUntilSaturday == 0 &&
                (now.get(Calendar.HOUR_OF_DAY) < 20 ||
                 (now.get(Calendar.HOUR_OF_DAY) == 20 && now.get(Calendar.MINUTE) < 35))) {
                // 오늘 토요일 20:35
            } else if (daysUntilSaturday == 0) {
                daysUntilSaturday = 7; // 다음 주 토요일
            }

            targetTime.add(Calendar.DAY_OF_YEAR, daysUntilSaturday);
            targetTime.set(Calendar.HOUR_OF_DAY, 20);
            targetTime.set(Calendar.MINUTE, 35);
            targetTime.set(Calendar.SECOND, 0);
            targetTime.set(Calendar.MILLISECOND, 0);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, QuickDataCheckReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetTime.getTimeInMillis(),
                    pendingIntent
            );

            Log.i(TAG, "토요일 빠른 체크 스케줄링 완료: " + targetTime.getTime());

        } catch (Exception e) {
            Log.e(TAG, "토요일 빠른 체크 스케줄링 실패", e);
        }
    }
}
