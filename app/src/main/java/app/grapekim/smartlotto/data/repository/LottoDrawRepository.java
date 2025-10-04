package app.grapekim.smartlotto.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import app.grapekim.smartlotto.data.remote.NetworkProvider;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;

import java.io.IOException;

import retrofit2.Response;

/**
 * 최신 회차를 효율적으로 찾는 리포지토리.
 * 전략:
 *  1) 캐시된 마지막 성공 회차(lastKnown)에서 시작
 *  2) 지수 탐색으로 상한(bound)를 빠르게 찾음: lastKnown + 32, +64, +128 ... 하다 처음 실패 지점(highFail)을 찾는다
 *  3) [lowSuccess+1, highFail-1] 범위에서 이진 탐색으로 '마지막 성공 회차'를 정확히 찾는다
 *
 * 장점: 첫 실행처럼 회차 차이가 큰 경우에도 호출 횟수를 O(log N)으로 줄여 최신 회차를 빠르게 찾음
 */
public class LottoDrawRepository {

    private static final String PREFS = "lotto_draw_cache";
    private static final String KEY_LAST_NO = "last_no";
    private static final String KEY_N1="n1", KEY_N2="n2", KEY_N3="n3", KEY_N4="n4", KEY_N5="n5", KEY_N6="n6", KEY_B="bn";
    private static final String KEY_DATE="date";

    // 첫 실행 시 보수적 시작 회차 (너무 과거면 호출 수만 늘어남 → 적당히 최근으로)
    private static final int FIRST_GUESS = 1000;

    // 안전장치: 지수/이진 포함 총 호출 상한 (과도한 루프 방지)
    private static final int MAX_TOTAL_CALLS = 60;

    private final SharedPreferences sp;

    public LottoDrawRepository(Context appContext) {
        this.sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 캐시 즉시 조회(없으면 null). UI 첫 표시용 */
    @Nullable
    public LottoDrawDto getCached() {
        int no = sp.getInt(KEY_LAST_NO, -1);
        if (no < 0) return null;
        LottoDrawDto d = new LottoDrawDto();
        d.drwNo = no;
        d.n1 = sp.getInt(KEY_N1, 0);
        d.n2 = sp.getInt(KEY_N2, 0);
        d.n3 = sp.getInt(KEY_N3, 0);
        d.n4 = sp.getInt(KEY_N4, 0);
        d.n5 = sp.getInt(KEY_N5, 0);
        d.n6 = sp.getInt(KEY_N6, 0);
        d.bonus = sp.getInt(KEY_B, 0);
        d.date = sp.getString(KEY_DATE, null);
        d.returnValue = "success";
        return d.n1 != 0 ? d : null;
    }

    /** 네트워크로 최신 회차를 찾아 캐시하고 반환(실패 시 null). */
    @Nullable
    public LottoDrawDto fetchAndCacheLatest() {
        int calls = 0;

        // 1) 시작점: 캐시 있으면 그 다음 회차부터, 없으면 FIRST_GUESS
        int lastKnown = sp.getInt(KEY_LAST_NO, -1);
        if (lastKnown < 1) lastKnown = FIRST_GUESS;

        // 먼저 lastKnown가 실제로 존재하는지 재확인 (오래된 캐시/과거 추정치 방어)
        ProbeResult pr = probe(lastKnown); calls += pr.calls;
        if (calls > MAX_TOTAL_CALLS) return null;
        if (!pr.success) {
            // 시작점 자체가 실패라면, 한 단계 낮춰가며 성공 지점을 찾자 (최대 몇 번만)
            int back = Math.max(1, lastKnown - 1);
            int backTries = 0;
            while (back >= 1 && backTries < 5) {
                ProbeResult prBack = probe(back);
                calls += prBack.calls;
                if (prBack.success) {
                    lastKnown = back;
                    break;
                }
                back--; backTries++;
                if (calls > MAX_TOTAL_CALLS) return null;
            }
            // 그래도 못 찾으면 첫 추정치 근방에 데이터가 없다는 의미 → 실패 처리
            if (backTries >= 5) return null;
        }

        // 이 시점: lastKnown는 성공 회차(존재하는 회차)
        int lowSuccess = lastKnown;
        int step = 32;
        int highFail = -1;

        // 2) 지수 탐색으로 실패 지점(최신을 넘어서는 지점) 찾기
        while (calls <= MAX_TOTAL_CALLS) {
            int candidate = lowSuccess + step;
            ProbeResult prUp = probe(candidate); calls += prUp.calls;
            if (!prUp.success) {
                highFail = candidate; // 최초 실패
                break;
            }
            // 성공이면 더 앞으로 나아감
            lowSuccess = candidate;
            step *= 2;
        }

        if (calls > MAX_TOTAL_CALLS) return null;

        // 만약 실패 지점을 못 찾았으면(= 계속 성공) 호출 한도 내에서 몇 번 더 전진해 보되, 그래도 못 찾으면
        // 마지막 성공을 최신으로 간주
        if (highFail == -1) {
            int extraForward = 0;
            int cur = lowSuccess + 1;
            while (calls <= MAX_TOTAL_CALLS && extraForward < 10) {
                ProbeResult prFwd = probe(cur); calls += prFwd.calls;
                if (!prFwd.success) { highFail = cur; break; }
                lowSuccess = cur;
                cur++; extraForward++;
            }
            if (highFail == -1) {
                // 호출 한도 내에서 실패 지점을 못 찾았지만, 현재까지의 마지막 성공을 최신으로 사용
                LottoDrawDto latest = probe(lowSuccess).dto;
                if (latest != null) {
                    cache(latest);
                    return latest;
                }
                return null;
            }
        }

        // 3) 이진 탐색: (lowSuccess, highFail) 사이에서 마지막 성공 회차를 정확히 찾기
        int left = lowSuccess + 1;
        int right = highFail - 1;
        int lastOk = lowSuccess;
        LottoDrawDto lastOkDto = probe(lowSuccess).dto; // lowSuccess는 성공이었음

        while (left <= right && calls <= MAX_TOTAL_CALLS) {
            int mid = left + (right - left) / 2;
            ProbeResult midRes = probe(mid); calls += midRes.calls;
            if (midRes.success) {
                lastOk = mid;
                lastOkDto = midRes.dto;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        if (lastOkDto != null) {
            cache(lastOkDto);
            return lastOkDto;
        }
        return null;
    }

    /** 단일 회차 조회 */
    private ProbeResult probe(int drawNo) {
        int c = 0;
        try {
            c++;
            Response<LottoDrawDto> res = NetworkProvider.api().getDraw(drawNo).execute();
            LottoDrawDto body = res.body();
            if (res.isSuccessful() && body != null && body.isSuccess()) {
                return new ProbeResult(true, body, c);
            }
            return new ProbeResult(false, null, c);
        } catch (IOException e) {
            return new ProbeResult(false, null, c);
        }
    }

    private void cache(LottoDrawDto d) {
        sp.edit()
                .putInt(KEY_LAST_NO, d.drwNo == null ? 0 : d.drwNo)
                .putInt(KEY_N1, d.n1 == null ? 0 : d.n1)
                .putInt(KEY_N2, d.n2 == null ? 0 : d.n2)
                .putInt(KEY_N3, d.n3 == null ? 0 : d.n3)
                .putInt(KEY_N4, d.n4 == null ? 0 : d.n4)
                .putInt(KEY_N5, d.n5 == null ? 0 : d.n5)
                .putInt(KEY_N6, d.n6 == null ? 0 : d.n6)
                .putInt(KEY_B,  d.bonus == null ? 0 : d.bonus)
                .putString(KEY_DATE, d.date)
                .apply();
    }

    // 내부 결과 홀더
    private static class ProbeResult {
        final boolean success;
        final LottoDrawDto dto;
        final int calls;
        ProbeResult(boolean success, LottoDrawDto dto, int calls) {
            this.success = success;
            this.dto = dto;
            this.calls = calls;
        }
    }
}
