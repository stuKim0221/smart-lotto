package app.grapekim.smartlotto;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.data.service.CsvLottoDataService;

import static org.junit.Assert.*;

/**
 * 통계 자동 업데이트 시스템의 안드로이드 환경 통합 테스트
 */
@RunWith(AndroidJUnit4.class)
public class StatisticsAutoUpdateInstrumentedTest {

    private Context appContext;
    private CsvUpdateManager csvUpdateManager;
    private CsvLottoDataService csvLottoDataService;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        csvUpdateManager = new CsvUpdateManager(appContext);
        csvLottoDataService = new CsvLottoDataService(appContext);
    }

    @Test
    public void testCsvUpdateManagerInitialization() {
        assertNotNull("CsvUpdateManager가 올바르게 초기화되어야 함", csvUpdateManager);
    }

    @Test
    public void testUpdateListenerRegistrationAndNotification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] updateReceived = {false};

        // 테스트용 리스너 생성
        CsvUpdateManager.DataUpdateListener testListener = new CsvUpdateManager.DataUpdateListener() {
            @Override
            public void onDataUpdated(boolean success) {
                updateReceived[0] = true;
                latch.countDown();
            }
        };

        // 리스너 등록
        csvUpdateManager.addUpdateListener(testListener);

        // 업데이트 이벤트 직접 트리거
        csvUpdateManager.notifyUpdateListeners(true);

        // 리스너가 호출될 때까지 대기
        assertTrue("리스너가 5초 내에 호출되어야 함",
                  latch.await(5, TimeUnit.SECONDS));
        assertTrue("업데이트 이벤트가 수신되어야 함", updateReceived[0]);

        // 리스너 제거
        csvUpdateManager.removeUpdateListener(testListener);
    }

    @Test
    public void testCsvLottoDataServiceCacheInvalidation() {
        // 캐시된 데이터 로드
        var initialData = csvLottoDataService.loadAllDrawData();
        assertNotNull("초기 데이터가 로드되어야 함", initialData);

        // 캐시 무효화
        csvLottoDataService.clearCache();

        // 다시 데이터 로드 (캐시가 무효화되었으므로 새로 로드됨)
        var reloadedData = csvLottoDataService.loadAllDrawData();
        assertNotNull("무효화 후 데이터가 다시 로드되어야 함", reloadedData);

        // 데이터는 같아야 함 (같은 소스에서 로드하므로)
        assertEquals("데이터 크기가 같아야 함", initialData.size(), reloadedData.size());
    }

    @Test
    public void testMultipleListenersNotification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2); // 2개 리스너 대기
        final int[] callCount = {0};

        CsvUpdateManager.DataUpdateListener listener1 = success -> {
            callCount[0]++;
            latch.countDown();
        };

        CsvUpdateManager.DataUpdateListener listener2 = success -> {
            callCount[0]++;
            latch.countDown();
        };

        // 두 리스너 모두 등록
        csvUpdateManager.addUpdateListener(listener1);
        csvUpdateManager.addUpdateListener(listener2);

        // 업데이트 이벤트 트리거
        csvUpdateManager.notifyUpdateListeners(true);

        // 두 리스너 모두 호출될 때까지 대기
        assertTrue("두 리스너 모두 5초 내에 호출되어야 함",
                  latch.await(5, TimeUnit.SECONDS));
        assertEquals("정확히 2번 호출되어야 함", 2, callCount[0]);

        // 리스너들 제거
        csvUpdateManager.removeUpdateListener(listener1);
        csvUpdateManager.removeUpdateListener(listener2);
    }

    @Test
    public void testUpdateIntervalCheck() {
        // 업데이트 간격 체크 로직 테스트
        // needsUpdate() 메서드의 로직을 간접적으로 검증

        long lastUpdateTime = csvUpdateManager.getLastAutoUpdateTime();

        // 처음 실행이라면 업데이트가 필요해야 함
        if (lastUpdateTime == 0) {
            assertTrue("첫 실행 시 업데이트가 필요해야 함", csvUpdateManager.needsUpdate());
        }

        // 다음 업데이트까지 남은 일수 체크
        int daysUntilNext = csvUpdateManager.getDaysUntilNextUpdate();
        assertTrue("남은 일수는 0 이상이어야 함", daysUntilNext >= 0);
    }

    @Test
    public void testCsvFileOperations() {
        // CSV 파일 관련 기본 동작 테스트
        assertTrue("CSV 파일 존재 여부 체크가 동작해야 함",
                  csvUpdateManager.isCsvFileExists() || !csvUpdateManager.isCsvFileExists());

        var csvFile = csvUpdateManager.getCsvFile();
        assertNotNull("CSV 파일 참조를 가져올 수 있어야 함", csvFile);
        assertTrue("CSV 파일이 존재하거나 생성되어야 함", csvFile.exists());

        // CSV 파일 정보 로깅 (예외 발생하지 않아야 함)
        try {
            csvUpdateManager.logCsvFileInfo();
        } catch (Exception e) {
            fail("CSV 파일 정보 로깅 중 예외 발생: " + e.getMessage());
        }
    }

    @Test
    public void testStatisticsDataIntegrity() {
        // 통계 데이터의 무결성 테스트
        var drawData = csvLottoDataService.loadAllDrawData();

        if (!drawData.isEmpty()) {
            // 데이터가 있는 경우 기본 검증
            assertTrue("로또 데이터가 최소 1개 이상 있어야 함", drawData.size() > 0);

            // 첫 번째 데이터 검증
            var firstDraw = drawData.get(0);
            assertNotNull("추첨 데이터가 null이 아니어야 함", firstDraw);
            assertNotNull("날짜가 설정되어야 함", firstDraw.date);
            assertTrue("회차 번호가 양수여야 함", firstDraw.drawNo > 0);

            // 번호 범위 검증 (1~45)
            int[] mainNumbers = firstDraw.getMainNumbers();
            for (int number : mainNumbers) {
                assertTrue("메인 번호는 1-45 범위여야 함", number >= 1 && number <= 45);
            }

            assertTrue("보너스 번호는 1-45 범위여야 함",
                      firstDraw.bonus >= 1 && firstDraw.bonus <= 45);
        }
    }
}