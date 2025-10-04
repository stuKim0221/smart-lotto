package app.grapekim.smartlotto.domain.usecase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 1~45 중 6개 번호를 무작위로 생성(중복 없음, 오름차순)하는 최소 구현 */
public class GenerateNumbersUseCase {

    private final Random random;

    /** 기본 생성자: 내부 Random 사용 */
    public GenerateNumbersUseCase() {
        this.random = new Random();
    }

    /** 테스트 등을 위해 Random 주입 가능 */
    public GenerateNumbersUseCase(Random random) {
        this.random = (random != null) ? random : new Random();
    }

    /** 6개 번호 생성 */
    public List<Integer> exec() {
        List<Integer> pool = new ArrayList<>(45);
        for (int i = 1; i <= 45; i++) pool.add(i);
        Collections.shuffle(pool, random);
        List<Integer> pick = new ArrayList<>(pool.subList(0, 6));
        Collections.sort(pick);
        return pick;
    }
}
