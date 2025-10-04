package app.grapekim.smartlotto.util;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 동행복권 영수증/QR 원문(raw)을 "대강" 파싱한다.
 * - 1..45 사이 정수를 스캔해 여러 게임의 번호들을 추출 (기존: 첫 6개만, 신규: 모든 게임)
 * - "drwNo", "round", "회" 등 토큰에서 회차를 유추
 * - 날짜/시간 포맷(여러 가지)을 탐지해 구매시각 타임스탬프 유추
 */
public final class QrParser {

    private QrParser() {}

    public static final class Parsed {
        public final List<Integer> numbers;           // size==6 (기존 호환성: 첫 번째 게임)
        public final List<List<Integer>> allGames;    // 모든 게임들 (신규 기능)
        @Nullable public final Integer round;         // null 허용
        @Nullable public final Long purchaseAt;       // epoch millis, null 허용

        public Parsed(List<Integer> numbers, List<List<Integer>> allGames, @Nullable Integer round, @Nullable Long purchaseAt) {
            this.numbers = numbers;           // 기존 호환성
            this.allGames = allGames;         // 신규 기능
            this.round = round;
            this.purchaseAt = purchaseAt;
        }

        // 편의 메서드들 (신규)
        public int getGameCount() {
            return allGames.size();
        }

        public List<Integer> getGame(int index) {
            if (index >= 0 && index < allGames.size()) {
                return allGames.get(index);
            }
            return new ArrayList<>();
        }

        public List<Integer> getFirstGame() {
            return numbers; // 기존 호환성
        }
    }

    public static @Nullable Parsed parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();

        // 1) 모든 번호 추출 (1..45) - 확장된 로직
        Pattern pNum = Pattern.compile("(?<!\\d)([1-9]|[1-3]\\d|4[0-5])(?!\\d)");
        Matcher m = pNum.matcher(s);
        List<Integer> allNumbers = new ArrayList<>();
        while (m.find()) {
            allNumbers.add(Integer.parseInt(m.group(1)));
        }

        // 기존 호환성: 최소 6개는 있어야 함
        if (allNumbers.size() < 6) return null;

        // 2) 여러 게임으로 분할 (6개씩 그룹핑)
        List<List<Integer>> allGames = new ArrayList<>();
        for (int i = 0; i + 5 < allNumbers.size(); i += 6) {
            List<Integer> game = new ArrayList<>();
            for (int j = 0; j < 6; j++) {
                game.add(allNumbers.get(i + j));
            }
            Collections.sort(game); // 번호 정렬
            allGames.add(game);

            // 최대 5게임까지만 (A,B,C,D,E)
            if (allGames.size() >= 5) break;
        }

        // 기존 호환성: 첫 번째 게임을 numbers로 설정
        List<Integer> firstGame = allGames.isEmpty() ? new ArrayList<>() : allGames.get(0);

        // 3) 회차 유추 (기존 로직 그대로)
        Integer round = null;
        // ex) drwNo=1184, "round=1184", "1184회"
        Pattern pDrw = Pattern.compile("(?:drwNo|round)\\s*=?\\s*(\\d{1,6})");
        Matcher md = pDrw.matcher(s);
        if (md.find()) {
            round = safeInt(md.group(1));
        } else {
            Matcher mh = Pattern.compile("(\\d{1,6})\\s*회").matcher(s);
            if (mh.find()) round = safeInt(mh.group(1));
        }

        // 4) 날짜 유추 (기존 로직 그대로)
        Long purchaseAt = null;
        List<SimpleDateFormat> fmts = Arrays.asList(
                new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
                new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()),
                new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        );
        // 간단히: yyyy[.-/]MM[.-/]dd[ HH:mm] 패턴 추출
        Matcher mdts = Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?")
                .matcher(s);
        if (mdts.find()) {
            String yyyy = mdts.group(1);
            String MM = mdts.group(2);
            String dd = mdts.group(3);
            String HH = mdts.group(4);
            String mm = mdts.group(5);
            String candidate = (HH != null)
                    ? String.format(Locale.US, "%s-%02d-%02d %02d:%02d",
                    yyyy, toInt(MM), toInt(dd), toInt(HH), toInt(mm))
                    : String.format(Locale.US, "%s-%02d-%02d", yyyy, toInt(MM), toInt(dd));
            for (SimpleDateFormat f : fmts) {
                try {
                    f.setLenient(false);
                    Date d = f.parse(candidate);
                    if (d != null) { purchaseAt = d.getTime(); break; }
                } catch (ParseException ignored) {}
            }
        }

        return new Parsed(firstGame, allGames, round, purchaseAt);
    }

    private static int toInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private static Integer safeInt(String s) { try { return Integer.valueOf(s); } catch (Exception e) { return null; } }
}