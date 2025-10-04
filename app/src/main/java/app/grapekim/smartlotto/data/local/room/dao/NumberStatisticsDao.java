package app.grapekim.smartlotto.data.local.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import app.grapekim.smartlotto.data.local.room.entity.NumberStatisticsEntity;

import java.util.List;

/**
 * 번호 통계 정보 접근을 위한 DAO
 */
@Dao
public interface NumberStatisticsDao {

    /**
     * 모든 번호 통계 조회
     */
    @Query("SELECT * FROM number_statistics ORDER BY number ASC")
    List<NumberStatisticsEntity> getAllStatistics();

    /**
     * 특정 번호의 통계 조회
     */
    @Query("SELECT * FROM number_statistics WHERE number = :number")
    NumberStatisticsEntity getStatisticsByNumber(int number);

    /**
     * 인기 번호 조회 (출현 횟수 기준)
     */
    @Query("SELECT * FROM number_statistics ORDER BY popularity_score DESC, appearance_count DESC LIMIT :limit")
    List<NumberStatisticsEntity> getPopularNumbers(int limit);

    /**
     * 소외 번호 조회 (오래 안나온 번호)
     */
    @Query("SELECT * FROM number_statistics ORDER BY neglect_score DESC, last_appearance_gap DESC LIMIT :limit")
    List<NumberStatisticsEntity> getNeglectedNumbers(int limit);

    /**
     * 트렌드 번호 조회 (최근 경향)
     */
    @Query("SELECT * FROM number_statistics ORDER BY trend_score DESC LIMIT :limit")
    List<NumberStatisticsEntity> getTrendNumbers(int limit);

    /**
     * 홀수/짝수별 번호 조회
     */
    @Query("SELECT * FROM number_statistics WHERE is_odd = :isOdd ORDER BY popularity_score DESC LIMIT :limit")
    List<NumberStatisticsEntity> getNumbersByOddEven(boolean isOdd, int limit);

    /**
     * 끝자리별 번호 조회
     */
    @Query("SELECT * FROM number_statistics WHERE last_digit = :lastDigit ORDER BY popularity_score DESC")
    List<NumberStatisticsEntity> getNumbersByLastDigit(int lastDigit);

    /**
     * AI 점수 기반 번호 조회
     */
    @Query("SELECT * FROM number_statistics ORDER BY " +
            "(popularity_score * :popWeight + " +
            "neglect_score * :neglectWeight + " +
            "trend_score * :trendWeight + " +
            "avoidance_score * :avoidanceWeight) DESC " +
            "LIMIT :limit")
    List<NumberStatisticsEntity> getNumbersByAiScore(
            double popWeight, double neglectWeight,
            double trendWeight, double avoidanceWeight,
            int limit);

    /**
     * 구간별 번호 조회 (1-9, 10-18, 19-27, 28-36, 37-45)
     */
    @Query("SELECT * FROM number_statistics WHERE number BETWEEN :start AND :end ORDER BY popularity_score DESC LIMIT :limit")
    List<NumberStatisticsEntity> getNumbersByRange(int start, int end, int limit);

    /**
     * 번호 통계 삽입 또는 업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateStatistics(NumberStatisticsEntity... statistics);

    /**
     * 번호 통계 업데이트
     */
    @Update
    void updateStatistics(NumberStatisticsEntity statistics);

    /**
     * 모든 통계 삭제
     */
    @Query("DELETE FROM number_statistics")
    void deleteAllStatistics();

    /**
     * 특정 번호의 통계 삭제
     */
    @Query("DELETE FROM number_statistics WHERE number = :number")
    void deleteStatisticsByNumber(int number);

    /**
     * 통계 데이터 존재 여부 확인
     */
    @Query("SELECT COUNT(*) FROM number_statistics")
    int getStatisticsCount();

    /**
     * 마지막 업데이트 시간 조회
     */
    @Query("SELECT MAX(updated_at) FROM number_statistics")
    long getLastUpdateTime();

    /**
     * 초기 통계 데이터 삽입 (1-45번 모든 번호)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertInitialStatistics(List<NumberStatisticsEntity> initialStats);
}