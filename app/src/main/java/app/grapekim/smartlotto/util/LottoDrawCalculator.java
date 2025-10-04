package app.grapekim.smartlotto.util;

import android.util.Log;
import java.util.Calendar;
import app.grapekim.smartlotto.data.service.OfficialLottoApiService;
import app.grapekim.smartlotto.data.model.LottoDrawData;

/**
 * 로또 회차 번호를 자동으로 계산하는 유틸리티 클래스
 * 2002년 12월 7일 1회차를 기준으로 매주 토요일마다 회차 증가
 */
public class LottoDrawCalculator {
    private static final String TAG = "LottoDrawCalculator";

    // 로또 1회차 기준일 (2002년 12월 7일 토요일)
    private static final int START_YEAR = 2002;
    private static final int START_MONTH = Calendar.DECEMBER;
    private static final int START_DAY = 7;
    private static final int FIRST_DRAW_NO = 1;

    /**
     * 현재 날짜를 기준으로 예상되는 최신 회차 번호 계산
     * @return 현재 날짜 기준 예상 최신 회차 번호
     */
    public static int getCurrentExpectedDrawNumber() {
        Calendar startDate = Calendar.getInstance();
        startDate.set(START_YEAR, START_MONTH, START_DAY, 20, 35, 0); // 토요일 8시 35분
        startDate.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();

        // 시작일보다 이전이면 0 반환
        if (now.before(startDate)) {
            Log.d(TAG, "현재 날짜가 로또 시작일보다 이전입니다.");
            return 0;
        }

        // 주 단위로 차이 계산
        long diffInMillis = now.getTimeInMillis() - startDate.getTimeInMillis();
        long diffInWeeks = diffInMillis / (7 * 24 * 60 * 60 * 1000L);

        int expectedDrawNo = FIRST_DRAW_NO + (int) diffInWeeks;

        Log.i(TAG, String.format(
            "회차 계산 - 시작일: %04d-%02d-%02d, 현재일: %04d-%02d-%02d, 경과 주수: %d, 예상 회차: %d",
            startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH) + 1, startDate.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            diffInWeeks, expectedDrawNo
        ));

        return expectedDrawNo;
    }

    /**
     * 특정 회차의 예상 추첨일 계산
     * @param drawNo 회차 번호
     * @return 해당 회차의 추첨일 (YYYY-MM-DD 형식)
     */
    public static String getDrawDateForNumber(int drawNo) {
        if (drawNo < FIRST_DRAW_NO) {
            return null;
        }

        Calendar drawDate = Calendar.getInstance();
        drawDate.set(START_YEAR, START_MONTH, START_DAY, 20, 35, 0);
        drawDate.set(Calendar.MILLISECOND, 0);

        // 회차 번호에 따라 주 수 추가
        int weeksToAdd = drawNo - FIRST_DRAW_NO;
        drawDate.add(Calendar.WEEK_OF_YEAR, weeksToAdd);

        return String.format("%04d-%02d-%02d",
            drawDate.get(Calendar.YEAR),
            drawDate.get(Calendar.MONTH) + 1,
            drawDate.get(Calendar.DAY_OF_MONTH)
        );
    }

    /**
     * 현재 날짜가 해당 회차의 추첨 이후인지 확인
     * @param drawNo 회차 번호
     * @return 추첨 이후이면 true, 아니면 false
     */
    public static boolean isDrawNumberAvailable(int drawNo) {
        String drawDate = getDrawDateForNumber(drawNo);
        if (drawDate == null) {
            return false;
        }

        Calendar drawDateTime = Calendar.getInstance();
        String[] dateParts = drawDate.split("-");
        drawDateTime.set(
            Integer.parseInt(dateParts[0]),
            Integer.parseInt(dateParts[1]) - 1,
            Integer.parseInt(dateParts[2]),
            20, 35, 0  // 토요일 8시 35분
        );
        drawDateTime.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();
        boolean available = now.after(drawDateTime);

        Log.d(TAG, String.format(
            "%d회차 가용성 확인 - 추첨일시: %s 20:35, 현재: %04d-%02d-%02d %02d:%02d, 가용: %s",
            drawNo, drawDate,
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
            available ? "예" : "아니오"
        ));

        return available;
    }

    /**
     * 현재 시점에서 결과가 발표된 최신 회차 번호를 API로 정확하게 확인
     * 공휴일, 지연, 시간차 등을 모두 고려한 정확한 추적
     * @return 실제 발표된 최신 회차 번호
     */
    public static int getLatestAvailableDrawNumber() {
        int expectedDraw = getCurrentExpectedDrawNumber();

        Log.i(TAG, "API 기반 최신 회차 확인 시작 - 예상 회차: " + expectedDraw);

        OfficialLottoApiService apiService = null;

        try {
            apiService = new OfficialLottoApiService();

            // 예상 회차부터 최대 3회차 뒤까지 역순으로 실제 API에서 확인
            int searchStart = expectedDraw;
            int searchEnd = Math.max(expectedDraw - 3, FIRST_DRAW_NO);

            for (int drawNo = searchStart; drawNo >= searchEnd; drawNo--) {
                Log.d(TAG, drawNo + "회차 API 확인 중...");

                try {
                    LottoDrawData result = apiService.getDrawData(drawNo);

                    if (result != null && result.drawNo == drawNo) {
                        Log.i(TAG, "✅ API 확인: " + drawNo + "회차 실제 발표됨 - " + result.date);
                        return drawNo;
                    } else {
                        Log.d(TAG, "❌ " + drawNo + "회차 API 응답 없음 또는 무효");
                    }

                    // API 호출 간 짧은 대기 (과부하 방지)
                    Thread.sleep(200);

                } catch (Exception e) {
                    Log.w(TAG, drawNo + "회차 API 호출 중 오류: " + e.getMessage());
                    continue; // 다음 회차 확인
                }
            }

            // API로 확인 실패 시 시간 기반 fallback
            Log.w(TAG, "API 확인 실패, 시간 기반 fallback으로 전환");
            return getLatestAvailableDrawNumberByTime();

        } catch (Exception e) {
            Log.e(TAG, "API 서비스 초기화 실패", e);
            return getLatestAvailableDrawNumberByTime();
        } finally {
            if (apiService != null) {
                try {
                    apiService.shutdown();
                } catch (Exception e) {
                    Log.e(TAG, "API 서비스 종료 중 오류", e);
                }
            }
        }
    }

    /**
     * 시간 기반으로 최신 회차 추정 (fallback 방식)
     * @return 시간 기준 예상 최신 회차
     */
    private static int getLatestAvailableDrawNumberByTime() {
        int expectedDraw = getCurrentExpectedDrawNumber();

        Log.i(TAG, "시간 기반 회차 확인 - 예상: " + expectedDraw);

        // 현재 예상 회차부터 역순으로 확인하여 시간상 발표된 회차 찾기
        for (int drawNo = expectedDraw; drawNo >= Math.max(expectedDraw - 2, FIRST_DRAW_NO); drawNo--) {
            if (isDrawNumberAvailable(drawNo)) {
                Log.i(TAG, "시간 기준 발표된 최신 회차: " + drawNo + "회차");
                return drawNo;
            }
        }

        // 안전한 fallback: 예상 회차 - 1
        int safeDraw = Math.max(expectedDraw - 1, FIRST_DRAW_NO);
        Log.w(TAG, "안전한 fallback: " + safeDraw + "회차로 설정");
        return safeDraw;
    }
}