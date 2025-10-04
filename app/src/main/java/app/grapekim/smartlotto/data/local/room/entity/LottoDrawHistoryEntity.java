package app.grapekim.smartlotto.data.local.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 과거 로또 당첨번호 저장 Entity
 * AI 번호 생성을 위한 통계 분석용 데이터
 */
@Entity(
        tableName = "lotto_draw_history",
        indices = {
                @Index(value = "draw_number", unique = true),  // 회차 번호 중복 방지
                @Index(value = "draw_date")                    // 날짜별 조회 최적화
        }
)
public class LottoDrawHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 회차 번호 (예: 1132, 1133...) */
    @ColumnInfo(name = "draw_number")
    public int drawNumber;

    /** 추첨일 (YYYY-MM-DD 형태) */
    @ColumnInfo(name = "draw_date")
    public String drawDate;

    /** 당첨번호 1 */
    @ColumnInfo(name = "number1")
    public int number1;

    /** 당첨번호 2 */
    @ColumnInfo(name = "number2")
    public int number2;

    /** 당첨번호 3 */
    @ColumnInfo(name = "number3")
    public int number3;

    /** 당첨번호 4 */
    @ColumnInfo(name = "number4")
    public int number4;

    /** 당첨번호 5 */
    @ColumnInfo(name = "number5")
    public int number5;

    /** 당첨번호 6 */
    @ColumnInfo(name = "number6")
    public int number6;

    /** 보너스 번호 */
    @ColumnInfo(name = "bonus_number")
    public int bonusNumber;

    /** 데이터 저장 시각 */
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public long createdAt;

    // ==================== 편의 메서드들 ====================

    /**
     * 당첨번호들을 List로 반환 (보너스 번호 제외)
     */
    public List<Integer> getWinningNumbers() {
        return Arrays.asList(number1, number2, number3, number4, number5, number6);
    }

    /**
     * 보너스 번호 포함한 모든 번호들을 List로 반환
     */
    public List<Integer> getAllNumbers() {
        return Arrays.asList(number1, number2, number3, number4, number5, number6, bonusNumber);
    }

    /**
     * 당첨번호들을 CSV 문자열로 반환
     */
    public String getNumbersCsv() {
        return getWinningNumbers().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    /**
     * 당첨번호 합계 계산
     */
    public int getNumberSum() {
        return number1 + number2 + number3 + number4 + number5 + number6;
    }

    /**
     * 홀수 개수 계산
     */
    public int getOddCount() {
        int count = 0;
        List<Integer> numbers = getWinningNumbers();
        for (int num : numbers) {
            if (num % 2 == 1) count++;
        }
        return count;
    }

    /**
     * 짝수 개수 계산
     */
    public int getEvenCount() {
        return 6 - getOddCount();
    }

    /**
     * 특정 번호가 당첨번호에 포함되어 있는지 확인
     */
    public boolean containsNumber(int number) {
        return getWinningNumbers().contains(number);
    }

    /**
     * 특정 번호가 보너스 번호인지 확인
     */
    public boolean isBonusNumber(int number) {
        return bonusNumber == number;
    }

    /**
     * 구간별 분포 계산 (1-9, 10-19, 20-29, 30-39, 40-45)
     * @return 각 구간별 번호 개수 배열 [5개 구간]
     */
    public int[] getZoneDistribution() {
        int[] zones = new int[5]; // 5개 구간

        for (int num : getWinningNumbers()) {
            if (num >= 1 && num <= 9) zones[0]++;
            else if (num >= 10 && num <= 19) zones[1]++;
            else if (num >= 20 && num <= 29) zones[2]++;
            else if (num >= 30 && num <= 39) zones[3]++;
            else if (num >= 40 && num <= 45) zones[4]++;
        }

        return zones;
    }

    /**
     * 연속 번호 쌍의 개수 (예: 1,2 또는 23,24)
     */
    public int getConsecutivePairCount() {
        List<Integer> sorted = getWinningNumbers().stream()
                .sorted()
                .collect(Collectors.toList());

        int count = 0;
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i + 1) == sorted.get(i) + 1) {
                count++;
            }
        }
        return count;
    }

    /**
     * 생성자 (편의용)
     */
    public LottoDrawHistoryEntity() {
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 생성자 (모든 필드)
     */
    public LottoDrawHistoryEntity(int drawNumber, String drawDate,
                                  int num1, int num2, int num3, int num4, int num5, int num6,
                                  int bonusNumber) {
        this();
        this.drawNumber = drawNumber;
        this.drawDate = drawDate;
        this.number1 = num1;
        this.number2 = num2;
        this.number3 = num3;
        this.number4 = num4;
        this.number5 = num5;
        this.number6 = num6;
        this.bonusNumber = bonusNumber;
    }

    /**
     * 디버깅용 toString
     */
    @Override
    public String toString() {
        return String.format("LottoDrawHistory{drawNumber=%d, date='%s', numbers=[%s], bonus=%d}",
                drawNumber, drawDate, getNumbersCsv(), bonusNumber);
    }
}