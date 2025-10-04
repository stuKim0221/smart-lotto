package app.grapekim.smartlotto.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 로또 회차 정보 캐시 관리 클래스 (싱글톤)
 * 앱 시작 시 최신 회차를 백그라운드에서 조회하여 캐시하고,
 * AI 번호 생성 시 빠르게 회차 정보를 제공합니다.
 */
public class RoundCache {

    private static final String TAG = "RoundCache";
    private static final String PREF_NAME = "round_cache";
    private static final String KEY_LATEST_ROUND = "latest_round";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final long CACHE_VALID_DURATION = 24 * 60 * 60 * 1000L; // 24시간

    private static RoundCache instance;
    private final ExecutorService executor;
    private SharedPreferences prefs;

    // 캐시된 데이터
    private Integer cachedLatestRound;
    private long lastUpdateTime;
    private boolean isLoading = false;

    private RoundCache() {
        executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized RoundCache getInstance() {
        if (instance == null) {
            instance = new RoundCache();
        }
        return instance;
    }

    /**
     * 초기화 (앱 시작 시 호출)
     */
    public void initialize(Context context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            loadFromPrefs();
        }

        // 캐시가 없거나 오래되었으면 업데이트
        if (shouldUpdateCache()) {
            updateCacheInBackground();
        }
    }

    /**
     * SharedPreferences에서 캐시 로드
     */
    private void loadFromPrefs() {
        cachedLatestRound = prefs.getInt(KEY_LATEST_ROUND, -1);
        lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE, 0);

        if (cachedLatestRound == -1) {
            cachedLatestRound = null;
        }

        Log.d(TAG, "캐시에서 로드: round=" + cachedLatestRound + ", lastUpdate=" + lastUpdateTime);
    }

    /**
     * SharedPreferences에 캐시 저장
     */
    private void saveToPrefs() {
        if (prefs != null && cachedLatestRound != null) {
            prefs.edit()
                    .putInt(KEY_LATEST_ROUND, cachedLatestRound)
                    .putLong(KEY_LAST_UPDATE, lastUpdateTime)
                    .apply();
            Log.d(TAG, "캐시 저장: round=" + cachedLatestRound);
        }
    }

    /**
     * 캐시 업데이트가 필요한지 확인
     */
    private boolean shouldUpdateCache() {
        return cachedLatestRound == null ||
                (System.currentTimeMillis() - lastUpdateTime) > CACHE_VALID_DURATION;
    }

    /**
     * 백그라운드에서 캐시 업데이트
     */
    public void updateCacheInBackground() {
        if (isLoading) {
            Log.d(TAG, "이미 업데이트 중입니다.");
            return;
        }

        isLoading = true;
        Log.d(TAG, "백그라운드에서 회차 정보 업데이트 시작");

        executor.execute(() -> {
            try {
                LottoDrawDto latest = RoundUtils.findLatestRoundSync();
                if (latest != null && latest.drwNo != null) {
                    cachedLatestRound = latest.drwNo;
                    lastUpdateTime = System.currentTimeMillis();
                    saveToPrefs();
                    Log.d(TAG, "회차 업데이트 성공: " + cachedLatestRound);
                } else {
                    Log.w(TAG, "회차 업데이트 실패: latest가 null");
                }
            } catch (Exception e) {
                Log.e(TAG, "회차 업데이트 중 오류", e);
            } finally {
                isLoading = false;
            }
        });
    }

    /**
     * 캐시된 최신 회차 조회 (메인 스레드에서 안전하게 호출 가능)
     * @return 최신 회차 번호, 캐시가 없으면 null
     */
    public Integer getLatestRound() {
        return cachedLatestRound;
    }

    /**
     * 다음 회차 번호 조회 (AI 생성용)
     * @return 다음 회차 번호, 캐시가 없으면 대략적인 추정값
     */
    public Integer getNextRound() {
        if (cachedLatestRound != null) {
            return cachedLatestRound + 1;
        }

        // 캐시가 없으면 날짜 기반 추정
        return estimateRoundByDate();
    }

    /**
     * 날짜 기반 회차 추정 (fallback)
     */
    private Integer estimateRoundByDate() {
        try {
            long currentTime = System.currentTimeMillis();
            // 2002년 12월 7일 (첫 로또 추첨일)부터 주 단위 계산
            long weeksSince2002 = (currentTime - 1041724800000L) / (7 * 24 * 60 * 60 * 1000L);
            int estimatedRound = (int) weeksSince2002 + 1;

            Log.d(TAG, "날짜 기반 회차 추정: " + estimatedRound);
            return estimatedRound;
        } catch (Exception e) {
            Log.e(TAG, "날짜 기반 추정 실패", e);
            return 1186; // 최후의 fallback
        }
    }

    /**
     * 캐시 상태 정보
     */
    public String getCacheInfo() {
        return String.format("Round: %s, LastUpdate: %d, IsLoading: %b",
                cachedLatestRound, lastUpdateTime, isLoading);
    }

    /**
     * 강제 캐시 업데이트 (개발/테스트용)
     */
    public void forceUpdate() {
        isLoading = false; // 로딩 상태 리셋
        updateCacheInBackground();
    }

    /**
     * 캐시 클리어 (개발/테스트용)
     */
    public void clearCache() {
        cachedLatestRound = null;
        lastUpdateTime = 0;
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
        Log.d(TAG, "캐시 클리어 완료");
    }
}