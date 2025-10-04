package app.grapekim.smartlotto.util;

import androidx.annotation.Nullable;

import app.grapekim.smartlotto.data.remote.NetworkProvider;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import retrofit2.Response;

/**
 * 로또 회차 계산 및 최신 회차 탐색 유틸.
 */
public final class RoundUtils {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private RoundUtils() {}

    /** 동기 호출: 최신 회차(가장 최근에 발표된 회차) 정보를 찾는다. 실패 시 null */
    @Nullable
    public static LottoDrawDto findLatestRoundSync() {
        int cur = 1000;
        LottoDrawDto lastOk = null;

        int step = 128;
        while (step > 0) {
            LottoDrawDto ok = probe(cur);
            if (ok != null) {
                lastOk = ok;
                cur += step;   // 더 앞쪽으로
            } else {
                cur -= step;   // 한 스텝 뒤로
                step /= 2;     // 반으로 줄임
            }
        }
        // 마지막으로 조금만 앞으로 스캔
        for (int i = 0; i < 16; i++) {
            LottoDrawDto ok = probe(cur + i);
            if (ok == null) break;
            lastOk = ok;
        }
        return lastOk;
    }

    @Nullable
    private static LottoDrawDto probe(int round) {
        try {
            Response<LottoDrawDto> res = NetworkProvider.api().getDraw(round).execute();
            if (res.isSuccessful() && res.body() != null && res.body().isSuccess()) return res.body();
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 생성 시각 기준의 "다음 또는 같은 토요일"을 목표로 하여
     * 그 토요일에 해당하는 '예상 회차 번호'를 계산한다.
     *
     * - targetSat 가 최신 토요일(latestSat) 보다 과거/같음: 최신회차에서 주차만큼 빼서 계산
     * - targetSat 가 최신 토요일보다 미래: 최신회차에서 주차만큼 더해서 '다가올 회차' 번호를 계산
     *
     * @return 계산된 회차 번호(1 이상). latest 가 없으면 -1.
     */
    public static int computeExpectedRoundForTimestamp(long createdAtMillis, LottoDrawDto latest) {
        if (latest == null || latest.drwNo == null || latest.date == null) return -1;

        LocalDate latestDate = LocalDate.parse(latest.date); // yyyy-MM-dd
        LocalDate latestSat  = latestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));

        LocalDate createdDate = Instant.ofEpochMilli(createdAtMillis).atZone(SEOUL).toLocalDate();
        LocalDate targetSat   = createdDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        long diffWeeks = java.time.temporal.ChronoUnit.WEEKS.between(latestSat, targetSat);
        // latestSat → targetSat (미래면 양수, 과거면 음수)
        // 회차 = latest.drwNo + diffWeeks
        long round = latest.drwNo + diffWeeks;
        return (round < 1) ? 1 : (int) round;
    }
}
