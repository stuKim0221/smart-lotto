package app.grapekim.smartlotto.data.remote.dto;

import com.google.gson.annotations.SerializedName;

public class LottoDrawDto {
    @SerializedName("returnValue") public String returnValue; // "success" | "fail"
    @SerializedName("drwNo") public Integer drwNo;

    @SerializedName("drwtNo1") public Integer n1;
    @SerializedName("drwtNo2") public Integer n2;
    @SerializedName("drwtNo3") public Integer n3;
    @SerializedName("drwtNo4") public Integer n4;
    @SerializedName("drwtNo5") public Integer n5;
    @SerializedName("drwtNo6") public Integer n6;
    @SerializedName("bnusNo")  public Integer bonus;

    @SerializedName("drwNoDate") public String date; // "YYYY-MM-DD"

    public boolean isSuccess() { return "success".equalsIgnoreCase(returnValue); }
}
