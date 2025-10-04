package app.grapekim.smartlotto.ui.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.CsvUpdateManager;
import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DataStatusActivity extends AppCompatActivity {

    // 데이터 상태 관련 뷰들
    private TextView tvDataRange, tvLatestRound, tvNextUpdate, tvDataStatus;
    private TextView tvTotalRecords, tvDataPeriod, tvAnalysisStatus, tvLastChecked;
    private MaterialButton btnRefreshData;

    // GitHub CSV 업데이트 매니저
    private CsvUpdateManager csvUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_status);

        setupToolbar();
        initializeViews();
        initializeCsvUpdateManager();
        setupClickListeners();
        loadDataStatus();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("데이터 상태");
        }
    }

    private void initializeViews() {
        tvDataRange = findViewById(R.id.tvDataRange);
        tvLatestRound = findViewById(R.id.tvLatestRound);
        tvNextUpdate = findViewById(R.id.tvNextUpdate);
        tvDataStatus = findViewById(R.id.tvDataStatus);
        tvTotalRecords = findViewById(R.id.tvTotalRecords);
        tvDataPeriod = findViewById(R.id.tvDataPeriod);
        tvAnalysisStatus = findViewById(R.id.tvAnalysisStatus);
        tvLastChecked = findViewById(R.id.tvLastChecked);
        btnRefreshData = findViewById(R.id.btnRefreshData);
    }

    /**
     * CSV 업데이트 매니저 초기화
     */
    private void initializeCsvUpdateManager() {
        csvUpdateManager = new CsvUpdateManager(this);
        android.util.Log.d("DataStatusActivity", "CSV 업데이트 매니저 초기화 완료");
    }

    private void setupClickListeners() {
        if (btnRefreshData != null) {
            btnRefreshData.setOnClickListener(view -> {
                btnRefreshData.setEnabled(false);
                btnRefreshData.setText("업데이트 중...");
                refreshDataFromGitHub();
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * GitHub에서 데이터 새로고침 후 상태 업데이트
     */
    private void refreshDataFromGitHub() {
        new Thread(() -> {
            try {
                android.util.Log.d("DataStatusActivity", "GitHub에서 데이터 새로고침 시작...");

                // GitHub에서 최신 데이터 가져오기
                boolean updated = csvUpdateManager.forceUpdateCsvFile();

                runOnUiThread(() -> {
                    btnRefreshData.setEnabled(true);
                    btnRefreshData.setText("데이터 새로고침");

                    if (updated) {
                        Toast.makeText(this, "GitHub에서 최신 데이터를 가져왔습니다", Toast.LENGTH_SHORT).show();
                        android.util.Log.d("DataStatusActivity", "GitHub 데이터 업데이트 성공");
                    } else {
                        Toast.makeText(this, "이미 최신 데이터입니다", Toast.LENGTH_SHORT).show();
                        android.util.Log.d("DataStatusActivity", "데이터가 이미 최신 상태");
                    }

                    // 업데이트 후 상태 다시 로드
                    loadDataStatus();
                });

            } catch (Exception e) {
                android.util.Log.e("DataStatusActivity", "GitHub 데이터 새로고침 실패", e);

                runOnUiThread(() -> {
                    btnRefreshData.setEnabled(true);
                    btnRefreshData.setText("데이터 새로고침");
                    Toast.makeText(this, "데이터 새로고침 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 데이터 상태 정보 로드 및 표시 (GitHub 업데이트된 데이터 사용)
     */
    private void loadDataStatus() {
        try {
            android.util.Log.d("DataStatusActivity", "데이터 상태 로드 시작...");

            // GitHub에서 업데이트된 CSV 파일 사용
            File csvFile = csvUpdateManager.getCsvFile();
            FileInputStream fis = new FileInputStream(csvFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));

            String line;
            String firstLine = null;
            String lastLine = null;
            int lineCount = 0;

            // 헤더 스킵
            String header = reader.readLine();
            android.util.Log.d("DataStatusActivity", "CSV 헤더: " + header);

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                if (firstLine == null) {
                    firstLine = line;
                    android.util.Log.d("DataStatusActivity", "최신 데이터: " + firstLine);
                }
                lastLine = line;
                lineCount++;
            }
            reader.close();
            fis.close();

            android.util.Log.d("DataStatusActivity", "총 " + lineCount + "개 회차 데이터 확인");
            android.util.Log.d("DataStatusActivity", "가장 오래된 데이터: " + lastLine);

            if (firstLine != null && lastLine != null) {
                updateDataStatusViews(firstLine, lastLine, lineCount);

                // 파일 정보 표시
                long fileSize = csvFile.length();
                Date lastModified = new Date(csvFile.lastModified());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                android.util.Log.d("DataStatusActivity", "파일 크기: " + fileSize + " bytes, 마지막 수정: " + sdf.format(lastModified));

            } else {
                showDataError("CSV 파일에 데이터가 없습니다.");
            }

        } catch (Exception e) {
            android.util.Log.e("DataStatusActivity", "데이터 로드 실패", e);
            showDataError("데이터 로드 실패: " + e.getMessage());
        }
    }

    /**
     * 데이터 상태 뷰 업데이트
     */
    private void updateDataStatusViews(String firstLine, String lastLine, int totalCount) {
        try {
            String[] firstData = firstLine.split(",");
            String[] lastData = lastLine.split(",");

            int firstRound = Integer.parseInt(firstData[1].trim()); // drawNo (최신)
            int lastRound = Integer.parseInt(lastData[1].trim());   // drawNo (가장 오래된)
            String firstDate = firstData[2].trim();                // date (최신 날짜)
            int firstYear = Integer.parseInt(firstData[0].trim()); // year (최신 년도)
            int lastYear = Integer.parseInt(lastData[0].trim());   // year (가장 오래된 년도)

            android.util.Log.d("DataStatusActivity", "최신 회차: " + firstRound + ", 가장 오래된 회차: " + lastRound);

            // 데이터 범위 표시
            tvDataRange.setText(lastRound + "회 ~ " + firstRound + "회");

            // CSV 파싱용 (2025-08-09 형식)
            SimpleDateFormat csvSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date csvLatestDate = csvSdf.parse(firstDate);

            // 화면 표시용 (2025년 8월 9일 금요일 형식)
            SimpleDateFormat displaySdf = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.getDefault());

            // 최신 회차 표시
            tvLatestRound.setText("제 " + firstRound + "회 (" + displaySdf.format(csvLatestDate) + ")");

            // 다음 추첨일 계산 (현재 시점 기준)
            Calendar now = Calendar.getInstance();
            Calendar nextDrawDate = calculateNextDrawDate(now);

            // D-Day 계산
            long diffMillis = nextDrawDate.getTimeInMillis() - now.getTimeInMillis();
            int daysUntilNextDraw = (int) Math.max(0, diffMillis / (1000 * 60 * 60 * 24));

            tvNextUpdate.setText("D-" + daysUntilNextDraw + " (" + displaySdf.format(nextDrawDate.getTime()) + ")");

            // 상세 정보들
            tvTotalRecords.setText(totalCount + "개 회차");
            // 데이터 기간
            tvDataPeriod.setText(lastYear + "년 ~ " + firstYear + "년 (" + (firstYear - lastYear + 1) + "년간)");
            // AI 분석 상태
            tvAnalysisStatus.setText(firstRound + "회차까지 완료");

            // 마지막 확인 시간 및 3개월 자동 업데이트 정보 표시
            updateLastCheckedInfo();

            // 상태 표시 (수정된 로직 사용)
            updateDataStatusIndicator(csvLatestDate, daysUntilNextDraw, firstRound);

        } catch (Exception e) {
            android.util.Log.e("DataStatusActivity", "데이터 파싱 실패", e);
            showDataError("데이터 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * 현재 시점 기준 다음 추첨일 계산
     */
    private Calendar calculateNextDrawDate(Calendar now) {
        Calendar nextDraw = Calendar.getInstance();
        nextDraw.setTimeInMillis(now.getTimeInMillis());

        // 이번 주 토요일로 설정
        int daysUntilSaturday = Calendar.SATURDAY - now.get(Calendar.DAY_OF_WEEK);
        if (daysUntilSaturday <= 0) {
            daysUntilSaturday += 7; // 다음 주 토요일
        }

        nextDraw.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);
        nextDraw.set(Calendar.HOUR_OF_DAY, 20);
        nextDraw.set(Calendar.MINUTE, 45);
        nextDraw.set(Calendar.SECOND, 0);
        nextDraw.set(Calendar.MILLISECOND, 0);

        // 만약 현재가 토요일이고 추첨 시간(20:45) 이후라면 다음 주로
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            if (now.get(Calendar.HOUR_OF_DAY) > 20 ||
                    (now.get(Calendar.HOUR_OF_DAY) == 20 && now.get(Calendar.MINUTE) >= 45)) {
                nextDraw.add(Calendar.WEEK_OF_YEAR, 1);
            }
        }

        return nextDraw;
    }

    /**
     * 마지막 확인 시간 및 자동 업데이트 정보 업데이트
     */
    private void updateLastCheckedInfo() {
        SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy년 M월 d일 HH:mm", Locale.getDefault());
        StringBuilder infoText = new StringBuilder();

        // 현재 확인 시간
        infoText.append("확인 시간: ").append(timeSdf.format(new Date()));

        // 3개월 자동 업데이트 정보 추가
        long lastAutoUpdate = csvUpdateManager.getLastAutoUpdateTime();
        int daysUntilNext = csvUpdateManager.getDaysUntilNextUpdate();

        if (lastAutoUpdate > 0) {
            // 마지막 자동 업데이트가 있었던 경우
            infoText.append("\n마지막 자동 업데이트: ").append(timeSdf.format(new Date(lastAutoUpdate)));

            if (daysUntilNext > 0) {
                infoText.append("\n다음 자동 업데이트: ").append(daysUntilNext).append("일 후");
            } else {
                infoText.append("\n자동 업데이트 예정 (3개월 주기)");
            }
        } else {
            // 첫 실행인 경우
            infoText.append("\n자동 업데이트: 3개월마다 실행");
        }

        // 파일 수정 시간도 추가
        long fileLastModified = csvUpdateManager.getLastUpdateTime();
        if (fileLastModified > 0) {
            infoText.append("\n파일 수정: ").append(timeSdf.format(new Date(fileLastModified)));
        }

        tvLastChecked.setText(infoText.toString());

        android.util.Log.d("DataStatusActivity", "업데이트 정보 - 마지막 자동: " +
                (lastAutoUpdate > 0 ? new Date(lastAutoUpdate) : "없음") +
                ", 다음까지: " + daysUntilNext + "일");
    }

    /**
     * 데이터 상태 인디케이터 업데이트 (수정된 로직)
     */
    private void updateDataStatusIndicator(Date csvLatestDate, int daysUntilNextDraw, int latestRound) {
        String statusText;

        // 현재 시점 기준으로 상태 판단
        Calendar now = Calendar.getInstance();
        Calendar csvDate = Calendar.getInstance();
        csvDate.setTime(csvLatestDate);

        // 이번 주 토요일 계산
        Calendar thisWeekSaturday = Calendar.getInstance();
        thisWeekSaturday.setTimeInMillis(now.getTimeInMillis());

        int daysUntilSaturday = Calendar.SATURDAY - now.get(Calendar.DAY_OF_WEEK);
        if (daysUntilSaturday < 0) {
            daysUntilSaturday += 7; // 일요일인 경우
        }
        thisWeekSaturday.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);

        // 토요일 추첨 시간 이후인지 확인
        boolean isAfterDrawTime = false;
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            if (now.get(Calendar.HOUR_OF_DAY) > 20 ||
                    (now.get(Calendar.HOUR_OF_DAY) == 20 && now.get(Calendar.MINUTE) >= 45)) {
                isAfterDrawTime = true;
            }
        }

        // CSV 최신 날짜와 현재 상황 비교
        long csvTimeMillis = csvDate.getTimeInMillis();
        long nowTimeMillis = now.getTimeInMillis();
        long daysDiff = (nowTimeMillis - csvTimeMillis) / (1000 * 60 * 60 * 24);

        android.util.Log.d("DataStatusActivity", "상태 판단 - CSV 날짜: " + csvLatestDate +
                ", 현재: " + now.getTime() + ", 일수 차이: " + daysDiff +
                ", 다음 추첨까지: " + daysUntilNextDraw + "일");

        // 현재 시점에서 예상되는 최신 회차와 CSV의 최신 회차 비교
        int expectedLatestRound = app.grapekim.smartlotto.util.LottoDrawCalculator.getCurrentExpectedDrawNumber();
        int availableLatestRound = app.grapekim.smartlotto.util.LottoDrawCalculator.getLatestAvailableDrawNumber();

        if (latestRound >= availableLatestRound && daysDiff <= 3) {
            // CSV가 실제 발표된 최신 회차와 동일하거나 최신
            statusText = "✅ 최신 상태";
            tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (latestRound >= availableLatestRound - 1 && daysDiff <= 7) {
            // CSV가 최신에서 1회차 정도 뒤처지지만 1주일 이내
            if (daysUntilNextDraw <= 1) {
                statusText = "🎯 곧 새 회차 발표";
                tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                statusText = "✅ 양호한 상태";
                tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        } else if (latestRound < availableLatestRound) {
            // CSV가 실제 발표된 회차보다 뒤처짐
            statusText = "🔄 업데이트 필요";
            tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else if (daysDiff > 7) {
            // CSV가 1주일 이상 오래됨
            statusText = "🔄 업데이트 필요";
            tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        } else {
            // 기본값: 양호한 상태
            statusText = "✅ 양호한 상태";
            tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }

        tvDataStatus.setText(statusText);

        android.util.Log.d("DataStatusActivity", "상태 결정: " + statusText +
                " (CSV: " + latestRound + "회, 예상: " + expectedLatestRound + "회, 실제최신: " + availableLatestRound + "회, 경과: " + daysDiff + "일)");
    }

    /**
     * 데이터 오류 표시
     */
    private void showDataError(String errorMessage) {
        tvDataRange.setText("데이터 로드 실패");
        tvLatestRound.setText(errorMessage);
        tvNextUpdate.setText("");
        tvTotalRecords.setText("-");
        tvDataPeriod.setText("-");
        tvAnalysisStatus.setText("-");

        // 에러 발생 시에도 현재 시간과 자동 업데이트 정보 표시
        updateLastCheckedInfo();

        tvDataStatus.setText("❌ 오류 발생");
        tvDataStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();

        android.util.Log.e("DataStatusActivity", "데이터 오류 표시: " + errorMessage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 리소스 정리
        csvUpdateManager = null;
    }
}