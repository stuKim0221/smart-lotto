package app.grapekim.smartlotto.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import app.grapekim.smartlotto.data.local.room.entity.AiGenerationLogEntity;
import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;
import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberPairsEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberStatisticsEntity;

import java.util.List;

public interface LottoRepository {

    // ==================== 기존 메서드들 (변경 없음) ====================

    /** 완전자동 저장 */
    long saveAutoPick(List<Integer> numbers);

    /**
     * 수동/QR 저장 (회차/구매시각을 아는 경우 전달)
     * @deprecated QR의 경우 saveQrGames 사용 권장
     */
    @Deprecated
    long saveManualPick(List<Integer> numbers,
                        @Nullable Integer round,
                        @Nullable Long purchaseAt);

    LiveData<List<GeneratedPickEntity>> observeHistory(boolean onlyFav, boolean newestFirst);

    void setFavorite(long id, boolean fav);

    void deletePick(long id);

    void deletePickIfNotFavorite(long id);

    void clearAll();

    void clearAllExceptFavorites();

    // ==================== AI 데이터 정리 메서드들 (신규) ====================

    /**
     * AI 메서드 라벨 업데이트 ("수정" → "AI" 변경 및 제목 새로고침)
     */
    void updateAiMethodLabels();

    /**
     * 앱 시작 시 AI 데이터 정리 (일회성 실행)
     */
    void initializeAiDataCleanup();

    // ==================== 결과 확인 기능 ====================

    /**
     * 로또 결과 업데이트
     * @param id 항목 ID
     * @param rank 등수 (1~5=등수, -1=낙첨, 0=미확인)
     * @param matchCount 맞춘 번호 개수
     * @param targetRound 해당 로또 회차 번호
     */
    void updateResult(long id, int rank, int matchCount, int targetRound);

    // ==================== QR 다중 게임 지원 (신규) ====================

    /**
     * QR에서 파싱된 다중 게임들을 일괄 저장
     * @param allGames 모든 게임의 번호들 (예: [[1,7,15,23,34,41], [5,12,18,26,33,44]])
     * @param round 파싱된 회차 정보 (nullable)
     * @param purchaseAt 파싱된 구매 시각 (nullable)
     * @param rawData QR 원본 데이터 (디버깅용, nullable)
     * @param sourceType 소스 타입 ("QR_STRUCTURED" 또는 "QR_TEXT")
     * @return 저장된 항목들의 ID 리스트
     */
    List<Long> saveQrGames(List<List<Integer>> allGames,
                           @Nullable Integer round,
                           @Nullable Long purchaseAt,
                           @Nullable String rawData,
                           String sourceType);

    /**
     * QR 그룹에 속한 모든 게임 조회
     * @param qrGroupId QR 그룹 ID
     * @return 해당 그룹의 모든 게임들
     */
    List<GeneratedPickEntity> getQrGroup(String qrGroupId);

    /**
     * QR 그룹 전체 삭제
     * @param qrGroupId QR 그룹 ID
     */
    void deleteQrGroup(String qrGroupId);

    /**
     * QR 그룹 삭제 (즐겨찾기가 아닌 것만)
     * @param qrGroupId QR 그룹 ID
     */
    void deleteQrGroupIfNotFavorite(String qrGroupId);

    /**
     * QR 그룹 전체 즐겨찾기 설정
     * @param qrGroupId QR 그룹 ID
     * @param favorite 즐겨찾기 여부
     */
    void setQrGroupFavorite(String qrGroupId, boolean favorite);

    /**
     * 특정 항목의 QR 그룹 ID 조회
     * @param id 항목 ID
     * @return QR 그룹 ID (QR 게임이 아니면 null)
     */
    @Nullable
    String getQrGroupId(long id);

    /**
     * QR 그룹의 게임 수 조회
     * @param qrGroupId QR 그룹 ID
     * @return 해당 그룹의 게임 수
     */
    int getQrGroupSize(String qrGroupId);

    // ==================== 편의 메서드들 ====================

    /**
     * 단일 QR 게임 저장 (기존 호환성)
     * @param numbers 번호들
     * @param round 회차
     * @param purchaseAt 구매시각
     * @param rawData 원본 데이터
     * @param sourceType 소스 타입
     * @return 저장된 ID
     */
    long saveQrSingleGame(List<Integer> numbers,
                          @Nullable Integer round,
                          @Nullable Long purchaseAt,
                          @Nullable String rawData,
                          String sourceType);

    /**
     * QR 게임 여부 확인
     * @param id 항목 ID
     * @return QR 게임 여부
     */
    boolean isQrGame(long id);

    /**
     * 모든 QR 그룹 ID 목록 조회 (관리용)
     * @return 현재 저장된 모든 QR 그룹 ID들
     */
    List<String> getAllQrGroupIds();

    // ==================== AI 번호 생성 기능 (신규) ====================

    // ********** 로또 당첨번호 이력 관리 **********

    /**
     * 과거 당첨번호 저장
     * @param drawNumber 회차
     * @param drawDate 추첨일 (YYYY-MM-DD)
     * @param numbers 당첨번호 6개 + 보너스번호 1개 (총 7개)
     * @return 저장된 ID
     */
    long saveLottoDrawHistory(int drawNumber, String drawDate, List<Integer> numbers);

    /**
     * 여러 당첨번호 일괄 저장
     * @param drawHistories 당첨번호 이력 목록
     * @return 저장된 ID 목록
     */
    List<Long> saveLottoDrawHistories(List<LottoDrawHistoryEntity> drawHistories);

    /**
     * 모든 당첨번호 이력 조회
     * @return 당첨번호 이력 목록 (최신순)
     */
    List<LottoDrawHistoryEntity> getAllDrawHistory();

    /**
     * 최근 N회차 당첨번호 조회
     * @param count 조회할 회차 수
     * @return 최근 당첨번호 목록
     */
    List<LottoDrawHistoryEntity> getRecentDrawHistory(int count);

    /**
     * 특정 회차 당첨번호 조회
     * @param drawNumber 회차
     * @return 당첨번호 정보 (없으면 null)
     */
    @Nullable
    LottoDrawHistoryEntity getDrawHistory(int drawNumber);

    /**
     * 최신 회차 번호 조회
     * @return 최신 회차 번호 (저장된 데이터가 없으면 null)
     */
    @Nullable
    Integer getLatestDrawNumber();

    /**
     * 저장된 당첨번호 총 개수
     * @return 총 회차 수
     */
    int getTotalDrawCount();

    // ********** 번호별 통계 관리 **********

    /**
     * 모든 번호(1-45) 통계 조회
     * @return 번호별 통계 목록
     */
    List<NumberStatisticsEntity> getAllNumberStatistics();

    /**
     * 특정 번호 통계 조회
     * @param number 번호 (1-45)
     * @return 번호 통계 (없으면 null)
     */
    @Nullable
    NumberStatisticsEntity getNumberStatistics(int number);

    /**
     * 여러 번호 통계 조회
     * @param numbers 번호 목록
     * @return 번호별 통계 목록
     */
    List<NumberStatisticsEntity> getNumberStatistics(List<Integer> numbers);

    /**
     * 인기 번호 조회 (인기도 높은 순)
     * @param count 조회할 개수
     * @return 인기 번호 목록
     */
    List<NumberStatisticsEntity> getPopularNumbers(int count);

    /**
     * 소외 번호 조회 (소외도 높은 순)
     * @param count 조회할 개수
     * @return 소외 번호 목록
     */
    List<NumberStatisticsEntity> getNeglectedNumbers(int count);

    /**
     * 트렌드 번호 조회 (최근 트렌드 높은 순)
     * @param count 조회할 개수
     * @return 트렌드 번호 목록
     */
    List<NumberStatisticsEntity> getTrendingNumbers(int count);

    /**
     * 대중 기피 번호 조회 (기피도 높은 순)
     * @param count 조회할 개수
     * @return 기피 번호 목록
     */
    List<NumberStatisticsEntity> getAvoidedNumbers(int count);

    /**
     * 홀수/짝수 번호 조회
     * @param isOdd true=홀수, false=짝수
     * @return 홀수 또는 짝수 번호 목록
     */
    List<NumberStatisticsEntity> getNumbersByOddEven(boolean isOdd);

    /**
     * 특정 구간 번호 조회
     * @param zone 구간 (1=1~9, 2=10~19, 3=20~29, 4=30~39, 5=40~45)
     * @return 해당 구간 번호 목록
     */
    List<NumberStatisticsEntity> getNumbersByZone(int zone);

    /**
     * 번호별 통계 업데이트 (당첨번호 이력 기반으로 재계산)
     */
    void updateNumberStatistics();

    // ********** 번호 쌍 분석 관리 **********

    /**
     * 모든 번호 쌍 분석 결과 조회
     * @return 번호 쌍 목록 (점수 높은 순)
     */
    List<NumberPairsEntity> getAllNumberPairs();

    /**
     * 상위 번호 쌍 조회
     * @param count 조회할 개수
     * @return 상위 번호 쌍 목록
     */
    List<NumberPairsEntity> getTopNumberPairs(int count);

    /**
     * 특정 번호를 포함하는 쌍들 조회
     * @param number 번호
     * @return 해당 번호를 포함하는 쌍 목록
     */
    List<NumberPairsEntity> getNumberPairsContaining(int number);

    /**
     * 특정 번호의 최고 파트너 조회
     * @param number 번호
     * @return 최고 파트너 쌍 (없으면 null)
     */
    @Nullable
    NumberPairsEntity getBestPartnerFor(int number);

    /**
     * 연속번호 쌍들 조회
     * @return 연속번호 쌍 목록
     */
    List<NumberPairsEntity> getConsecutivePairs();

    /**
     * 비연속번호 쌍들 조회
     * @return 비연속번호 쌍 목록
     */
    List<NumberPairsEntity> getNonConsecutivePairs();

    /**
     * 번호 쌍 분석 데이터 업데이트 (당첨번호 이력 기반으로 재계산)
     */
    void updateNumberPairs();

    // ********** AI 번호 생성 **********

    /**
     * AI 번호 생성 (전략 조합)
     * @param strategies 사용할 전략 목록
     * @param strategyWeights 전략별 가중치 (strategies와 동일한 순서)
     * @param count 생성할 조합 개수 (기본 1개)
     * @return 생성된 번호 조합들
     */
    List<List<Integer>> generateAiNumbers(List<String> strategies,
                                          List<Double> strategyWeights,
                                          int count);

    /**
     * AI 번호 생성 (간단 버전 - 동일 가중치)
     * @param strategies 사용할 전략 목록
     * @param count 생성할 조합 개수
     * @return 생성된 번호 조합들
     */
    List<List<Integer>> generateAiNumbers(List<String> strategies, int count);

    /**
     * AI 생성 번호 저장 (GeneratedPickEntity로 저장 + 로그 기록)
     * @param numbers 생성된 번호들
     * @param strategies 사용된 전략들
     * @param generationTimeMs 생성 소요 시간
     * @return 저장된 GeneratedPickEntity ID
     */
    long saveAiGeneratedPick(List<Integer> numbers,
                             List<String> strategies,
                             long generationTimeMs);

    // ********** AI 생성 기록 관리 **********

    /**
     * AI 생성 기록 저장
     * @param log AI 생성 기록
     * @return 저장된 ID
     */
    long saveAiGenerationLog(AiGenerationLogEntity log);

    /**
     * 모든 AI 생성 기록 조회
     * @return AI 생성 기록 목록 (최신순)
     */
    List<AiGenerationLogEntity> getAllAiGenerationLogs();

    /**
     * 최근 AI 생성 기록 조회
     * @param count 조회할 개수
     * @return 최근 생성 기록 목록
     */
    List<AiGenerationLogEntity> getRecentAiGenerationLogs(int count);

    /**
     * 사용자가 저장한 AI 생성 기록들 조회
     * @return 저장된 생성 기록 목록
     */
    List<AiGenerationLogEntity> getSavedAiGenerationLogs();

    /**
     * 특정 전략이 사용된 AI 생성 기록들 조회
     * @param strategy 전략명
     * @return 해당 전략이 사용된 기록 목록
     */
    List<AiGenerationLogEntity> getAiGenerationLogsByStrategy(String strategy);

    /**
     * AI 생성 기록에 사용자 피드백 업데이트
     * @param logId 기록 ID
     * @param saved 저장 여부
     * @param liked 좋아요 여부 (null이면 변경 안함)
     */
    void updateAiGenerationLogFeedback(long logId, boolean saved, @Nullable Boolean liked);

    // ********** 데이터 초기화 및 관리 **********

    /**
     * AI 관련 모든 데이터 초기화
     */
    void clearAllAiData();

    /**
     * 당첨번호 이력만 초기화
     */
    void clearDrawHistory();

    /**
     * 번호별 통계만 초기화
     */
    void clearNumberStatistics();

    /**
     * 번호 쌍 분석 데이터만 초기화
     */
    void clearNumberPairs();

    /**
     * AI 생성 기록만 초기화
     */
    void clearAiGenerationLogs();

    /**
     * AI 통계 데이터 전체 재계산 (당첨번호 이력 기반)
     */
    void recalculateAllAiStatistics();
}