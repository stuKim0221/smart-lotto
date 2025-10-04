package app.grapekim.smartlotto.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import app.grapekim.smartlotto.data.local.room.AppDatabase;
import app.grapekim.smartlotto.data.local.room.dao.AiGenerationLogDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberPairsDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberStatisticsDao;
import app.grapekim.smartlotto.data.local.room.entity.AiGenerationLogEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberPairsEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberStatisticsEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 번호 생성을 위한 데이터 Repository (스레드 안전 처리)
 */
public class AiRepository {

    private final NumberStatisticsDao statisticsDao;
    private final NumberPairsDao pairsDao;
    private final AiGenerationLogDao logDao;

    // 백그라운드 작업용 ExecutorService
    private final ExecutorService backgroundExecutor;
    // UI 스레드 Handler
    private final Handler mainHandler;

    public AiRepository(Context context) {
        AppDatabase database = AppDatabase.get(context);
        this.statisticsDao = database.numberStatisticsDao();
        this.pairsDao = database.numberPairsDao();
        this.logDao = database.aiGenerationLogDao();

        // 스레드 관리 초기화
        this.backgroundExecutor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==================== 콜백 인터페이스 정의 ====================
    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public interface SimpleCallback {
        void onComplete();
        void onError(Exception error);
    }

    // ==================== 통계 데이터 초기화 (비동기 처리) ====================

    /**
     * 통계 데이터 초기화 (비동기)
     */
    public void initializeStatisticsDataAsync(SimpleCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                int count = statisticsDao.getStatisticsCount();
                if (count == 0) {
                    List<NumberStatisticsEntity> initialStats = new ArrayList<>();
                    long currentTime = System.currentTimeMillis();

                    for (int i = 1; i <= 45; i++) {
                        NumberStatisticsEntity entity = new NumberStatisticsEntity(
                                i,                          // number
                                0,                          // appearanceCount
                                0,                          // lastDrawNumber
                                0,                          // lastAppearanceGap
                                Math.random() * 100,        // popularityScore (임시 랜덤값)
                                Math.random() * 100,        // neglectScore (임시 랜덤값)
                                Math.random() * 100,        // trendScore (임시 랜덤값)
                                i % 2 == 1,                 // isOdd
                                i % 10,                     // lastDigit
                                Math.random() * 100,        // avoidanceScore (임시 랜덤값)
                                currentTime                 // updatedAt
                        );
                        initialStats.add(entity);
                    }

                    statisticsDao.insertInitialStatistics(initialStats);
                }

                mainHandler.post(callback::onComplete);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 통계 데이터 초기화 (호환성을 위한 동기 버전 - 사용 시 주의)
     */
    @Deprecated
    public void initializeStatisticsData() {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");

        int count = statisticsDao.getStatisticsCount();
        if (count == 0) {
            List<NumberStatisticsEntity> initialStats = new ArrayList<>();
            long currentTime = System.currentTimeMillis();

            for (int i = 1; i <= 45; i++) {
                NumberStatisticsEntity entity = new NumberStatisticsEntity(
                        i,                          // number
                        0,                          // appearanceCount
                        0,                          // lastDrawNumber
                        0,                          // lastAppearanceGap
                        Math.random() * 100,        // popularityScore (임시 랜덤값)
                        Math.random() * 100,        // neglectScore (임시 랜덤값)
                        Math.random() * 100,        // trendScore (임시 랜덤값)
                        i % 2 == 1,                 // isOdd
                        i % 10,                     // lastDigit
                        Math.random() * 100,        // avoidanceScore (임시 랜덤값)
                        currentTime                 // updatedAt
                );
                initialStats.add(entity);
            }

            statisticsDao.insertInitialStatistics(initialStats);
        }
    }

    // ==================== 번호 조회 메서드들 (비동기 버전 추가) ====================

    /**
     * 인기 번호 조회 (비동기)
     */
    public void getPopularNumbersAsync(int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getPopularNumbers(limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 인기 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getPopularNumbers(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getPopularNumbers(limit);
    }

    /**
     * 소외 번호 조회 (비동기)
     */
    public void getNeglectedNumbersAsync(int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getNeglectedNumbers(limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 소외 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getNeglectedNumbers(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getNeglectedNumbers(limit);
    }

    /**
     * 트렌드 번호 조회 (비동기)
     */
    public void getTrendNumbersAsync(int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getTrendNumbers(limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 트렌드 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getTrendNumbers(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getTrendNumbers(limit);
    }

    /**
     * 홀수 번호 조회 (비동기)
     */
    public void getOddNumbersAsync(int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getNumbersByOddEven(true, limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 홀수 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getOddNumbers(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getNumbersByOddEven(true, limit);
    }

    /**
     * 짝수 번호 조회 (비동기)
     */
    public void getEvenNumbersAsync(int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getNumbersByOddEven(false, limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 짝수 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getEvenNumbers(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getNumbersByOddEven(false, limit);
    }

    /**
     * 구간별 번호 조회 (비동기)
     */
    public void getNumbersByZoneAsync(int zone, int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                int start = (zone - 1) * 9 + 1;
                int end = Math.min(zone * 9, 45);
                List<NumberStatisticsEntity> result = statisticsDao.getNumbersByRange(start, end, limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 구간별 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getNumbersByZone(int zone, int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        int start = (zone - 1) * 9 + 1;
        int end = Math.min(zone * 9, 45);
        return statisticsDao.getNumbersByRange(start, end, limit);
    }

    /**
     * AI 점수 기반 번호 조회 (비동기)
     */
    public void getAiRecommendedNumbersAsync(
            double popWeight, double neglectWeight,
            double trendWeight, double avoidanceWeight,
            int limit, DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = statisticsDao.getNumbersByAiScore(
                        popWeight, neglectWeight, trendWeight, avoidanceWeight, limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * AI 점수 기반 번호 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<NumberStatisticsEntity> getAiRecommendedNumbers(
            double popWeight, double neglectWeight,
            double trendWeight, double avoidanceWeight,
            int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return statisticsDao.getNumbersByAiScore(
                popWeight, neglectWeight, trendWeight, avoidanceWeight, limit);
    }

    /**
     * 페어 분석 기반 번호 추천 (비동기)
     */
    public void getNumbersByPairAnalysisAsync(List<Integer> selectedNumbers, int additionalCount,
                                              DataCallback<List<Integer>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<Integer> recommended = new ArrayList<>();

                for (int selectedNumber : selectedNumbers) {
                    List<NumberPairsEntity> pairs = pairsDao.getPairsByNumber(selectedNumber, 5);
                    for (NumberPairsEntity pair : pairs) {
                        int partnerNumber = (pair.number1 == selectedNumber) ? pair.number2 : pair.number1;
                        if (!selectedNumbers.contains(partnerNumber) && !recommended.contains(partnerNumber)) {
                            recommended.add(partnerNumber);
                            if (recommended.size() >= additionalCount) {
                                break;
                            }
                        }
                    }
                    if (recommended.size() >= additionalCount) {
                        break;
                    }
                }

                mainHandler.post(() -> callback.onSuccess(recommended));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 페어 분석 기반 번호 추천 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<Integer> getNumbersByPairAnalysis(List<Integer> selectedNumbers, int additionalCount) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");

        List<Integer> recommended = new ArrayList<>();

        for (int selectedNumber : selectedNumbers) {
            List<NumberPairsEntity> pairs = pairsDao.getPairsByNumber(selectedNumber, 5);
            for (NumberPairsEntity pair : pairs) {
                int partnerNumber = (pair.number1 == selectedNumber) ? pair.number2 : pair.number1;
                if (!selectedNumbers.contains(partnerNumber) && !recommended.contains(partnerNumber)) {
                    recommended.add(partnerNumber);
                    if (recommended.size() >= additionalCount) {
                        break;
                    }
                }
            }
            if (recommended.size() >= additionalCount) {
                break;
            }
        }

        return recommended;
    }

    // ==================== 필터링 메서드들 (동기식 유지 - CPU 작업) ====================

    /**
     * 연속번호 필터링
     */
    public List<Integer> filterConsecutiveNumbers(List<Integer> numbers) {
        Collections.sort(numbers);
        List<Integer> filtered = new ArrayList<>();

        for (int number : numbers) {
            boolean isConsecutive = false;
            for (int existing : filtered) {
                if (Math.abs(number - existing) == 1) {
                    isConsecutive = true;
                    break;
                }
            }
            if (!isConsecutive) {
                filtered.add(number);
            }
        }

        return filtered;
    }

    /**
     * 끝자리 다양성 필터링
     */
    public List<Integer> filterLastDigitDiversity(List<Integer> numbers) {
        Map<Integer, Integer> digitCount = new HashMap<>();
        List<Integer> filtered = new ArrayList<>();

        for (int number : numbers) {
            int lastDigit = number % 10;
            int count = digitCount.getOrDefault(lastDigit, 0);
            if (count < 2) { // 같은 끝자리는 최대 2개까지
                filtered.add(number);
                digitCount.put(lastDigit, count + 1);
            }
        }

        return filtered;
    }

    // ==================== AI 생성 로그 관리 (비동기 처리) ====================

    /**
     * AI 생성 로그 저장 (비동기)
     */
    public void saveGenerationLogAsync(List<Integer> numbers, List<String> strategies,
                                       double qualityScore, String method, DataCallback<Long> callback) {
        backgroundExecutor.execute(() -> {
            try {
                String numbersJson = convertNumbersToJson(numbers);
                String strategiesJson = convertStrategiesToJson(strategies);

                AiGenerationLogEntity log = new AiGenerationLogEntity(
                        numbersJson,
                        strategiesJson,
                        qualityScore,
                        method,
                        System.currentTimeMillis(),
                        false,
                        ""
                );

                long result = logDao.insertLog(log);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * AI 생성 로그 저장 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public long saveGenerationLog(List<Integer> numbers, List<String> strategies,
                                  double qualityScore, String method) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");

        String numbersJson = convertNumbersToJson(numbers);
        String strategiesJson = convertStrategiesToJson(strategies);

        AiGenerationLogEntity log = new AiGenerationLogEntity(
                numbersJson,
                strategiesJson,
                qualityScore,
                method,
                System.currentTimeMillis(),
                false,
                ""
        );

        return logDao.insertLog(log);
    }

    /**
     * 생성 로그의 저장 상태 업데이트 (비동기)
     */
    public void updateLogSaveStatusAsync(long logId, boolean isSaved, SimpleCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                logDao.updateSaveStatus(logId, isSaved);
                mainHandler.post(callback::onComplete);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 생성 로그의 저장 상태 업데이트 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public void updateLogSaveStatus(long logId, boolean isSaved) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        logDao.updateSaveStatus(logId, isSaved);
    }

    /**
     * 최근 생성 로그 조회 (비동기)
     */
    public void getRecentLogsAsync(int limit, DataCallback<List<AiGenerationLogEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<AiGenerationLogEntity> result = logDao.getRecentLogs(limit);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 최근 생성 로그 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<AiGenerationLogEntity> getRecentLogs(int limit) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return logDao.getRecentLogs(limit);
    }

    /**
     * 저장된 로그들 조회 (비동기)
     */
    public void getSavedLogsAsync(DataCallback<List<AiGenerationLogEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<AiGenerationLogEntity> result = logDao.getSavedLogs();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 저장된 로그들 조회 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public List<AiGenerationLogEntity> getSavedLogs() {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        return logDao.getSavedLogs();
    }

    /**
     * 중복 번호 조합 확인 (비동기)
     */
    public void isDuplicateNumbersAsync(List<Integer> numbers, DataCallback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                String numbersJson = convertNumbersToJson(numbers);
                boolean result = logDao.checkDuplicateNumbers(numbersJson) > 0;
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 중복 번호 조합 확인 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public boolean isDuplicateNumbers(List<Integer> numbers) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        String numbersJson = convertNumbersToJson(numbers);
        return logDao.checkDuplicateNumbers(numbersJson) > 0;
    }

    // ==================== Helper 메서드들 (동기식 유지) ====================

    private String convertNumbersToJson(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < numbers.size(); i++) {
            sb.append(numbers.get(i));
            if (i < numbers.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String convertStrategiesToJson(List<String> strategies) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < strategies.size(); i++) {
            sb.append("\"").append(strategies.get(i)).append("\"");
            if (i < strategies.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ==================== 통계 데이터 업데이트 (비동기 처리) ====================

    /**
     * 통계 데이터 업데이트 (비동기)
     */
    public void updateStatisticsFromDrawDataAsync(int drawNumber, List<Integer> winningNumbers,
                                                  SimpleCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                updateStatisticsFromDrawDataInternal(drawNumber, winningNumbers);
                mainHandler.post(callback::onComplete);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * 통계 데이터 업데이트 (호환성을 위한 동기 버전)
     */
    @Deprecated
    public void updateStatisticsFromDrawData(int drawNumber, List<Integer> winningNumbers) {
        android.util.Log.w("AiRepository", "동기 메서드 사용 - 백그라운드에서만 호출하세요");
        updateStatisticsFromDrawDataInternal(drawNumber, winningNumbers);
    }

    private void updateStatisticsFromDrawDataInternal(int drawNumber, List<Integer> winningNumbers) {
        long currentTime = System.currentTimeMillis();

        for (int i = 1; i <= 45; i++) {
            NumberStatisticsEntity stats = statisticsDao.getStatisticsByNumber(i);
            if (stats == null) {
                stats = new NumberStatisticsEntity();
                stats.number = i;
                stats.appearanceCount = 0;
                stats.lastDrawNumber = 0;
                stats.lastAppearanceGap = drawNumber;
                stats.isOdd = i % 2 == 1;
                stats.lastDigit = i % 10;
            }

            if (winningNumbers.contains(i)) {
                stats.appearanceCount++;
                stats.lastAppearanceGap = 0;
                stats.lastDrawNumber = drawNumber;
            } else {
                stats.lastAppearanceGap = drawNumber - stats.lastDrawNumber;
            }

            // 점수 재계산
            calculateScores(stats, drawNumber);
            stats.updatedAt = currentTime;

            statisticsDao.insertOrUpdateStatistics(stats);
        }
    }

    private void calculateScores(NumberStatisticsEntity stats, int currentDrawNumber) {
        // 인기도 점수 (출현 빈도 기반)
        stats.popularityScore = stats.appearanceCount * 2.0;

        // 소외도 점수 (오래 안나온 정도)
        stats.neglectScore = stats.lastAppearanceGap * 1.5;

        // 트렌드 점수 (최근 10회 내 출현 시 높은 점수)
        if (stats.lastAppearanceGap <= 10) {
            stats.trendScore = 100 - (stats.lastAppearanceGap * 5);
        } else {
            stats.trendScore = Math.max(0, 50 - stats.lastAppearanceGap);
        }

        // 기피도 점수 (대중이 선호하지 않는 번호일수록 높음)
        if (stats.number == 4 || stats.number == 13 || stats.number == 24 || stats.number == 44) {
            stats.avoidanceScore = 80;
        } else if (stats.number % 10 == 4) {
            stats.avoidanceScore = 60;
        } else {
            stats.avoidanceScore = 40;
        }
    }

    // ==================== 리소스 정리 ====================

    public void cleanup() {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}