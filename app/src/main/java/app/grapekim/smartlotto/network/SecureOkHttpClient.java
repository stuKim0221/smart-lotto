package app.grapekim.smartlotto.network;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Certificate Pinning이 적용된 보안 강화 OkHttp 클라이언트
 */
public class SecureOkHttpClient {
    private static OkHttpClient instance;

    public static synchronized OkHttpClient getInstance() {
        if (instance == null) {
            instance = createSecureClient();
        }
        return instance;
    }

    private static OkHttpClient createSecureClient() {
        // Certificate Pinning 설정
        CertificatePinner certificatePinner = new CertificatePinner.Builder()
                // 동행복권 API - 서버 인증서 (주 핀)
                .add("www.dhlottery.co.kr", "sha256/v2c6lS+SqjfStoziorajyvN+1YhbFhBNIIllcVL8WWo=")
                .add("dhlottery.co.kr", "sha256/v2c6lS+SqjfStoziorajyvN+1YhbFhBNIIllcVL8WWo=")

                // Sectigo 중간 CA (백업용)
                .add("www.dhlottery.co.kr", "sha256/RkhWTcfJAQN/YxOR12VkPo+PhmIoSfWd/JVkg44einY=")
                .add("dhlottery.co.kr", "sha256/RkhWTcfJAQN/YxOR12VkPo+PhmIoSfWd/JVkg44einY=")

                // USERTrust 루트 CA (추가 백업용)
                .add("www.dhlottery.co.kr", "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4=")
                .add("dhlottery.co.kr", "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4=")

                .build();

        // HTTP 로깅 (프로덕션용 최소 레벨)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);

        return new OkHttpClient.Builder()
                .certificatePinner(certificatePinner)
                .addInterceptor(loggingInterceptor)

                // 타임아웃 설정
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)

                // 재시도 정책
                .retryOnConnectionFailure(true)

                .build();
    }
}