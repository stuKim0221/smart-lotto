package app.grapekim.smartlotto.data.model;

/**
 * 로또 한 회차의 데이터를 담는 클래스
 */
public class LottoDrawData {
    public final int year;
    public final int drawNo;
    public final String date;
    public final int n1, n2, n3, n4, n5, n6;
    public final int bonus;

    public LottoDrawData(int year, int drawNo, String date,
                         int n1, int n2, int n3, int n4, int n5, int n6, int bonus) {
        this.year = year;
        this.drawNo = drawNo;
        this.date = date;
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
        this.n4 = n4;
        this.n5 = n5;
        this.n6 = n6;
        this.bonus = bonus;
    }

    /**
     * 메인 번호들을 배열로 반환
     */
    public int[] getMainNumbers() {
        return new int[]{n1, n2, n3, n4, n5, n6};
    }

    /**
     * 디버깅용 toString
     */
    @Override
    public String toString() {
        return String.format("LottoDrawData{drawNo=%d, date='%s', numbers=[%d,%d,%d,%d,%d,%d], bonus=%d}",
                drawNo, date, n1, n2, n3, n4, n5, n6, bonus);
    }
}