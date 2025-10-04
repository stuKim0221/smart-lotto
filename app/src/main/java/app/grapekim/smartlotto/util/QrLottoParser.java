package app.grapekim.smartlotto.util;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 동행복권 QR 데이터 파싱 유틸 */
public class QrLottoParser {

    public static class Result {
        public final List<Integer> numbers;           // 기존 호환성: 첫 번째 게임 (1..6, 보너스 제외)
        public final List<List<Integer>> allGames;    // 신규: 모든 게임들
        @Nullable public final Integer round;         // 회차
        @Nullable public final Long purchaseAt;       // 구매(스캔) 시각 (epoch millis)

        public Result(List<Integer> numbers, List<List<Integer>> allGames, @Nullable Integer round, @Nullable Long purchaseAt) {
            this.numbers = numbers;           // 기존 호환성
            this.allGames = allGames;         // 신규 기능
            this.round = round;
            this.purchaseAt = purchaseAt;
        }

        // 편의 메서드들
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

    /**
     * 동행복권 앱/영수증 QR 문자열을 파싱한다.
     * 예) http://m.dhlottery.co.kr/?v=1186q0813180203350141527313336q050607222639021521294144...
     */
    @Nullable
    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;

        try {
            // 동행복권 URL 확인
            if (raw.contains("dhlottery.co.kr")) {
                return parseDhlotteryFormat(raw);
            }

            // 기존 형식도 지원 (하위 호환성)
            Uri uri = Uri.parse(raw);
            String v = uri.getQueryParameter("v");
            if (v != null) {
                return parseOldFormat(v);
            }

            return null;
        } catch (Exception e) {
            android.util.Log.w("QrLottoParser", "파싱 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 실제 동행복권 QR 형식 파싱
     * URL: http://m.dhlottery.co.kr/?v=1186q0813180203350141527313336q050607222639021521294144q1011151639451070443822
     */
    private static Result parseDhlotteryFormat(String raw) {
        try {
            // URL에서 v 파라미터 추출
            Uri uri = Uri.parse(raw);
            String v = uri.getQueryParameter("v");

            if (v == null || v.length() < 4) {
                return null;
            }

            // 'q'로 분할
            String[] parts = v.split("q");

            if (parts.length < 2) {
                return null;
            }

            // 첫 번째 부분은 회차
            Integer round = safeInt(parts[0]);
            if (round == null) {
                return null;
            }

            // 나머지 부분들은 게임 데이터
            List<List<Integer>> allGames = new ArrayList<>();

            for (int i = 1; i < parts.length && i <= 5; i++) { // 최대 5게임
                String gameData = parts[i];
                List<Integer> gameNumbers = parseGameData(gameData);

                if (gameNumbers.size() == 6) { // 정확히 6개 번호인 게임만 추가
                    Collections.sort(gameNumbers);
                    allGames.add(gameNumbers);
                }
            }

            if (!allGames.isEmpty()) {
                List<Integer> firstGame = allGames.get(0);
                return new Result(firstGame, allGames, round, null);
            }

            return null;

        } catch (Exception e) {
            android.util.Log.w("QrLottoParser", "동행복권 형식 파싱 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 게임 데이터에서 번호들 추출
     * 예: "0813180203350141527313336" → [8, 13, 18, 20, 33, 35], [14, 15, 27, 31, 33, 36]
     */
    private static List<Integer> parseGameData(String gameData) {
        List<Integer> numbers = new ArrayList<>();

        try {
            // 2자리씩 끊어서 번호로 변환
            for (int i = 0; i < gameData.length() - 1; i += 2) {
                String numStr = gameData.substring(i, i + 2);
                int num = Integer.parseInt(numStr);

                // 유효한 로또 번호 범위 (1-45)
                if (num >= 1 && num <= 45) {
                    numbers.add(num);
                }

                // 6개 번호가 모이면 하나의 게임 완성
                if (numbers.size() >= 6) {
                    break;
                }
            }

        } catch (Exception e) {
            android.util.Log.w("QrLottoParser", "게임 데이터 파싱 실패: " + gameData, e);
        }

        return numbers;
    }

    /**
     * 기존 형식 파싱 (하위 호환성)
     */
    private static Result parseOldFormat(String v) {
        if (v.contains("games=")) {
            return parseMultiGameFormat(v);
        } else if (v.contains("n=")) {
            return parseSingleGameFormat(v);
        }
        return null;
    }

    /**
     * 다중 게임 포맷 파싱 (기존)
     */
    private static Result parseMultiGameFormat(String v) {
        String[] parts = v.split(";");
        Integer round = null;
        Long ts = null;
        List<List<Integer>> allGames = new ArrayList<>();

        for (String p : parts) {
            if (p.startsWith("round=")) {
                round = safeInt(p.substring(6));
            }
            else if (p.startsWith("games=")) {
                String gamesStr = p.substring(6);
                String[] gameStrings = gamesStr.split("\\|");

                for (String gameStr : gameStrings) {
                    List<Integer> game = new ArrayList<>();
                    String[] nums = gameStr.split(",");
                    for (String numStr : nums) {
                        Integer num = safeInt(numStr);
                        if (num != null && num >= 1 && num <= 45) {
                            game.add(num);
                        }
                    }
                    if (game.size() == 6) {
                        Collections.sort(game);
                        allGames.add(game);
                    }

                    if (allGames.size() >= 5) break;
                }
            }
            else if (p.startsWith("ts=")) {
                ts = safeLong(p.substring(3));
            }
        }

        if (!allGames.isEmpty()) {
            List<Integer> firstGame = allGames.get(0);
            return new Result(firstGame, allGames, round, ts);
        }
        return null;
    }

    /**
     * 단일 게임 포맷 파싱 (기존)
     */
    private static Result parseSingleGameFormat(String v) {
        String[] parts = v.split(";");
        Integer round = null;
        Long ts = null;
        List<Integer> nums = new ArrayList<>();

        for (String p : parts) {
            if (p.startsWith("round=")) {
                round = safeInt(p.substring(6));
            }
            else if (p.startsWith("n=")) {
                String[] nn = p.substring(2).split(",");
                for (String s : nn) {
                    Integer num = safeInt(s);
                    if (num != null && num >= 1 && num <= 45) {
                        nums.add(num);
                    }
                }
            }
            else if (p.startsWith("ts=")) {
                ts = safeLong(p.substring(3));
            }
        }

        if (nums.size() >= 6) {
            List<Integer> firstGame = nums.subList(0, 6);
            Collections.sort(firstGame);

            List<List<Integer>> allGames = new ArrayList<>();
            allGames.add(new ArrayList<>(firstGame));

            return new Result(firstGame, allGames, round, ts);
        }
        return null;
    }

    private static Integer safeInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long safeLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}