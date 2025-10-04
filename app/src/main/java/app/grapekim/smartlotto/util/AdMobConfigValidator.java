package app.grapekim.smartlotto.util;

import android.content.Context;
import android.util.Log;

import app.grapekim.smartlotto.BuildConfig;

/**
 * AdMob 설정이 올바르게 구성되어 있는지 검증하는 유틸리티 클래스
 */
public class AdMobConfigValidator {
    private static final String TAG = "AdMobConfigValidator";

    // Google의 공식 테스트 AdMob ID들
    private static final String GOOGLE_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    private static final String GOOGLE_TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String GOOGLE_TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917";

    // 실제 프로덕션 AdMob ID들 (사용자 ID)
    private static final String PROD_APP_ID = "ca-app-pub-5416988082526174~7214365675";
    private static final String PROD_INTERSTITIAL_ID = "ca-app-pub-5416988082526174/7410355538";
    private static final String PROD_REWARDED_ID = "ca-app-pub-5416988082526174/9714188894";

    /**
     * 현재 빌드 타입에 따른 AdMob 설정 검증
     */
    public static void validateAdMobConfig(Context context) {
        Log.i(TAG, "========== AdMob 설정 검증 시작 ==========");

        // 빌드 타입 확인
        boolean isDebugBuild = BuildConfig.DEBUG;
        String buildType = isDebugBuild ? "DEBUG" : "RELEASE";

        Log.i(TAG, "빌드 타입: " + buildType);
        Log.i(TAG, "앱 패키지: " + context.getPackageName());

        // 현재 사용 중인 AdMob ID들 출력
        Log.i(TAG, "현재 AdMob 설정:");
        Log.i(TAG, "  App ID: " + BuildConfig.ADMOB_APP_ID);
        Log.i(TAG, "  Interstitial ID: " + BuildConfig.ADMOB_INTERSTITIAL_ID);
        Log.i(TAG, "  Rewarded ID: " + BuildConfig.ADMOB_REWARDED_ID);

        // 설정 검증
        if (isDebugBuild) {
            validateDebugConfig();
        } else {
            validateReleaseConfig();
        }

        // ID 형식 유효성 검사
        validateIdFormat();

        Log.i(TAG, "========== AdMob 설정 검증 완료 ==========");
    }

    /**
     * 디버그 빌드 설정 검증
     */
    private static void validateDebugConfig() {
        Log.i(TAG, "디버그 빌드 설정 검증 중...");

        boolean appIdValid = GOOGLE_TEST_APP_ID.equals(BuildConfig.ADMOB_APP_ID);
        boolean interstitialIdValid = GOOGLE_TEST_INTERSTITIAL_ID.equals(BuildConfig.ADMOB_INTERSTITIAL_ID);
        boolean rewardedIdValid = GOOGLE_TEST_REWARDED_ID.equals(BuildConfig.ADMOB_REWARDED_ID);

        Log.i(TAG, "App ID 검증: " + (appIdValid ? "✅ 올바른 테스트 ID" : "❌ 잘못된 ID"));
        Log.i(TAG, "Interstitial ID 검증: " + (interstitialIdValid ? "✅ 올바른 테스트 ID" : "❌ 잘못된 ID"));
        Log.i(TAG, "Rewarded ID 검증: " + (rewardedIdValid ? "✅ 올바른 테스트 ID" : "❌ 잘못된 ID"));

        if (appIdValid && interstitialIdValid && rewardedIdValid) {
            Log.i(TAG, "🎉 디버그 빌드 AdMob 설정이 완벽합니다!");
        } else {
            Log.w(TAG, "⚠️ 디버그 빌드에서 테스트 ID가 아닌 ID가 사용되고 있습니다.");
        }
    }

    /**
     * 릴리즈 빌드 설정 검증
     */
    private static void validateReleaseConfig() {
        Log.i(TAG, "릴리즈 빌드 설정 검증 중...");

        boolean appIdValid = PROD_APP_ID.equals(BuildConfig.ADMOB_APP_ID);
        boolean interstitialIdValid = PROD_INTERSTITIAL_ID.equals(BuildConfig.ADMOB_INTERSTITIAL_ID);
        boolean rewardedIdValid = PROD_REWARDED_ID.equals(BuildConfig.ADMOB_REWARDED_ID);

        // 테스트 ID가 사용되고 있는지도 확인 (실수 방지)
        boolean usingTestIds =
            GOOGLE_TEST_APP_ID.equals(BuildConfig.ADMOB_APP_ID) ||
            GOOGLE_TEST_INTERSTITIAL_ID.equals(BuildConfig.ADMOB_INTERSTITIAL_ID) ||
            GOOGLE_TEST_REWARDED_ID.equals(BuildConfig.ADMOB_REWARDED_ID);

        Log.i(TAG, "App ID 검증: " + (appIdValid ? "✅ 올바른 프로덕션 ID" : "❌ 잘못된 ID"));
        Log.i(TAG, "Interstitial ID 검증: " + (interstitialIdValid ? "✅ 올바른 프로덕션 ID" : "❌ 잘못된 ID"));
        Log.i(TAG, "Rewarded ID 검증: " + (rewardedIdValid ? "✅ 올바른 프로덕션 ID" : "❌ 잘못된 ID"));

        if (usingTestIds) {
            Log.e(TAG, "🚨 릴리즈 빌드에서 테스트 ID가 사용되고 있습니다! 즉시 수정하세요!");
        } else if (appIdValid && interstitialIdValid && rewardedIdValid) {
            Log.i(TAG, "🎉 릴리즈 빌드 AdMob 설정이 완벽합니다!");
        } else {
            Log.w(TAG, "⚠️ 일부 AdMob ID가 예상과 다릅니다. 설정을 확인하세요.");
        }
    }

    /**
     * AdMob ID 형식 유효성 검사
     */
    private static void validateIdFormat() {
        Log.i(TAG, "AdMob ID 형식 검증 중...");

        // App ID 형식: ca-app-pub-xxxxxxxxxxxxxxxx~xxxxxxxxxx
        boolean appIdFormatValid = BuildConfig.ADMOB_APP_ID.matches("ca-app-pub-\\d{16}~\\d{10}");

        // Ad Unit ID 형식: ca-app-pub-xxxxxxxxxxxxxxxx/xxxxxxxxxx
        boolean interstitialFormatValid = BuildConfig.ADMOB_INTERSTITIAL_ID.matches("ca-app-pub-\\d{16}/\\d{10}");
        boolean rewardedFormatValid = BuildConfig.ADMOB_REWARDED_ID.matches("ca-app-pub-\\d{16}/\\d{10}");

        Log.i(TAG, "App ID 형식: " + (appIdFormatValid ? "✅ 유효" : "❌ 무효"));
        Log.i(TAG, "Interstitial ID 형식: " + (interstitialFormatValid ? "✅ 유효" : "❌ 무효"));
        Log.i(TAG, "Rewarded ID 형식: " + (rewardedFormatValid ? "✅ 유효" : "❌ 무효"));

        if (!(appIdFormatValid && interstitialFormatValid && rewardedFormatValid)) {
            Log.e(TAG, "⚠️ 일부 AdMob ID의 형식이 올바르지 않습니다!");
        }
    }

    /**
     * AdMob 설정 요약 정보 출력 (간단 버전)
     */
    public static void logAdMobSummary() {
        String buildType = BuildConfig.DEBUG ? "DEBUG (테스트)" : "RELEASE (프로덕션)";
        String expectedType = BuildConfig.DEBUG ? "Google 테스트 ID" : "사용자 프로덕션 ID";

        Log.i(TAG, String.format("📱 AdMob 설정 요약: %s 빌드에서 %s 사용 중", buildType, expectedType));
    }
}