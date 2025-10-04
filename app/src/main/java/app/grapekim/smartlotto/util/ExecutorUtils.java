package app.grapekim.smartlotto.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ExecutorService 안전 종료 유틸리티
 * 메모리 누수를 방지하기 위한 안전한 ExecutorService 종료 메서드들을 제공합니다.
 */
public final class ExecutorUtils {

    private ExecutorUtils() {
        // 유틸리티 클래스이므로 인스턴스 생성 방지
    }

    /**
     * ExecutorService를 안전하게 종료합니다
     * @param executor 종료할 ExecutorService
     * @param timeoutSeconds 대기 시간 (초)
     */
    public static void shutdownSafely(ExecutorService executor, int timeoutSeconds) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.shutdown(); // 새로운 작업 수락 중단

        try {
            // 지정된 시간 동안 기존 작업 완료 대기
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                // 시간 초과 시 강제 종료
                executor.shutdownNow();

                // 강제 종료 후 1초 더 대기
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            // 현재 스레드가 인터럽트되면 강제 종료
            executor.shutdownNow();
            // 인터럽트 상태 복원
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 기본 5초 대기로 ExecutorService를 안전하게 종료합니다
     * @param executor 종료할 ExecutorService
     */
    public static void shutdownSafely(ExecutorService executor) {
        shutdownSafely(executor, 5);
    }

    /**
     * ExecutorService가 안전하게 종료되었는지 확인합니다
     * @param executor 확인할 ExecutorService
     * @return 종료되었으면 true, 그렇지 않으면 false
     */
    public static boolean isTerminated(ExecutorService executor) {
        return executor == null || executor.isTerminated();
    }

    /**
     * ExecutorService가 종료 중인지 확인합니다
     * @param executor 확인할 ExecutorService
     * @return 종료 중이면 true, 그렇지 않으면 false
     */
    public static boolean isShuttingDown(ExecutorService executor) {
        return executor != null && executor.isShutdown() && !executor.isTerminated();
    }
}