package app.grapekim.smartlotto.ui.period;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.grapekim.smartlotto.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

/**
 * 기간 선택을 위한 BottomSheet
 */
public class PeriodBottomSheet extends BottomSheetDialogFragment {

    public interface PeriodSelectionListener {
        void onPeriodSelected(String periodText, int periodValue);
    }

    private PeriodSelectionListener listener;

    private MaterialButton optRandom;
    private MaterialButton opt3;
    private MaterialButton opt5;
    private MaterialButton opt10;
    private MaterialButton opt20;
    private MaterialButton opt30;
    private MaterialButton opt50;

    public void setPeriodSelectionListener(PeriodSelectionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_period, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupClickListeners();
    }

    private void initViews(View view) {
        optRandom = view.findViewById(R.id.optRandom);
        opt3 = view.findViewById(R.id.opt3);
        opt5 = view.findViewById(R.id.opt5);
        opt10 = view.findViewById(R.id.opt10);
        opt20 = view.findViewById(R.id.opt20);
        opt30 = view.findViewById(R.id.opt30);
        opt50 = view.findViewById(R.id.opt50);
    }

    private void setupClickListeners() {
        optRandom.setOnClickListener(v -> selectPeriod("완전히 랜덤", 0));
        opt3.setOnClickListener(v -> selectPeriod("최근 3개 회차", 3));
        opt5.setOnClickListener(v -> selectPeriod("최근 5개 회차", 5));
        opt10.setOnClickListener(v -> selectPeriod("최근 10개 회차", 10));
        opt20.setOnClickListener(v -> selectPeriod("최근 20개 회차", 20));
        opt30.setOnClickListener(v -> selectPeriod("최근 30개 회차", 30));
        opt50.setOnClickListener(v -> selectPeriod("최근 50개 회차", 50));
    }

    private void selectPeriod(String text, int value) {
        if (listener != null) {
            listener.onPeriodSelected(text, value);
        }
        dismiss();
    }
}