package app.grapekim.smartlotto.data.service;

import android.util.Log;

import app.grapekim.smartlotto.data.model.LottoDrawData;
import app.grapekim.smartlotto.data.model.LottoStatistics;
import app.grapekim.smartlotto.data.model.NumberFrequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 로또 통계를 계산하는 서비스 클래스
 */
public class LottoStatisticsCalculator {

    private static final String TAG = "LottoStatisticsCalculator";

    /**
     * 로또 데이터 리스트로부터 통계를 계산
     * @param drawDataList 로또 데이터 리스트
     * @param periodDescription 기간 설명 (예: "전체 기간", "최근 1년")
     * @return 계산된 통계 객체
     */
    public static LottoStatistics calculateStatistics(List<LottoDrawData> drawDataList, String periodDescription) {
        if (drawDataList == null || drawDataList.isEmpty()) {
            Log.w(TAG, "Draw data list is empty or null");
            return createEmptyStatistics(periodDescription);
        }

        Log.d(TAG, String.format("Calculating statistics for %d draws (%s)", drawDataList.size(), periodDescription));

        int totalDraws = drawDataList.size();

        // 메인 번호별 출현 빈도 계산 (메인 6개 번호만)
        Map<Integer, Integer> numberFrequencyMap = calculateMainNumberFrequencies(drawDataList);

        // 메인 번호 HOT/COLD 번호 계산
        List<NumberFrequency> hotNumbers = getTopNumbers(numberFrequencyMap, totalDraws, 10, true);
        List<NumberFrequency> coldNumbers = getTopNumbers(numberFrequencyMap, totalDraws, 10, false);

        // 메인 번호 홀짝 통계 계산
        OddEvenStats oddEvenStats = calculateOddEvenStats(numberFrequencyMap);

        // 메인 번호 구간별 통계 계산
        SectionStats sectionStats = calculateSectionStats(numberFrequencyMap);

        // 보너스 번호 통계 계산
        BonusStats bonusStats = calculateBonusStats(drawDataList);

        // 보너스 번호 Hot/Cold 계산
        List<NumberFrequency> bonusHotNumbers = getBonusHotColdNumbers(bonusStats.bonusFrequencyMap, totalDraws, 10, true);
        List<NumberFrequency> bonusColdNumbers = getBonusHotColdNumbers(bonusStats.bonusFrequencyMap, totalDraws, 10, false);

        // 보너스 번호 홀짝 통계 계산
        OddEvenStats bonusOddEvenStats = calculateBonusOddEvenStats(bonusStats.bonusFrequencyMap);

        // 보너스 번호 구간별 통계 계산
        SectionStats bonusSectionStats = calculateBonusSectionStats(bonusStats.bonusFrequencyMap);

        return new LottoStatistics(
                totalDraws, periodDescription,
                numberFrequencyMap,
                hotNumbers, coldNumbers,
                oddEvenStats.oddCount, oddEvenStats.evenCount,
                oddEvenStats.oddPercentage, oddEvenStats.evenPercentage,
                sectionStats.counts, sectionStats.percentages,
                bonusStats.bonusFrequencyMap,
                bonusStats.mostFrequent, bonusStats.leastFrequent,
                bonusHotNumbers, bonusColdNumbers,
                bonusOddEvenStats.oddCount, bonusOddEvenStats.evenCount,
                bonusOddEvenStats.oddPercentage, bonusOddEvenStats.evenPercentage,
                bonusSectionStats.counts, bonusSectionStats.percentages
        );
    }

    /**
     * 메인 번호별 출현 빈도 계산 (1~45번, 메인 6개 번호만)
     */
    private static Map<Integer, Integer> calculateMainNumberFrequencies(List<LottoDrawData> drawDataList) {
        Map<Integer, Integer> frequencyMap = new HashMap<>();

        // 1~45번 초기화
        for (int i = 1; i <= 45; i++) {
            frequencyMap.put(i, 0);
        }

        // 각 회차의 메인 번호들만 카운트 (보너스 번호 제외)
        for (LottoDrawData drawData : drawDataList) {
            int[] numbers = drawData.getMainNumbers();
            for (int number : numbers) {
                if (number >= 1 && number <= 45) {
                    frequencyMap.put(number, frequencyMap.get(number) + 1);
                }
            }
        }

        return frequencyMap;
    }

    /**
     * 상위/하위 번호들 추출
     * @param frequencyMap 번호별 빈도 맵
     * @param totalDraws 총 회차 수
     * @param count 추출할 개수
     * @param isHot true면 HOT(상위), false면 COLD(하위)
     */
    private static List<NumberFrequency> getTopNumbers(Map<Integer, Integer> frequencyMap,
                                                       int totalDraws, int count, boolean isHot) {
        List<NumberFrequency> frequencies = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            frequencies.add(new NumberFrequency(entry.getKey(), entry.getValue(), totalDraws));
        }

        // 빈도순으로 정렬
        Collections.sort(frequencies, new Comparator<NumberFrequency>() {
            @Override
            public int compare(NumberFrequency a, NumberFrequency b) {
                if (isHot) {
                    return Integer.compare(b.frequency, a.frequency); // 내림차순 (HOT)
                } else {
                    return Integer.compare(a.frequency, b.frequency); // 오름차순 (COLD)
                }
            }
        });

        // 상위/하위 count개 반환
        return frequencies.subList(0, Math.min(count, frequencies.size()));
    }

    /**
     * 홀짝 통계 계산 (메인 번호)
     */
    private static OddEvenStats calculateOddEvenStats(Map<Integer, Integer> frequencyMap) {
        int oddCount = 0;
        int evenCount = 0;

        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            int number = entry.getKey();
            int frequency = entry.getValue();

            if (number % 2 == 0) {
                evenCount += frequency;
            } else {
                oddCount += frequency;
            }
        }

        int totalCount = oddCount + evenCount;
        double oddPercentage = totalCount > 0 ? (oddCount * 100.0) / totalCount : 0.0;
        double evenPercentage = totalCount > 0 ? (evenCount * 100.0) / totalCount : 0.0;

        return new OddEvenStats(oddCount, evenCount, oddPercentage, evenPercentage);
    }

    /**
     * 구간별 통계 계산 (1-10, 11-20, 21-30, 31-40, 41-45)
     */
    private static SectionStats calculateSectionStats(Map<Integer, Integer> frequencyMap) {
        int[] sectionCounts = new int[5]; // 5개 구간

        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            int number = entry.getKey();
            int frequency = entry.getValue();

            int section = (number - 1) / 10; // 0~4
            if (section > 4) section = 4; // 41-45는 구간 4

            sectionCounts[section] += frequency;
        }

        // 구간별 비율 계산
        int totalCount = 0;
        for (int count : sectionCounts) {
            totalCount += count;
        }

        double[] sectionPercentages = new double[5];
        for (int i = 0; i < 5; i++) {
            sectionPercentages[i] = totalCount > 0 ? (sectionCounts[i] * 100.0) / totalCount : 0.0;
        }

        return new SectionStats(sectionCounts, sectionPercentages);
    }

    /**
     * 보너스 번호 통계 계산
     */
    private static BonusStats calculateBonusStats(List<LottoDrawData> drawDataList) {
        Map<Integer, Integer> bonusFrequencyMap = new HashMap<>();

        // 1~45번 초기화
        for (int i = 1; i <= 45; i++) {
            bonusFrequencyMap.put(i, 0);
        }

        // 보너스 번호 카운트
        for (LottoDrawData drawData : drawDataList) {
            int bonus = drawData.bonus;
            if (bonus >= 1 && bonus <= 45) {
                bonusFrequencyMap.put(bonus, bonusFrequencyMap.get(bonus) + 1);
            }
        }

        // 최대/최소 빈도 찾기
        NumberFrequency mostFrequent = null;
        NumberFrequency leastFrequent = null;

        for (Map.Entry<Integer, Integer> entry : bonusFrequencyMap.entrySet()) {
            NumberFrequency freq = new NumberFrequency(entry.getKey(), entry.getValue(),
                    entry.getValue() * 100.0 / drawDataList.size());

            if (mostFrequent == null || freq.frequency > mostFrequent.frequency) {
                mostFrequent = freq;
            }
            if (leastFrequent == null || freq.frequency < leastFrequent.frequency) {
                leastFrequent = freq;
            }
        }

        return new BonusStats(bonusFrequencyMap, mostFrequent, leastFrequent);
    }

    /**
     * 보너스 번호 Hot/Cold 리스트 생성
     */
    private static List<NumberFrequency> getBonusHotColdNumbers(Map<Integer, Integer> bonusFrequencyMap,
                                                                int totalDraws, int count, boolean isHot) {
        List<NumberFrequency> frequencies = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : bonusFrequencyMap.entrySet()) {
            frequencies.add(new NumberFrequency(entry.getKey(), entry.getValue(),
                    entry.getValue() * 100.0 / totalDraws));
        }

        // 빈도순으로 정렬
        Collections.sort(frequencies, new Comparator<NumberFrequency>() {
            @Override
            public int compare(NumberFrequency a, NumberFrequency b) {
                if (isHot) {
                    return Integer.compare(b.frequency, a.frequency); // 내림차순 (HOT)
                } else {
                    return Integer.compare(a.frequency, b.frequency); // 오름차순 (COLD)
                }
            }
        });

        // 상위/하위 count개 반환
        return frequencies.subList(0, Math.min(count, frequencies.size()));
    }

    /**
     * 보너스 번호 홀짝 통계 계산
     */
    private static OddEvenStats calculateBonusOddEvenStats(Map<Integer, Integer> bonusFrequencyMap) {
        int oddCount = 0;
        int evenCount = 0;

        for (Map.Entry<Integer, Integer> entry : bonusFrequencyMap.entrySet()) {
            int number = entry.getKey();
            int frequency = entry.getValue();

            if (number % 2 == 0) {
                evenCount += frequency;
            } else {
                oddCount += frequency;
            }
        }

        int totalCount = oddCount + evenCount;
        double oddPercentage = totalCount > 0 ? (oddCount * 100.0) / totalCount : 0.0;
        double evenPercentage = totalCount > 0 ? (evenCount * 100.0) / totalCount : 0.0;

        return new OddEvenStats(oddCount, evenCount, oddPercentage, evenPercentage);
    }

    /**
     * 보너스 번호 구간별 통계 계산
     */
    private static SectionStats calculateBonusSectionStats(Map<Integer, Integer> bonusFrequencyMap) {
        int[] sectionCounts = new int[5]; // 5개 구간

        for (Map.Entry<Integer, Integer> entry : bonusFrequencyMap.entrySet()) {
            int number = entry.getKey();
            int frequency = entry.getValue();

            int section = (number - 1) / 10; // 0~4
            if (section > 4) section = 4; // 41-45는 구간 4

            sectionCounts[section] += frequency;
        }

        // 구간별 비율 계산
        int totalCount = 0;
        for (int count : sectionCounts) {
            totalCount += count;
        }

        double[] sectionPercentages = new double[5];
        for (int i = 0; i < 5; i++) {
            sectionPercentages[i] = totalCount > 0 ? (sectionCounts[i] * 100.0) / totalCount : 0.0;
        }

        return new SectionStats(sectionCounts, sectionPercentages);
    }

    /**
     * 빈 통계 객체 생성 (데이터가 없을 때)
     */
    private static LottoStatistics createEmptyStatistics(String periodDescription) {
        Map<Integer, Integer> emptyMap = new HashMap<>();
        for (int i = 1; i <= 45; i++) {
            emptyMap.put(i, 0);
        }

        return new LottoStatistics(
                0, periodDescription,
                emptyMap,
                new ArrayList<NumberFrequency>(),
                new ArrayList<NumberFrequency>(),
                0, 0, 0.0, 0.0,
                new int[5], new double[5],
                new HashMap<Integer, Integer>(),
                new NumberFrequency(0, 0, 0.0),
                new NumberFrequency(0, 0, 0.0),
                new ArrayList<NumberFrequency>(),
                new ArrayList<NumberFrequency>(),
                0, 0, 0.0, 0.0,
                new int[5], new double[5]
        );
    }

    // 내부 헬퍼 클래스들
    private static class OddEvenStats {
        final int oddCount, evenCount;
        final double oddPercentage, evenPercentage;

        OddEvenStats(int oddCount, int evenCount, double oddPercentage, double evenPercentage) {
            this.oddCount = oddCount;
            this.evenCount = evenCount;
            this.oddPercentage = oddPercentage;
            this.evenPercentage = evenPercentage;
        }
    }

    private static class SectionStats {
        final int[] counts;
        final double[] percentages;

        SectionStats(int[] counts, double[] percentages) {
            this.counts = counts;
            this.percentages = percentages;
        }
    }

    private static class BonusStats {
        final Map<Integer, Integer> bonusFrequencyMap;
        final NumberFrequency mostFrequent, leastFrequent;

        BonusStats(Map<Integer, Integer> bonusFrequencyMap,
                   NumberFrequency mostFrequent, NumberFrequency leastFrequent) {
            this.bonusFrequencyMap = bonusFrequencyMap;
            this.mostFrequent = mostFrequent;
            this.leastFrequent = leastFrequent;
        }
    }
}