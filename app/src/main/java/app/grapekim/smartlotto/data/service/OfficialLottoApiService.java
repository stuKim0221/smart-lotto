package app.grapekim.smartlotto.data.service;

import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.grapekim.smartlotto.data.model.LottoDrawData;
import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import app.grapekim.smartlotto.util.LottoDrawCalculator;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 공식 동행복권 API에서 최신 로또 데이터를 가져오는 서비스
 * GitHub CSV가 업데이트되지 않았을 때의 대체 데이터 소스
 */
public class OfficialLottoApiService {
    private static final String TAG = "OfficialLottoApiService";

    // 공식 동행복권 API URL
    private static final String API_BASE_URL = "https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=";

    private final OkHttpClient client;
    private final Gson gson;

    public OfficialLottoApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * 특정 회차의 로또 데이터를 공식 API에서 가져오기
     * @param drawNo 회차 번호
     * @return 로또 추첨 데이터, 실패 시 null
     */
    public LottoDrawData getDrawData(int drawNo) {
        String url = API_BASE_URL + drawNo;

        Log.d(TAG, "공식 API에서 " + drawNo + "회차 데이터 요청: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "SmartLotto-Android/1.0")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonResponse = response.body().string();
                Log.d(TAG, "API 응답 받음: " + jsonResponse.substring(0, Math.min(200, jsonResponse.length())));

                LottoDrawDto dto = gson.fromJson(jsonResponse, LottoDrawDto.class);

                if (dto != null && dto.isSuccess() && dto.drwNo != null) {
                    // 날짜 형식 변환 (YYYY-MM-DD)
                    String formattedDate = formatDate(dto.date);

                    LottoDrawData drawData = new LottoDrawData(
                            2025, // 현재 년도로 고정 (또는 날짜에서 추출)
                            dto.drwNo,
                            formattedDate,
                            dto.n1, dto.n2, dto.n3, dto.n4, dto.n5, dto.n6,
                            dto.bonus
                    );

                    Log.i(TAG, "공식 API에서 " + drawNo + "회차 데이터 획득 성공: " + drawData.toString());
                    return drawData;
                } else {
                    Log.w(TAG, "API 응답은 받았지만 데이터가 유효하지 않음");
                }
            } else {
                Log.e(TAG, "API 요청 실패: HTTP " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "API 요청 중 네트워크 오류", e);
        } catch (Exception e) {
            Log.e(TAG, "API 응답 파싱 오류", e);
        }

        return null;
    }

    /**
     * 최근 N회차의 누락된 로또 데이터를 가져오기
     * @param lastKnownDrawNo 마지막으로 알려진 회차 번호
     * @param maxDrawsToFetch 최대 가져올 회차 수
     * @return 누락된 회차들의 데이터 리스트
     */
    public List<LottoDrawData> getMissingDraws(int lastKnownDrawNo, int maxDrawsToFetch) {
        List<LottoDrawData> missingDraws = new ArrayList<>();

        Log.i(TAG, "누락된 회차 확인 시작 - 마지막 알려진 회차: " + lastKnownDrawNo);

        // 최대 5회차까지만 확인 (과도한 API 호출 방지)
        int maxChecks = Math.min(maxDrawsToFetch, 5);

        for (int drawNo = lastKnownDrawNo + 1; drawNo <= lastKnownDrawNo + maxChecks; drawNo++) {
            Log.d(TAG, drawNo + "회차 확인 중...");

            LottoDrawData drawData = getDrawData(drawNo);
            if (drawData != null) {
                missingDraws.add(drawData);
                Log.i(TAG, "누락된 " + drawNo + "회차 발견 및 추가");
            } else {
                Log.d(TAG, drawNo + "회차는 아직 추첨되지 않았거나 데이터가 없음");
                break; // 연속된 회차가 없으면 중단
            }
        }

        Log.i(TAG, "누락된 회차 확인 완료 - 총 " + missingDraws.size() + "개 회차 발견");
        return missingDraws;
    }

    /**
     * 특정 회차의 수동 데이터를 반환 (공식 사이트에서 확인된 데이터)
     * API가 작동하지 않을 때의 범용적인 대체 방안
     * @param drawNo 회차 번호
     * @return 해당 회차의 로또 데이터, 없으면 null
     */
    public LottoDrawData getManualDrawData(int drawNo) {
        Log.i(TAG, "수동으로 " + drawNo + "회차 데이터 제공 시도");

        // 알려진 수동 데이터들 (공식 사이트에서 확인된 데이터)
        switch (drawNo) {
            case 1189:
                Log.i(TAG, "✅ 1189회차 수동 데이터 제공");
                return new LottoDrawData(
                        2025, 1189, "2025-09-13",
                        9, 19, 29, 35, 37, 38, 31
                );

            case 1190:
                // 1190회차 발표 시 이곳에 데이터 추가 (하지만 이제 자동으로 처리됨)
                Log.d(TAG, "1190회차 수동 데이터 미준비 - 공식 API 우선 시도 필요");
                break;

            case 1191:
                // 1191회차 발표 시 이곳에 데이터 추가 (하지만 이제 자동으로 처리됨)
                Log.d(TAG, "1191회차 수동 데이터 미준비 - 공식 API 우선 시도 필요");
                break;

            default:
                Log.d(TAG, drawNo + "회차에 대한 수동 데이터 없음");
                break;
        }

        return null; // 해당 회차의 수동 데이터가 없음
    }

    /**
     * @deprecated 이 메서드는 더 이상 사용되지 않습니다. getManualDrawData(1189)를 사용하세요.
     */
    @Deprecated
    public LottoDrawData getDraw1189Manual() {
        Log.w(TAG, "⚠️ getDraw1189Manual()은 deprecated입니다. getManualDrawData(1189)를 사용하세요.");
        return getManualDrawData(1189);
    }

    /**
     * 날짜 문자열을 YYYY-MM-DD 형식으로 변환
     * @param dateString 원본 날짜 문자열
     * @return 형식화된 날짜 문자열
     */
    private String formatDate(String dateString) {
        if (dateString == null) {
            return "2025-09-14"; // 기본값
        }

        // 이미 YYYY-MM-DD 형식이면 그대로 반환
        if (dateString.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return dateString;
        }

        // 다른 형식이면 파싱해서 변환 (여기서는 간단하게 처리)
        // 실제로는 SimpleDateFormat 등을 사용해야 함
        return "2025-09-14"; // 임시로 고정값 반환
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}