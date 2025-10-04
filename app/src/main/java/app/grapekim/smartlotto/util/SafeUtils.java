package app.grapekim.smartlotto.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 안전한 작업 수행을 위한 유틸리티 클래스
 * Null 체크, 예외 처리, 안전한 변환 등을 제공합니다.
 */
public final class SafeUtils {

    private SafeUtils() {
        // 유틸리티 클래스이므로 인스턴스 생성 방지
    }

    /**
     * 안전한 정수 변환
     * @param value 변환할 값
     * @param defaultValue 실패 시 기본값
     * @return 변환된 정수 또는 기본값
     */
    public static int safeParseInt(@Nullable String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 안전한 정수 변환 (기본값 0)
     */
    public static int safeParseInt(@Nullable String value) {
        return safeParseInt(value, 0);
    }

    /**
     * 안전한 Long 변환
     * @param value 변환할 값
     * @param defaultValue 실패 시 기본값
     * @return 변환된 Long 또는 기본값
     */
    public static long safeParseLong(@Nullable String value, long defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 안전한 Long 변환 (기본값 0)
     */
    public static long safeParseLong(@Nullable String value) {
        return safeParseLong(value, 0L);
    }

    /**
     * 안전한 문자열 반환
     * @param value 확인할 문자열
     * @param defaultValue null이거나 빈 문자열일 때 기본값
     * @return 안전한 문자열
     */
    @NonNull
    public static String safeString(@Nullable String value, @NonNull String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * 안전한 문자열 반환 (기본값 "")
     */
    @NonNull
    public static String safeString(@Nullable String value) {
        return safeString(value, "");
    }

    /**
     * 안전한 리스트 반환
     * @param list 확인할 리스트
     * @return null이면 빈 리스트, 아니면 원본 리스트
     */
    @NonNull
    public static <T> List<T> safeList(@Nullable List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 범위 내 값 확인
     * @param value 확인할 값
     * @param min 최소값
     * @param max 최대값
     * @return 범위 내에 있으면 true
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * 로또 번호 유효성 검사 (1-45)
     * @param number 확인할 번호
     * @return 유효하면 true
     */
    public static boolean isValidLottoNumber(int number) {
        return isInRange(number, 1, 45);
    }

    /**
     * 안전한 토스트 표시
     * @param context Context (null 체크 포함)
     * @param message 메시지
     * @param duration Toast.LENGTH_SHORT 또는 Toast.LENGTH_LONG
     */
    public static void safeToast(@Nullable Context context, @Nullable String message, int duration) {
        if (context == null || message == null || message.trim().isEmpty()) {
            return;
        }

        try {
            Toast.makeText(context, message.trim(), duration).show();
        } catch (Exception e) {
            // 토스트 표시 실패 시 무시 (크래시 방지)
        }
    }

    /**
     * 안전한 토스트 표시 (기본 SHORT 길이)
     */
    public static void safeToast(@Nullable Context context, @Nullable String message) {
        safeToast(context, message, Toast.LENGTH_SHORT);
    }

    /**
     * 안전한 Runnable 실행
     * @param runnable 실행할 Runnable
     */
    public static void safeRun(@Nullable Runnable runnable) {
        if (runnable == null) {
            return;
        }

        try {
            runnable.run();
        } catch (Exception e) {
            // 예외 로그만 출력하고 크래시 방지
            System.err.println("SafeUtils.safeRun() failed: " + e.getMessage());
        }
    }

    /**
     * 안전한 값 비교 (null 허용)
     * @param a 첫 번째 값
     * @param b 두 번째 값
     * @return 두 값이 같으면 true
     */
    public static boolean safeEquals(@Nullable Object a, @Nullable Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}