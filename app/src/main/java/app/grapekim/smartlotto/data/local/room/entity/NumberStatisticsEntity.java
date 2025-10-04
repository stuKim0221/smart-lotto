package app.grapekim.smartlotto.data.local.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 로또 번호 통계 정보를 저장하는 Entity
 */
@Entity(tableName = "number_statistics")
public class NumberStatisticsEntity {

    @PrimaryKey
    @ColumnInfo(name = "number")
    public int number;

    @ColumnInfo(name = "appearance_count")
    public int appearanceCount;

    @ColumnInfo(name = "last_draw_number")
    public int lastDrawNumber;

    @ColumnInfo(name = "last_appearance_gap")
    public int lastAppearanceGap;

    @ColumnInfo(name = "popularity_score")
    public double popularityScore;

    @ColumnInfo(name = "neglect_score")
    public double neglectScore;

    @ColumnInfo(name = "trend_score")
    public double trendScore;

    @ColumnInfo(name = "is_odd")
    public boolean isOdd;

    @ColumnInfo(name = "last_digit")
    public int lastDigit;

    @ColumnInfo(name = "avoidance_score")
    public double avoidanceScore;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    // 기본 생성자 (Room 필수)
    public NumberStatisticsEntity() {
    }

    // 전체 매개변수 생성자 (Room이 사용하지 않음)
    @Ignore
    public NumberStatisticsEntity(int number, int appearanceCount, int lastDrawNumber,
                                  int lastAppearanceGap, double popularityScore, double neglectScore,
                                  double trendScore, boolean isOdd, int lastDigit,
                                  double avoidanceScore, long updatedAt) {
        this.number = number;
        this.appearanceCount = appearanceCount;
        this.lastDrawNumber = lastDrawNumber;
        this.lastAppearanceGap = lastAppearanceGap;
        this.popularityScore = popularityScore;
        this.neglectScore = neglectScore;
        this.trendScore = trendScore;
        this.isOdd = isOdd;
        this.lastDigit = lastDigit;
        this.avoidanceScore = avoidanceScore;
        this.updatedAt = updatedAt;
    }

    // Getter와 Setter (Room에서 필요할 수 있음)
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public int getAppearanceCount() { return appearanceCount; }
    public void setAppearanceCount(int appearanceCount) { this.appearanceCount = appearanceCount; }

    public int getLastDrawNumber() { return lastDrawNumber; }
    public void setLastDrawNumber(int lastDrawNumber) { this.lastDrawNumber = lastDrawNumber; }

    public int getLastAppearanceGap() { return lastAppearanceGap; }
    public void setLastAppearanceGap(int lastAppearanceGap) { this.lastAppearanceGap = lastAppearanceGap; }

    public double getPopularityScore() { return popularityScore; }
    public void setPopularityScore(double popularityScore) { this.popularityScore = popularityScore; }

    public double getNeglectScore() { return neglectScore; }
    public void setNeglectScore(double neglectScore) { this.neglectScore = neglectScore; }

    public double getTrendScore() { return trendScore; }
    public void setTrendScore(double trendScore) { this.trendScore = trendScore; }

    public boolean isOdd() { return isOdd; }
    public void setOdd(boolean odd) { isOdd = odd; }

    public int getLastDigit() { return lastDigit; }
    public void setLastDigit(int lastDigit) { this.lastDigit = lastDigit; }

    public double getAvoidanceScore() { return avoidanceScore; }
    public void setAvoidanceScore(double avoidanceScore) { this.avoidanceScore = avoidanceScore; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}