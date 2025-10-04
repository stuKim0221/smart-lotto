package app.grapekim.smartlotto.ui.base;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import app.grapekim.smartlotto.util.SafeUtils;

/**
 * 모든 Fragment의 기본 클래스
 * 공통 기능과 유틸리티 메서드들을 제공합니다.
 */
public abstract class BaseFragment<VM extends ViewModel> extends Fragment {

    protected VM viewModel;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            initializeViewModel();
            initializeViews(view);
            setupClickListeners();
            observeViewModel();
        } catch (Exception e) {
            handleError("화면 초기화 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * ViewModel 초기화 (하위 클래스에서 구현)
     */
    protected abstract void initializeViewModel();

    /**
     * View 초기화 (하위 클래스에서 구현)
     */
    protected abstract void initializeViews(@NonNull View view);

    /**
     * 클릭 리스너 설정 (하위 클래스에서 구현)
     */
    protected abstract void setupClickListeners();

    /**
     * ViewModel 관찰 설정 (하위 클래스에서 구현)
     */
    protected abstract void observeViewModel();

    /**
     * ViewModel 클래스 반환 (하위 클래스에서 구현)
     */
    protected abstract Class<VM> getViewModelClass();

    /**
     * ViewModelProvider를 사용한 ViewModel 생성
     */
    protected VM createViewModel() {
        return new ViewModelProvider(this).get(getViewModelClass());
    }

    /**
     * 안전한 토스트 표시
     */
    protected void showToast(String message) {
        SafeUtils.safeToast(getContext(), message);
    }

    /**
     * 에러 처리
     */
    protected void handleError(String message, Exception e) {
        showToast(message);
        // 로그 출력 (실제 앱에서는 로깅 라이브러리 사용 권장)
        if (e != null) {
            e.printStackTrace();
        }
    }

    /**
     * 에러 처리 (예외 없이)
     */
    protected void handleError(String message) {
        handleError(message, null);
    }

    /**
     * Fragment가 활성 상태인지 확인
     */
    protected boolean isFragmentActive() {
        return isAdded() && !isRemoving() && !isDetached() && getActivity() != null;
    }
}