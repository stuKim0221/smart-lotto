package app.grapekim.smartlotto.data.local.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 로또 번호 페어 분석 정보를 저장하는 Entity
 */
@Entity(tableName = "number_pairs")
public class NumberPairsEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "number1")
    public int number1;

    @ColumnInfo(name = "number2")
    public int number2;

    @ColumnInfo(name = "pair_count")
    public int pairCount;

    @ColumnInfo(name = "last_draw_together")
    public int lastDrawTogether;

    @ColumnInfo(name = "pair_score")
    public double pairScore;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    // 기본 생성자 (Room 필수)
    public NumberPairsEntity() {
    }

    // 전체 매개변수 생성자 (Room이 사용하지 않음)
    @Ignore
    public NumberPairsEntity(int number1, int number2, int pairCount,
                             int lastDrawTogether, double pairScore, long updatedAt) {
        this.number1 = number1;
        this.number2 = number2;
        this.pairCount = pairCount;
        this.lastDrawTogether = lastDrawTogether;
        this.pairScore = pairScore;
        this.updatedAt = updatedAt;
    }

    // Getter와 Setter
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public int getNumber1() { return number1; }
    public void setNumber1(int number1) { this.number1 = number1; }

    public int getNumber2() { return number2; }
    public void setNumber2(int number2) { this.number2 = number2; }

    public int getPairCount() { return pairCount; }
    public void setPairCount(int pairCount) { this.pairCount = pairCount; }

    public int getLastDrawTogether() { return lastDrawTogether; }
    public void setLastDrawTogether(int lastDrawTogether) { this.lastDrawTogether = lastDrawTogether; }

    public double getPairScore() { return pairScore; }
    public void setPairScore(double pairScore) { this.pairScore = pairScore; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}