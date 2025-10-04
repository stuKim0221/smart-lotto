package app.grapekim.smartlotto.data.csv;

import android.content.Context;
import android.util.Log;

import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.CsvUpdateManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 로또 데이터 로더 (GitHub 자동 업데이트 지원)
 * GitHub에서 최신 draw_kor.csv 파일을 가져와서 데이터베이스에 로드하고 AI 통계를 계산
 */
public class LottoDataLoader {

    private static final String TAG = "LottoDataLoader";
    private static final String CSV_FILE_NAME = "draw_kor.csv";

    private final Context context;
    private final LottoRepository repository;
    private final ExecutorService executor;
    private final CsvUpdateManager csvUpdateManager;

    /**
     * 데이터 로딩 진행률 콜백 인터페이스
     */
    public interface LoadingCallback {
        /**
         * 진행률 업데이트
         * @param progress 진행률 (0-100)
         * @param message 현재 작업 메시지
         */
        void onProgress(int progress, String message);

        /**
         * 로딩 완료
         * @param success 성공 여부
         * @param loadedCount 로드된 데이터 개수
         * @param message 완료 메시지
         */
        void onComplete(boolean success, int loadedCount, String message);

        /**
         * 에러 발생
         * @param error 에러 메시지
         * @param exception 예외 객체 (nullable)
         */
        void onError(String error, Exception exception);
    }

    public LottoDataLoader(Context context, LottoRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.executor = Executors.newSingleThreadExecutor();
        this.csvUpdateManager = new CsvUpdateManager(this.context);
    }

    /**
     * CSV 데이터 로드 및 AI 통계 계산 (비동기) - GitHub 업데이트 지원
     * @param callback 진행률 콜백
     */
    public void loadLottoData(LoadingCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress(2, "GitHub에서 최신 데이터 확인 중...");

                // GitHub에서 최신 CSV 업데이트 시도
                boolean updated = csvUpdateManager.updateCsvFile();
                if (updated) {
                    Log.i(TAG, "GitHub에서 최신 데이터를 가져왔습니다.");
                } else {
                    Log.i(TAG, "GitHub 업데이트 실패 또는 불필요. 기존 데이터 사용.");
                }

                callback.onProgress(5, "데이터 상태 확인 중...");

                // 1. DB의 최신 회차 확인
                Integer latestDbRound = repository.getLatestDrawNumber();
                Log.i(TAG, "DB 최신 회차: " + (latestDbRound != null ? latestDbRound : "없음"));

                // 2. CSV의 최신 회차 확인 (업데이트된 파일에서)
                Integer latestCsvRound = getLatestRoundFromCsv();
                Log.i(TAG, "CSV 최신 회차: " + (latestCsvRound != null ? latestCsvRound : "없음"));

                if (latestCsvRound == null) {
                    callback.onError("CSV 파일에서 유효한 회차 데이터를 찾을 수 없습니다.", null);
                    return;
                }

                // 3. 데이터 상태에 따른 처리 분기
                if (latestDbRound == null) {
                    // DB가 비어있음 - 전체 CSV 로드
                    loadAllCsvData(callback, latestCsvRound);
                } else if (latestCsvRound > latestDbRound) {
                    // CSV가 더 최신 - 새로운 회차만 로드
                    loadNewCsvData(callback, latestDbRound, latestCsvRound);
                } else if (latestCsvRound.equals(latestDbRound)) {
                    // 데이터가 최신 상태 - 통계만 재계산
                    updateStatisticsOnly(callback, repository.getTotalDrawCount());
                } else {
                    // DB가 더 최신 (비정상 상황) - 경고 후 통계 재계산
                    Log.w(TAG, "DB가 CSV보다 최신입니다. DB: " + latestDbRound + ", CSV: " + latestCsvRound);
                    updateStatisticsOnly(callback, repository.getTotalDrawCount());
                }

            } catch (Exception e) {
                Log.e(TAG, "데이터 로딩 중 오류 발생", e);
                callback.onError("데이터 로딩 중 오류가 발생했습니다: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 전체 CSV 데이터 로드 (초기 설치시)
     */
    private void loadAllCsvData(LoadingCallback callback, int totalRounds) throws Exception {
        Log.i(TAG, "전체 CSV 데이터 로드 시작");

        callback.onProgress(10, "CSV 파일 전체 읽는 중...");
        List<LottoDrawHistoryEntity> allDrawHistories = parseCsvFile();

        if (allDrawHistories.isEmpty()) {
            callback.onError("CSV 파일에서 유효한 데이터를 찾을 수 없습니다.", null);
            return;
        }

        Log.i(TAG, "CSV에서 " + allDrawHistories.size() + "개의 당첨번호 데이터를 파싱했습니다.");

        // 데이터베이스에 저장
        callback.onProgress(30, allDrawHistories.size() + "개 데이터 저장 중...");
        List<Long> savedIds = repository.saveLottoDrawHistories(allDrawHistories);

        int savedCount = savedIds.size();
        Log.i(TAG, savedCount + "개의 당첨번호가 데이터베이스에 저장되었습니다.");

        if (savedCount == 0) {
            callback.onError("데이터 저장에 실패했습니다.", null);
            return;
        }

        // AI 통계 계산
        calculateStatistics(callback, 60);

        callback.onComplete(true, savedCount,
                savedCount + "개의 당첨번호가 로드되고 AI 통계가 계산되었습니다!");
    }

    /**
     * 새로운 회차 데이터만 로드 (증분 업데이트)
     */
    private void loadNewCsvData(LoadingCallback callback, int fromRound, int toRound) throws Exception {
        int newRoundsCount = toRound - fromRound;
        Log.i(TAG, "증분 업데이트: " + (fromRound + 1) + "회 ~ " + toRound + "회 (" + newRoundsCount + "회차)");

        callback.onProgress(15, "새로운 " + newRoundsCount + "회차 데이터 읽는 중...");
        List<LottoDrawHistoryEntity> newDrawHistories = parseNewCsvData(fromRound);

        if (newDrawHistories.isEmpty()) {
            Log.i(TAG, "새로운 회차 데이터가 없습니다.");
            updateStatisticsOnly(callback, repository.getTotalDrawCount());
            return;
        }

        Log.i(TAG, "새로운 " + newDrawHistories.size() + "개 회차 데이터를 파싱했습니다.");

        // 새 데이터 저장
        callback.onProgress(40, newDrawHistories.size() + "개 새 회차 저장 중...");
        List<Long> savedIds = repository.saveLottoDrawHistories(newDrawHistories);

        int savedCount = savedIds.size();
        Log.i(TAG, savedCount + "개의 새 당첨번호가 저장되었습니다.");

        if (savedCount == 0) {
            callback.onError("새 데이터 저장에 실패했습니다.", null);
            return;
        }

        // AI 통계 재계산
        calculateStatistics(callback, 70);

        int totalCount = repository.getTotalDrawCount();
        callback.onComplete(true, savedCount,
                savedCount + "개의 새 회차가 추가되었습니다! (총 " + totalCount + "회차)");
    }

    /**
     * 통계만 재계산 (데이터가 이미 최신인 경우)
     */
    private void updateStatisticsOnly(LoadingCallback callback, int existingCount) {
        Log.i(TAG, "데이터가 최신 상태입니다. 통계만 재계산합니다.");

        try {
            callback.onProgress(50, "기존 데이터로 AI 통계 계산 중...");
            repository.recalculateAllAiStatistics();
            callback.onProgress(100, "AI 통계 계산 완료!");

            callback.onComplete(true, existingCount,
                    "기존 데이터 " + existingCount + "개로 AI 통계가 업데이트되었습니다.");
        } catch (Exception e) {
            callback.onError("통계 계산 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * AI 통계 계산 (공통 로직)
     */
    private void calculateStatistics(LoadingCallback callback, int startProgress) {
        callback.onProgress(startProgress, "번호별 통계 계산 중...");
        repository.updateNumberStatistics();

        callback.onProgress(startProgress + 15, "번호 쌍 분석 중...");
        repository.updateNumberPairs();

        callback.onProgress(100, "AI 통계 계산 완료!");
        Log.i(TAG, "AI 통계 계산이 완료되었습니다.");
    }

    /**
     * CSV 파일에서 최신 회차 번호 추출 (GitHub 업데이트된 파일에서)
     * @return 최신 회차 번호 (CSV의 첫 번째 데이터 라인)
     */
    private Integer getLatestRoundFromCsv() {
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            // GitHub에서 업데이트된 파일 또는 기본 파일 사용
            File csvFile = csvUpdateManager.getCsvFile();
            inputStream = new FileInputStream(csvFile);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            String line;
            boolean isFirstLine = true;

            // 헤더와 첫 번째 데이터 라인 읽기
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 헤더 스킵
                }

                if (line.trim().isEmpty()) {
                    continue; // 빈 줄 스킵
                }

                // 첫 번째 유효한 데이터 라인에서 회차 추출
                String[] parts = line.split(",");
                if (parts.length >= 10) {
                    String drawNoStr = parts[1].trim();
                    if (!drawNoStr.isEmpty()) {
                        return Integer.parseInt(drawNoStr);
                    }
                }

                break; // 첫 번째 데이터 라인만 확인
            }

        } catch (Exception e) {
            Log.e(TAG, "CSV 최신 회차 확인 실패", e);
        } finally {
            closeQuietly(reader, inputStream);
        }

        return null;
    }

    /**
     * 특정 회차 이후의 새로운 데이터만 파싱 (GitHub 업데이트된 파일에서)
     * @param fromRound 이 회차보다 큰 회차들만 파싱
     * @return 새로운 당첨번호 목록
     */
    private List<LottoDrawHistoryEntity> parseNewCsvData(int fromRound) throws IOException {
        List<LottoDrawHistoryEntity> newDrawHistories = new ArrayList<>();

        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            // GitHub에서 업데이트된 파일 사용
            File csvFile = csvUpdateManager.getCsvFile();
            inputStream = new FileInputStream(csvFile);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 첫 번째 줄(헤더) 스킵
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // 빈 줄 스킵
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    LottoDrawHistoryEntity entity = parseCsvLine(line, lineNumber);
                    if (entity != null && entity.drawNumber > fromRound) {
                        // fromRound보다 큰 회차만 추가
                        newDrawHistories.add(entity);
                        Log.d(TAG, "새 회차 추가: " + entity.drawNumber + "회");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "줄 " + lineNumber + " 파싱 오류: " + line, e);
                }
            }

        } finally {
            closeQuietly(reader, inputStream);
        }

        Log.i(TAG, fromRound + "회 이후 " + newDrawHistories.size() + "개의 새 회차를 파싱했습니다.");
        return newDrawHistories;
    }

    /**
     * CSV 파일 파싱 (전체 데이터, GitHub 업데이트된 파일에서)
     * @return 파싱된 당첨번호 목록
     * @throws IOException 파일 읽기 오류
     */
    private List<LottoDrawHistoryEntity> parseCsvFile() throws IOException {
        List<LottoDrawHistoryEntity> drawHistories = new ArrayList<>();

        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            // GitHub에서 업데이트된 파일 사용
            File csvFile = csvUpdateManager.getCsvFile();
            inputStream = new FileInputStream(csvFile);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 첫 번째 줄(헤더) 스킵
                if (isFirstLine) {
                    isFirstLine = false;
                    Log.d(TAG, "CSV 헤더: " + line);
                    continue;
                }

                // 빈 줄 스킵
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    LottoDrawHistoryEntity entity = parseCsvLine(line, lineNumber);
                    if (entity != null) {
                        drawHistories.add(entity);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "줄 " + lineNumber + " 파싱 오류: " + line, e);
                    // 개별 줄 오류는 무시하고 계속 진행
                }
            }

        } finally {
            closeQuietly(reader, inputStream);
        }

        Log.i(TAG, "총 " + drawHistories.size() + "개의 유효한 당첨번호를 파싱했습니다.");
        return drawHistories;
    }

    /**
     * CSV 한 줄 파싱
     * @param line CSV 라인 (예: "2025,1184,2025-08-09,14,16,23,25,31,37,42")
     * @param lineNumber 줄 번호 (디버깅용)
     * @return 파싱된 LottoDrawHistoryEntity (파싱 실패 시 null)
     */
    private LottoDrawHistoryEntity parseCsvLine(String line, int lineNumber) {
        try {
            // CSV 파싱 (콤마로 분할)
            String[] parts = line.split(",");

            // 최소 필드 수 체크 (year, drawNo, date, n1~n6, bonus = 10개)
            if (parts.length < 10) {
                Log.w(TAG, "줄 " + lineNumber + ": 필드 수 부족 (" + parts.length + "개) - " + line);
                return null;
            }

            // 필드 파싱
            String year = parts[0].trim();
            String drawNoStr = parts[1].trim();
            String date = parts[2].trim();

            // 회차 번호 파싱
            if (drawNoStr.isEmpty()) {
                Log.w(TAG, "줄 " + lineNumber + ": 회차 번호가 비어있음 - " + line);
                return null;
            }

            int drawNumber = Integer.parseInt(drawNoStr);

            // 날짜 검증
            if (date.isEmpty()) {
                Log.w(TAG, "줄 " + lineNumber + ": 날짜가 비어있음 - " + line);
                return null;
            }

            // 번호들 파싱 (n1~n6, bonus)
            int[] numbers = new int[7]; // 본번호 6개 + 보너스 1개
            for (int i = 0; i < 7; i++) {
                String numberStr = parts[3 + i].trim();
                if (numberStr.isEmpty()) {
                    Log.w(TAG, "줄 " + lineNumber + ": " + (i+1) + "번째 번호가 비어있음 - " + line);
                    return null;
                }

                numbers[i] = Integer.parseInt(numberStr);

                // 번호 범위 검증 (1-45)
                if (numbers[i] < 1 || numbers[i] > 45) {
                    Log.w(TAG, "줄 " + lineNumber + ": " + (i+1) + "번째 번호 범위 초과 (" + numbers[i] + ") - " + line);
                    return null;
                }
            }

            // 중복 번호 체크 (본번호 6개)
            if (hasDuplicateNumbers(Arrays.copyOf(numbers, 6))) {
                Log.w(TAG, "줄 " + lineNumber + ": 중복 번호 존재 - " + line);
                return null;
            }

            // LottoDrawHistoryEntity 생성
            LottoDrawHistoryEntity entity = new LottoDrawHistoryEntity(
                    drawNumber, date,
                    numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], // 본번호 6개
                    numbers[6] // 보너스번호
            );

            Log.d(TAG, "파싱 성공 - 회차: " + drawNumber + ", 날짜: " + date +
                    ", 번호: " + Arrays.toString(Arrays.copyOf(numbers, 6)) +
                    ", 보너스: " + numbers[6]);

            return entity;

        } catch (NumberFormatException e) {
            Log.w(TAG, "줄 " + lineNumber + ": 숫자 파싱 오류 - " + line, e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "줄 " + lineNumber + ": 일반 파싱 오류 - " + line, e);
            return null;
        }
    }

    /**
     * 리소스 안전 종료
     */
    private void closeQuietly(BufferedReader reader, InputStream inputStream) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * 배열에 중복 번호가 있는지 체크
     * @param numbers 번호 배열
     * @return 중복이 있으면 true
     */
    private boolean hasDuplicateNumbers(int[] numbers) {
        for (int i = 0; i < numbers.length; i++) {
            for (int j = i + 1; j < numbers.length; j++) {
                if (numbers[i] == numbers[j]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 데이터 로더 정리
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * 간단한 데이터 로드 (콜백 없이, 동기 방식)
     * 주의: UI 스레드에서 호출하지 말 것!
     */
    public boolean loadLottoDataSync() {
        try {
            // GitHub 업데이트 시도
            csvUpdateManager.updateCsvFile();

            Integer latestDbRound = repository.getLatestDrawNumber();
            Integer latestCsvRound = getLatestRoundFromCsv();

            if (latestCsvRound == null) {
                return false;
            }

            if (latestDbRound == null) {
                // 전체 로드
                List<LottoDrawHistoryEntity> drawHistories = parseCsvFile();
                if (drawHistories.isEmpty()) {
                    return false;
                }
                List<Long> savedIds = repository.saveLottoDrawHistories(drawHistories);
                if (savedIds.isEmpty()) {
                    return false;
                }
            } else if (latestCsvRound > latestDbRound) {
                // 증분 로드
                List<LottoDrawHistoryEntity> newData = parseNewCsvData(latestDbRound);
                if (!newData.isEmpty()) {
                    repository.saveLottoDrawHistories(newData);
                }
            }

            repository.recalculateAllAiStatistics();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "동기 데이터 로딩 실패", e);
            return false;
        }
    }

    /**
     * 현재 데이터 상태 조회
     */
    public DataStatus getDataStatus() {
        try {
            int drawCount = repository.getTotalDrawCount();
            int statsCount = repository.getAllNumberStatistics().size();
            int pairsCount = repository.getAllNumberPairs().size();

            return new DataStatus(drawCount, statsCount, pairsCount);

        } catch (Exception e) {
            Log.e(TAG, "데이터 상태 조회 실패", e);
            return new DataStatus(0, 0, 0);
        }
    }

    /**
     * 데이터 상태 정보 클래스
     */
    public static class DataStatus {
        public final int drawHistoryCount;      // 당첨번호 이력 개수
        public final int numberStatisticsCount; // 번호별 통계 개수
        public final int numberPairsCount;      // 번호 쌍 개수

        public DataStatus(int drawHistoryCount, int numberStatisticsCount, int numberPairsCount) {
            this.drawHistoryCount = drawHistoryCount;
            this.numberStatisticsCount = numberStatisticsCount;
            this.numberPairsCount = numberPairsCount;
        }

        public boolean isDataLoaded() {
            return drawHistoryCount > 0 && numberStatisticsCount > 0;
        }

        @Override
        public String toString() {
            return String.format("DataStatus{draws=%d, stats=%d, pairs=%d}",
                    drawHistoryCount, numberStatisticsCount, numberPairsCount);
        }
    }
}