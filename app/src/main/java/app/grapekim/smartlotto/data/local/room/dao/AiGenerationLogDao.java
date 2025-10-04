package app.grapekim.smartlotto.data.local.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import app.grapekim.smartlotto.data.local.room.entity.AiGenerationLogEntity;

import java.util.List;

/**
 * AI 생성 로그 정보 접근을 위한 DAO
 */
@Dao
public interface AiGenerationLogDao {

    /**
     * 모든 AI 생성 로그 조회 (최신순)
     */
    @Query("SELECT * FROM ai_generation_log ORDER BY created_at DESC")
    List<AiGenerationLogEntity> getAllLogs();

    /**
     * 최근 N개의 AI 생성 로그 조회
     */
    @Query("SELECT * FROM ai_generation_log ORDER BY created_at DESC LIMIT :limit")
    List<AiGenerationLogEntity> getRecentLogs(int limit);

    /**
     * 저장된 번호들만 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE is_saved = 1 ORDER BY created_at DESC")
    List<AiGenerationLogEntity> getSavedLogs();

    /**
     * 특정 생성 방법의 로그들 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE generation_method = :method ORDER BY created_at DESC LIMIT :limit")
    List<AiGenerationLogEntity> getLogsByMethod(String method, int limit);

    /**
     * 특정 품질 점수 이상의 로그들 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE quality_score >= :minScore ORDER BY quality_score DESC, created_at DESC")
    List<AiGenerationLogEntity> getLogsByQualityScore(double minScore);

    /**
     * 특정 날짜 범위의 로그들 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE created_at BETWEEN :startTime AND :endTime ORDER BY created_at DESC")
    List<AiGenerationLogEntity> getLogsByDateRange(long startTime, long endTime);

    /**
     * 특정 전략이 사용된 로그들 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE strategies_used LIKE '%' || :strategy || '%' ORDER BY created_at DESC")
    List<AiGenerationLogEntity> getLogsByStrategy(String strategy);

    /**
     * AI 생성 로그 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertLog(AiGenerationLogEntity log);

    /**
     * AI 생성 로그 업데이트
     */
    @Update
    void updateLog(AiGenerationLogEntity log);

    /**
     * 로그의 저장 상태 업데이트
     */
    @Query("UPDATE ai_generation_log SET is_saved = :isSaved WHERE id = :logId")
    void updateSaveStatus(long logId, boolean isSaved);

    /**
     * 특정 로그 조회
     */
    @Query("SELECT * FROM ai_generation_log WHERE id = :logId")
    AiGenerationLogEntity getLogById(long logId);

    /**
     * 특정 로그 삭제
     */
    @Query("DELETE FROM ai_generation_log WHERE id = :logId")
    void deleteLogById(long logId);

    /**
     * 오래된 로그들 삭제 (N일 이전)
     */
    @Query("DELETE FROM ai_generation_log WHERE created_at < :cutoffTime")
    void deleteOldLogs(long cutoffTime);

    /**
     * 모든 로그 삭제
     */
    @Query("DELETE FROM ai_generation_log")
    void deleteAllLogs();

    /**
     * 로그 개수 조회
     */
    @Query("SELECT COUNT(*) FROM ai_generation_log")
    int getLogsCount();

    /**
     * 저장된 로그 개수 조회
     */
    @Query("SELECT COUNT(*) FROM ai_generation_log WHERE is_saved = 1")
    int getSavedLogsCount();

    /**
     * 평균 품질 점수 조회
     */
    @Query("SELECT AVG(quality_score) FROM ai_generation_log")
    double getAverageQualityScore();

    /**
     * 가장 많이 사용된 전략 조회 (수정됨)
     */
    @Query("SELECT strategies_used FROM ai_generation_log GROUP BY strategies_used ORDER BY COUNT(*) DESC LIMIT :limit")
    List<String> getMostUsedStrategies(int limit);

    /**
     * 특정 번호 조합이 생성된 적이 있는지 확인
     */
    @Query("SELECT COUNT(*) FROM ai_generation_log WHERE generated_numbers = :numbersJson")
    int checkDuplicateNumbers(String numbersJson);
}