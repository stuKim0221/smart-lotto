package app.grapekim.smartlotto.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import app.grapekim.smartlotto.data.remote.dto.LottoDrawDto;
import app.grapekim.smartlotto.data.repository.LottoDrawRepository;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.di.RepositoryModule;
import app.grapekim.smartlotto.util.ExecutorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HomeFragment를 위한 ViewModel (DI 적용)
 * UI 로직과 비즈니스 로직을 분리하여 관리합니다.
 */
public class HomeViewModel extends AndroidViewModel {

    // Repository들 (DI를 통해 주입)
    private final LottoRepository lottoRepository;
    private final LottoDrawRepository drawRepository;

    // 백그라운드 작업용
    private final ExecutorService backgroundExecutor;

    // LiveData들 - UI 상태 관리
    private final MutableLiveData<LottoDrawDto> latestDraw = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<List<Integer>> generatedNumbers = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);

        // DI를 통한 Repository 주입
        lottoRepository = RepositoryModule.provideLottoRepository(application);
        drawRepository = RepositoryModule.provideLottoDrawRepository(application);

        // ExecutorService 초기화
        backgroundExecutor = Executors.newSingleThreadExecutor();

        // 초기 데이터 로드
        loadInitialData();
    }

    // ========================= LiveData Getters =========================

    /**
     * 최신 회차 정보 LiveData
     */
    public LiveData<LottoDrawDto> getLatestDraw() {
        return latestDraw;
    }

    /**
     * 로딩 상태 LiveData
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * 에러 메시지 LiveData
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * 생성된 번호 LiveData
     */
    public LiveData<List<Integer>> getGeneratedNumbers() {
        return generatedNumbers;
    }

    /**
     * 저장 성공 상태 LiveData
     */
    public LiveData<Boolean> getSaveSuccess() {
        return saveSuccess;
    }

    // ========================= Public Methods =========================

    /**
     * 초기 데이터 로드 (캐시 + 네트워크)
     */
    public void loadInitialData() {
        // 1. 캐시된 데이터 즉시 표시
        loadCachedData();

        // 2. 네트워크에서 최신 데이터 가져오기
        refreshLatestDraw();
    }

    /**
     * 최신 회차 데이터 새로고침
     */
    public void refreshLatestDraw() {
        isLoading.setValue(true);

        backgroundExecutor.execute(() -> {
            try {
                LottoDrawDto latest = drawRepository.fetchAndCacheLatest();

                if (latest != null) {
                    latestDraw.postValue(latest);
                    errorMessage.postValue(null); // 에러 클리어
                } else {
                    errorMessage.postValue("최신 회차 정보를 불러올 수 없습니다.");
                }
            } catch (Exception e) {
                String errorMsg = "네트워크 오류가 발생했습니다";
                if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
                    errorMsg += ": " + e.getMessage();
                }
                errorMessage.postValue(errorMsg);
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    /**
     * 자동 번호 생성
     */
    public void generateAutoNumbers() {
        backgroundExecutor.execute(() -> {
            try {
                List<Integer> numbers = generateRandomNumbers();
                generatedNumbers.postValue(numbers);

                // 에러 클리어 (성공 시)
                errorMessage.postValue(null);
            } catch (Exception e) {
                String errorMsg = "번호 생성 중 오류가 발생했습니다";
                if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
                    errorMsg += ": " + e.getMessage();
                }
                errorMessage.postValue(errorMsg);
            }
        });
    }

    /**
     * 생성된 번호 저장
     */
    public void saveGeneratedNumbers(List<Integer> numbers) {
        if (numbers == null || numbers.size() != 6) {
            errorMessage.setValue("유효하지 않은 번호입니다. (6개 번호가 필요합니다)");
            return;
        }

        // 번호 유효성 검사
        for (Integer num : numbers) {
            if (num == null || num < 1 || num > 45) {
                errorMessage.setValue("유효하지 않은 번호가 포함되어 있습니다. (1-45 범위)");
                return;
            }
        }

        backgroundExecutor.execute(() -> {
            try {
                long id = lottoRepository.saveAutoPick(new ArrayList<>(numbers));

                if (id > 0) {
                    saveSuccess.postValue(true);
                    errorMessage.postValue(null); // 에러 클리어
                } else {
                    errorMessage.postValue("저장에 실패했습니다.");
                }
            } catch (Exception e) {
                String errorMsg = "저장 중 오류가 발생했습니다";
                if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
                    errorMsg += ": " + e.getMessage();
                }
                errorMessage.postValue(errorMsg);
            }
        });
    }

    /**
     * 에러 메시지 클리어
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * 저장 성공 상태 클리어
     */
    public void clearSaveSuccess() {
        saveSuccess.setValue(false);
    }

    // ========================= Private Methods =========================

    /**
     * 캐시된 데이터 로드
     */
    private void loadCachedData() {
        backgroundExecutor.execute(() -> {
            try {
                LottoDrawDto cached = drawRepository.getCached();
                if (cached != null) {
                    latestDraw.postValue(cached);
                }
            } catch (Exception e) {
                // 캐시 로드 실패는 무시 (네트워크로 대체)
                // 로그만 출력하고 계속 진행
                System.err.println("캐시 로드 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 랜덤 번호 생성 (1-45 중 6개, 중복 없음, 정렬됨)
     */
    private List<Integer> generateRandomNumbers() {
        try {
            List<Integer> pool = new ArrayList<>(45);
            for (int i = 1; i <= 45; i++) {
                pool.add(i);
            }

            Collections.shuffle(pool, new Random());
            List<Integer> selected = new ArrayList<>(pool.subList(0, 6));
            Collections.sort(selected);

            return selected;
        } catch (Exception e) {
            // 생성 실패 시 기본 번호 반환
            List<Integer> fallback = new ArrayList<>();
            fallback.add(1); fallback.add(7); fallback.add(15);
            fallback.add(23); fallback.add(34); fallback.add(41);
            return fallback;
        }
    }

    // ========================= Lifecycle =========================

    @Override
    protected void onCleared() {
        super.onCleared();
        // ExecutorService 안전하게 종료
        ExecutorUtils.shutdownSafely(backgroundExecutor);
    }

    /*
     * 생성된 번호 클리어
     */
    public void clearGeneratedNumbers() {
        generatedNumbers.setValue(null);
    }
}