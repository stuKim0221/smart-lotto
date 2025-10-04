package app.grapekim.smartlotto.data.local.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;

import java.util.List;

/**
 * 로또 당첨번호 이력 DAO
 * AI 번호 생성을 위한 과거 당첨번호 데이터 관리
 */
@Dao
public interface LottoDrawHistoryDao {

    // ==================== 기본 CRUD 메서드들 ====================

    /**
     * 당첨번호 저장 (중복 시 무시)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(LottoDrawHistoryEntity entity);

    /**
     * 여러 당첨번호 일괄 저장
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<LottoDrawHistoryEntity> entities);

    /**
     * 당첨번호 업데이트 (중복 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(LottoDrawHistoryEntity entity);

    // ==================== 조회 메서드들 ====================

    /**
     * 모든 당첨번호 조회 (회차 내림차순)
     */
    @Query("SELECT * FROM lotto_draw_history ORDER BY draw_number DESC")
    List<LottoDrawHistoryEntity> getAll();

    /**
     * 모든 당첨번호 LiveData 조회 (회차 내림차순)
     */
    @Query("SELECT * FROM lotto_draw_history ORDER BY draw_number DESC")
    LiveData<List<LottoDrawHistoryEntity>> observeAll();

    /**
     * 최근 N회차 당첨번호 조회
     */
    @Query("SELECT * FROM lotto_draw_history ORDER BY draw_number DESC LIMIT :limit")
    List<LottoDrawHistoryEntity> getRecent(int limit);

    /**
     * 특정 회차 당첨번호 조회
     */
    @Query("SELECT * FROM lotto_draw_history WHERE draw_number = :drawNumber")
    LottoDrawHistoryEntity getByDrawNumber(int drawNumber);

    /**
     * 특정 회차 범위 당첨번호 조회
     */
    @Query("SELECT * FROM lotto_draw_history WHERE draw_number BETWEEN :startDraw AND :endDraw ORDER BY draw_number DESC")
    List<LottoDrawHistoryEntity> getByDrawRange(int startDraw, int endDraw);

    /**
     * 최신 회차 번호 조회
     */
    @Query("SELECT MAX(draw_number) FROM lotto_draw_history")
    Integer getLatestDrawNumber();

    /**
     * 저장된 당첨번호 총 개수
     */
    @Query("SELECT COUNT(*) FROM lotto_draw_history")
    int getTotalCount();

    /**
     * 특정 날짜 이후 당첨번호 조회
     */
    @Query("SELECT * FROM lotto_draw_history WHERE draw_date >= :fromDate ORDER BY draw_number DESC")
    List<LottoDrawHistoryEntity> getFromDate(String fromDate);

    // ==================== AI 분석용 통계 쿼리들 ====================

    /**
     * 특정 번호의 전체 출현 횟수 조회
     */
    @Query("SELECT COUNT(*) FROM lotto_draw_history WHERE " +
            "number1 = :number OR number2 = :number OR number3 = :number OR " +
            "number4 = :number OR number5 = :number OR number6 = :number")
    int getNumberAppearanceCount(int number);

    /**
     * 특정 번호가 마지막으로 나온 회차 조회
     */
    @Query("SELECT MAX(draw_number) FROM lotto_draw_history WHERE " +
            "number1 = :number OR number2 = :number OR number3 = :number OR " +
            "number4 = :number OR number5 = :number OR number6 = :number")
    Integer getNumberLastAppearance(int number);

    /**
     * 최근 N회차에서 특정 번호 출현 횟수
     */
    @Query("SELECT COUNT(*) FROM (" +
            "  SELECT * FROM lotto_draw_history ORDER BY draw_number DESC LIMIT :recentCount" +
            ") WHERE " +
            "number1 = :number OR number2 = :number OR number3 = :number OR " +
            "number4 = :number OR number5 = :number OR number6 = :number")
    int getNumberRecentAppearanceCount(int number, int recentCount);

    /**
     * 모든 번호(1-45)의 전체 출현 횟수 조회 (AI 통계 계산용)
     */
    @Query("SELECT " +
            "1 as number, COUNT(*) as count FROM lotto_draw_history WHERE number1=1 OR number2=1 OR number3=1 OR number4=1 OR number5=1 OR number6=1 " +
            "UNION ALL SELECT 2, COUNT(*) FROM lotto_draw_history WHERE number1=2 OR number2=2 OR number3=2 OR number4=2 OR number5=2 OR number6=2 " +
            "UNION ALL SELECT 3, COUNT(*) FROM lotto_draw_history WHERE number1=3 OR number2=3 OR number3=3 OR number4=3 OR number5=3 OR number6=3 " +
            "UNION ALL SELECT 4, COUNT(*) FROM lotto_draw_history WHERE number1=4 OR number2=4 OR number3=4 OR number4=4 OR number5=4 OR number6=4 " +
            "UNION ALL SELECT 5, COUNT(*) FROM lotto_draw_history WHERE number1=5 OR number2=5 OR number3=5 OR number4=5 OR number5=5 OR number6=5 " +
            "UNION ALL SELECT 6, COUNT(*) FROM lotto_draw_history WHERE number1=6 OR number2=6 OR number3=6 OR number4=6 OR number5=6 OR number6=6 " +
            "UNION ALL SELECT 7, COUNT(*) FROM lotto_draw_history WHERE number1=7 OR number2=7 OR number3=7 OR number4=7 OR number5=7 OR number6=7 " +
            "UNION ALL SELECT 8, COUNT(*) FROM lotto_draw_history WHERE number1=8 OR number2=8 OR number3=8 OR number4=8 OR number5=8 OR number6=8 " +
            "UNION ALL SELECT 9, COUNT(*) FROM lotto_draw_history WHERE number1=9 OR number2=9 OR number3=9 OR number4=9 OR number5=9 OR number6=9 " +
            "UNION ALL SELECT 10, COUNT(*) FROM lotto_draw_history WHERE number1=10 OR number2=10 OR number3=10 OR number4=10 OR number5=10 OR number6=10 " +
            "UNION ALL SELECT 11, COUNT(*) FROM lotto_draw_history WHERE number1=11 OR number2=11 OR number3=11 OR number4=11 OR number5=11 OR number6=11 " +
            "UNION ALL SELECT 12, COUNT(*) FROM lotto_draw_history WHERE number1=12 OR number2=12 OR number3=12 OR number4=12 OR number5=12 OR number6=12 " +
            "UNION ALL SELECT 13, COUNT(*) FROM lotto_draw_history WHERE number1=13 OR number2=13 OR number3=13 OR number4=13 OR number5=13 OR number6=13 " +
            "UNION ALL SELECT 14, COUNT(*) FROM lotto_draw_history WHERE number1=14 OR number2=14 OR number3=14 OR number4=14 OR number5=14 OR number6=14 " +
            "UNION ALL SELECT 15, COUNT(*) FROM lotto_draw_history WHERE number1=15 OR number2=15 OR number3=15 OR number4=15 OR number5=15 OR number6=15 " +
            "UNION ALL SELECT 16, COUNT(*) FROM lotto_draw_history WHERE number1=16 OR number2=16 OR number3=16 OR number4=16 OR number5=16 OR number6=16 " +
            "UNION ALL SELECT 17, COUNT(*) FROM lotto_draw_history WHERE number1=17 OR number2=17 OR number3=17 OR number4=17 OR number5=17 OR number6=17 " +
            "UNION ALL SELECT 18, COUNT(*) FROM lotto_draw_history WHERE number1=18 OR number2=18 OR number3=18 OR number4=18 OR number5=18 OR number6=18 " +
            "UNION ALL SELECT 19, COUNT(*) FROM lotto_draw_history WHERE number1=19 OR number2=19 OR number3=19 OR number4=19 OR number5=19 OR number6=19 " +
            "UNION ALL SELECT 20, COUNT(*) FROM lotto_draw_history WHERE number1=20 OR number2=20 OR number3=20 OR number4=20 OR number5=20 OR number6=20 " +
            "UNION ALL SELECT 21, COUNT(*) FROM lotto_draw_history WHERE number1=21 OR number2=21 OR number3=21 OR number4=21 OR number5=21 OR number6=21 " +
            "UNION ALL SELECT 22, COUNT(*) FROM lotto_draw_history WHERE number1=22 OR number2=22 OR number3=22 OR number4=22 OR number5=22 OR number6=22 " +
            "UNION ALL SELECT 23, COUNT(*) FROM lotto_draw_history WHERE number1=23 OR number2=23 OR number3=23 OR number4=23 OR number5=23 OR number6=23 " +
            "UNION ALL SELECT 24, COUNT(*) FROM lotto_draw_history WHERE number1=24 OR number2=24 OR number3=24 OR number4=24 OR number5=24 OR number6=24 " +
            "UNION ALL SELECT 25, COUNT(*) FROM lotto_draw_history WHERE number1=25 OR number2=25 OR number3=25 OR number4=25 OR number5=25 OR number6=25 " +
            "UNION ALL SELECT 26, COUNT(*) FROM lotto_draw_history WHERE number1=26 OR number2=26 OR number3=26 OR number4=26 OR number5=26 OR number6=26 " +
            "UNION ALL SELECT 27, COUNT(*) FROM lotto_draw_history WHERE number1=27 OR number2=27 OR number3=27 OR number4=27 OR number5=27 OR number6=27 " +
            "UNION ALL SELECT 28, COUNT(*) FROM lotto_draw_history WHERE number1=28 OR number2=28 OR number3=28 OR number4=28 OR number5=28 OR number6=28 " +
            "UNION ALL SELECT 29, COUNT(*) FROM lotto_draw_history WHERE number1=29 OR number2=29 OR number3=29 OR number4=29 OR number5=29 OR number6=29 " +
            "UNION ALL SELECT 30, COUNT(*) FROM lotto_draw_history WHERE number1=30 OR number2=30 OR number3=30 OR number4=30 OR number5=30 OR number6=30 " +
            "UNION ALL SELECT 31, COUNT(*) FROM lotto_draw_history WHERE number1=31 OR number2=31 OR number3=31 OR number4=31 OR number5=31 OR number6=31 " +
            "UNION ALL SELECT 32, COUNT(*) FROM lotto_draw_history WHERE number1=32 OR number2=32 OR number3=32 OR number4=32 OR number5=32 OR number6=32 " +
            "UNION ALL SELECT 33, COUNT(*) FROM lotto_draw_history WHERE number1=33 OR number2=33 OR number3=33 OR number4=33 OR number5=33 OR number6=33 " +
            "UNION ALL SELECT 34, COUNT(*) FROM lotto_draw_history WHERE number1=34 OR number2=34 OR number3=34 OR number4=34 OR number5=34 OR number6=34 " +
            "UNION ALL SELECT 35, COUNT(*) FROM lotto_draw_history WHERE number1=35 OR number2=35 OR number3=35 OR number4=35 OR number5=35 OR number6=35 " +
            "UNION ALL SELECT 36, COUNT(*) FROM lotto_draw_history WHERE number1=36 OR number2=36 OR number3=36 OR number4=36 OR number5=36 OR number6=36 " +
            "UNION ALL SELECT 37, COUNT(*) FROM lotto_draw_history WHERE number1=37 OR number2=37 OR number3=37 OR number4=37 OR number5=37 OR number6=37 " +
            "UNION ALL SELECT 38, COUNT(*) FROM lotto_draw_history WHERE number1=38 OR number2=38 OR number3=38 OR number4=38 OR number5=38 OR number6=38 " +
            "UNION ALL SELECT 39, COUNT(*) FROM lotto_draw_history WHERE number1=39 OR number2=39 OR number3=39 OR number4=39 OR number5=39 OR number6=39 " +
            "UNION ALL SELECT 40, COUNT(*) FROM lotto_draw_history WHERE number1=40 OR number2=40 OR number3=40 OR number4=40 OR number5=40 OR number6=40 " +
            "UNION ALL SELECT 41, COUNT(*) FROM lotto_draw_history WHERE number1=41 OR number2=41 OR number3=41 OR number4=41 OR number5=41 OR number6=41 " +
            "UNION ALL SELECT 42, COUNT(*) FROM lotto_draw_history WHERE number1=42 OR number2=42 OR number3=42 OR number4=42 OR number5=42 OR number6=42 " +
            "UNION ALL SELECT 43, COUNT(*) FROM lotto_draw_history WHERE number1=43 OR number2=43 OR number3=43 OR number4=43 OR number5=43 OR number6=43 " +
            "UNION ALL SELECT 44, COUNT(*) FROM lotto_draw_history WHERE number1=44 OR number2=44 OR number3=44 OR number4=44 OR number5=44 OR number6=44 " +
            "UNION ALL SELECT 45, COUNT(*) FROM lotto_draw_history WHERE number1=45 OR number2=45 OR number3=45 OR number4=45 OR number5=45 OR number6=45 " +
            "ORDER BY number")
    List<NumberAppearanceResult> getAllNumberAppearanceCounts();

    /**
     * 페어 분석용 - 두 번호가 함께 나온 횟수 조회
     */
    @Query("SELECT COUNT(*) FROM lotto_draw_history WHERE " +
            "(:num1 IN (number1, number2, number3, number4, number5, number6) AND " +
            " :num2 IN (number1, number2, number3, number4, number5, number6))")
    int getNumberPairCount(int num1, int num2);

    /**
     * 두 번호가 마지막으로 함께 나온 회차 조회
     */
    @Query("SELECT MAX(draw_number) FROM lotto_draw_history WHERE " +
            "(:num1 IN (number1, number2, number3, number4, number5, number6) AND " +
            " :num2 IN (number1, number2, number3, number4, number5, number6))")
    Integer getNumberPairLastAppearance(int num1, int num2);

    // ==================== 데이터 관리 메서드들 ====================

    /**
     * 모든 당첨번호 삭제
     */
    @Query("DELETE FROM lotto_draw_history")
    void deleteAll();

    /**
     * 특정 회차 삭제
     */
    @Query("DELETE FROM lotto_draw_history WHERE draw_number = :drawNumber")
    void deleteByDrawNumber(int drawNumber);

    /**
     * 특정 회차 이전 데이터 삭제 (오래된 데이터 정리용)
     */
    @Query("DELETE FROM lotto_draw_history WHERE draw_number < :beforeDrawNumber")
    void deleteBeforeDrawNumber(int beforeDrawNumber);

    /**
     * 데이터 존재 여부 확인
     */
    @Query("SELECT EXISTS(SELECT 1 FROM lotto_draw_history WHERE draw_number = :drawNumber)")
    boolean exists(int drawNumber);

    // ==================== 내부 클래스: 결과 타입들 ====================

    /**
     * 번호별 출현 횟수 결과 클래스
     */
    class NumberAppearanceResult {
        public int number;
        public int count;

        public NumberAppearanceResult(int number, int count) {
            this.number = number;
            this.count = count;
        }
    }
}