package app.grapekim.smartlotto.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 로또 번호 분석을 위한 공통 유틸리티 클래스
 * 모든 곳에서 동일한 품질 점수 계산 방식을 사용하도록 통일
 */
public class LottoNumberAnalyzer {

    /**
     * 번호의 품질 점수를 계산 (0-100점)
     * @param numbers 분석할 번호 리스트 (6개)
     * @param appliedStrategies 적용된 전략 리스트 (옵션, 현재 사용하지 않음)
     * @return 품질 점수 (0-100)
     */
    public static int calculateQualityScore(List<Integer> numbers, List<String> appliedStrategies) {
        if (numbers == null || numbers.size() != 6) {
            return 0;
        }

        int totalScore = 0;

        // 1. 홀짝 균형 점수 (30점)
        totalScore += calculateOddEvenScore(numbers);

        // 2. 구간 분산 점수 (40점)
        totalScore += calculateRangeDistributionScore(numbers);

        // 3. 연속성 점수 (30점)
        totalScore += calculateConsecutiveScore(numbers);

        return Math.min(100, totalScore);
    }

    /**
     * 홀짝 균형 점수 계산 (최대 30점)
     */
    private static int calculateOddEvenScore(List<Integer> numbers) {
        int oddCount = 0;
        int evenCount = 0;

        for (int num : numbers) {
            if (num % 2 == 0) {
                evenCount++;
            } else {
                oddCount++;
            }
        }

        // 홀짝 비율이 균형잡힐수록 높은 점수
        int difference = Math.abs(oddCount - evenCount);

        switch (difference) {
            case 0: return 30; // 3:3 완벽 균형
            case 2: return 20; // 2:4 또는 4:2 적당한 균형
            case 4: return 10; // 1:5 또는 5:1 불균형
            case 6: return 0;  // 0:6 또는 6:0 완전 불균형
            default: return 0;
        }
    }

    /**
     * 구간 분산 점수 계산 (최대 40점)
     * 1-45를 5개 구간으로 나누어 분석: 1-9, 10-18, 19-27, 28-36, 37-45
     */
    private static int calculateRangeDistributionScore(List<Integer> numbers) {
        int[] rangeCounts = new int[5]; // 5개 구간

        for (int num : numbers) {
            int rangeIndex = Math.min((num - 1) / 9, 4);
            rangeCounts[rangeIndex]++;
        }

        int score = 40; // 기본 점수
        int emptyRanges = 0;
        int maxInRange = 0;

        for (int count : rangeCounts) {
            if (count == 0) emptyRanges++;
            maxInRange = Math.max(maxInRange, count);
        }

        // 빈 구간이 많을수록 감점
        score -= emptyRanges * 8;

        // 한 구간에 너무 많이 몰릴 경우 감점
        if (maxInRange > 3) {
            score -= 15;
        }

        return Math.max(0, score);
    }

    /**
     * 연속성 점수 계산 (최대 30점)
     */
    private static int calculateConsecutiveScore(List<Integer> numbers) {
        List<Integer> sortedNumbers = new ArrayList<>(numbers);
        Collections.sort(sortedNumbers);

        int consecutiveCount = 0;

        for (int i = 0; i < sortedNumbers.size() - 1; i++) {
            if (sortedNumbers.get(i + 1) - sortedNumbers.get(i) == 1) {
                consecutiveCount++;
            }
        }

        // 연속 번호가 적을수록 높은 점수
        return Math.max(0, 30 - (consecutiveCount * 10));
    }

    /**
     * 홀짝 분석 결과 클래스
     */
    public static class OddEvenAnalysis {
        public final int oddCount;
        public final int evenCount;
        public final String ratio;

        public OddEvenAnalysis(int oddCount, int evenCount) {
            this.oddCount = oddCount;
            this.evenCount = evenCount;
            this.ratio = oddCount + ":" + evenCount;
        }
    }

    /**
     * 구간 분석 결과 클래스
     */
    public static class RangeAnalysis {
        public final int[] rangeCounts;
        public final String[] rangeLabels = {"1-9", "10-18", "19-27", "28-36", "37-45"};

        public RangeAnalysis(int[] rangeCounts) {
            this.rangeCounts = rangeCounts.clone();
        }

        public int getEmptyRangeCount() {
            int count = 0;
            for (int rangeCount : rangeCounts) {
                if (rangeCount == 0) count++;
            }
            return count;
        }

        public int getMaxNumbersInRange() {
            int max = 0;
            for (int rangeCount : rangeCounts) {
                max = Math.max(max, rangeCount);
            }
            return max;
        }
    }

    /**
     * 연속성 분석 결과 클래스
     */
    public static class ConsecutiveAnalysis {
        public final int consecutiveCount;
        public final List<String> consecutivePairs;

        public ConsecutiveAnalysis(List<Integer> numbers) {
            List<Integer> sorted = new ArrayList<>(numbers);
            Collections.sort(sorted);

            int count = 0;
            List<String> pairs = new ArrayList<>();

            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i + 1) - sorted.get(i) == 1) {
                    count++;
                    pairs.add(sorted.get(i) + "-" + sorted.get(i + 1));
                }
            }

            this.consecutiveCount = count;
            this.consecutivePairs = pairs;
        }
    }

    /**
     * 완전한 번호 분석 수행
     */
    public static AnalysisResult analyzeNumbers(List<Integer> numbers, List<String> strategies) {
        if (numbers == null || numbers.size() != 6) {
            return null;
        }

        AnalysisResult result = new AnalysisResult();
        result.numbers = new ArrayList<>(numbers);
        result.oddEvenAnalysis = new OddEvenAnalysis(
                (int) numbers.stream().filter(n -> n % 2 == 1).count(),
                (int) numbers.stream().filter(n -> n % 2 == 0).count()
        );
        result.rangeAnalysis = calculateRangeAnalysis(numbers);
        result.consecutiveAnalysis = new ConsecutiveAnalysis(numbers);
        result.qualityScore = calculateQualityScore(numbers, strategies);
        result.appliedStrategies = strategies != null ? new ArrayList<>(strategies) : new ArrayList<>();

        return result;
    }

    private static RangeAnalysis calculateRangeAnalysis(List<Integer> numbers) {
        int[] rangeCounts = new int[5];

        for (int num : numbers) {
            int rangeIndex = Math.min((num - 1) / 9, 4);
            rangeCounts[rangeIndex]++;
        }

        return new RangeAnalysis(rangeCounts);
    }

    /**
     * 분석 결과를 담는 클래스
     */
    public static class AnalysisResult {
        public List<Integer> numbers;
        public OddEvenAnalysis oddEvenAnalysis;
        public RangeAnalysis rangeAnalysis;
        public ConsecutiveAnalysis consecutiveAnalysis;
        public int qualityScore;
        public List<String> appliedStrategies;

        public String getQualityGrade() {
            if (qualityScore >= 80) return "우수";
            if (qualityScore >= 60) return "양호";
            if (qualityScore >= 40) return "보통";
            return "미흡";
        }

        public String getFormattedNumbers() {
            Collections.sort(numbers);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numbers.size(); i++) {
                if (i > 0) sb.append(" - ");
                sb.append(String.format("%2d", numbers.get(i)));
            }
            return sb.toString();
        }
    }
}