package app.grapekim.smartlotto.data.local.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import app.grapekim.smartlotto.data.local.room.entity.NumberPairsEntity;

import java.util.List;

/**
 * 번호 페어 분석 정보 접근을 위한 DAO
 */
@Dao
public interface NumberPairsDao {

    /**
     * 모든 번호 페어 조회
     */
    @Query("SELECT * FROM number_pairs ORDER BY pair_score DESC")
    List<NumberPairsEntity> getAllPairs();

    /**
     * 특정 번호와 함께 나오는 페어들 조회
     */
    @Query("SELECT * FROM number_pairs WHERE number1 = :number OR number2 = :number ORDER BY pair_score DESC LIMIT :limit")
    List<NumberPairsEntity> getPairsByNumber(int number, int limit);

    /**
     * 특정 두 번호의 페어 정보 조회
     */
    @Query("SELECT * FROM number_pairs WHERE (number1 = :num1 AND number2 = :num2) OR (number1 = :num2 AND number2 = :num1)")
    NumberPairsEntity getPairByNumbers(int num1, int num2);

    /**
     * 높은 페어 점수 순으로 조회
     */
    @Query("SELECT * FROM number_pairs ORDER BY pair_score DESC LIMIT :limit")
    List<NumberPairsEntity> getTopPairs(int limit);

    /**
     * 최근에 함께 나온 페어들 조회
     */
    @Query("SELECT * FROM number_pairs WHERE last_draw_together > :drawNumber ORDER BY last_draw_together DESC LIMIT :limit")
    List<NumberPairsEntity> getRecentPairs(int drawNumber, int limit);

    /**
     * 특정 점수 이상의 페어들 조회
     */
    @Query("SELECT * FROM number_pairs WHERE pair_score >= :minScore ORDER BY pair_score DESC")
    List<NumberPairsEntity> getPairsByMinScore(double minScore);

    /**
     * 페어 정보 삽입 또는 업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdatePairs(NumberPairsEntity... pairs);

    /**
     * 페어 정보 대량 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPairs(List<NumberPairsEntity> pairs);

    /**
     * 모든 페어 삭제
     */
    @Query("DELETE FROM number_pairs")
    void deleteAllPairs();

    /**
     * 특정 번호가 포함된 페어들 삭제
     */
    @Query("DELETE FROM number_pairs WHERE number1 = :number OR number2 = :number")
    void deletePairsByNumber(int number);

    /**
     * 페어 데이터 개수 조회
     */
    @Query("SELECT COUNT(*) FROM number_pairs")
    int getPairsCount();

    /**
     * 마지막 업데이트 시간 조회
     */
    @Query("SELECT MAX(updated_at) FROM number_pairs")
    long getLastUpdateTime();

    /**
     * 특정 번호들이 포함된 페어들의 평균 점수 조회
     */
    @Query("SELECT AVG(pair_score) FROM number_pairs WHERE number1 IN (:numbers) OR number2 IN (:numbers)")
    double getAveragePairScore(List<Integer> numbers);
}