package app.grapekim.smartlotto.data.model;

import java.util.List;
import java.util.Map;

/**
 * 로또 통계 결과를 담는 클래스
 */
public class LottoStatistics {

    // 기본 정보
    public final int totalDraws;
    public final String periodDescription;

    // 메인 번호별 출현 빈도 (메인 6개 번호만)
    public final Map<Integer, Integer> numberFrequencyMap; // 번호 -> 출현횟수
    public final List<NumberFrequency> hotNumbers;  // 상위 10개 HOT 번호 (메인만)
    public final List<NumberFrequency> coldNumbers; // 하위 10개 COLD 번호 (메인만)

    // 메인 번호 홀짝 통계
    public final int oddCount;
    public final int evenCount;
    public final double oddPercentage;
    public final double evenPercentage;

    // 메인 번호 구간별 통계 (1-10, 11-20, 21-30, 31-40, 41-45)
    public final int[] sectionCounts;      // 각 구간별 출현 횟수
    public final double[] sectionPercentages; // 각 구간별 출현 비율

    // 보너스 번호 통계
    public final Map<Integer, Integer> bonusFrequencyMap; // 보너스 번호 -> 출현횟수
    public final NumberFrequency mostFrequentBonus;  // 가장 많이 나온 보너스
    public final NumberFrequency leastFrequentBonus; // 가장 적게 나온 보너스

    // 보너스 번호 Hot/Cold
    public final List<NumberFrequency> bonusHotNumbers;  // 상위 10개 보너스 HOT 번호
    public final List<NumberFrequency> bonusColdNumbers; // 하위 10개 보너스 COLD 번호

    // 보너스 번호 홀짝 통계
    public final int bonusOddCount;
    public final int bonusEvenCount;
    public final double bonusOddPercentage;
    public final double bonusEvenPercentage;

    // 보너스 번호 구간별 통계
    public final int[] bonusSectionCounts;
    public final double[] bonusSectionPercentages;

    public LottoStatistics(int totalDraws, String periodDescription,
                           Map<Integer, Integer> numberFrequencyMap,
                           List<NumberFrequency> hotNumbers,
                           List<NumberFrequency> coldNumbers,
                           int oddCount, int evenCount,
                           double oddPercentage, double evenPercentage,
                           int[] sectionCounts, double[] sectionPercentages,
                           Map<Integer, Integer> bonusFrequencyMap,
                           NumberFrequency mostFrequentBonus,
                           NumberFrequency leastFrequentBonus,
                           List<NumberFrequency> bonusHotNumbers,
                           List<NumberFrequency> bonusColdNumbers,
                           int bonusOddCount, int bonusEvenCount,
                           double bonusOddPercentage, double bonusEvenPercentage,
                           int[] bonusSectionCounts, double[] bonusSectionPercentages) {

        this.totalDraws = totalDraws;
        this.periodDescription = periodDescription;
        this.numberFrequencyMap = numberFrequencyMap;
        this.hotNumbers = hotNumbers;
        this.coldNumbers = coldNumbers;
        this.oddCount = oddCount;
        this.evenCount = evenCount;
        this.oddPercentage = oddPercentage;
        this.evenPercentage = evenPercentage;
        this.sectionCounts = sectionCounts;
        this.sectionPercentages = sectionPercentages;
        this.bonusFrequencyMap = bonusFrequencyMap;
        this.mostFrequentBonus = mostFrequentBonus;
        this.leastFrequentBonus = leastFrequentBonus;
        this.bonusHotNumbers = bonusHotNumbers;
        this.bonusColdNumbers = bonusColdNumbers;
        this.bonusOddCount = bonusOddCount;
        this.bonusEvenCount = bonusEvenCount;
        this.bonusOddPercentage = bonusOddPercentage;
        this.bonusEvenPercentage = bonusEvenPercentage;
        this.bonusSectionCounts = bonusSectionCounts;
        this.bonusSectionPercentages = bonusSectionPercentages;
    }

    /**
     * 특정 번호의 출현 횟수 반환 (메인 번호만)
     */
    public int getNumberFrequency(int number) {
        return numberFrequencyMap.getOrDefault(number, 0);
    }

    /**
     * 특정 구간의 출현 비율 반환 (메인 번호)
     * @param section 0=1-10, 1=11-20, 2=21-30, 3=31-40, 4=41-45
     */
    public double getSectionPercentage(int section) {
        if (section >= 0 && section < sectionPercentages.length) {
            return sectionPercentages[section];
        }
        return 0.0;
    }

    /**
     * 특정 구간의 보너스 출현 비율 반환
     * @param section 0=1-10, 1=11-20, 2=21-30, 3=31-40, 4=41-45
     */
    public double getBonusSectionPercentage(int section) {
        if (section >= 0 && section < bonusSectionPercentages.length) {
            return bonusSectionPercentages[section];
        }
        return 0.0;
    }

    /**
     * 통계 요약 정보 반환
     */
    public String getSummary() {
        return String.format("로또 통계 [%s]: 총 %d회차, HOT번호: %d번(%d회), COLD번호: %d번(%d회)",
                periodDescription, totalDraws,
                hotNumbers.isEmpty() ? 0 : hotNumbers.get(0).number,
                hotNumbers.isEmpty() ? 0 : hotNumbers.get(0).frequency,
                coldNumbers.isEmpty() ? 0 : coldNumbers.get(0).number,
                coldNumbers.isEmpty() ? 0 : coldNumbers.get(0).frequency);
    }
}