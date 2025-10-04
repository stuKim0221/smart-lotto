package app.grapekim.smartlotto.util;

import android.content.Context;

import app.grapekim.smartlotto.notify.ReminderScheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 알림 스케줄링 테스트 및 디버깅용 유틸리티
 */
public final class ReminderTestUtils {

    private ReminderTestUtils() {}

    /**
     * 현재 알림 설정 상태를 문자열로 반환
     */
    public static String getCurrentReminderInfo(Context context) {
        StringBuilder info = new StringBuilder();

        // 현재 시간
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm:ss", Locale.KOREA);

        info.append("=== 로또 알림 설정 정보 ===\n");
        info.append("현재 시간: ").append(fmt.format(now.getTime())).append("\n");
        info.append("알림 활성화: ").append(ReminderScheduler.isEnabled(context) ? "ON" : "OFF").append("\n");
        info.append("알림 시간: ").append(ReminderScheduler.getMinutes(context)).append("분 전\n");

        if (ReminderScheduler.isEnabled(context)) {
            long nextTrigger = ReminderScheduler.peekNextTrigger(context);
            if (nextTrigger > 0) {
                Date nextDate = new Date(nextTrigger);
                info.append("다음 알림: ").append(fmt.format(nextDate)).append("\n");

                // 다음 토요일 20:35 계산
                Calendar nextSaturday = getNextSaturday2035();
                info.append("다음 추첨: ").append(fmt.format(nextSaturday.getTime())).append("\n");

                // 시간 차이 계산
                long diffMillis = nextTrigger - now.getTimeInMillis();
                long diffMinutes = diffMillis / (1000 * 60);
                long diffHours = diffMinutes / 60;
                long diffDays = diffHours / 24;

                if (diffDays > 0) {
                    info.append("남은 시간: ").append(diffDays).append("일 ").append(diffHours % 24).append("시간 ").append(diffMinutes % 60).append("분\n");
                } else if (diffHours > 0) {
                    info.append("남은 시간: ").append(diffHours).append("시간 ").append(diffMinutes % 60).append("분\n");
                } else {
                    info.append("남은 시간: ").append(diffMinutes).append("분\n");
                }
            }
        }

        return info.toString();
    }

    /**
     * 다음 토요일 20:35 계산
     */
    private static Calendar getNextSaturday2035() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        Calendar target = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));

        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        target.set(Calendar.HOUR_OF_DAY, 20);
        target.set(Calendar.MINUTE, 35);
        target.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);

        // 오늘이 토요일 20:35 이후면 다음주로
        if (!now.before(target)) {
            target.add(Calendar.WEEK_OF_YEAR, 1);
        }

        return target;
    }

    /**
     * 알림 시간이 올바르게 계산되는지 테스트
     */
    public static String testReminderCalculation(Context context) {
        StringBuilder result = new StringBuilder();

        result.append("=== 알림 계산 테스트 ===\n");

        // 여러 알림 시간에 대해 테스트
        int[] testMinutes = {5, 10, 20, 30};

        for (int minutes : testMinutes) {
            // 임시로 설정 변경
            int originalMinutes = ReminderScheduler.getMinutes(context);
            ReminderScheduler.setMinutes(context, minutes);

            long triggerTime = ReminderScheduler.peekNextTrigger(context);
            Calendar nextSaturday = getNextSaturday2035();

            // N분 전 시간 계산
            Calendar expectedTrigger = (Calendar) nextSaturday.clone();
            expectedTrigger.add(Calendar.MINUTE, -minutes);

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREA);

            result.append(minutes).append("분 전 설정:\n");
            result.append("  추첨 시간: ").append(fmt.format(nextSaturday.getTime())).append("\n");
            result.append("  예상 알림: ").append(fmt.format(expectedTrigger.getTime())).append("\n");
            result.append("  실제 알림: ").append(fmt.format(new Date(triggerTime))).append("\n");
            result.append("  일치 여부: ").append(triggerTime == expectedTrigger.getTimeInMillis() ? "✓" : "✗").append("\n\n");

            // 원래 설정 복원
            ReminderScheduler.setMinutes(context, originalMinutes);
        }

        return result.toString();
    }
}