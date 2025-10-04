package app.grapekim.smartlotto.ui.favorites;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import app.grapekim.smartlotto.R;

import java.util.List;

public class NumberStatisticsAdapter extends RecyclerView.Adapter<NumberStatisticsAdapter.ViewHolder> {

    private List<FavoritesFragment.NumberStat> numbers;
    private boolean isHotNumbers; // true면 Hot Numbers, false면 Cold Numbers

    public NumberStatisticsAdapter(List<FavoritesFragment.NumberStat> numbers, boolean isHotNumbers) {
        this.numbers = numbers;
        this.isHotNumbers = isHotNumbers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_number_stat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoritesFragment.NumberStat numberStat = numbers.get(position);

        // 번호 설정
        holder.tvNumber.setText(String.valueOf(numberStat.number));

        // 빈도 설정
        holder.tvFrequency.setText(numberStat.frequency + "회");

        // Hot/Cold에 따른 색상 설정
        if (isHotNumbers) {
            // Hot Numbers - 빨간색 계열
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FF5722"));
            holder.tvNumber.setTextColor(Color.WHITE);
            holder.tvFrequency.setTextColor(Color.WHITE);
        } else {
            // Cold Numbers - 파란색 계열
            holder.cardView.setCardBackgroundColor(Color.parseColor("#2196F3"));
            holder.tvNumber.setTextColor(Color.WHITE);
            holder.tvFrequency.setTextColor(Color.WHITE);
        }
    }

    @Override
    public int getItemCount() {
        return numbers.size();
    }

    public void updateData(List<FavoritesFragment.NumberStat> newNumbers) {
        this.numbers = newNumbers;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvNumber;
        TextView tvFrequency;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvFrequency = itemView.findViewById(R.id.tvFrequency);
        }
    }
}