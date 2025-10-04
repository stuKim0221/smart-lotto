package app.grapekim.smartlotto.data.model;

/**
 * 번호별 출현 빈도를 담는 클래스
 */
public class NumberFrequency {
    public final int number;
    public final int frequency;
    public final double percentage;

    public NumberFrequency(int number, int frequency, int totalDraws) {
        this.number = number;
        this.frequency = frequency;
        this.percentage = totalDraws > 0 ? (frequency * 100.0) / (totalDraws * 6) : 0.0; // 6개 번호 중 하나
    }

    public NumberFrequency(int number, int frequency, double percentage) {
        this.number = number;
        this.frequency = frequency;
        this.percentage = percentage;
    }

    @Override
    public String toString() {
        return String.format("NumberFrequency{number=%d, frequency=%d, percentage=%.1f%%}",
                number, frequency, percentage);
    }
}