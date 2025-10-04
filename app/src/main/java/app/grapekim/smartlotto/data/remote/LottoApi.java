package app.grapekim.smartlotto.data.remote;

import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface LottoApi {
    // 예) /common.do?method=getLottoNumber&drwNo=1123
    @GET("common.do?method=getLottoNumber")
    Call<LottoDrawDto> getDraw(@Query("drwNo") int drawNo);
}
