package app.grapekim.smartlotto.data.scheduler;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * 주기적으로 GitHub CSV를 체크하여 새로운 데이터가 있으면 자동 업데이트하는 워커
 *
 * 스케줄링 전략:
 * - 평일 정오(12시): 조용히 업데이트 (알림 없음)
 * - 토요일: AlarmManager가 담당 (QuickDataCheckReceiver)
 */
public class AutoDataUpdateWorker extends Worker {
    private static final String TAG = "AutoDataUpdateWorker";
    private static final String CSV_URL = "https://raw.githubusercontent.com/stuKim0221/smart-lotto/refs/heads/main/app/src/main/assets/draw_kor.csv";

    public AutoDataUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "=== 자동 데이터 업데이트 체크 시작 ===");

        try {
            // 시간대별 체크 빈도 조절
            if (!shouldCheckNow()) {
                Log.d(TAG, "현재 시간대는 체크 스킵 (배터리 절약)");
                return Result.success();
            }
            Context context = getApplicationContext();
            LottoRepository repository = new LottoRepositoryImpl(context);

            // 1. 로컬 DB의 최신 회차 확인
            Integer localLatestRound = repository.getLatestDrawNumber();
            Log.i(TAG, "로컬 DB 최신 회차: " + (localLatestRound != null ? localLatestRound : "없음"));

            // 2. GitHub CSV의 최신 회차 확인
            Integer githubLatestRound = getLatestRoundFromGitHub();
            Log.i(TAG, "GitHub CSV 최신 회차: " + (githubLatestRound != null ? githubLatestRound : "확인 실패"));

            if (githubLatestRound == null) {
                Log.w(TAG, "GitHub CSV 회차 확인 실패");
                return Result.retry();
            }

            // 3. 새로운 데이터가 있는지 확인
            if (localLatestRound == null || githubLatestRound > localLatestRound) {
                int newRounds = githubLatestRound - (localLatestRound != null ? localLatestRound : 0);
                Log.i(TAG, String.format("새로운 데이터 발견! %d → %d (%d개 회차)",
                        localLatestRound, githubLatestRound, newRounds));

                // 4. 데이터 업데이트 실행
                boolean updateSuccess = performUpdate(context);

                if (updateSuccess) {
                    // 5. 평일 정오 업데이트는 조용히 진행 (알림 없음)
                    Log.i(TAG, "✅ 평일 정오 자동 업데이트 성공 (알림 없음)");
                    return Result.success();
                } else {
                    Log.w(TAG, "⚠️ 업데이트 실패, 재시도 예정");
                    return Result.retry();
                }
            } else {
                Log.d(TAG, "새로운 데이터 없음 (GitHub: " + githubLatestRound + ", Local: " + localLatestRound + ")");
                return Result.success();
            }

        } catch (Exception e) {
            Log.e(TAG, "자동 업데이트 체크 중 오류", e);
            return Result.retry();
        }
    }

    /**
     * GitHub CSV에서 최신 회차 번호 가져오기
     */
    private Integer getLatestRoundFromGitHub() {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // 캐시 방지를 위한 타임스탬프 추가
            String urlWithTimestamp = CSV_URL + "?t=" + System.currentTimeMillis();
            URL url = new URL(urlWithTimestamp);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Cache-Control", "no-cache");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP 응답 코드: " + responseCode);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 헤더 스킵
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                // 첫 번째 데이터 라인에서 회차 추출
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String drawNoStr = parts[1].trim();
                    if (!drawNoStr.isEmpty()) {
                        int drawNo = Integer.parseInt(drawNoStr);
                        Log.d(TAG, "GitHub CSV 최신 회차 파싱: " + drawNo);
                        return drawNo;
                    }
                }
                break;
            }

        } catch (Exception e) {
            Log.e(TAG, "GitHub CSV 회차 확인 실패", e);
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "리소스 정리 실패", e);
            }
        }

        return null;
    }

    /**
     * 데이터 업데이트 실행
     */
    private boolean performUpdate(Context context) {
        try {
            Log.i(TAG, "GitHub에서 최신 CSV 다운로드 시작...");

            CsvUpdateManager updateManager = new CsvUpdateManager(context);
            boolean success = updateManager.updateCsvFile();

            if (success) {
                Log.i(TAG, "✅ CSV 다운로드 및 DB 저장 완료");
            } else {
                Log.w(TAG, "⚠️ CSV 다운로드 실패");
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "데이터 업데이트 실행 실패", e);
            return false;
        }
    }


    /**
     * 현재 시간대에 체크해야 하는지 판단
     *
     * 로직:
     * - 평일 정오(12시)에만 체크 (AlarmManager가 토요일 담당)
     */
    private boolean shouldCheckNow() {
        Calendar now = Calendar.getInstance();
        int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        // 토요일은 AlarmManager가 담당하므로 스킵
        if (dayOfWeek == Calendar.SATURDAY) {
            Log.d(TAG, "토요일은 AlarmManager가 담당 - 스킵");
            return false;
        }

        // 평일 정오(12시)에만 체크
        if (hour == 12 && minute < 15) {
            Log.i(TAG, "⏰ 평일 정오 정기 체크 수행");
            return true;
        }

        return false;
    }
}
