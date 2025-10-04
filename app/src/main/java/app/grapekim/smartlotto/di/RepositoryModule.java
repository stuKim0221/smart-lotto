package app.grapekim.smartlotto.di;

import android.content.Context;

import app.grapekim.smartlotto.data.repository.LottoDrawRepository;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;

/**
 * Repository 의존성 주입을 위한 모듈
 * 간단한 Factory 패턴을 사용하여 DI 구현
 */
public final class RepositoryModule {

    private static volatile LottoRepository lottoRepository;
    private static volatile LottoDrawRepository lottoDrawRepository;

    private RepositoryModule() {
        // 유틸리티 클래스이므로 인스턴스 생성 방지
    }

    /**
     * LottoRepository 싱글톤 인스턴스 제공
     * @param context Application Context
     * @return LottoRepository 인스턴스
     */
    public static LottoRepository provideLottoRepository(Context context) {
        if (lottoRepository == null) {
            synchronized (RepositoryModule.class) {
                if (lottoRepository == null) {
                    lottoRepository = new LottoRepositoryImpl(context.getApplicationContext());
                }
            }
        }
        return lottoRepository;
    }

    /**
     * LottoDrawRepository 싱글톤 인스턴스 제공
     * @param context Application Context
     * @return LottoDrawRepository 인스턴스
     */
    public static LottoDrawRepository provideLottoDrawRepository(Context context) {
        if (lottoDrawRepository == null) {
            synchronized (RepositoryModule.class) {
                if (lottoDrawRepository == null) {
                    lottoDrawRepository = new LottoDrawRepository(context.getApplicationContext());
                }
            }
        }
        return lottoDrawRepository;
    }

    /**
     * 테스트용: Repository 인스턴스 초기화
     * 단위 테스트에서 Mock 객체 주입을 위해 사용
     */
    public static void resetRepositories() {
        synchronized (RepositoryModule.class) {
            lottoRepository = null;
            lottoDrawRepository = null;
        }
    }

    /**
     * 테스트용: LottoRepository Mock 설정
     */
    public static void setLottoRepository(LottoRepository repository) {
        synchronized (RepositoryModule.class) {
            lottoRepository = repository;
        }
    }

    /**
     * 테스트용: LottoDrawRepository Mock 설정
     */
    public static void setLottoDrawRepository(LottoDrawRepository repository) {
        synchronized (RepositoryModule.class) {
            lottoDrawRepository = repository;
        }
    }
}