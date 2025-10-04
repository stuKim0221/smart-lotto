package app.grapekim.smartlotto.data.local.room.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * AI 번호 생성 로그를 저장하는 Entity
 */
@Entity(tableName = "ai_generation_log")
public class AiGenerationLogEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "generated_numbers")
    public String generatedNumbers; // JSON 형태로 저장: "[1,7,15,23,34,41]"

    @ColumnInfo(name = "strategies_used")
    public String strategiesUsed; // JSON 형태로 저장: "['홀짝균형','구간분산']"

    @ColumnInfo(name = "quality_score")
    public double qualityScore;

    @ColumnInfo(name = "generation_method")
    public String generationMethod; // "AI_STATISTICAL", "AI_TREND", etc.

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "is_saved")
    public boolean isSaved;

    @ColumnInfo(name = "notes")
    public String notes;

    // 기본 생성자 (Room 필수)
    public AiGenerationLogEntity() {
    }

    // 전체 매개변수 생성자 (Room이 사용하지 않음)
    @Ignore
    public AiGenerationLogEntity(String generatedNumbers, String strategiesUsed,
                                 double qualityScore, String generationMethod,
                                 long createdAt, boolean isSaved, String notes) {
        this.generatedNumbers = generatedNumbers;
        this.strategiesUsed = strategiesUsed;
        this.qualityScore = qualityScore;
        this.generationMethod = generationMethod;
        this.createdAt = createdAt;
        this.isSaved = isSaved;
        this.notes = notes;
    }

    // Getter와 Setter
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getGeneratedNumbers() { return generatedNumbers; }
    public void setGeneratedNumbers(String generatedNumbers) { this.generatedNumbers = generatedNumbers; }

    public String getStrategiesUsed() { return strategiesUsed; }
    public void setStrategiesUsed(String strategiesUsed) { this.strategiesUsed = strategiesUsed; }

    public double getQualityScore() { return qualityScore; }
    public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }

    public String getGenerationMethod() { return generationMethod; }
    public void setGenerationMethod(String generationMethod) { this.generationMethod = generationMethod; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) { isSaved = saved; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}