package app.grapekim.smartlotto.data;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CsvUpdateWorker extends Worker {
    private static final String TAG = "CsvUpdateWorker";

    public CsvUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting CSV update work...");

        try {
            CsvUpdateManager csvManager = new CsvUpdateManager(getApplicationContext());

            // GitHub에서 CSV 파일 업데이트 시도
            boolean success = csvManager.updateCsvFile();

            if (success) {
                Log.d(TAG, "CSV update completed successfully");
                return Result.success();
            } else {
                Log.w(TAG, "CSV update failed, will retry later");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during CSV update", e);
            return Result.failure();
        }
    }
}