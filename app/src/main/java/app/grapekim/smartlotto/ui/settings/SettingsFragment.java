package app.grapekim.smartlotto.ui.settings;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.CsvUpdateManager;
import app.grapekim.smartlotto.notify.ReminderScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private MaterialSwitch swReminder;
    private Spinner spinnerReminderTime;
    private TextView tvVersion, tvNextAlarm;
    private MaterialButton btnManualUpdate;
    private CsvUpdateManager csvUpdateManager;

    private ActivityResultLauncher<String> notifPermissionLauncher;
    private ActivityResultLauncher<Intent> exactAlarmPermissionLauncher;

    // 초기화 플래그 - 토스트 메시지 문제 해결용
    private boolean isInitializing = true;

    // Spinner 옵션들
    private final String[] reminderOptions = {"5분", "10분", "20분", "30분"};
    private final int[] reminderMinutes = {5, 10, 20, 30};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        initializeViews(v);
        initializeCsvUpdateManager();
        setupPermissionLaunchers();
        setupSpinner();
        setupClickListeners();
        initializeSettings();  // 여기서 리스너도 함께 설정됨
    }

    private void initializeViews(@NonNull View v) {
        swReminder = v.findViewById(R.id.swReminder);
        spinnerReminderTime = v.findViewById(R.id.spinnerReminderTime);
        tvVersion = v.findViewById(R.id.tvVersion);
        tvNextAlarm = v.findViewById(R.id.tvNextAlarm);
        btnManualUpdate = v.findViewById(R.id.btnManualUpdate);
    }

    private void initializeCsvUpdateManager() {
        csvUpdateManager = new CsvUpdateManager(requireContext());
    }


    private void setupPermissionLaunchers() {
        // POST_NOTIFICATIONS 권한 런처
        notifPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        Log.d(TAG, "POST_NOTIFICATIONS permission granted");
                        checkAndRequestExactAlarmPermission();
                    } else {
                        Log.w(TAG, "POST_NOTIFICATIONS permission denied");
                        Toast.makeText(requireContext(), R.string.notif_perm_denied, Toast.LENGTH_LONG).show();
                        swReminder.setChecked(false);
                    }
                }
        );

        // SCHEDULE_EXACT_ALARM 권한 런처 (Android 12+)
        exactAlarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 권한 설정 화면에서 돌아온 후 재확인
                    if (ReminderScheduler.hasExactAlarmPermission(requireContext())) {
                        Log.d(TAG, "SCHEDULE_EXACT_ALARM permission granted");
                        proceedWithReminderSetup();
                    } else {
                        Log.w(TAG, "SCHEDULE_EXACT_ALARM permission still denied");
                        showExactAlarmPermissionDeniedDialog();
                    }
                }
        );
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                reminderOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReminderTime.setAdapter(adapter);
    }

    private void initializeSettings() {
        tvVersion.setText(getAppVersion());

        // 리스너를 임시로 제거
        swReminder.setOnCheckedChangeListener(null);
        spinnerReminderTime.setOnItemSelectedListener(null);

        // 값 설정 (이제 리스너가 호출되지 않음)
        boolean enabled = ReminderScheduler.isEnabled(requireContext());
        swReminder.setChecked(enabled);

        int minutes = ReminderScheduler.getMinutes(requireContext());
        setSpinnerSelection(minutes);

        setSpinnerEnabled(enabled);
        updateNextAlarmPreview();

        // 이제 리스너 다시 설정
        setupChangeListeners();

        // 초기화 완료
        isInitializing = false;
    }

    private void setupClickListeners() {
        View btnOpenPrivacy = requireView().findViewById(R.id.btnOpenPrivacy);
        if (btnOpenPrivacy != null) {
            btnOpenPrivacy.setOnClickListener(view ->
                    startActivity(new Intent(requireContext(), PrivacyPolicyActivity.class))
            );
        }

        // 수동 업데이트 버튼
        if (btnManualUpdate != null) {
            btnManualUpdate.setOnClickListener(view -> performManualUpdate());
        }
    }

    private void setupChangeListeners() {
        swReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 초기화 중에는 리스너 무시 - 토스트 메시지 방지
            if (isInitializing) {
                Log.d(TAG, "Skipping swReminder onCheckedChanged during initialization");
                return;
            }
            onToggleReminder(isChecked);
        });

        spinnerReminderTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 초기화 중에는 리스너 무시 - 토스트 메시지 방지
                if (isInitializing) {
                    Log.d(TAG, "Skipping onItemSelected during initialization");
                    return;
                }

                if (position >= 0 && position < reminderMinutes.length) {
                    int minutes = reminderMinutes[position];
                    ReminderScheduler.setMinutes(requireContext(), minutes);

                    if (swReminder.isChecked()) {
                        rescheduleReminder();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 기본값 사용
            }
        });
    }

    private void onToggleReminder(boolean isChecked) {
        if (isChecked) {
            // 알림 활성화 시 권한 체크
            checkPermissionsAndEnable();
        } else {
            // 알림 비활성화
            onReminderChanged(false);
        }
    }

    private void checkPermissionsAndEnable() {
        // Android 13+ POST_NOTIFICATIONS 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        // 알림 채널 활성화 상태 확인 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = nm.getNotificationChannel(app.grapekim.smartlotto.App.CHANNEL_DRAW_REMINDER_ID);
                if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    Log.w(TAG, "Notification channel is blocked by user");
                    showChannelBlockedDialog();
                    swReminder.setChecked(false);
                    return;
                }
            }
        }

        // SCHEDULE_EXACT_ALARM 권한 체크 (Android 12+)
        checkAndRequestExactAlarmPermission();
    }

    private void showChannelBlockedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("알림 채널 차단됨")
                .setMessage("알림 채널이 시스템 설정에서 차단되어 있습니다.\n\n" +
                        "설정에서 알림을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, app.grapekim.smartlotto.App.CHANNEL_DRAW_REMINDER_ID);
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void checkAndRequestExactAlarmPermission() {
        if (!ReminderScheduler.hasExactAlarmPermission(requireContext())) {
            Log.d(TAG, "SCHEDULE_EXACT_ALARM permission needed");
            showExactAlarmPermissionDialog();
        } else {
            Log.d(TAG, "All permissions granted, proceeding with reminder setup");
            proceedWithReminderSetup();
        }
    }

    private void showExactAlarmPermissionDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("정확한 알림 권한 필요")
                .setMessage("정시에 알림을 받으시려면 '정확한 알람 및 리마인더' 권한이 필요합니다.\n\n" +
                        "설정에서 이 앱에 대한 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    Intent intent = ReminderScheduler.createExactAlarmPermissionIntent(requireContext());
                    if (intent != null) {
                        exactAlarmPermissionLauncher.launch(intent);
                    }
                })
                .setNegativeButton("나중에", (dialog, which) -> {
                    proceedWithReminderSetup(); // 권한 없이도 진행 (부정확한 알람 사용)
                })
                .setCancelable(false)
                .show();
    }

    private void showExactAlarmPermissionDeniedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("알림 정확도 제한")
                .setMessage("정확한 알람 권한이 없어 알림이 최대 15분 정도 지연될 수 있습니다.\n\n" +
                        "정시 알림을 원하시면 설정에서 권한을 허용해주세요.")
                .setPositiveButton("확인", (dialog, which) -> proceedWithReminderSetup())
                .setNeutralButton("설정으로 이동", (dialog, which) -> {
                    Intent intent = ReminderScheduler.createExactAlarmPermissionIntent(requireContext());
                    if (intent != null) {
                        exactAlarmPermissionLauncher.launch(intent);
                    }
                })
                .show();
    }

    private void proceedWithReminderSetup() {
        onReminderChanged(true);
    }

    private void onReminderChanged(boolean enable) {
        try {
            ReminderScheduler.setEnabled(requireContext(), enable);

            if (enable) {
                boolean scheduled = ReminderScheduler.scheduleNext(requireContext());
                setSpinnerEnabled(true);
                updateNextAlarmPreview();

                if (scheduled) {
                    String message = ReminderScheduler.hasExactAlarmPermission(requireContext())
                            ? getString(R.string.reminder_on)
                            : "알림이 설정되었습니다 (정확한 시간 권한 없음)";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "알림 설정에 실패했습니다.", Toast.LENGTH_LONG).show();
                    swReminder.setChecked(false);
                    ReminderScheduler.setEnabled(requireContext(), false);
                }
            } else {
                ReminderScheduler.cancel(requireContext());
                setSpinnerEnabled(false);
                tvNextAlarm.setText("");
                Toast.makeText(requireContext(), R.string.reminder_off, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onReminderChanged", e);
            Toast.makeText(requireContext(), "알림 설정 중 오류가 발생했습니다: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            swReminder.setChecked(ReminderScheduler.isEnabled(requireContext()));
        }
    }

    private void rescheduleReminder() {
        try {
            ReminderScheduler.cancel(requireContext());
            boolean scheduled = ReminderScheduler.scheduleNext(requireContext());
            updateNextAlarmPreview();

            if (scheduled) {
                Toast.makeText(requireContext(), R.string.reminder_rescheduled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "알림 재설정에 실패했습니다.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rescheduling reminder", e);
            Toast.makeText(requireContext(), "알림 재설정 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void setSpinnerEnabled(boolean enabled) {
        spinnerReminderTime.setEnabled(enabled);
    }

    private void setSpinnerSelection(int minutes) {
        for (int i = 0; i < reminderMinutes.length; i++) {
            if (reminderMinutes[i] == minutes) {
                spinnerReminderTime.setSelection(i);
                return;
            }
        }
        spinnerReminderTime.setSelection(0); // 기본값 5분
    }

    private void updateNextAlarmPreview() {
        try {
            if (!ReminderScheduler.isEnabled(requireContext())) {
                tvNextAlarm.setText("");
                return;
            }

            long ts = ReminderScheduler.peekNextTrigger(requireContext());
            if (ts <= 0L) {
                tvNextAlarm.setText("알림 시간 계산 실패");
                Log.w(TAG, "peekNextTrigger returned invalid timestamp: " + ts);
                return;
            }

            Date alarmDate = new Date(ts);
            String timeFormat = ReminderScheduler.hasExactAlarmPermission(requireContext())
                    ? "yyyy-MM-dd (E) HH:mm"
                    : "yyyy-MM-dd (E) HH:mm (±15분)";

            String text = getString(R.string.next_alarm_at,
                    DateFormat.format(timeFormat, alarmDate));
            tvNextAlarm.setText(text);

            Log.d(TAG, "Next alarm preview updated: " + text);

        } catch (Exception e) {
            Log.e(TAG, "Error updating alarm preview", e);
            tvNextAlarm.setText("알림 시간 오류");
        }
    }

    private String getAppVersion() {
        try {
            PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);

            String versionCode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = String.valueOf(pi.getLongVersionCode());
            } else {
                versionCode = String.valueOf(pi.versionCode);
            }

            return pi.versionName + " (" + versionCode + ")";
        } catch (Exception e) {
            Log.e(TAG, "Error getting app version", e);
            return "-";
        }
    }

    // =====================================================================
    // 🔧 수정된 데이터 상태 관련 메서드들 (DataStatusActivity와 동일한 로직)
    // =====================================================================

    /**
     * DataStatusActivity와 동일한 다음 추첨일 계산 로직
     */
    private Calendar calculateNextDrawDate() {
        Calendar now = Calendar.getInstance();
        Calendar nextDraw = Calendar.getInstance();

        // 현재 시점에서 다음 토요일 찾기
        int currentDay = now.get(Calendar.DAY_OF_WEEK);
        int daysUntilSaturday = (Calendar.SATURDAY - currentDay + 7) % 7;

        if (daysUntilSaturday == 0) {
            // 오늘이 토요일
            if (now.get(Calendar.HOUR_OF_DAY) < 20 ||
                    (now.get(Calendar.HOUR_OF_DAY) == 20 && now.get(Calendar.MINUTE) < 45)) {
                // 아직 추첨 전이면 오늘
                nextDraw.set(Calendar.HOUR_OF_DAY, 20);
                nextDraw.set(Calendar.MINUTE, 45);
            } else {
                // 추첨 후면 다음 주
                nextDraw.add(Calendar.WEEK_OF_YEAR, 1);
                nextDraw.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
                nextDraw.set(Calendar.HOUR_OF_DAY, 20);
                nextDraw.set(Calendar.MINUTE, 45);
            }
        } else {
            // 다음 토요일로 설정
            nextDraw.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);
            nextDraw.set(Calendar.HOUR_OF_DAY, 20);
            nextDraw.set(Calendar.MINUTE, 45);
        }

        nextDraw.set(Calendar.SECOND, 0);
        nextDraw.set(Calendar.MILLISECOND, 0);

        return nextDraw;
    }

    /**
     * 수동 업데이트 실행
     */
    private void performManualUpdate() {
        btnManualUpdate.setEnabled(false);
        btnManualUpdate.setText("업데이트 중...");

        // 백그라운드에서 업데이트 실행
        new Thread(() -> {
            try {
                boolean success = csvUpdateManager.forceUpdateCsvFile();

                // UI 스레드에서 결과 처리
                requireActivity().runOnUiThread(() -> {
                    btnManualUpdate.setEnabled(true);
                    btnManualUpdate.setText("수동 업데이트");

                    if (success) {
                        Toast.makeText(requireContext(), "데이터 업데이트 완료", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "업데이트 실패", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Manual update failed", e);
                requireActivity().runOnUiThread(() -> {
                    btnManualUpdate.setEnabled(true);
                    btnManualUpdate.setText("수동 업데이트");
                    Toast.makeText(requireContext(), "업데이트 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }


    @Override
    public void onResume() {
        super.onResume();

        // onResume에서도 초기화 플래그 사용
        isInitializing = true;
        updateNextAlarmPreview();
        isInitializing = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        csvUpdateManager = null;
    }
}