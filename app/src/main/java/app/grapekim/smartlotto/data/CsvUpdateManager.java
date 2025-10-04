package app.grapekim.smartlotto.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import app.grapekim.smartlotto.data.model.LottoDrawData;
import app.grapekim.smartlotto.data.service.OfficialLottoApiService;
import app.grapekim.smartlotto.util.LottoDrawCalculator;

public class CsvUpdateManager {
    private static final String TAG = "CsvUpdateManager";
    private static final String CSV_URL = "https://raw.githubusercontent.com/stuKim0221/smart-lotto/refs/heads/main/draw_kor.csv";
    private static final String CSV_FILE_NAME = "draw_kor.csv";

    // 자동 업데이트 관련 상수 (매일 확인으로 변경)
    private static final String PREFS_NAME = "csv_update_prefs";
    private static final String KEY_LAST_UPDATE = "csv_last_update";
    private static final long UPDATE_INTERVAL = 1 * 24 * 60 * 60 * 1000L; // 1일 (24시간)

    private Context context;
    private OkHttpClient client;

    // 데이터 업데이트 리스너들
    private final List<DataUpdateListener> updateListeners = new ArrayList<>();

    /**
     * 데이터 업데이트 이벤트를 받기 위한 인터페이스
     */
    public interface DataUpdateListener {
        void onDataUpdated(boolean success);
    }

    public CsvUpdateManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /**
     * 데이터 업데이트 리스너 등록
     */
    public void addUpdateListener(DataUpdateListener listener) {
        if (!updateListeners.contains(listener)) {
            updateListeners.add(listener);
            Log.d(TAG, "Added update listener. Total listeners: " + updateListeners.size());
        }
    }

    /**
     * 데이터 업데이트 리스너 제거
     */
    public void removeUpdateListener(DataUpdateListener listener) {
        updateListeners.remove(listener);
        Log.d(TAG, "Removed update listener. Total listeners: " + updateListeners.size());
    }

    /**
     * 등록된 모든 리스너에게 업데이트 이벤트 통지
     */
    public void notifyUpdateListeners(boolean success) {
        Log.i(TAG, "========== 데이터 업데이트 이벤트 통지 시작 ==========");
        Log.i(TAG, "등록된 리스너 수: " + updateListeners.size());
        Log.i(TAG, "업데이트 성공 여부: " + success);

        int notifiedCount = 0;
        int errorCount = 0;

        for (DataUpdateListener listener : updateListeners) {
            try {
                Log.d(TAG, "리스너 #" + (notifiedCount + 1) + " 통지 중: " + listener.getClass().getSimpleName());
                listener.onDataUpdated(success);
                notifiedCount++;
                Log.d(TAG, "리스너 #" + notifiedCount + " 통지 완료");
            } catch (Exception e) {
                errorCount++;
                Log.e(TAG, "리스너 통지 중 오류 #" + errorCount + ": " + e.getMessage(), e);
            }
        }

        Log.i(TAG, "통지 완료 - 성공: " + notifiedCount + ", 실패: " + errorCount);
        Log.i(TAG, "========== 데이터 업데이트 이벤트 통지 완료 ==========");
    }

    /**
     * 공식 API에서 누락된 회차 데이터를 가져와서 CSV에 추가
     * @return 누락된 데이터가 추가되었는지 여부
     */
    private boolean addMissingDrawsFromOfficialAPI() {
        try {
            // 현재 CSV 파일에서 최신 회차 번호 확인
            int lastDrawNo = getLastDrawNumberFromCsv();

            if (lastDrawNo <= 0) {
                Log.w(TAG, "CSV에서 유효한 회차 번호를 찾을 수 없음");
                return false;
            }

            Log.i(TAG, "현재 CSV 최신 회차: " + lastDrawNo);

            // 공식 API 서비스 초기화
            OfficialLottoApiService apiService = new OfficialLottoApiService();

            // 자동으로 현재 발표된 최신 회차까지 확인
            int latestAvailableDraw = LottoDrawCalculator.getLatestAvailableDrawNumber();

            Log.i(TAG, String.format("자동 회차 확인 - CSV 최신: %d회차, 발표된 최신: %d회차",
                lastDrawNo, latestAvailableDraw));

            boolean anyAdded = false;

            // 누락된 모든 회차를 자동으로 처리
            for (int drawNo = lastDrawNo + 1; drawNo <= latestAvailableDraw; drawNo++) {
                Log.i(TAG, drawNo + "회차가 누락됨, API에서 자동 가져오기 시도...");

                // 1. 먼저 공식 API에서 시도
                LottoDrawData drawData = apiService.getDrawData(drawNo);

                // 2. 공식 API 실패 시 수동 데이터 시도
                if (drawData == null) {
                    Log.w(TAG, drawNo + "회차 공식 API 실패, 수동 데이터 시도...");
                    drawData = apiService.getManualDrawData(drawNo);
                }

                // 3. 데이터 획득 성공 시 CSV에 추가
                if (drawData != null) {
                    boolean added = appendDrawToCsv(drawData);
                    if (added) {
                        Log.i(TAG, "✅ " + drawNo + "회차 데이터 추가 성공!");
                        anyAdded = true;
                    } else {
                        Log.e(TAG, "❌ " + drawNo + "회차 CSV 추가 실패");
                        break; // 추가 실패하면 중단
                    }
                } else {
                    Log.d(TAG, drawNo + "회차 데이터를 가져올 수 없음 (아직 발표되지 않았거나 API 오류)");
                    break; // 데이터가 없으면 중단
                }
            }

            if (anyAdded) {
                Log.i(TAG, "🎉 누락된 회차들이 자동으로 추가되었습니다!");
            } else {
                Log.d(TAG, "공식 API에서 추가 누락된 회차를 찾지 못함");
            }

            apiService.shutdown();
            return false;

        } catch (Exception e) {
            Log.e(TAG, "공식 API에서 누락된 회차 추가 중 오류", e);
            return false;
        }
    }

    /**
     * CSV 파일에서 가장 최신 회차 번호 추출
     * @return 최신 회차 번호, 오류 시 0
     */
    private int getLastDrawNumberFromCsv() {
        File csvFile = getCsvFile();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            String firstDataLine = null;

            // 헤더 스킵하고 첫 번째 데이터 라인 찾기
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("year,drawNo,date") && !line.trim().isEmpty()) {
                    firstDataLine = line;
                    break;
                }
            }

            if (firstDataLine != null) {
                String[] parts = firstDataLine.split(",");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1].trim()); // drawNo
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "CSV에서 최신 회차 번호 추출 중 오류", e);
        }

        return 0;
    }

    /**
     * 새로운 회차 데이터를 CSV 파일 맨 위에 추가
     * @param drawData 추가할 회차 데이터
     * @return 추가 성공 여부
     */
    private boolean appendDrawToCsv(LottoDrawData drawData) {
        File csvFile = getCsvFile();

        try {
            // 기존 CSV 내용 읽기
            List<String> existingLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    existingLines.add(line);
                }
            }

            // 새로운 데이터 라인 생성
            String newDataLine = String.format("%d,%d,%s,%d,%d,%d,%d,%d,%d,%d",
                    drawData.year, drawData.drawNo, drawData.date,
                    drawData.n1, drawData.n2, drawData.n3, drawData.n4, drawData.n5, drawData.n6,
                    drawData.bonus);

            // 새로운 내용으로 파일 다시 쓰기 (헤더 다음에 새 데이터 삽입)
            try (FileWriter writer = new FileWriter(csvFile)) {
                // 헤더 쓰기
                if (!existingLines.isEmpty()) {
                    writer.write(existingLines.get(0) + "\n");
                }

                // 새로운 데이터 쓰기
                writer.write(newDataLine + "\n");

                // 기존 데이터 쓰기 (헤더 제외)
                for (int i = 1; i < existingLines.size(); i++) {
                    writer.write(existingLines.get(i) + "\n");
                }
            }

            Log.d(TAG, "CSV에 새 회차 데이터 추가: " + newDataLine);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "CSV에 데이터 추가 중 오류", e);
            return false;
        }
    }

    // ==================== 3개월 자동 업데이트 기능 ====================

    /**
     * 업데이트가 필요한지 확인 (테스트를 위해 항상 true 반환으로 임시 수정)
     */
    public boolean needsUpdate() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0);
        long now = System.currentTimeMillis();

        boolean normalCheck = (now - lastUpdate) > UPDATE_INTERVAL;

        Log.i(TAG, "Update check - Last update: " + new java.util.Date(lastUpdate) +
                ", Hours ago: " + ((now - lastUpdate) / (60 * 60 * 1000L)) +
                ", Normal check: " + normalCheck +
                ", FORCING UPDATE FOR TESTING: true");

        // 테스트를 위해 항상 업데이트 필요로 반환
        return true;
    }

    /**
     * 필요시 백그라운드에서 자동 업데이트 수행
     */
    public void checkAndUpdateIfNeeded() {
        if (!needsUpdate()) {
            Log.d(TAG, "Auto-update not needed yet");
            return;
        }

        Log.d(TAG, "Starting auto-update (weekly interval)");

        // 백그라운드 스레드에서 업데이트 실행
        new Thread(() -> {
            try {
                boolean success = updateCsvFile();

                if (success) {
                    // 업데이트 성공 시 시간 저장
                    saveLastUpdateTime();
                    Log.i(TAG, "Auto-update completed successfully");
                } else {
                    Log.w(TAG, "Auto-update failed, will retry next time");
                }

                // 리스너들에게 업데이트 결과 통지
                notifyUpdateListeners(success);

            } catch (Exception e) {
                Log.e(TAG, "Error during auto-update", e);
            }
        }).start();
    }

    /**
     * 마지막 업데이트 시간을 SharedPreferences에 저장
     */
    private void saveLastUpdateTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        prefs.edit()
                .putLong(KEY_LAST_UPDATE, now)
                .apply();

        Log.d(TAG, "Saved last update time: " + new java.util.Date(now));
    }

    /**
     * SharedPreferences에서 마지막 업데이트 시간 반환
     */
    public long getLastAutoUpdateTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_UPDATE, 0);
    }

    /**
     * 다음 자동 업데이트까지 남은 일수 계산
     */
    public int getDaysUntilNextUpdate() {
        if (!needsUpdate()) {
            long lastUpdate = getLastAutoUpdateTime();
            long nextUpdate = lastUpdate + UPDATE_INTERVAL;
            long now = System.currentTimeMillis();
            long remainingMs = nextUpdate - now;

            return (int) Math.max(0, remainingMs / (24 * 60 * 60 * 1000L));
        }
        return 0; // 업데이트가 필요한 상태면 0일 반환
    }

    // ==================== 기존 업데이트 기능들 ====================

    // GitHub에서 CSV 파일 업데이트 후 누락된 회차 보완
    public boolean updateCsvFile() {
        boolean csvUpdateSuccess = updateCsvFileFromGitHub();

        if (csvUpdateSuccess) {
            Log.i(TAG, "GitHub CSV 업데이트 성공, 누락된 회차 확인 시작...");
            boolean missingDataAdded = addMissingDrawsFromOfficialAPI();

            if (missingDataAdded) {
                Log.i(TAG, "누락된 회차 보완 완료!");
            } else {
                Log.d(TAG, "추가할 누락된 회차가 없음");
            }
        }

        return csvUpdateSuccess;
    }

    // GitHub에서 CSV 파일 업데이트 (기존 메서드명 변경)
    private boolean updateCsvFileFromGitHub() {
        try {
            Log.d(TAG, "Updating CSV file from GitHub (cache-busting enabled)...");

            // 캐시 무효화를 위한 타임스탬프 추가
            String cacheBustUrl = CSV_URL + "?t=" + System.currentTimeMillis() + "&r=" + Math.random();

            Request request = new Request.Builder()
                    .url(cacheBustUrl)
                    // 강력한 캐시 무효화 헤더들
                    .addHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                    .addHeader("Pragma", "no-cache")
                    .addHeader("Expires", "0")
                    .addHeader("If-Modified-Since", "Thu, 01 Jan 1970 00:00:00 GMT")
                    .addHeader("If-None-Match", "\"\"")
                    // User-Agent 추가로 더 확실한 요청
                    .addHeader("User-Agent", "SmartLotto-Android/1.0")
                    .build();

            Log.d(TAG, "Request URL: " + cacheBustUrl);

            Response response = client.newCall(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                String csvData = response.body().string();

                // 데이터 유효성 기본 검증
                if (csvData.length() < 100 || !csvData.contains("year,drawNo,date")) {
                    Log.e(TAG, "Invalid CSV data received. Size: " + csvData.length());
                    return false;
                }

                // 내부 저장소에 저장
                File file = new File(context.getFilesDir(), CSV_FILE_NAME);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(csvData.getBytes("UTF-8"));
                fos.close();

                Log.d(TAG, "CSV file updated successfully. Size: " + csvData.length());
                Log.d(TAG, "File saved to: " + file.getAbsolutePath());

                // 첫 몇 줄 로그로 확인
                String[] lines = csvData.split("\n");
                Log.d(TAG, "CSV Header: " + lines[0]);
                if (lines.length > 1) {
                    Log.d(TAG, "CSV First Data: " + lines[1]);
                }
                if (lines.length > 2) {
                    Log.d(TAG, "CSV Second Data: " + lines[2]);
                }

                return true;
            } else {
                Log.e(TAG, "Failed to fetch CSV file. Response code: " + response.code());
                if (response.body() != null) {
                    String errorBody = response.body().string();
                    Log.e(TAG, "Error response body: " + errorBody.substring(0, Math.min(200, errorBody.length())));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to update CSV file", e);
        }

        return false;
    }

    // CSV 파일 반환 (없으면 assets에서 복사)
    public File getCsvFile() {
        File file = new File(context.getFilesDir(), CSV_FILE_NAME);

        // 파일이 없으면 assets에서 복사
        if (!file.exists()) {
            Log.d(TAG, "CSV file not found in internal storage. Copying from assets...");
            copyFromAssets();
        } else {
            Log.d(TAG, "Using existing CSV file: " + file.getAbsolutePath() +
                    ", Size: " + file.length() + " bytes, Last modified: " +
                    new java.util.Date(file.lastModified()));
        }

        return file;
    }

    // Assets에서 CSV 파일 복사 (초기 설정 또는 fallback)
    private boolean copyFromAssets() {
        try {
            InputStream inputStream = context.getAssets().open(CSV_FILE_NAME);
            File outputFile = new File(context.getFilesDir(), CSV_FILE_NAME);

            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            Log.d(TAG, "CSV file copied from assets successfully. Size: " + outputFile.length());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy CSV file from assets", e);
            return false;
        }
    }

    // CSV 파일이 존재하는지 확인
    public boolean isCsvFileExists() {
        File file = new File(context.getFilesDir(), CSV_FILE_NAME);
        return file.exists();
    }

    // 마지막 업데이트 시간 반환 (파일 수정 시간 기준)
    public long getLastUpdateTime() {
        File file = new File(context.getFilesDir(), CSV_FILE_NAME);
        if (file.exists()) {
            return file.lastModified();
        }
        return 0;
    }

    // 강제 업데이트 (캐시 무시하고 즉시 GitHub에서 가져오기)
    public boolean forceUpdateCsvFile() {
        Log.d(TAG, "Force updating CSV file from GitHub...");

        // 기존 파일 백업
        File currentFile = new File(context.getFilesDir(), CSV_FILE_NAME);
        File backupFile = null;

        if (currentFile.exists()) {
            backupFile = new File(context.getFilesDir(), CSV_FILE_NAME + ".backup");
            try {
                java.nio.file.Files.copy(currentFile.toPath(), backupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d(TAG, "Created backup file");
            } catch (Exception e) {
                Log.w(TAG, "Failed to create backup", e);
            }
        }

        // 강제 업데이트 시도
        boolean success = updateCsvFile();

        if (success) {
            // 수동 업데이트 성공 시에도 시간 저장
            saveLastUpdateTime();
            Log.d(TAG, "Manual update completed, saved timestamp");
        } else if (backupFile != null && backupFile.exists()) {
            // 실패 시 백업 파일 복원
            try {
                java.nio.file.Files.copy(backupFile.toPath(), currentFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d(TAG, "Restored backup file after update failure");
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore backup", e);
            }
        }

        // 백업 파일 정리
        if (backupFile != null && backupFile.exists()) {
            backupFile.delete();
        }

        // 리스너들에게 업데이트 결과 통지
        notifyUpdateListeners(success);

        return success;
    }

    // CSV 파일 정보 로그
    public void logCsvFileInfo() {
        File file = getCsvFile();
        Log.d(TAG, "=== CSV File Info ===");
        Log.d(TAG, "Path: " + file.getAbsolutePath());
        Log.d(TAG, "Exists: " + file.exists());
        Log.d(TAG, "Size: " + file.length() + " bytes");
        Log.d(TAG, "Last Modified: " + new java.util.Date(file.lastModified()));

        // 3개월 자동 업데이트 정보 추가
        long lastAutoUpdate = getLastAutoUpdateTime();
        if (lastAutoUpdate > 0) {
            Log.d(TAG, "Last Auto Update: " + new java.util.Date(lastAutoUpdate));
            Log.d(TAG, "Days Until Next Update: " + getDaysUntilNextUpdate());
        } else {
            Log.d(TAG, "No auto-update history");
        }

        if (file.exists()) {
            try {
                // 첫 몇 줄 읽어서 로그 출력
                java.nio.file.Path path = file.toPath();
                java.util.List<String> lines = java.nio.file.Files.readAllLines(path,
                        java.nio.charset.StandardCharsets.UTF_8);

                Log.d(TAG, "Total lines: " + lines.size());
                if (lines.size() > 0) {
                    Log.d(TAG, "Header: " + lines.get(0));
                }
                if (lines.size() > 1) {
                    Log.d(TAG, "First data: " + lines.get(1));
                }
                if (lines.size() > 2) {
                    Log.d(TAG, "Second data: " + lines.get(2));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read file info", e);
            }
        }
        Log.d(TAG, "==================");
    }
}