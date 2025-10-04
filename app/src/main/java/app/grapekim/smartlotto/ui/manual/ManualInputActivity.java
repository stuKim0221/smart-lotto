package app.grapekim.smartlotto.ui.manual;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 수동 번호 입력 액티비티
 * 어르신 친화적 UI + 가로 배치 + 실시간 검증
 */
public class ManualInputActivity extends AppCompatActivity {

    // UI Components
    private EditText etNum1, etNum2, etNum3, etNum4, etNum5, etNum6;
    private EditText[] numberInputs;
    private TextView tvProgress;
    private TextView tvEnteredNumbers;
    private MaterialButton btnSave;

    // Error Card Components
    private com.google.android.material.card.MaterialCardView cardError;
    private TextView tvErrorMessage;
    private android.widget.ImageButton btnCloseError;

    // Data & Logic
    private LottoRepository repository;
    private ExecutorService executor;

    // Colors
    private int colorValid;
    private int colorInvalid;
    private int colorDuplicate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_input);

        initializeComponents();
        initializeViews();
        setupInputFields();
        setupClickListeners();
        setupKeyboardDismiss(); // 추가: 화면 터치 시 키보드 숨김
        updateProgress();
    }

    /**
     * 컴포넌트 초기화
     */
    private void initializeComponents() {
        repository = new LottoRepositoryImpl(getApplicationContext());
        executor = Executors.newSingleThreadExecutor();

        // 색상 초기화
        colorValid = ContextCompat.getColor(this, R.color.blue_500);
        colorInvalid = ContextCompat.getColor(this, R.color.red_500);
        colorDuplicate = ContextCompat.getColor(this, R.color.orange_500);
    }

    /**
     * 뷰 초기화
     */
    private void initializeViews() {
        // 번호 입력 필드들
        etNum1 = findViewById(R.id.etNum1);
        etNum2 = findViewById(R.id.etNum2);
        etNum3 = findViewById(R.id.etNum3);
        etNum4 = findViewById(R.id.etNum4);
        etNum5 = findViewById(R.id.etNum5);
        etNum6 = findViewById(R.id.etNum6);

        numberInputs = new EditText[]{etNum1, etNum2, etNum3, etNum4, etNum5, etNum6};

        // 진행률 및 결과 표시
        tvProgress = findViewById(R.id.tvProgress);
        tvEnteredNumbers = findViewById(R.id.tvEnteredNumbers);

        // 저장 버튼
        btnSave = findViewById(R.id.btnSave);

        // 에러 카드 컴포넌트들
        cardError = findViewById(R.id.cardError);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnCloseError = findViewById(R.id.btnCloseError);

        // 뒤로가기 버튼
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    /**
     * 입력 필드 설정
     */
    private void setupInputFields() {
        for (int i = 0; i < numberInputs.length; i++) {
            EditText editText = numberInputs[i];
            final int index = i;

            // 기본 입력 설정
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
            editText.setTextSize(20); // 어르신 친화적 큰 폰트
            editText.setPadding(16, 16, 16, 16);

            // 힌트 설정 (순서 표시)
            editText.setHint(String.valueOf(i + 1));

            // 모든 선택 관련 UI 완전 제거
            editText.setCursorVisible(false);
            editText.setTextIsSelectable(false);
            editText.setLongClickable(false);

            // 추가 커서 제거 설정
            try {
                // 리플렉션을 통한 커서 완전 제거
                java.lang.reflect.Field field = TextView.class.getDeclaredField("mCursorDrawableRes");
                field.setAccessible(true);
                field.set(editText, 0);
            } catch (Exception ignored) {
                // 리플렉션 실패 시 무시
            }

            // 포커스 시에도 커서 숨김
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    editText.setCursorVisible(false);
                }
                validateInput(index);
            });
            editText.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    return false; // 텍스트 선택 메뉴 완전 비활성화
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });

            // 텍스트 변경 리스너 (자동 이동 제거됨)
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String input = s.toString().trim();

                    // 01, 02 같은 형태 방지 (십의 자리에 0이 오는 경우)
                    if (input.length() == 2 && input.startsWith("0")) {
                        // 01 → 1로 자동 변환
                        String corrected = input.substring(1);
                        editText.setText(corrected);
                        editText.setSelection(corrected.length());
                        return;
                    }

                    validateInput(index);
                    updateProgress();

                    // 자동 포커스 이동 제거 - 사용자가 직접 다음 칸 선택
                    // 입력 완료 시 시각적 피드백만 제공
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    /**
     * 클릭 리스너 설정
     */
    private void setupClickListeners() {
        // 저장 버튼
        btnSave.setOnClickListener(v -> saveNumbers());

        // 에러 카드 닫기 버튼
        btnCloseError.setOnClickListener(v -> hideErrorCard());
    }

    /**
     * 특정 입력 필드 검증 (시각적 피드백 강화)
     */
    private void validateInput(int index) {
        EditText editText = numberInputs[index];
        String input = editText.getText().toString().trim();

        if (input.isEmpty()) {
            // 빈 입력: 기본 배경으로 복원
            editText.setBackgroundTintList(null);
            hideErrorCard(); // 에러 카드 숨김
            return;
        }

        try {
            int number = Integer.parseInt(input);

            // 범위 검증 (1~45)
            if (number < 1 || number > 45) {
                setInputBorderColor(editText, colorInvalid);
                if (number < 1) {
                    showErrorCard("1보다 작은 번호는 입력할 수 없습니다. (1~45 범위)");
                } else {
                    showErrorCard("45보다 큰 번호는 입력할 수 없습니다. (1~45 범위)");
                }
                return;
            }

            // 중복 검증
            if (isDuplicate(number, index)) {
                setInputBorderColor(editText, colorDuplicate);
                showErrorCard("이미 입력된 번호입니다. 서로 다른 6개의 번호를 입력해주세요.");
                return;
            }

            // 유효한 입력 - 완료 표시 강화
            setInputBorderColor(editText, colorValid);
            hideErrorCard(); // 유효한 입력 시 에러 카드 숨김

        } catch (NumberFormatException e) {
            // 잘못된 숫자 형식
            setInputBorderColor(editText, colorInvalid);
            showErrorCard("올바른 숫자를 입력해주세요. (1~45 범위의 숫자)");
        }
    }

    /**
     * 중복 번호 확인
     */
    private boolean isDuplicate(int number, int currentIndex) {
        for (int i = 0; i < numberInputs.length; i++) {
            if (i == currentIndex) continue;

            String otherInput = numberInputs[i].getText().toString().trim();
            if (!otherInput.isEmpty()) {
                try {
                    int otherNumber = Integer.parseInt(otherInput);
                    if (otherNumber == number) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }

    /**
     * 입력 필드 테두리 색상 설정
     */
    private void setInputBorderColor(EditText editText, int color) {
        // 테두리 색상 변경 (실제 drawable에 따라 구현 방식 조정 필요)
        editText.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    /**
     * 진행률 및 입력된 번호 업데이트
     */
    private void updateProgress() {
        List<Integer> validNumbers = getValidNumbers();
        int validCount = validNumbers.size();

        // 진행률 표시
        tvProgress.setText(String.format("6개 중 %d개 입력됨", validCount));

        // 입력된 번호 표시 (정렬됨)
        if (validNumbers.isEmpty()) {
            tvEnteredNumbers.setText("아직 입력된 번호가 없습니다");
            tvEnteredNumbers.setTextColor(Color.GRAY);
        } else {
            Collections.sort(validNumbers);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < validNumbers.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%02d", validNumbers.get(i)));
            }
            tvEnteredNumbers.setText(sb.toString());
            tvEnteredNumbers.setTextColor(Color.BLACK);
        }

        // 저장 버튼 활성화/비활성화
        updateSaveButton(validCount == 6 && hasNoDuplicates());
    }

    /**
     * 유효한 번호들 추출
     */
    private List<Integer> getValidNumbers() {
        List<Integer> validNumbers = new ArrayList<>();

        for (EditText editText : numberInputs) {
            String input = editText.getText().toString().trim();
            if (!input.isEmpty()) {
                try {
                    int number = Integer.parseInt(input);
                    if (number >= 1 && number <= 45) {
                        validNumbers.add(number);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return validNumbers;
    }

    /**
     * 중복 번호 없는지 확인
     */
    private boolean hasNoDuplicates() {
        List<Integer> numbers = getValidNumbers();
        Set<Integer> uniqueNumbers = new HashSet<>(numbers);
        return numbers.size() == uniqueNumbers.size();
    }

    /**
     * 저장 버튼 상태 업데이트
     */
    private void updateSaveButton(boolean enabled) {
        btnSave.setEnabled(enabled);
        if (enabled) {
            btnSave.setText("저장하기");
            btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.blue_500)));
        } else {
            btnSave.setText("조건을 모두 만족해주세요");
            btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.gray_400)));
        }
    }

    /**
     * 번호 저장
     */
    private void saveNumbers() {
        List<Integer> numbers = getValidNumbers();

        if (numbers.size() != 6 || !hasNoDuplicates()) {
            Toast.makeText(this, "6개의 서로 다른 번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 저장 버튼 비활성화 (중복 저장 방지)
        btnSave.setEnabled(false);
        btnSave.setText("저장 중...");

        executor.execute(() -> {
            try {
                // Repository를 통해 저장
                long id = repository.saveManualPick(numbers, null, null);

                runOnUiThread(() -> {
                    if (id > 0) {
                        Toast.makeText(this, "수동 번호가 저장되었습니다!", Toast.LENGTH_SHORT).show();

                        // 저장 성공 시 이력으로 이동 (선택사항)
                        // Intent intent = new Intent(this, MainActivity.class);
                        // intent.putExtra("tab", "history");
                        // startActivity(intent);

                        finish(); // 현재 액티비티 종료
                    } else {
                        Toast.makeText(this, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        updateSaveButton(true); // 버튼 재활성화
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "저장 중 오류가 발생했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    updateSaveButton(true); // 버튼 재활성화
                });
            }
        });
    }

    /**
     * 에러 카드 표시
     */
    private void showErrorCard(String message) {
        if (tvErrorMessage != null && cardError != null) {
            tvErrorMessage.setText(message);
            cardError.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 에러 카드 숨김
     */
    private void hideErrorCard() {
        if (cardError != null) {
            cardError.setVisibility(View.GONE);
        }
    }

    /**
     * 화면 터치 시 키보드 숨김 설정
     */
    private void setupKeyboardDismiss() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                // 현재 포커스된 뷰가 EditText가 아닌 곳을 터치하면 키보드 숨김
                View focusedView = getCurrentFocus();
                if (focusedView instanceof EditText) {
                    hideKeyboard();
                    focusedView.clearFocus(); // 포커스도 제거
                }
                return false; // 다른 터치 이벤트도 정상 처리
            });
        }
    }

    /**
     * 키보드 숨김
     */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}