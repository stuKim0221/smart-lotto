package app.grapekim.smartlotto.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import app.grapekim.smartlotto.data.local.room.AppDatabase;
import app.grapekim.smartlotto.data.local.room.dao.AiGenerationLogDao;
import app.grapekim.smartlotto.data.local.room.dao.GeneratedPickDao;
import app.grapekim.smartlotto.data.local.room.dao.LottoDrawHistoryDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberPairsDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberStatisticsDao;
import app.grapekim.smartlotto.data.local.room.entity.AiGenerationLogEntity;
import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;
import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberPairsEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberStatisticsEntity;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import app.grapekim.smartlotto.util.RoundUtils;
import app.grapekim.smartlotto.util.RoundCache;
import app.grapekim.smartlotto.util.LottoNumberAnalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class LottoRepositoryImpl implements LottoRepository {

    // ==================== DAO + 스레드 관리 ====================
    private final GeneratedPickDao dao;
    private final LottoDrawHistoryDao drawHistoryDao;
    private final NumberStatisticsDao numberStatisticsDao;
    private final NumberPairsDao numberPairsDao;
    private final AiGenerationLogDao aiGenerationLogDao;

    // 백그라운드 작업용 ExecutorService
    private final ExecutorService backgroundExecutor;
    // UI 스레드 Handler
    private final Handler mainHandler;

    public LottoRepositoryImpl(Context context) {
        AppDatabase database = AppDatabase.get(context);
        this.dao = database.generatedPickDao();
        this.drawHistoryDao = database.lottoDrawHistoryDao();
        this.numberStatisticsDao = database.numberStatisticsDao();
        this.numberPairsDao = database.numberPairsDao();
        this.aiGenerationLogDao = database.aiGenerationLogDao();

        // 스레드 관리 초기화
        this.backgroundExecutor = Executors.newFixedThreadPool(3);
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

    // ==================== 기존 메서드들 (동기식 유지) ====================

    @Override
    public long saveAutoPick(List<Integer> numbers) {
        return saveWithMethod("AUTO", numbers, /*round*/ null, /*purchaseAt*/ null);
    }

    @Override
    @Deprecated
    public long saveManualPick(List<Integer> numbers, @Nullable Integer round, @Nullable Long purchaseAt) {
        return saveWithMethod("MANUAL", numbers, round, purchaseAt);
    }

    private long saveWithMethod(String method,
                                List<Integer> numbers,
                                @Nullable Integer round,
                                @Nullable Long purchaseAt) {
        if (numbers == null) numbers = Collections.emptyList();

        String csv = numbers.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        int sum = numbers.stream().mapToInt(i -> i).sum();

        // 저장 시각: QR로부터 구매시각이 왔으면 그것을 사용
        long when = (purchaseAt != null) ? purchaseAt : System.currentTimeMillis();

        // 타이틀 계산
        String title = buildTitle(method, sum, when, round);

        GeneratedPickEntity e = new GeneratedPickEntity();
        e.numbersCsv = csv;
        e.createdAt = when;
        e.favorite = false;
        e.method = method; // "AUTO" / "MANUAL"
        e.title = title;

        return dao.insert(e);
    }

    /**
     * 제목 생성 (동기식 유지 - RoundCache 사용)
     */
    private String buildTitle(String method, int sum, long whenMillis, @Nullable Integer providedRound) {
        android.util.Log.d("BuildTitle", "=== buildTitle 시작 ===");
        android.util.Log.d("BuildTitle", "method: " + method + ", sum: " + sum + ", providedRound: " + providedRound);

        String prefix;
        switch (method) {
            case "AUTO":
                prefix = "[자동]";
                break;
            case "MANUAL":
                prefix = "[수동]";
                break;
            case "AI":
            case "수정":
                prefix = "[AI]";
                break;
            case "QR":
                prefix = "[QR]";
                break;
            default:
                prefix = "[" + method + "]";
                break;
        }

        Integer roundNo = providedRound;

        if (roundNo == null) {
            try {
                LottoDrawDto latest = RoundUtils.findLatestRoundSync();
                roundNo = RoundUtils.computeExpectedRoundForTimestamp(whenMillis, latest);
            } catch (Exception e) {
                android.util.Log.d("BuildTitle", "RoundUtils 실패: " + e.getMessage());
                roundNo = null;
            }
        }

        // AI의 경우 회차를 더 적극적으로 추정
        if (roundNo == null && ("AI".equals(method) || "수정".equals(method))) {
            try {
                LottoDrawDto latest = RoundUtils.findLatestRoundSync();
                if (latest != null && latest.drwNo != null) {
                    roundNo = latest.drwNo + 1;
                } else {
                    // fallback 계산
                    long currentTime = System.currentTimeMillis();
                    long weeksSince2002 = (currentTime - 1041724800000L) / (7 * 24 * 60 * 60 * 1000L);
                    roundNo = (int) weeksSince2002 + 1;
                }
            } catch (Exception e) {
                android.util.Log.d("BuildTitle", "AI 회차 추정 실패: " + e.getMessage());
            }
        }

        String finalTitle;
        if (roundNo != null && roundNo > 0) {
            finalTitle = prefix + " " + roundNo + "회차 예상 로또번호";
        } else {
            finalTitle = String.format(Locale.getDefault(), "%s 합계 %d", prefix, sum);
        }

        android.util.Log.d("BuildTitle", "=== buildTitle 완료: " + finalTitle + " ===");
        return finalTitle;
    }

    // ==================== AI 데이터 정리 메서드들 (비동기 처리) ====================

    @Override
    public void updateAiMethodLabels() {
        backgroundExecutor.execute(() -> {
            try {
                dao.updateMethodFromTo("수정", "AI");
                refreshAiTitles();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "updateAiMethodLabels 실패", e);
            }
        });
    }

    @Override
    public void initializeAiDataCleanup() {
        updateAiMethodLabels();
    }

    /**
     * AI 제목 새로고침 (백그라운드에서 실행)
     */
    private void refreshAiTitles() {
        List<GeneratedPickEntity> aiPicks = dao.getByMethod("AI");
        for (GeneratedPickEntity pick : aiPicks) {
            List<Integer> numbers = parseNumbersFromCsv(pick.numbersCsv);
            int sum = numbers.stream().mapToInt(i -> i).sum();
            String newTitle = buildTitle("AI", sum, pick.createdAt, pick.targetRound);
            dao.updateTitle(pick.id, newTitle);
        }
    }

    /**
     * CSV에서 숫자 리스트로 파싱
     */
    private List<Integer> parseNumbersFromCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    // ==================== QR 관련 메서드들 (동기식 유지) ====================

    @Override
    public List<Long> saveQrGames(List<List<Integer>> allGames,
                                  @Nullable Integer round,
                                  @Nullable Long purchaseAt,
                                  @Nullable String rawData,
                                  String sourceType) {
        if (allGames == null || allGames.isEmpty()) {
            return Collections.emptyList();
        }

        String qrGroupId = GeneratedPickEntity.generateQrGroupId();
        long when = (purchaseAt != null) ? purchaseAt : System.currentTimeMillis();

        List<Long> savedIds = new ArrayList<>();
        String[] gameLabels = {"A", "B", "C", "D", "E"};

        for (int i = 0; i < allGames.size(); i++) {
            List<Integer> gameNumbers = allGames.get(i);
            String gameLabel = (i < gameLabels.length) ? gameLabels[i] : String.valueOf(i + 1);

            long id = saveQrGame(gameNumbers, round, when, rawData, sourceType,
                    qrGroupId, gameLabel, allGames.size());
            savedIds.add(id);
        }

        return savedIds;
    }

    @Override
    public long saveQrSingleGame(List<Integer> numbers,
                                 @Nullable Integer round,
                                 @Nullable Long purchaseAt,
                                 @Nullable String rawData,
                                 String sourceType) {
        long when = (purchaseAt != null) ? purchaseAt : System.currentTimeMillis();
        return saveQrGame(numbers, round, when, rawData, sourceType, null, null, 1);
    }

    private long saveQrGame(List<Integer> numbers,
                            @Nullable Integer round,
                            long when,
                            @Nullable String rawData,
                            String sourceType,
                            @Nullable String qrGroupId,
                            @Nullable String gameLabel,
                            int totalGameCount) {
        if (numbers == null) numbers = Collections.emptyList();

        String csv = numbers.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        String title = buildQrTitle(gameLabel, round, totalGameCount);

        GeneratedPickEntity e = new GeneratedPickEntity();
        e.numbersCsv = csv;
        e.createdAt = System.currentTimeMillis();
        e.favorite = false;
        e.method = "QR";
        e.title = title;
        e.qrGroupId = qrGroupId;
        e.gameLabel = gameLabel;
        e.qrRawData = rawData;
        e.purchaseTime = when;
        e.parsedRound = round;
        e.sourceType = sourceType;
        e.targetRound = round;

        return dao.insert(e);
    }

    private String buildQrTitle(@Nullable String gameLabel, @Nullable Integer round, int totalGameCount) {
        return GeneratedPickEntity.createQrTitle(gameLabel, round, totalGameCount);
    }

    // ==================== QR 그룹 관리 (동기식 유지) ====================

    @Override
    public List<GeneratedPickEntity> getQrGroup(String qrGroupId) {
        return dao.getByQrGroupId(qrGroupId);
    }

    @Override
    public void deleteQrGroup(String qrGroupId) {
        dao.deleteByQrGroupId(qrGroupId);
    }

    @Override
    public void deleteQrGroupIfNotFavorite(String qrGroupId) {
        dao.deleteByQrGroupIdIfNotFavorite(qrGroupId);
    }

    @Override
    public void setQrGroupFavorite(String qrGroupId, boolean favorite) {
        dao.updateQrGroupFavorite(qrGroupId, favorite);
    }

    @Override
    @Nullable
    public String getQrGroupId(long id) {
        return dao.getQrGroupId(id);
    }

    @Override
    public int getQrGroupSize(String qrGroupId) {
        return dao.getQrGroupSize(qrGroupId);
    }

    @Override
    public boolean isQrGame(long id) {
        String method = dao.getMethod(id);
        return "QR".equals(method);
    }

    @Override
    public List<String> getAllQrGroupIds() {
        return dao.getAllQrGroupIds();
    }

    // ==================== 기존 조회/수정/삭제 메서드들 (동기식 유지) ====================

    @Override
    public LiveData<List<GeneratedPickEntity>> observeHistory(boolean onlyFav, boolean newestFirst) {
        if (onlyFav) {
            return newestFirst ? dao.observeFavoritesDesc() : dao.observeFavoritesAsc();
        } else {
            return newestFirst ? dao.observeAllDesc() : dao.observeAllAsc();
        }
    }

    @Override
    public void setFavorite(long id, boolean fav) {
        dao.updateFavorite(id, fav);
    }

    @Override
    public void deletePick(long id) {
        dao.deleteById(id);
    }

    @Override
    public void deletePickIfNotFavorite(long id) {
        dao.deleteByIdIfNotFavorite(id);
    }

    @Override
    public void clearAll() {
        dao.clearAll();
    }

    @Override
    public void clearAllExceptFavorites() {
        dao.clearAllExceptFavorites();
    }

    @Override
    public void updateResult(long id, int rank, int matchCount, int targetRound) {
        dao.updateResult(id, rank, matchCount, targetRound);
    }

    // ==================== AI 번호 생성 기능 (비동기 처리로 변경) ====================

    // ********** 로또 당첨번호 이력 관리 (비동기) **********

    @Override
    public long saveLottoDrawHistory(int drawNumber, String drawDate, List<Integer> numbers) {
        if (numbers == null || numbers.size() != 7) {
            throw new IllegalArgumentException("당첨번호는 정확히 7개(본번호 6개 + 보너스 1개)여야 합니다.");
        }

        LottoDrawHistoryEntity entity = new LottoDrawHistoryEntity();
        entity.drawNumber = drawNumber;
        entity.drawDate = drawDate;
        entity.number1 = numbers.get(0);
        entity.number2 = numbers.get(1);
        entity.number3 = numbers.get(2);
        entity.number4 = numbers.get(3);
        entity.number5 = numbers.get(4);
        entity.number6 = numbers.get(5);
        entity.bonusNumber = numbers.get(6);
        entity.createdAt = System.currentTimeMillis();

        // 동기 호출 유지 (이미 백그라운드에서 호출됨)
        return drawHistoryDao.insert(entity);
    }

    @Override
    public List<Long> saveLottoDrawHistories(List<LottoDrawHistoryEntity> drawHistories) {
        if (drawHistories == null || drawHistories.isEmpty()) {
            return Collections.emptyList();
        }
        return drawHistoryDao.insertAll(drawHistories);
    }

    // 비동기 버전 추가
    public void getAllDrawHistoryAsync(DataCallback<List<LottoDrawHistoryEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<LottoDrawHistoryEntity> result = drawHistoryDao.getAll();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    @Override
    public List<LottoDrawHistoryEntity> getAllDrawHistory() {
        // 호환성을 위해 동기 메서드 유지 - 호출 시 주의 필요
        return drawHistoryDao.getAll();
    }

    @Override
    public List<LottoDrawHistoryEntity> getRecentDrawHistory(int count) {
        return drawHistoryDao.getRecent(count);
    }

    @Override
    @Nullable
    public LottoDrawHistoryEntity getDrawHistory(int drawNumber) {
        return drawHistoryDao.getByDrawNumber(drawNumber);
    }

    @Override
    @Nullable
    public Integer getLatestDrawNumber() {
        return drawHistoryDao.getLatestDrawNumber();
    }

    @Override
    public int getTotalDrawCount() {
        return drawHistoryDao.getTotalCount();
    }

    // ********** 번호별 통계 관리 (비동기) **********

    public void getAllNumberStatisticsAsync(DataCallback<List<NumberStatisticsEntity>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<NumberStatisticsEntity> result = numberStatisticsDao.getAllStatistics();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    @Override
    public List<NumberStatisticsEntity> getAllNumberStatistics() {
        // 호환성을 위해 동기 메서드 유지 - 호출 시 주의 필요
        return numberStatisticsDao.getAllStatistics();
    }

    @Override
    @Nullable
    public NumberStatisticsEntity getNumberStatistics(int number) {
        return numberStatisticsDao.getStatisticsByNumber(number);
    }

    @Override
    public List<NumberStatisticsEntity> getNumberStatistics(List<Integer> numbers) {
        List<NumberStatisticsEntity> result = new ArrayList<>();
        for (Integer number : numbers) {
            NumberStatisticsEntity stats = numberStatisticsDao.getStatisticsByNumber(number);
            if (stats != null) {
                result.add(stats);
            }
        }
        return result;
    }

    @Override
    public List<NumberStatisticsEntity> getPopularNumbers(int count) {
        return numberStatisticsDao.getPopularNumbers(count);
    }

    @Override
    public List<NumberStatisticsEntity> getNeglectedNumbers(int count) {
        return numberStatisticsDao.getNeglectedNumbers(count);
    }

    @Override
    public List<NumberStatisticsEntity> getTrendingNumbers(int count) {
        return numberStatisticsDao.getTrendNumbers(count);
    }

    @Override
    public List<NumberStatisticsEntity> getAvoidedNumbers(int count) {
        List<NumberStatisticsEntity> all = numberStatisticsDao.getAllStatistics();
        return all.stream()
                .sorted((a, b) -> Double.compare(b.avoidanceScore, a.avoidanceScore))
                .limit(count)
                .collect(Collectors.toList());
    }

    @Override
    public List<NumberStatisticsEntity> getNumbersByOddEven(boolean isOdd) {
        return numberStatisticsDao.getNumbersByOddEven(isOdd, 25);
    }

    @Override
    public List<NumberStatisticsEntity> getNumbersByZone(int zone) {
        int start = (zone - 1) * 9 + 1;
        int end = Math.min(zone * 9, 45);
        return numberStatisticsDao.getNumbersByRange(start, end, 9);
    }

    @Override
    public void updateNumberStatistics() {
        backgroundExecutor.execute(() -> {
            try {
                updateNumberStatisticsInternal();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "updateNumberStatistics 실패", e);
            }
        });
    }

    private void updateNumberStatisticsInternal() {
        List<LottoDrawHistoryEntity> allDraws = drawHistoryDao.getAll();
        if (allDraws.isEmpty()) return;

        int totalDrawCount = allDraws.size();
        Integer latestDrawNumber = drawHistoryDao.getLatestDrawNumber();
        if (latestDrawNumber == null) latestDrawNumber = 0;

        // 각 번호별 통계 업데이트
        for (int number = 1; number <= 45; number++) {
            NumberStatisticsEntity stats = numberStatisticsDao.getStatisticsByNumber(number);
            if (stats == null) {
                stats = new NumberStatisticsEntity();
                stats.number = number;
                stats.isOdd = (number % 2 == 1);
                stats.lastDigit = number % 10;
                stats.avoidanceScore = calculateAvoidanceScore(number);
            }

            // 통계 계산
            stats.appearanceCount = calculateAppearanceCount(allDraws, number);
            stats.lastDrawNumber = calculateLastDrawNumber(allDraws, number);
            stats.lastAppearanceGap = latestDrawNumber - stats.lastDrawNumber;
            stats.popularityScore = calculatePopularityScore(stats.appearanceCount, totalDrawCount);
            stats.neglectScore = calculateNeglectScore(stats.lastAppearanceGap);
            stats.trendScore = calculateTrendScore(allDraws, number);
            stats.updatedAt = System.currentTimeMillis();

            numberStatisticsDao.insertOrUpdateStatistics(stats);
        }
    }

    // 통계 계산 헬퍼 메서드들 (기존과 동일)
    private int calculateAppearanceCount(List<LottoDrawHistoryEntity> draws, int number) {
        int count = 0;
        for (LottoDrawHistoryEntity draw : draws) {
            if (draw.number1 == number || draw.number2 == number || draw.number3 == number ||
                    draw.number4 == number || draw.number5 == number || draw.number6 == number) {
                count++;
            }
        }
        return count;
    }

    private int calculateLastDrawNumber(List<LottoDrawHistoryEntity> draws, int number) {
        for (int i = draws.size() - 1; i >= 0; i--) {
            LottoDrawHistoryEntity draw = draws.get(i);
            if (draw.number1 == number || draw.number2 == number || draw.number3 == number ||
                    draw.number4 == number || draw.number5 == number || draw.number6 == number) {
                return draw.drawNumber;
            }
        }
        return 0;
    }

    private double calculatePopularityScore(int appearanceCount, int totalDrawCount) {
        if (totalDrawCount == 0) return 0.0;
        return (double) appearanceCount / totalDrawCount * 100.0;
    }

    private double calculateNeglectScore(int gap) {
        return Math.max(0, gap * 2.0);
    }

    private double calculateTrendScore(List<LottoDrawHistoryEntity> draws, int number) {
        if (draws.size() < 10) return 50.0;

        int recentCount = 0;
        int checkCount = Math.min(10, draws.size());

        for (int i = draws.size() - checkCount; i < draws.size(); i++) {
            LottoDrawHistoryEntity draw = draws.get(i);
            if (draw.number1 == number || draw.number2 == number || draw.number3 == number ||
                    draw.number4 == number || draw.number5 == number || draw.number6 == number) {
                recentCount++;
            }
        }

        return (double) recentCount / checkCount * 100.0;
    }

    private double calculateAvoidanceScore(int number) {
        // 기피되는 번호들 (높은 점수)
        if (number == 4 || number == 13 || number == 14 || number == 24 || number == 34 || number == 44) {
            return 80.0;
        }

        // 중간 정도 기피
        if (number % 10 == 4 || number % 10 == 0) {
            return 60.0;
        }

        // 선호되는 번호들 (낮은 점수)
        if (number == 7 || number == 3 || number == 8 || number == 1 || number == 9) {
            return 20.0;
        }

        return 50.0; // 기본값
    }

    // ********** 번호 쌍 분석 관리 (비동기) **********

    @Override
    public List<NumberPairsEntity> getAllNumberPairs() {
        return numberPairsDao.getAllPairs();
    }

    @Override
    public List<NumberPairsEntity> getTopNumberPairs(int count) {
        return numberPairsDao.getTopPairs(count);
    }

    @Override
    public List<NumberPairsEntity> getNumberPairsContaining(int number) {
        return numberPairsDao.getPairsByNumber(number, 20);
    }

    @Override
    @Nullable
    public NumberPairsEntity getBestPartnerFor(int number) {
        List<NumberPairsEntity> pairs = numberPairsDao.getPairsByNumber(number, 1);
        return pairs.isEmpty() ? null : pairs.get(0);
    }

    @Override
    public List<NumberPairsEntity> getConsecutivePairs() {
        List<NumberPairsEntity> all = numberPairsDao.getAllPairs();
        return all.stream()
                .filter(pair -> Math.abs(pair.number1 - pair.number2) == 1)
                .collect(Collectors.toList());
    }

    @Override
    public List<NumberPairsEntity> getNonConsecutivePairs() {
        List<NumberPairsEntity> all = numberPairsDao.getAllPairs();
        return all.stream()
                .filter(pair -> Math.abs(pair.number1 - pair.number2) != 1)
                .collect(Collectors.toList());
    }

    @Override
    public void updateNumberPairs() {
        backgroundExecutor.execute(() -> {
            try {
                updateNumberPairsInternal();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "updateNumberPairs 실패", e);
            }
        });
    }

    private void updateNumberPairsInternal() {
        List<LottoDrawHistoryEntity> allDraws = drawHistoryDao.getAll();
        if (allDraws.isEmpty()) return;

        int totalDrawCount = allDraws.size();
        Map<String, NumberPairsEntity> pairMap = new HashMap<>();

        // 모든 가능한 번호 쌍 생성 및 출현 횟수 계산
        for (int i = 1; i <= 45; i++) {
            for (int j = i + 1; j <= 45; j++) {
                int pairCount = calculatePairCount(allDraws, i, j);
                if (pairCount > 0) {
                    NumberPairsEntity pair = new NumberPairsEntity();
                    pair.number1 = i;
                    pair.number2 = j;
                    pair.pairCount = pairCount;
                    pair.pairScore = (double) pairCount / totalDrawCount * 100.0;
                    pair.lastDrawTogether = calculateLastTogetherDraw(allDraws, i, j);
                    pair.updatedAt = System.currentTimeMillis();

                    String key = i + "-" + j;
                    pairMap.put(key, pair);
                }
            }
        }

        // 데이터베이스에 저장
        numberPairsDao.deleteAllPairs(); // 기존 데이터 삭제
        if (!pairMap.isEmpty()) {
            numberPairsDao.insertPairs(new ArrayList<>(pairMap.values()));
        }
    }

    private int calculatePairCount(List<LottoDrawHistoryEntity> draws, int num1, int num2) {
        int count = 0;
        for (LottoDrawHistoryEntity draw : draws) {
            List<Integer> numbers = new ArrayList<>();
            numbers.add(draw.number1);
            numbers.add(draw.number2);
            numbers.add(draw.number3);
            numbers.add(draw.number4);
            numbers.add(draw.number5);
            numbers.add(draw.number6);

            if (numbers.contains(num1) && numbers.contains(num2)) {
                count++;
            }
        }
        return count;
    }

    private int calculateLastTogetherDraw(List<LottoDrawHistoryEntity> draws, int num1, int num2) {
        for (int i = draws.size() - 1; i >= 0; i--) {
            LottoDrawHistoryEntity draw = draws.get(i);
            List<Integer> numbers = new ArrayList<>();
            numbers.add(draw.number1);
            numbers.add(draw.number2);
            numbers.add(draw.number3);
            numbers.add(draw.number4);
            numbers.add(draw.number5);
            numbers.add(draw.number6);

            if (numbers.contains(num1) && numbers.contains(num2)) {
                return draw.drawNumber;
            }
        }
        return 0;
    }

    // ********** AI 번호 생성 (콜백 패턴으로 변경) **********

    public void generateAiNumbersAsync(List<String> strategies, List<Double> strategyWeights, int count,
                                       DataCallback<List<List<Integer>>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<List<Integer>> result = generateAiNumbersInternal(strategies, strategyWeights, count);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    @Override
    public List<List<Integer>> generateAiNumbers(List<String> strategies, List<Double> strategyWeights, int count) {
        // 호환성을 위해 동기 메서드 유지 - 호출 시 주의 필요
        return generateAiNumbersInternal(strategies, strategyWeights, count);
    }

    @Override
    public List<List<Integer>> generateAiNumbers(List<String> strategies, int count) {
        List<Double> weights = new ArrayList<>();
        double equalWeight = 1.0 / strategies.size();
        for (int i = 0; i < strategies.size(); i++) {
            weights.add(equalWeight);
        }
        return generateAiNumbers(strategies, weights, count);
    }

    private List<List<Integer>> generateAiNumbersInternal(List<String> strategies, List<Double> strategyWeights, int count) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("최소 하나의 전략을 선택해야 합니다.");
        }

        if (strategyWeights != null && strategies.size() != strategyWeights.size()) {
            throw new IllegalArgumentException("전략과 가중치의 개수가 일치하지 않습니다.");
        }

        List<List<Integer>> results = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            List<Integer> generatedNumbers = generateSingleAiNumbers(strategies, strategyWeights);
            results.add(generatedNumbers);
        }

        return results;
    }

    /**
     * 단일 AI 번호 조합 생성 (백그라운드에서 실행) - 개선된 랜덤성
     */
    private List<Integer> generateSingleAiNumbers(List<String> strategies, List<Double> strategyWeights) {
        long startTime = System.currentTimeMillis();
        android.util.Log.d("AI_GENERATION", "=== AI 번호 생성 시작 (개선된 랜덤성) ===");
        android.util.Log.d("AI_GENERATION", "전략: " + strategies);

        // 각 번호(1-45)에 대한 점수 계산
        Map<Integer, Double> numberScores = new HashMap<>();

        // 모든 번호별 통계 로드
        List<NumberStatisticsEntity> allStats = numberStatisticsDao.getAllStatistics();
        Map<Integer, NumberStatisticsEntity> statsMap = new HashMap<>();
        for (NumberStatisticsEntity stat : allStats) {
            statsMap.put(stat.number, stat);
        }

        // 초기 점수 설정
        for (int number = 1; number <= 45; number++) {
            numberScores.put(number, 0.0);
        }

        // 매번 다른 시드로 전략 적용에 랜덤성 추가
        long strategySeed = System.currentTimeMillis() + Thread.currentThread().getId();
        Random strategyRandom = new Random(strategySeed);

        // 전략별 점수 계산 및 가중치 적용
        for (int i = 0; i < strategies.size(); i++) {
            String strategy = strategies.get(i);
            double weight = (strategyWeights != null) ? strategyWeights.get(i) : 1.0;

            // 각 전략에 약간의 랜덤 변동 추가 (±5% 변동)
            double weightVariation = 0.95 + strategyRandom.nextDouble() * 0.1; // 0.95 ~ 1.05
            double adjustedWeight = weight * weightVariation;

            applyStrategy(strategy, adjustedWeight, numberScores, statsMap);
        }

        // 제약 조건 적용하여 최종 6개 번호 선택
        List<Integer> finalNumbers = selectFinalNumbers(numberScores, strategies);

        long endTime = System.currentTimeMillis();
        android.util.Log.d("AI_GENERATION", "생성된 번호: " + finalNumbers);
        android.util.Log.d("AI_GENERATION", "생성 시간: " + (endTime - startTime) + "ms");
        android.util.Log.d("AI_GENERATION", "=== AI 번호 생성 완료 ===");

        return finalNumbers;
    }

    // 전략 적용 메서드들 (기존과 동일하지만 DAO 호출이 이미 백그라운드에서 실행됨)
    private void applyStrategy(String strategy, double weight, Map<Integer, Double> numberScores,
                               Map<Integer, NumberStatisticsEntity> statsMap) {

        switch (strategy) {
            case "인기번호":
                // 랜덤 시드로 약간의 변동 추가
                Random popularRandom = new Random(System.currentTimeMillis() + strategy.hashCode());
                for (NumberStatisticsEntity stat : statsMap.values()) {
                    double score = numberScores.get(stat.number);
                    // ±3% 랜덤 변동 추가
                    double variation = 0.97 + popularRandom.nextDouble() * 0.06; // 0.97 ~ 1.03
                    numberScores.put(stat.number, score + (stat.popularityScore * weight * variation));
                }
                break;

            case "소외번호":
                Random neglectRandom = new Random(System.currentTimeMillis() + strategy.hashCode());
                for (NumberStatisticsEntity stat : statsMap.values()) {
                    double score = numberScores.get(stat.number);
                    double variation = 0.97 + neglectRandom.nextDouble() * 0.06;
                    numberScores.put(stat.number, score + (stat.neglectScore * weight * variation));
                }
                break;

            case "트렌드":
                Random trendRandom = new Random(System.currentTimeMillis() + strategy.hashCode());
                for (NumberStatisticsEntity stat : statsMap.values()) {
                    double score = numberScores.get(stat.number);
                    double variation = 0.97 + trendRandom.nextDouble() * 0.06;
                    numberScores.put(stat.number, score + (stat.trendScore * weight * variation));
                }
                break;

            case "페어분석":
                applyPairAnalysisStrategy(weight, numberScores);
                break;

            case "홀짝균형":
                applyOddEvenBalanceStrategy(weight, numberScores, statsMap);
                break;

            case "구간분산":
                applyZoneDistributionStrategy(weight, numberScores, statsMap);
                break;

            case "행운번호":
                applyLuckyNumberStrategy(weight, numberScores);
                break;

            case "대중기피":
                for (NumberStatisticsEntity stat : statsMap.values()) {
                    double score = numberScores.get(stat.number);
                    numberScores.put(stat.number, score + (stat.avoidanceScore * weight));
                }
                break;

            case "순수통계":
                applyPureStatisticsStrategy(weight, numberScores, statsMap);
                break;

            // 순수 통계 모드의 세부 전략들
            case "순수고빈도":
                applyPureHighFrequencyStrategy(weight, numberScores, statsMap);
                break;

            case "순수소외번호":
                applyPureNeglectedStrategy(weight, numberScores, statsMap);
                break;

            case "순수최근추세":
                applyPureRecentTrendStrategy(weight, numberScores, statsMap);
                break;

            case "순수고가중치":
                applyPureHighWeightStrategy(weight, numberScores, statsMap);
                break;

            case "순수균형가중치":
                applyPureBalanceWeightStrategy(weight, numberScores, statsMap);
                break;

            case "순수주기성":
                applyPureCyclicStrategy(weight, numberScores, statsMap);
                break;

            case "순수상관관계":
                applyPureCorrelationStrategy(weight, numberScores, statsMap);
                break;

            case "순수회귀분석":
                applyPureRegressionStrategy(weight, numberScores, statsMap);
                break;

            case "끝자리다양성":
                break;

            case "시각패턴방지":
                applyVisualPatternAvoidanceStrategy(weight, numberScores);
                break;

            default:
                android.util.Log.w("AI_STRATEGY", "알 수 없는 전략: " + strategy);
                break;
        }
    }

    // 나머지 전략 적용 메서드들 (기존과 동일)
    private void applyOddEvenBalanceStrategy(double weight, Map<Integer, Double> numberScores,
                                             Map<Integer, NumberStatisticsEntity> statsMap) {
        double bonusScore = 50.0 * weight;

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            numberScores.put(stat.number, currentScore + bonusScore);
        }
    }

    private void applyZoneDistributionStrategy(double weight, Map<Integer, Double> numberScores,
                                               Map<Integer, NumberStatisticsEntity> statsMap) {
        double bonusScore = 30.0 * weight;

        for (int zone = 1; zone <= 5; zone++) {
            int start = (zone - 1) * 9 + 1;
            int end = Math.min(zone * 9, 45);

            for (int number = start; number <= end; number++) {
                double currentScore = numberScores.get(number);
                numberScores.put(number, currentScore + bonusScore);
            }
        }
    }

    private void applyLuckyNumberStrategy(double weight, Map<Integer, Double> numberScores) {
        // 매번 다른 시드 사용: 현재 시각(밀리초) + 스레드 ID + 랜덤 요소
        long currentTime = System.currentTimeMillis();
        long threadId = Thread.currentThread().getId();
        long randomSeed = currentTime + threadId + (long)(Math.random() * 10000);

        Random luckyRandom = new Random(randomSeed);

        Set<Integer> luckyNumbers = new HashSet<>();

        // 더 다양한 행운번호 생성 (12-15개)
        int luckyCount = 12 + luckyRandom.nextInt(4); // 12~15개
        for (int i = 0; i < luckyCount; i++) {
            luckyNumbers.add(luckyRandom.nextInt(45) + 1);
        }

        // 기존 고정 번호들을 확률적으로 포함 (항상 포함하지 않음)
        if (luckyRandom.nextDouble() < 0.7) luckyNumbers.add(7);  // 70% 확률
        if (luckyRandom.nextDouble() < 0.6) luckyNumbers.add(3);  // 60% 확률
        if (luckyRandom.nextDouble() < 0.6) luckyNumbers.add(8);  // 60% 확률

        // 추가 인기 행운번호들도 확률적으로 포함
        if (luckyRandom.nextDouble() < 0.5) luckyNumbers.add(1);  // 50% 확률
        if (luckyRandom.nextDouble() < 0.5) luckyNumbers.add(9);  // 50% 확률
        if (luckyRandom.nextDouble() < 0.4) luckyNumbers.add(21); // 40% 확률

        // 가변적인 보너스 점수 (60-80점 사이)
        double bonusScore = (60.0 + luckyRandom.nextDouble() * 20.0) * weight;

        for (int luckyNumber : luckyNumbers) {
            double currentScore = numberScores.get(luckyNumber);
            // 각 번호마다 약간 다른 보너스 적용 (±10% 변동)
            double variation = 0.9 + luckyRandom.nextDouble() * 0.2; // 0.9 ~ 1.1
            numberScores.put(luckyNumber, currentScore + (bonusScore * variation));
        }

        android.util.Log.d("AI_LUCKY", "행운번호 " + luckyNumbers.size() + "개 생성: " + luckyNumbers);
    }

    private void applyVisualPatternAvoidanceStrategy(double weight, Map<Integer, Double> numberScores) {
        List<List<Integer>> patterns = new ArrayList<>();
        patterns.add(Arrays.asList(1, 2, 3, 4, 5, 6));
        patterns.add(Arrays.asList(7, 14, 21, 28, 35, 42));
        patterns.add(Arrays.asList(1, 8, 15, 22, 29, 36));

        double penaltyScore = 20.0 * weight;

        for (List<Integer> pattern : patterns) {
            for (int number : pattern) {
                if (number <= 45) {
                    double currentScore = numberScores.get(number);
                    numberScores.put(number, currentScore - penaltyScore);
                }
            }
        }
    }

    private void applyPairAnalysisStrategy(double weight, Map<Integer, Double> numberScores) {
        List<NumberPairsEntity> topPairs = numberPairsDao.getTopPairs(20);

        for (NumberPairsEntity pair : topPairs) {
            double pairBonus = pair.pairScore * weight * 0.1;

            double score1 = numberScores.get(pair.number1);
            double score2 = numberScores.get(pair.number2);

            numberScores.put(pair.number1, score1 + pairBonus);
            numberScores.put(pair.number2, score2 + pairBonus);
        }
    }

    /**
     * 순수 통계 기반 전략 (100% 통계 기반, 랜덤성 제거)
     * 실제 당첨 빈도와 통계만을 기반으로 점수 계산 (중복 가능성 경고 필요)
     */
    private void applyPureStatisticsStrategy(double weight, Map<Integer, Double> numberScores,
                                             Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 통계 전략 적용 - 100% 데이터 기반");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);

            // 순수하게 실제 출현 빈도만 사용 (랜덤성 제거)
            // 가중치 조합: 출현횟수(40%) + 인기도(30%) + 트렌드(20%) + 소외도/기타(10%)
            double pureScore = (stat.appearanceCount * 0.4 +
                               stat.popularityScore * 0.3 +
                               stat.trendScore * 0.2 +
                               (stat.neglectScore + stat.avoidanceScore / 10.0) * 0.1) * weight;

            numberScores.put(stat.number, currentScore + pureScore);

            android.util.Log.d("AI_STRATEGY",
                String.format("번호 %d: 출현횟수=%d, 점수=%.2f", stat.number, stat.appearanceCount, pureScore));
        }

        android.util.Log.d("AI_STRATEGY", "순수 통계 전략 적용 완료 - 동일한 조건시 동일한 결과 생성됨");
    }

    /**
     * 순수 고빈도 번호 전략 (출현횟수에만 집중)
     */
    private void applyPureHighFrequencyStrategy(double weight, Map<Integer, Double> numberScores,
                                               Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 고빈도 전략 적용 - 출현횟수 최우선");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 출현횟수만을 100% 반영
            double pureScore = stat.appearanceCount * weight;
            numberScores.put(stat.number, currentScore + pureScore);
        }
    }

    /**
     * 순수 소외번호 회귀 이론 (장기 미출현 번호 우선)
     */
    private void applyPureNeglectedStrategy(double weight, Map<Integer, Double> numberScores,
                                           Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 소외번호 전략 적용 - 회귀 이론 기반");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 소외도 점수만을 100% 반영
            double pureScore = stat.neglectScore * weight;
            numberScores.put(stat.number, currentScore + pureScore);
        }
    }

    /**
     * 순수 최근 추세 반영 (최근 10회 가중치)
     */
    private void applyPureRecentTrendStrategy(double weight, Map<Integer, Double> numberScores,
                                             Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 최근추세 전략 적용 - 최근 패턴 집중");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 트렌드 점수만을 100% 반영
            double pureScore = stat.trendScore * weight;
            numberScores.put(stat.number, currentScore + pureScore);
        }
    }

    /**
     * 순수 고가중치 모드 (극단적 통계 적용)
     */
    private void applyPureHighWeightStrategy(double weight, Map<Integer, Double> numberScores,
                                            Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 고가중치 전략 적용 - 극단적 통계");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 모든 통계를 극단적으로 가중치 적용 (출현횟수 70%, 인기도 30%)
            double pureScore = (stat.appearanceCount * 0.7 + stat.popularityScore * 0.3) * weight * 2.0;
            numberScores.put(stat.number, currentScore + pureScore);
        }
    }

    /**
     * 순수 균형 가중치 (모든 지표 동등 반영)
     */
    private void applyPureBalanceWeightStrategy(double weight, Map<Integer, Double> numberScores,
                                               Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 균형가중치 전략 적용 - 모든 지표 동등");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 모든 통계 지표를 동등하게 25%씩 반영
            double pureScore = (stat.appearanceCount * 0.25 +
                               stat.popularityScore * 0.25 +
                               stat.trendScore * 0.25 +
                               stat.neglectScore * 0.25) * weight;
            numberScores.put(stat.number, currentScore + pureScore);
        }
    }

    /**
     * 순수 주기성 분석 (출현 패턴 주기 추적)
     */
    private void applyPureCyclicStrategy(double weight, Map<Integer, Double> numberScores,
                                        Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 주기성 전략 적용 - 패턴 주기 분석");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 주기성 분석: 출현횟수와 소외도의 조합으로 주기 패턴 계산
            double cyclicScore = Math.abs(stat.appearanceCount - stat.neglectScore) * weight;
            numberScores.put(stat.number, currentScore + cyclicScore);
        }
    }

    /**
     * 순수 상관관계 분석 (번호간 연관성 계산)
     */
    private void applyPureCorrelationStrategy(double weight, Map<Integer, Double> numberScores,
                                             Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 상관관계 전략 적용 - 번호간 연관성");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 상관관계 분석: 인기도와 트렌드의 상관성 기반 점수
            double correlationScore = (stat.popularityScore * stat.trendScore) / 100.0 * weight;
            numberScores.put(stat.number, currentScore + correlationScore);
        }
    }

    /**
     * 순수 회귀 분석 (미래 출현 예측 모델)
     */
    private void applyPureRegressionStrategy(double weight, Map<Integer, Double> numberScores,
                                            Map<Integer, NumberStatisticsEntity> statsMap) {
        android.util.Log.d("AI_STRATEGY", "순수 회귀분석 전략 적용 - 미래 예측 모델");

        for (NumberStatisticsEntity stat : statsMap.values()) {
            double currentScore = numberScores.get(stat.number);
            // 회귀 분석: 과거 패턴을 기반으로 미래 출현 확률 예측
            // 출현횟수의 추세와 현재 소외 상태를 종합한 예측 점수
            double regressionScore = (stat.appearanceCount * 0.6 +
                                     (100 - stat.neglectScore) * 0.4) * weight;
            numberScores.put(stat.number, currentScore + regressionScore);
        }
    }

    // 최종 번호 선택 메서드들 (순수 통계 모드 지원)
    private List<Integer> selectFinalNumbers(Map<Integer, Double> numberScores, List<String> strategies) {
        List<Map.Entry<Integer, Double>> sortedNumbers = numberScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .collect(Collectors.toList());

        Set<Integer> selectedNumbers = new HashSet<>();

        // 순수 통계 모드 확인
        boolean isPureStatistics = strategies.contains("순수통계");

        if (isPureStatistics) {
            // 순수 통계 모드: 완전히 결정론적으로 상위 6개 선택
            android.util.Log.d("AI_GENERATION", "순수 통계 모드 - 결정론적 선택");

            for (int i = 0; i < Math.min(6, sortedNumbers.size()); i++) {
                selectedNumbers.add(sortedNumbers.get(i).getKey());
            }

            List<Integer> result = new ArrayList<>(selectedNumbers);
            Collections.sort(result);

            android.util.Log.d("AI_GENERATION", "순수 통계 선택 결과: " + result);
            return result;
        }

        // 일반 모드: 기존 랜덤성 적용
        // 매번 다른 시드로 Random 객체 생성
        long currentTime = System.currentTimeMillis();
        long threadId = Thread.currentThread().getId();
        long randomSeed = currentTime + threadId + (long)(Math.random() * 100000);
        Random random = new Random(randomSeed);

        boolean avoidConsecutive = strategies.contains("연속방지");
        boolean diversifyLastDigits = strategies.contains("끝자리다양성");
        boolean balanceOddEven = strategies.contains("홀짝균형");
        boolean distributeZones = strategies.contains("구간분산");

        // 상위 점수 번호들 중에서도 랜덤성 추가
        // 상위 10-15개 번호 중에서 선택 (기존에는 무조건 최고점 순서)
        int topCandidatesCount = Math.min(12 + random.nextInt(6), sortedNumbers.size()); // 12-17개
        List<Map.Entry<Integer, Double>> topCandidates = sortedNumbers.subList(0, topCandidatesCount);

        // 상위 후보들을 섞어서 다양성 확보
        List<Map.Entry<Integer, Double>> shuffledCandidates = new ArrayList<>(topCandidates);
        Collections.shuffle(shuffledCandidates, random);

        for (Map.Entry<Integer, Double> entry : shuffledCandidates) {
            if (selectedNumbers.size() >= 6) break;

            int number = entry.getKey();

            if (avoidConsecutive && hasConsecutiveNumber(selectedNumbers, number)) {
                continue;
            }

            if (diversifyLastDigits && hasTooManySameLastDigit(selectedNumbers, number)) {
                continue;
            }

            selectedNumbers.add(number);
        }

        int attempts = 0;
        while (selectedNumbers.size() < 6 && attempts < 100) {
            int randomNumber = random.nextInt(45) + 1;
            if (selectedNumbers.contains(randomNumber)) {
                attempts++;
                continue;
            }

            boolean canAdd = true;

            if (avoidConsecutive && hasConsecutiveNumber(selectedNumbers, randomNumber)) {
                canAdd = false;
            }

            if (canAdd && diversifyLastDigits && hasTooManySameLastDigit(selectedNumbers, randomNumber)) {
                canAdd = false;
            }

            if (canAdd) {
                selectedNumbers.add(randomNumber);
            }
            attempts++;
        }

        while (selectedNumbers.size() < 6) {
            int randomNumber = random.nextInt(45) + 1;
            selectedNumbers.add(randomNumber);
        }

        if (balanceOddEven) {
            selectedNumbers = adjustOddEvenBalance(new ArrayList<>(selectedNumbers));
        }

        if (distributeZones) {
            selectedNumbers = adjustZoneDistribution(new ArrayList<>(selectedNumbers));
        }

        List<Integer> result = new ArrayList<>(selectedNumbers);
        Collections.sort(result);

        return result.size() > 6 ? result.subList(0, 6) : result;
    }

    private boolean hasConsecutiveNumber(Set<Integer> selectedNumbers, int newNumber) {
        return selectedNumbers.contains(newNumber - 1) || selectedNumbers.contains(newNumber + 1);
    }

    private boolean hasTooManySameLastDigit(Set<Integer> selectedNumbers, int newNumber) {
        int lastDigit = newNumber % 10;
        long count = selectedNumbers.stream()
                .mapToInt(n -> n % 10)
                .filter(d -> d == lastDigit)
                .count();
        return count >= 2;
    }

    private Set<Integer> adjustOddEvenBalance(List<Integer> numbers) {
        List<Integer> odds = new ArrayList<>();
        List<Integer> evens = new ArrayList<>();

        for (int num : numbers) {
            if (num % 2 == 1) {
                odds.add(num);
            } else {
                evens.add(num);
            }
        }

        Set<Integer> balanced = new HashSet<>();

        Collections.shuffle(odds);
        Collections.shuffle(evens);

        for (int i = 0; i < Math.min(3, odds.size()); i++) {
            balanced.add(odds.get(i));
        }
        for (int i = 0; i < Math.min(3, evens.size()); i++) {
            balanced.add(evens.get(i));
        }

        while (balanced.size() < 6) {
            for (int num : numbers) {
                if (!balanced.contains(num) && balanced.size() < 6) {
                    balanced.add(num);
                }
            }
            if (balanced.size() < 6) {
                Random random = new Random();
                int newNumber = random.nextInt(45) + 1;
                if (!balanced.contains(newNumber)) {
                    balanced.add(newNumber);
                }
            }
        }

        return balanced;
    }

    private Set<Integer> adjustZoneDistribution(List<Integer> numbers) {
        int[] zoneCounts = new int[5];
        List<List<Integer>> zoneNumbers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            zoneNumbers.add(new ArrayList<>());
        }

        for (int number : numbers) {
            int zone = Math.min((number - 1) / 9, 4);
            zoneCounts[zone]++;
            zoneNumbers.get(zone).add(number);
        }

        Set<Integer> distributed = new HashSet<>();

        for (int zone = 0; zone < 5; zone++) {
            List<Integer> zoneList = zoneNumbers.get(zone);
            Collections.shuffle(zoneList);
            for (int i = 0; i < Math.min(2, zoneList.size()) && distributed.size() < 6; i++) {
                distributed.add(zoneList.get(i));
            }
        }

        while (distributed.size() < 6) {
            for (int number : numbers) {
                if (!distributed.contains(number) && distributed.size() < 6) {
                    distributed.add(number);
                }
            }
        }

        return distributed;
    }

    @Override
    public long saveAiGeneratedPick(List<Integer> numbers, List<String> strategies, long generationTimeMs) {
        android.util.Log.d("SaveAiPick", "=== saveAiGeneratedPick 시작 ===");

        Integer currentRound = null;
        try {
            currentRound = RoundCache.getInstance().getNextRound();
            android.util.Log.d("SaveAiPick", "캐시에서 회차 조회 성공: " + currentRound);
        } catch (Exception e) {
            android.util.Log.e("SaveAiPick", "캐시에서 회차 조회 실패: " + e.getMessage());
            currentRound = estimateRoundByDateFallback();
        }

        long pickId = saveWithMethod("AI", numbers, currentRound, null);

        // AI 생성 기록 로그 저장 (백그라운드에서)
        backgroundExecutor.execute(() -> {
            try {
                AiGenerationLogEntity log = new AiGenerationLogEntity();
                log.strategiesUsed = convertStrategiesToString(strategies);
                log.generatedNumbers = convertNumbersToString(numbers);
                log.qualityScore = LottoNumberAnalyzer.calculateQualityScore(numbers, strategies);
                log.generationMethod = "AI_GENERATED";
                log.createdAt = System.currentTimeMillis();
                log.isSaved = false;
                log.notes = "";

                aiGenerationLogDao.insertLog(log);
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "AI 로그 저장 실패", e);
            }
        });

        android.util.Log.d("SaveAiPick", "=== saveAiGeneratedPick 완료 ===");
        return pickId;
    }

    private Integer estimateRoundByDateFallback() {
        try {
            long currentTime = System.currentTimeMillis();
            long weeksSince2002 = (currentTime - 1041724800000L) / (7 * 24 * 60 * 60 * 1000L);
            return (int) weeksSince2002 + 1;
        } catch (Exception e) {
            return 1186;
        }
    }

    private String convertStrategiesToString(List<String> strategies) {
        return String.join(",", strategies);
    }

    private String convertNumbersToString(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    // ********** AI 생성 기록 관리 (기존과 동일) **********

    @Override
    public long saveAiGenerationLog(AiGenerationLogEntity log) {
        return aiGenerationLogDao.insertLog(log);
    }

    @Override
    public List<AiGenerationLogEntity> getAllAiGenerationLogs() {
        return aiGenerationLogDao.getAllLogs();
    }

    @Override
    public List<AiGenerationLogEntity> getRecentAiGenerationLogs(int count) {
        return aiGenerationLogDao.getRecentLogs(count);
    }

    @Override
    public List<AiGenerationLogEntity> getSavedAiGenerationLogs() {
        return aiGenerationLogDao.getSavedLogs();
    }

    @Override
    public List<AiGenerationLogEntity> getAiGenerationLogsByStrategy(String strategy) {
        return aiGenerationLogDao.getLogsByStrategy(strategy);
    }

    @Override
    public void updateAiGenerationLogFeedback(long logId, boolean saved, @Nullable Boolean liked) {
        aiGenerationLogDao.updateSaveStatus(logId, saved);
    }

    // ********** 데이터 초기화 및 관리 (비동기 처리) **********

    @Override
    public void clearAllAiData() {
        backgroundExecutor.execute(() -> {
            try {
                drawHistoryDao.deleteAll();
                numberStatisticsDao.deleteAllStatistics();
                numberPairsDao.deleteAllPairs();
                aiGenerationLogDao.deleteAllLogs();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "clearAllAiData 실패", e);
            }
        });
    }

    @Override
    public void clearDrawHistory() {
        backgroundExecutor.execute(() -> {
            try {
                drawHistoryDao.deleteAll();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "clearDrawHistory 실패", e);
            }
        });
    }

    @Override
    public void clearNumberStatistics() {
        backgroundExecutor.execute(() -> {
            try {
                numberStatisticsDao.deleteAllStatistics();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "clearNumberStatistics 실패", e);
            }
        });
    }

    @Override
    public void clearNumberPairs() {
        backgroundExecutor.execute(() -> {
            try {
                numberPairsDao.deleteAllPairs();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "clearNumberPairs 실패", e);
            }
        });
    }

    @Override
    public void clearAiGenerationLogs() {
        backgroundExecutor.execute(() -> {
            try {
                aiGenerationLogDao.deleteAllLogs();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "clearAiGenerationLogs 실패", e);
            }
        });
    }

    @Override
    public void recalculateAllAiStatistics() {
        backgroundExecutor.execute(() -> {
            try {
                updateNumberStatisticsInternal();
                updateNumberPairsInternal();
            } catch (Exception e) {
                android.util.Log.e("LottoRepository", "recalculateAllAiStatistics 실패", e);
            }
        });
    }

    // ********** 리소스 정리 **********
    public void cleanup() {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }
}