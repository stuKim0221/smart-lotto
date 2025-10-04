package app.grapekim.smartlotto.data.remote;

import app.grapekim.smartlotto.network.SecureOkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class NetworkProvider {
    private static volatile LottoApi API;
    private NetworkProvider(){}

    public static LottoApi api() {
        if (API == null) {
            synchronized (NetworkProvider.class) {
                if (API == null) {
                    // 모든 빌드에서 보안 강화된 OkHttp 클라이언트 사용
                    Retrofit rt = new Retrofit.Builder()
                            .baseUrl("https://www.dhlottery.co.kr/")
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(SecureOkHttpClient.getInstance())
                            .build();

                    API = rt.create(LottoApi.class);
                }
            }
        }
        return API;
    }
}