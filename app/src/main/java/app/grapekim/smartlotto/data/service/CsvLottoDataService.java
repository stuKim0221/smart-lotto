package app.grapekim.smartlotto.data.service;

import android.content.Context;
import android.util.Log;

import app.grapekim.smartlotto.data.model.LottoDrawData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * assets/draw_kor.csv 파일을 읽고 파싱하는 서비스
 */
public class CsvLottoDataService {

    private static final String TAG = "CsvLottoDataService";
    private static final String CSV_FILE_NAME = "draw_kor.csv";

    private final Context context;
    private List<LottoDrawData> cachedData;

    // 디버깅용 카운터
    private static int debugCount = 0;

    public CsvLottoDataService(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 전체 로또 데이터를 로드
     */
    public List<LottoDrawData> loadAllDrawData() {
        if (cachedData == null) {
            cachedData = parseCSVFromAssets();
        }
        return new ArrayList<>(cachedData);
    }

    /**
     * 기간별 로또 데이터를 로드
     * @param periodType 0=전체, 1=최근1년, 2=최근6개월, 3=최근3개월
     */
    public List<LottoDrawData> loadDrawDataByPeriod(int periodType) {
        List<LottoDrawData> allData = loadAllDrawData();

        Log.d(TAG, String.format("Total loaded data: %d entries", allData.size()));

        // 최신/최고 회차 정보 로깅
        if (!allData.isEmpty()) {
            LottoDrawData latest = allData.get(0);
            LottoDrawData oldest = allData.get(allData.size() - 1);
            Log.i(TAG, String.format("CSV 데이터 범위: %d회차(%s) ~ %d회차(%s)",
                oldest.drawNo, oldest.date,
                latest.drawNo, latest.date));
        }

        if (periodType == 0) {
            Log.d(TAG, "Returning all data (period = 0)");
            return allData; // 전체 기간
        }

        // 현재 날짜 기준으로 필터링
        Calendar calendar = Calendar.getInstance();
        Log.d(TAG, String.format("Current date: %04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)));

        switch (periodType) {
            case 1: // 최근 1년
                calendar.add(Calendar.YEAR, -1);
                break;
            case 2: // 최근 6개월
                calendar.add(Calendar.MONTH, -6);
                break;
            case 3: // 최근 3개월
                calendar.add(Calendar.MONTH, -3);
                break;
            default:
                return allData;
        }

        String cutoffDate = String.format("%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));

        Log.d(TAG, String.format("Cutoff date for period %d: %s", periodType, cutoffDate));

        List<LottoDrawData> filteredData = new ArrayList<>();
        int includedCount = 0;
        int excludedCount = 0;

        for (LottoDrawData data : allData) {
            if (data.date != null && data.date.compareTo(cutoffDate) >= 0) {
                filteredData.add(data);
                includedCount++;
            } else {
                excludedCount++;
                // 처음 몇 개의 제외된 데이터 로그
                if (excludedCount <= 3) {
                    Log.d(TAG, String.format("Excluded data: %s (date: %s)", data.toString(), data.date));
                }
            }
        }

        Log.d(TAG, String.format("Filtering result - Included: %d, Excluded: %d",
                includedCount, excludedCount));

        return filteredData;
    }

    /**
     * Assets에서 CSV 파일을 읽고 파싱
     */
    private List<LottoDrawData> parseCSVFromAssets() {
        List<LottoDrawData> dataList = new ArrayList<>();

        try (InputStream inputStream = context.getAssets().open(CSV_FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;
            int successCount = 0;
            int failCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (isFirstLine) {
                    Log.d(TAG, "CSV Header: " + line); // 헤더 확인
                    isFirstLine = false;
                    continue; // 헤더 라인 스킵
                }

                LottoDrawData data = parseCSVLine(line);
                if (data != null) {
                    dataList.add(data);
                    successCount++;

                    // 처음 5개와 마지막 5개 데이터 로그
                    if (successCount <= 5) {
                        Log.d(TAG, String.format("Parsed data #%d: %s", successCount, data.toString()));
                    }
                } else {
                    failCount++;
                    Log.w(TAG, String.format("Failed to parse line %d: %s", lineNumber, line));
                }
            }

            Log.d(TAG, String.format("CSV Parsing Summary - Success: %d, Failed: %d, Total lines: %d",
                    successCount, failCount, lineNumber));

            // 날짜 범위 확인
            if (!dataList.isEmpty()) {
                String firstDate = dataList.get(0).date;
                String lastDate = dataList.get(dataList.size() - 1).date;
                Log.d(TAG, String.format("Date range: %s to %s", firstDate, lastDate));
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading CSV file from assets", e);
        }

        return dataList;
    }

    /**
     * CSV 한 라인을 파싱해서 LottoDrawData 객체로 변환
     */
    private LottoDrawData parseCSVLine(String line) {
        try {
            String[] parts = line.split(",");

            // 수정: 10개 컬럼 확인 (year,drawNo,date,n1,n2,n3,n4,n5,n6,bonus)
            if (parts.length < 10) {
                Log.w(TAG, String.format("Invalid CSV line (expected 10 columns, got %d): %s",
                        parts.length, line));
                return null;
            }

            // year 처리 (첫 번째 컬럼이 비어있을 수 있음)
            int year = 0;
            if (!parts[0].trim().isEmpty()) {
                year = Integer.parseInt(parts[0].trim());
            }

            int drawNo = Integer.parseInt(parts[1].trim());
            String date = parts[2].trim();
            int n1 = Integer.parseInt(parts[3].trim());
            int n2 = Integer.parseInt(parts[4].trim());
            int n3 = Integer.parseInt(parts[5].trim());
            int n4 = Integer.parseInt(parts[6].trim());
            int n5 = Integer.parseInt(parts[7].trim());
            int n6 = Integer.parseInt(parts[8].trim());
            int bonus = Integer.parseInt(parts[9].trim());

            // 디버깅: 파싱된 데이터 로그 (처음 5개만)
            if (debugCount < 5) {
                Log.d(TAG, String.format("Parsed: drawNo=%d, date=%s, numbers=[%d,%d,%d,%d,%d,%d], bonus=%d",
                        drawNo, date, n1, n2, n3, n4, n5, n6, bonus));
                debugCount++;
            }

            return new LottoDrawData(year, drawNo, date, n1, n2, n3, n4, n5, n6, bonus);

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.w(TAG, "Error parsing CSV line: " + line, e);

            // 디버깅: 실패한 라인의 parts 정보 출력
            try {
                String[] parts = line.split(",");
                Log.d(TAG, String.format("Failed line parts count: %d, content: %s",
                        parts.length, java.util.Arrays.toString(parts)));
            } catch (Exception ex) {
                Log.e(TAG, "Error in debug parsing", ex);
            }

            return null;
        }
    }

    /**
     * 캐시된 데이터 초기화 (새로운 데이터 업데이트 시 사용)
     */
    public void clearCache() {
        cachedData = null;
        debugCount = 0; // 디버그 카운터도 리셋
        Log.d(TAG, "CSV data cache cleared");
    }

    /**
     * 로드된 데이터 개수 반환
     */
    public int getDataCount() {
        return loadAllDrawData().size();
    }

    /**
     * 최신 회차 번호 반환
     */
    public int getLatestDrawNo() {
        List<LottoDrawData> data = loadAllDrawData();
        if (data.isEmpty()) {
            return 0;
        }

        int maxDrawNo = 0;
        for (LottoDrawData draw : data) {
            if (draw.drawNo > maxDrawNo) {
                maxDrawNo = draw.drawNo;
            }
        }

        Log.d(TAG, String.format("Latest draw number: %d", maxDrawNo));
        return maxDrawNo;
    }
}