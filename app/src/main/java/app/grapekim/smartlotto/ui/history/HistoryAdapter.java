package app.grapekim.smartlotto.ui.history;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 이력 목록 어댑터
 * - preAnnounceIds: 발표 전 항목을 미리 표시/비활성화
 * - 결과 확인 버튼 클릭 시: row + 버튼 View 를 콜백으로 전달
 * - 결과 확인 완료된 항목은 등수 표시
 * - 🔧 추가: 최신 회차 정보로 미래 회차 안전장치
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private static final String TAG = "HistoryAdapter";

    public interface ToggleFavListener { void onToggle(GeneratedPickEntity row); }
    public interface DeleteListener { void onDelete(GeneratedPickEntity row); }
    public interface CheckResultListener { void onCheck(GeneratedPickEntity row, View buttonView); }
    public interface AnalyzeListener { void onAnalyze(GeneratedPickEntity row); }

    private final ToggleFavListener toggleFavListener;
    private final DeleteListener deleteListener;
    private final CheckResultListener checkResultListener;
    private final AnalyzeListener analyzeListener;

    private final List<GeneratedPickEntity> items = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    /** 발표 전으로 표시할 항목 id 집합 */
    private final Set<Long> preAnnounceIds = new HashSet<>();

    /** 🔧 추가: 최신 회차 정보 (안전장치) */
    private Integer latestDrawNumber = null;

    public HistoryAdapter(ToggleFavListener fav, DeleteListener del, CheckResultListener check, AnalyzeListener analyze) {
        this.toggleFavListener = fav;
        this.deleteListener = del;
        this.checkResultListener = check;
        this.analyzeListener = analyze;
    }

    public void submit(List<GeneratedPickEntity> list) {
        items.clear();
        if (list != null) items.addAll(list);

        // 디버깅: AI 관련 항목들 로그 출력
        for (GeneratedPickEntity item : items) {
            if ("AI".equals(item.method) || "수정".equals(item.method)) {
                Log.d(TAG, "AI/수정 항목 발견 - ID: " + item.id +
                        ", method: " + item.method +
                        ", title: " + item.title);
            }
        }

        notifyDataSetChanged();
    }

    /** 발표 전 항목 id 집합을 통째로 교체 */
    public void setPreAnnounce(Set<Long> ids) {
        preAnnounceIds.clear();
        if (ids != null) preAnnounceIds.addAll(ids);
        notifyDataSetChanged();
    }

    /** 🔧 추가: 최신 회차 정보 설정 (안전장치) */
    public void setLatestDrawNumber(Integer latestDrawNumber) {
        this.latestDrawNumber = latestDrawNumber;
        Log.d(TAG, "최신 회차 정보 업데이트: " + latestDrawNumber);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_generated_pick, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GeneratedPickEntity e = items.get(position);

        // 날짜 설정
        h.tvDate.setText(fmt.format(e.createdAt));

        // 제목 설정 (더 안전하게)
        String displayTitle = getDisplayTitle(e);
        h.tvTitle.setText(displayTitle);

        // 디버깅 로그
        if ("AI".equals(e.method) || "수정".equals(e.method)) {
            Log.d(TAG, "AI 항목 바인딩 - position: " + position +
                    ", method: " + e.method +
                    ", title: " + e.title +
                    ", displayTitle: " + displayTitle);
        }

        // 번호 설정
        h.tvNumbers.setText(e.numbersCsv != null ? e.numbersCsv : "");

        // 즐겨찾기
        h.btnFav.setImageResource(e.favorite
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        h.btnFav.setOnClickListener(v -> toggleFavListener.onToggle(e));

        // 번호 분석 기능 (실제 분석 다이얼로그 호출)
        h.btnAnalyze.setOnClickListener(v -> {
            if (analyzeListener != null) {
                analyzeListener.onAnalyze(e);
            }
        });

        // 삭제
        h.btnDelete.setOnClickListener(v -> deleteListener.onDelete(e));

        // ==================== 🔧 수정된 결과 확인 버튼 상태 처리 ====================

        // 1. 결과 확인 완료된 항목인지 체크
        if (e.resultChecked) {
            // 결과 확인 완료 → 등수 표시, 클릭 비활성화
            h.btnCheckResult.setEnabled(false);
            h.btnCheckResult.setText(getRankText(e.resultRank, e.resultMatchCount));
            h.btnCheckResult.setAlpha(0.8f);
            h.btnCheckResult.setOnClickListener(null);
            return;
        }

        // 2. 🔧 추가: 최신 회차 기준 미래 회차 체크 (안전장치)
        if (latestDrawNumber != null && e.targetRound != null && e.targetRound > latestDrawNumber) {
            h.btnCheckResult.setEnabled(false);
            h.btnCheckResult.setText("발표 전 (미래 회차)");
            h.btnCheckResult.setAlpha(0.6f);
            h.btnCheckResult.setOnClickListener(null);
            Log.d(TAG, "안전장치 작동: ID=" + e.id + ", 목표회차=" + e.targetRound + " > 최신회차=" + latestDrawNumber);
            return;
        }

        // 3. 발표 전 항목인지 체크 (기존 로직)
        boolean preAnnounce = preAnnounceIds.contains(e.id);
        if (preAnnounce) {
            h.btnCheckResult.setEnabled(false);
            h.btnCheckResult.setText(R.string.not_announced_short); // "발표 전"
            h.btnCheckResult.setAlpha(0.6f);
            h.btnCheckResult.setOnClickListener(null);
            return;
        }

        // 4. 결과 확인 가능한 상태
        h.btnCheckResult.setEnabled(true);
        h.btnCheckResult.setText(R.string.check_result); // "결과 확인"
        h.btnCheckResult.setAlpha(1f);
        h.btnCheckResult.setOnClickListener(v -> checkResultListener.onCheck(e, h.btnCheckResult));
    }

    /**
     * 안전한 제목 생성
     */
    private String getDisplayTitle(GeneratedPickEntity e) {
        // 1. title이 있으면 그대로 사용
        if (e.title != null && !e.title.trim().isEmpty()) {
            return e.title;
        }

        // 2. title이 없으면 method 기반으로 생성
        if (e.method != null) {
            String prefix;
            switch (e.method) {
                case "AUTO":
                    prefix = "[자동]";
                    break;
                case "MANUAL":
                    prefix = "[수동]";
                    break;
                case "AI":
                    prefix = "[AI]";
                    break;
                case "QR":
                    prefix = "[QR]";
                    break;
                default:
                    prefix = "[" + e.method + "]";
                    break;
            }

            // 간단한 제목 생성 (회차 정보가 있으면 사용, 없으면 기본)
            if (e.targetRound != null && e.targetRound > 0) {
                return prefix + " " + e.targetRound + "회차 예상 로또번호";
            } else {
                return prefix + " 로또번호";
            }
        }

        // 3. 최후의 fallback
        return "로또번호";
    }

    /**
     * 등수에 따른 표시 텍스트 반환
     * @param rank 등수 (1~5=등수, -1=낙첨, 0=미확인)
     * @param matchCount 맞춘 개수
     * @return 표시할 텍스트
     */
    private String getRankText(int rank, int matchCount) {
        switch (rank) {
            case 1:
                return "1등 (" + matchCount + "개)";
            case 2:
                return "2등 (" + matchCount + "개)";
            case 3:
                return "3등 (" + matchCount + "개)";
            case 4:
                return "4등 (" + matchCount + "개)";
            case 5:
                return "5등 (" + matchCount + "개)";
            case -1:
                return "낙첨 (" + matchCount + "개)";
            default:
                return "결과 확인"; // 예외 상황
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvTitle, tvNumbers;
        ImageButton btnFav, btnDelete;
        MaterialButton btnCheckResult, btnAnalyze;

        VH(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvNumbers = v.findViewById(R.id.tvNumbers);
            btnFav = v.findViewById(R.id.btnFav);
            btnAnalyze = v.findViewById(R.id.btnAnalyze);
            btnDelete = v.findViewById(R.id.btnDelete);
            btnCheckResult = v.findViewById(R.id.btnCheckResult);
        }
    }
}