package app.grapekim.smartlotto.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import app.grapekim.smartlotto.R;

public class MainButtonsAdapter extends RecyclerView.Adapter<MainButtonsAdapter.ButtonViewHolder> {

    public interface OnButtonClickListener {
        void onButtonClick(int position);
    }

    private final OnButtonClickListener listener;

    // 버튼 데이터
    private static final int[] ICONS = {
            R.drawable.ic_qrscan,
            R.drawable.ic_manual_touch,
            R.drawable.ic_ai_brain
    };

    private static final String[] TITLES = {
            "QR 당첨확인",
            "수동 선택",
            "AI 기반 번호 생성"
    };

    private static final String[] DESCRIPTIONS = {
            "QR 코드를 스캔하여\n당첨 여부를 확인하세요",
            "직접 번호를 선택하여\n로또 번호를 생성하세요",
            "과거 당첨 데이터를 분석해서\n추천 번호를 받아보세요"
    };

    public MainButtonsAdapter(OnButtonClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ButtonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_carousel_button, parent, false);
        return new ButtonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ButtonViewHolder holder, int position) {
        holder.icon.setImageResource(ICONS[position]);
        holder.title.setText(TITLES[position]);
        holder.description.setText(DESCRIPTIONS[position]);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onButtonClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return ICONS.length;
    }

    static class ButtonViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView description;

        ButtonViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivCarouselIcon);
            title = itemView.findViewById(R.id.tvCarouselTitle);
            description = itemView.findViewById(R.id.tvCarouselDescription);
        }
    }
}
