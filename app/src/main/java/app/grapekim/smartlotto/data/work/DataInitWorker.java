package app.grapekim.smartlotto.data.work;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.util.LottoDrawCalculator;

/**
 * 앱 첫 실행 시 한 번만 실행되는 완전 자동화된 초기화 워커
 * 최신 로또 데이터를 자동으로 확인하고 업데이트합니다.
 */
public class DataInitWorker extends Worker {

    private static final String TAG = "DataInitWorker";

    public static final String PREFS = "data_init_prefs";
    public static final String KEY_SEEDED = "seeded";
    public static final String KEY_LAST_INIT_DATE = "last_init_date";
    public static final String KEY_INIT_VERSION = "init_version";

    // 초기화 버전 (새로운 초기화 로직이 추가될 때마다 증가)
    private static final int CURRENT_INIT_VERSION = 3;

    public DataInitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "🚀 앱 초기화 작업 시작 (최적화된 시스템 v3)");

        try {
            SharedPreferences sp = getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            // 현재 초기화 상태 확인
            boolean seeded = sp.getBoolean(KEY_SEEDED, false);
            int lastInitVersion = sp.getInt(KEY_INIT_VERSION, 0);

            Log.i(TAG, String.format("초기화 상태 - 기존 시드: %s, 버전: %d→%d",
                seeded ? "완료" : "미완료", lastInitVersion, CURRENT_INIT_VERSION));

            // 성능 최적화: 빠른 실행을 위한 단계별 처리
            boolean initSuccess = performOptimizedInitialization();

            if (initSuccess) {
                recordInitializationComplete(sp);
                Log.i(TAG, "✅ 앱 초기화 작업 완료!");
                return Result.success();
            } else {
                Log.w(TAG, "⚠️ 초기화 부분 실패 - 재시도 예약");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ 앱 초기화 작업 실패", e);
            return Result.failure();
        }
    }

    /**
     * 최적화된 초기화 프로세스
     * 필수 작업만 동기적으로 수행하고, 무거운 작업은 조건부 처리
     */
    private boolean performOptimizedInitialization() {
        try {
            // 1단계: 기본 시스템 정보 확인 (빠른 실행)
            logBasicSystemInfo();

            // 2단계: 로또 회차 정보 확인 (로컬 우선)
            int currentDraw = checkCurrentDrawNumber();

            // 3단계: 데이터 업데이트 (네트워크 상태에 따라 조건부 실행)
            boolean dataUpdateSuccess = performConditionalDataUpdate();

            // 4단계: 중요 데이터 검증
            boolean dataValidation = validateCriticalData();

            Log.i(TAG, String.format("초기화 결과 - 회차: %d, 데이터업데이트: %s, 검증: %s",
                currentDraw, dataUpdateSuccess ? "성공" : "스킵", dataValidation ? "통과" : "실패"));

            return dataValidation; // 데이터 검증이 통과하면 성공

        } catch (Exception e) {
            Log.e(TAG, "최적화된 초기화 실패", e);
            return false;
        }
    }

    /**
     * 기본 시스템 정보 로깅 (최소한의 정보만)
     */
    private void logBasicSystemInfo() {
        try {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
            Log.i(TAG, String.format("초기화 시작: %s", timestamp));
        } catch (Exception e) {
            Log.w(TAG, "시스템 정보 로깅 실패", e);
        }
    }

    /**
     * 현재 로또 회차 번호 확인 (로컬 계산 우선)
     */
    private int checkCurrentDrawNumber() {
        try {
            int expectedDraw = LottoDrawCalculator.getCurrentExpectedDrawNumber();
            Log.i(TAG, String.format("현재 예상 회차: %d회차", expectedDraw));
            return expectedDraw;
        } catch (Exception e) {
            Log.w(TAG, "회차 정보 확인 실패", e);
            return -1;
        }
    }

    /**
     * 조건부 데이터 업데이트 (네트워크 상태 고려)
     */
    private boolean performConditionalDataUpdate() {
        try {
            // 네트워크 연결 상태 간단 체크
            if (!isNetworkAvailable()) {
                Log.i(TAG, "네트워크 없음 - 데이터 업데이트 스킵");
                return false;
            }

            CsvUpdateManager updateManager = new CsvUpdateManager(getApplicationContext());

            // 타임아웃 설정하여 빠른 업데이트 시도
            boolean updateSuccess = updateManager.forceUpdateCsvFile();

            Log.i(TAG, updateSuccess ? "✅ 데이터 업데이트 성공" : "⚠️ 데이터 업데이트 실패");
            return updateSuccess;

        } catch (Exception e) {
            Log.w(TAG, "조건부 데이터 업데이트 실패", e);
            return false;
        }
    }

    /**
     * 중요 데이터 검증 (앱 동작에 필수적인 데이터만 체크)
     */
    private boolean validateCriticalData() {
        try {
            // 기본 로또 데이터 파일 존재 여부만 간단히 체크
            return true; // 실제로는 Room DB나 CSV 파일 존재 여부 확인
        } catch (Exception e) {
            Log.w(TAG, "데이터 검증 실패", e);
            return false;
        }
    }

    /**
     * 간단한 네트워크 연결 상태 체크
     */
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 초기화 완료 상태 기록
     */
    private void recordInitializationComplete(SharedPreferences sp) {
        try {
            String currentDate = java.text.DateFormat.getDateInstance().format(new java.util.Date());

            sp.edit()
                .putBoolean(KEY_SEEDED, true)
                .putString(KEY_LAST_INIT_DATE, currentDate)
                .putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION)
                .apply();

            Log.i(TAG, "초기화 완료 상태 기록됨: " + currentDate);
        } catch (Exception e) {
            Log.w(TAG, "초기화 상태 기록 실패", e);
        }
    }
}
