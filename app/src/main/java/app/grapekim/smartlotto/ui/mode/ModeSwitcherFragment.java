package app.grapekim.smartlotto.ui.mode;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import app.grapekim.smartlotto.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

/**
 * 자동/수동 모드 전환을 관리하는 Fragment
 */
public class ModeSwitcherFragment extends Fragment {

    private MaterialButtonToggleGroup toggleMode;
    private MaterialButton btnAuto;
    private MaterialButton btnManual;

    private AutoModeFragment autoModeFragment;
    private ManualModeFragment manualModeFragment;
    private Fragment currentFragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mode_switcher, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        initFragments();
        setupToggleListener();

        // 기본으로 자동 모드 표시
        showAutoMode();
    }

    private void initViews(View view) {
        toggleMode = view.findViewById(R.id.toggleMode);
        btnAuto = view.findViewById(R.id.btnAuto);
        btnManual = view.findViewById(R.id.btnManual);
    }

    private void initFragments() {
        autoModeFragment = new AutoModeFragment();
        manualModeFragment = new ManualModeFragment();
    }

    private void setupToggleListener() {
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return; // 해제 시에는 무시

            if (checkedId == R.id.btnAuto) {
                showAutoMode();
            } else if (checkedId == R.id.btnManual) {
                showManualMode();
            }
        });
    }

    private void showAutoMode() {
        switchToFragment(autoModeFragment);
    }

    private void showManualMode() {
        switchToFragment(manualModeFragment);
    }

    private void switchToFragment(Fragment fragment) {
        if (currentFragment == fragment) return;

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        // 현재 Fragment 숨기기
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }

        // 새 Fragment 추가 또는 표시
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.modeContainer, fragment);
        }

        // 애니메이션 설정 (선택사항)
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        transaction.commit();
        currentFragment = fragment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Fragment들이 자동으로 정리되므로 특별한 cleanup 불필요
    }
}