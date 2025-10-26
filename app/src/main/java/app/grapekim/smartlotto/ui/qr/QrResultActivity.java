package app.grapekim.smartlotto.ui.qr;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.card.MaterialCardView;

import app.grapekim.smartlotto.MainActivity;
import app.grapekim.smartlotto.R;
import app.grapekim.smartlotto.data.repository.LottoRepository;
import app.grapekim.smartlotto.data.repository.LottoRepositoryImpl;
import app.grapekim.smartlotto.data.csv.LottoDataLoader;
import app.grapekim.smartlotto.util.QrLottoParser;
import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrResultActivity extends AppCompatActivity {

    private static final String TAG = "QrResultActivity";
    public static final String EXTRA_QR_RAW_DATA = "qr_raw_data";

    // UI Components
    private MaterialToolbar toolbar;
    private LinearLayout llGamesContainer;
    private MaterialButton btnScanAgain;
    private MaterialButton btnSaveToHistory;
    private TextView tvWinResult;

    // Data & Services
    private String rawQrData;
    private boolean isWinningCheck;
    private LottoRepository repository;
    private ExecutorService executor;
    private List<Long> savedGameIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_result);

        initializeServices();
        initializeViews();
        setupClickListeners();

        // Intent에서 데이터 받기
        rawQrData = getIntent().getStringExtra(EXTRA_QR_RAW_DATA);
        isWinningCheck = getIntent().getBooleanExtra("IS_WINNING_CHECK", false);

        if (rawQrData != null) {
            if (isWinningCheck) {
                processWinningCheck(rawQrData);
            } else {
                processQrData(rawQrData);
            }
        } else {
            showError("QR 데이터가 없습니다.");
        }
    }

    private void initializeServices() {
        repository = new LottoRepositoryImpl(getApplicationContext());
        executor = Executors.newSingleThreadExecutor();
        savedGameIds = new ArrayList<>();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        llGamesContainer = findViewById(R.id.llGamesContainer);
        btnScanAgain = findViewById(R.id.btnScanAgain);
        btnSaveToHistory = findViewById(R.id.btnSaveToHistory);
        tvWinResult = findViewById(R.id.tvWinResult);

        // 툴바 설정
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        btnScanAgain.setOnClickListener(v -> {
            Intent intent = new Intent(this, ZxingScanActivity.class);
            // 현재 모드를 유지하여 전달
            if (isWinningCheck) {
                intent.putExtra("SCAN_MODE", ZxingScanActivity.SCAN_MODE_WINNING_CHECK);
            } else {
                intent.putExtra("SCAN_MODE", ZxingScanActivity.SCAN_MODE_ADD_NUMBERS);
            }
            startActivity(intent);
            finish();
        });

        btnSaveToHistory.setOnClickListener(v -> saveToHistory());
    }

    /**
     * QR 데이터 파싱 및 UI 표시
     */
    private void processQrData(String rawData) {
        Log.d(TAG, getString(R.string.log_qr_parsing_start));
        Log.d(TAG, "원시 데이터: " + rawData);

        // QrLottoParser로 파싱
        QrLottoParser.Result parseResult = QrLottoParser.parse(rawData);

        if (parseResult == null) {
            // 파싱 실패 - 원시 데이터 표시
            showRawDataFallback(rawData);
            return;
        }

        // 파싱 성공 - 5게임 표시
        showParsedGames(parseResult);
    }

    /**
     * 파싱된 게임들을 UI에 표시
     */
    private void showParsedGames(QrLottoParser.Result result) {
        Log.d(TAG, getString(R.string.log_parsing_success, result.getGameCount()));

        // 제목 설정
        toolbar.setTitle(getString(R.string.qr_scan_result_title));

        // 5게임 표시 (부족하면 빈 게임으로 채움)
        displayFiveGames(result.allGames);


        // Repository에 저장
        saveGamesToRepository(result);
    }

    /**
     * 5게임을 UI에 표시 (A, B, C, D, E) - 새로운 Material 디자인 사용
     */
    private void displayFiveGames(List<List<Integer>> games) {
        llGamesContainer.removeAllViews();

        for (int i = 0; i < 5; i++) {
            char gameLabel = (char) ('A' + i);
            List<Integer> gameNumbers;

            if (i < games.size()) {
                gameNumbers = games.get(i);
            } else {
                // 게임이 부족하면 빈 게임 표시
                gameNumbers = new ArrayList<>();
            }

            // 단순 표시 모드로 새로운 Material 게임 뷰 생성 (당첨 확인 기능 비활성화)
            if (!gameNumbers.isEmpty()) {
                View gameView = createMaterialGameView(gameLabel, gameNumbers, false, null, null, 0);
                llGamesContainer.addView(gameView);
            }
        }

        llGamesContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 개별 게임 TextView 생성
     */
    private TextView createGameView(char gameLabel, List<Integer> numbers) {
        TextView gameView = new TextView(this);

        // 텍스트 설정
        String gameText;
        if (numbers.isEmpty()) {
            gameText = getString(R.string.game_not_selected, gameLabel);
            gameView.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurfaceVariant));
        } else {
            Collections.sort(numbers);
            gameText = getString(R.string.game_format, gameLabel, formatNumbers(numbers));
            gameView.setTextColor(ContextCompat.getColor(this, R.color.gray_800));
        }

        gameView.setText(gameText);
        gameView.setTextSize(16f);
        gameView.setPadding(16, 12, 16, 12);
        gameView.setBackgroundResource(R.drawable.game_background);

        // 레이아웃 파라미터 설정
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 4, 0, 4);
        gameView.setLayoutParams(params);

        return gameView;
    }

    /**
     * 번호 리스트를 문자열로 포맷
     */
    private String formatNumbers(List<Integer> numbers) {
        if (numbers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%02d", numbers.get(i)));
        }
        return sb.toString();
    }

    /**
     * 게임들을 Repository에 저장
     */
    private void saveGamesToRepository(QrLottoParser.Result result) {
        executor.execute(() -> {
            try {
                // Activity 종료 체크
                if (isFinishing() || isDestroyed()) {
                    Log.w(TAG, "Activity 종료됨, 저장 중단");
                    return;
                }

                // 빈 게임 제거 후 저장
                List<List<Integer>> validGames = new ArrayList<>();
                for (List<Integer> game : result.allGames) {
                    if (game != null && game.size() == 6) {
                        validGames.add(game);
                    }
                }

                if (!validGames.isEmpty()) {
                    List<Long> ids = repository.saveQrGames(
                            validGames,
                            result.round,
                            result.purchaseAt,
                            rawQrData,
                            "QR_STRUCTURED"
                    );

                    synchronized (this) {
                        savedGameIds.addAll(ids);
                    }

                    Log.d(TAG, getString(R.string.log_games_saved, validGames.size()));
                } else {
                    Log.w(TAG, getString(R.string.log_no_valid_games));
                }

            } catch (Exception e) {
                Log.e(TAG, getString(R.string.log_repository_save_failed), e);
            }
        });
    }

    /**
     * 파싱 실패시 원시 데이터 표시
     */
    private void showRawDataFallback(String rawData) {
        Log.d(TAG, getString(R.string.log_parsing_failed));

        toolbar.setTitle(getString(R.string.parsing_failed_title));

        // 원시 데이터를 단일 TextView로 표시
        llGamesContainer.removeAllViews();

        TextView rawDataView = new TextView(this);
        String displayText = getString(R.string.qr_content_prefix) +
                (rawData.length() > 200 ?
                        rawData.substring(0, 200) + getString(R.string.qr_content_truncated, rawData.length()) :
                        rawData);

        rawDataView.setText(displayText);
        rawDataView.setTextSize(12f);
        rawDataView.setPadding(16, 16, 16, 16);
        rawDataView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorSurfaceVariant));

        llGamesContainer.addView(rawDataView);
        llGamesContainer.setVisibility(View.VISIBLE);

    }

    /**
     * 히스토리에 저장 후 바로 이동 (GitHub CSV 동기화 포함)
     */
    private void saveToHistory() {
        synchronized (this) {
            if (savedGameIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_data_to_save), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 저장 완료 메시지 표시
        Toast.makeText(this, getString(R.string.saved_to_history), Toast.LENGTH_SHORT).show();

        // GitHub CSV 동기화 후 히스토리 화면으로 이동
        syncGitHubDataAndNavigate();
    }

    /**
     * GitHub CSV 동기화 실행 후 HistoryFragment로 이동
     */
    private void syncGitHubDataAndNavigate() {
        executor.execute(() -> {
            LottoDataLoader loader = null;
            try {
                // Activity 종료 체크
                if (isFinishing() || isDestroyed()) {
                    Log.w(TAG, "Activity 종료됨, 동기화 중단");
                    return;
                }

                Log.d(TAG, "QR 결과 저장 후 GitHub CSV 동기화 시작");

                // GitHub CSV 동기화 시도
                loader = new LottoDataLoader(getApplicationContext(), repository);
                boolean syncSuccess = loader.loadLottoDataSync();

                if (syncSuccess) {
                    Log.i(TAG, "GitHub CSV 동기화 성공");
                } else {
                    Log.w(TAG, "GitHub CSV 동기화 실패, 기존 데이터 사용");
                }

            } catch (Exception e) {
                Log.e(TAG, "GitHub CSV 동기화 중 오류 발생", e);
                // 동기화 실패해도 계속 진행
            } finally {
                // LottoDataLoader의 ExecutorService 정리
                if (loader != null) {
                    loader.shutdown();
                }

                // UI 스레드에서 화면 이동 실행
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        navigateToHistory();
                    }
                });
            }
        });
    }

    /**
     * HistoryFragment로 이동
     */
    private void navigateToHistory() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("tab", "history");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /**
     * 당첨 확인 처리
     */
    private void processWinningCheck(String rawData) {
        Log.d(TAG, "당첨 확인 처리 시작");

        // 제목을 당첨 확인으로 변경
        toolbar.setTitle("당첨 확인 결과");

        // 저장 버튼 숨기기 (당첨 확인 모드에서는 불필요)
        btnSaveToHistory.setVisibility(View.GONE);

        // QR 데이터 파싱
        QrLottoParser.Result parseResult = QrLottoParser.parse(rawData);

        if (parseResult == null || parseResult.allGames.isEmpty()) {
            showWinningError("QR 코드에서 로또 번호를 찾을 수 없습니다.");
            return;
        }

        // 로딩 메시지 표시
        showLoadingMessage();

        // 백그라운드에서 당첨 확인 실행
        executor.execute(() -> checkWinningNumbers(parseResult));
    }

    /**
     * 로딩 메시지 표시
     */
    private void showLoadingMessage() {
        llGamesContainer.removeAllViews();

        TextView loadingView = new TextView(this);
        loadingView.setText("GitHub에서 최신 당첨번호 데이터를 가져오는 중...\n잠시만 기다려주세요.");
        loadingView.setTextSize(16f);
        loadingView.setTextColor(ContextCompat.getColor(this, R.color.blue_600));
        loadingView.setPadding(16, 32, 16, 32);
        loadingView.setGravity(android.view.Gravity.CENTER);
        loadingView.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_50));

        llGamesContainer.addView(loadingView);
        llGamesContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 당첨 번호 확인 - QR에서 파싱된 회차 조회 (GitHub CSV 우선)
     */
    private void checkWinningNumbers(QrLottoParser.Result parseResult) {
        LottoDataLoader loader = null;
        try {
            // Activity가 종료되었는지 확인
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity 종료됨, 당첨 확인 중단");
                return;
            }

            // GitHub CSV에서 최신 데이터 업데이트 시도
            Log.d(TAG, "GitHub CSV 데이터 업데이트 시도");
            loader = new LottoDataLoader(getApplicationContext(), repository);
            boolean updated = loader.loadLottoDataSync();

            if (updated) {
                Log.d(TAG, "GitHub CSV 데이터 업데이트 성공");
            } else {
                Log.d(TAG, "GitHub CSV 업데이트 실패, 기존 로컬 데이터 사용");
            }

            // Activity 종료 체크
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity 종료됨, 당첨 확인 중단");
                return;
            }

            LottoDrawHistoryEntity targetDraw = null;
            int targetRound = 0;

            // QR에서 회차 정보가 파싱된 경우 해당 회차의 당첨번호 사용
            if (parseResult.round != null && parseResult.round > 0) {
                targetRound = parseResult.round;
                targetDraw = repository.getDrawHistory(targetRound);
                Log.d(TAG, "QR 회차 정보 사용: " + targetRound + "회");
            }

            // 해당 회차 데이터가 없으면 데이터 업데이트 후 재시도 제안
            if (targetDraw == null) {
                Log.d(TAG, "해당 회차(" + targetRound + ") 데이터 없음");

                final int missingRound = targetRound;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showDataMissingError(missingRound, parseResult);
                    }
                });
                return;
            }

            // 당첨 번호 추출
            List<Integer> winningNumbers = new ArrayList<>();
            winningNumbers.add(targetDraw.number1);
            winningNumbers.add(targetDraw.number2);
            winningNumbers.add(targetDraw.number3);
            winningNumbers.add(targetDraw.number4);
            winningNumbers.add(targetDraw.number5);
            winningNumbers.add(targetDraw.number6);
            int bonusNumber = targetDraw.bonusNumber;

            Log.d(TAG, String.format("%d회 당첨번호로 비교: %s + %d", targetRound, winningNumbers.toString(), bonusNumber));

            // UI 스레드에서 당첨 결과 표시
            final int finalRound = targetRound;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    displayWinningResults(parseResult.allGames, winningNumbers, bonusNumber, finalRound);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "당첨 확인 중 오류", e);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showWinningError("당첨 확인 중 오류가 발생했습니다.");
                }
            });
        } finally {
            // LottoDataLoader의 ExecutorService 정리
            if (loader != null) {
                loader.shutdown();
            }
        }
    }

    /**
     * 당첨 결과 표시
     */
    private void displayWinningResults(List<List<Integer>> userGames, List<Integer> winningNumbers, int bonusNumber, int drawNo) {
        llGamesContainer.removeAllViews();

        // 당첨 번호 표시
        TextView winningView = new TextView(this);
        Collections.sort(winningNumbers);
        String winningText = String.format("당첨번호 (%d회): %s + %02d",
                drawNo, formatNumbers(winningNumbers), bonusNumber);
        winningView.setText(winningText);
        winningView.setTextSize(16f);
        winningView.setTextColor(ContextCompat.getColor(this, R.color.red_600));
        winningView.setPadding(16, 16, 16, 16);
        winningView.setBackgroundResource(R.drawable.game_background);

        LinearLayout.LayoutParams winningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        winningParams.setMargins(0, 8, 0, 16);
        winningView.setLayoutParams(winningParams);
        llGamesContainer.addView(winningView);

        // 각 게임별 당첨 결과 확인
        boolean hasWinner = false;
        for (int i = 0; i < Math.min(userGames.size(), 5); i++) {
            char gameLabel = (char) ('A' + i);
            List<Integer> userNumbers = userGames.get(i);

            if (userNumbers == null || userNumbers.size() != 6) {
                continue;
            }

            // 당첨 확인
            WinningResult result = checkWinning(userNumbers, winningNumbers, bonusNumber);
            View gameView = createMaterialGameView(gameLabel, userNumbers, result, winningNumbers, bonusNumber);
            llGamesContainer.addView(gameView);

            if (result.rank > 0) {
                hasWinner = true;
            }
        }

        // 전체 결과 요약
        TextView summaryView = new TextView(this);
        String summaryText = hasWinner ? "🎉 축하합니다! 당첨된 번호가 있습니다!" : "아쉽게도 당첨되지 않았습니다.";
        summaryView.setText(summaryText);
        summaryView.setTextSize(18f);
        summaryView.setTextColor(ContextCompat.getColor(this, hasWinner ? R.color.blue_600 : R.color.gray_600));
        summaryView.setPadding(16, 24, 16, 16);
        summaryView.setBackgroundColor(ContextCompat.getColor(this, hasWinner ? R.color.blue_50 : R.color.gray_50));

        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMargins(0, 16, 0, 8);
        summaryView.setLayoutParams(summaryParams);
        llGamesContainer.addView(summaryView);

        llGamesContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 당첨 결과 클래스
     */
    private static class WinningResult {
        int matchCount;
        boolean hasBonus;
        int rank;
        String description;

        WinningResult(int matchCount, boolean hasBonus) {
            this.matchCount = matchCount;
            this.hasBonus = hasBonus;
            calculateRank();
        }

        private void calculateRank() {
            if (matchCount == 6) {
                rank = 1;
                description = "1등 🥇";
            } else if (matchCount == 5 && hasBonus) {
                rank = 2;
                description = "2등 🥈";
            } else if (matchCount == 5) {
                rank = 3;
                description = "3등 🥉";
            } else if (matchCount == 4) {
                rank = 4;
                description = "4등";
            } else if (matchCount == 3) {
                rank = 5;
                description = "5등";
            } else {
                rank = 0;
                description = "미당첨";
            }
        }
    }

    /**
     * 당첨 여부 확인
     */
    private WinningResult checkWinning(List<Integer> userNumbers, List<Integer> winningNumbers, int bonusNumber) {
        int matchCount = 0;
        boolean hasBonus = false;

        for (int userNumber : userNumbers) {
            if (winningNumbers.contains(userNumber)) {
                matchCount++;
            } else if (userNumber == bonusNumber) {
                hasBonus = true;
            }
        }

        return new WinningResult(matchCount, hasBonus);
    }

    /**
     * 당첨 결과 게임 뷰 생성
     */
    private TextView createWinningGameView(char gameLabel, List<Integer> numbers, WinningResult result) {
        TextView gameView = new TextView(this);

        Collections.sort(numbers);
        String gameText = String.format("%c게임: %s (%s)", gameLabel, formatNumbers(numbers), result.description);

        gameView.setText(gameText);
        gameView.setTextSize(16f);
        gameView.setPadding(16, 12, 16, 12);

        // 당첨 등급에 따른 색상 설정
        int textColor;
        int backgroundColor;
        if (result.rank == 1) {
            textColor = R.color.red_600;
            backgroundColor = R.color.blue_50;  // 1등은 파란 배경
        } else if (result.rank == 2) {
            textColor = R.color.orange_500;
            backgroundColor = R.color.purple_50;  // 2등은 보라 배경
        } else if (result.rank == 3) {
            textColor = R.color.yellow_600;
            backgroundColor = R.color.green_50;  // 3등은 연한 녹색 배경
        } else if (result.rank >= 4) {
            textColor = R.color.blue_600;
            backgroundColor = R.color.green_50;  // 4~5등은 녹색 배경
        } else {
            textColor = R.color.gray_800;
            backgroundColor = android.R.color.white;
        }

        gameView.setTextColor(ContextCompat.getColor(this, textColor));
        gameView.setBackgroundColor(ContextCompat.getColor(this, backgroundColor));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 4, 0, 4);
        gameView.setLayoutParams(params);

        return gameView;
    }

    /**
     * 당첨 확인 오류 표시
     */
    private void showWinningError(String message) {
        llGamesContainer.removeAllViews();

        TextView errorView = new TextView(this);
        errorView.setText(message);
        errorView.setTextSize(16f);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.red_600));
        errorView.setPadding(16, 32, 16, 32);
        errorView.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_50));

        llGamesContainer.addView(errorView);
        llGamesContainer.setVisibility(View.VISIBLE);

    }

    /**
     * 회차 데이터 없음 오류 표시 (업데이트 및 재시도 옵션 포함)
     */
    private void showDataMissingError(int missingRound, QrLottoParser.Result parseResult) {
        llGamesContainer.removeAllViews();

        // 오류 메시지 표시
        TextView errorView = new TextView(this);
        String errorMessage = String.format("%d회 당첨번호가 아직 앱에 저장되지 않았습니다.\n\n" +
                "아래 버튼을 눌러 최신 당첨번호를 업데이트한 후\n다시 확인해보세요.", missingRound);
        errorView.setText(errorMessage);
        errorView.setTextSize(16f);
        errorView.setTextColor(ContextCompat.getColor(this, R.color.gray_800));
        errorView.setPadding(16, 24, 16, 24);
        errorView.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_50));

        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        errorParams.setMargins(0, 0, 0, 16);
        errorView.setLayoutParams(errorParams);
        llGamesContainer.addView(errorView);

        // 업데이트 버튼 추가
        Button btnUpdate = new Button(this);
        btnUpdate.setText("최신 당첨번호 업데이트");
        btnUpdate.setTextSize(16f);
        btnUpdate.setPadding(32, 16, 32, 16);
        btnUpdate.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_600));
        btnUpdate.setTextColor(ContextCompat.getColor(this, R.color.white));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(16, 0, 16, 16);
        btnUpdate.setLayoutParams(btnParams);

        btnUpdate.setOnClickListener(v -> {
            btnUpdate.setEnabled(false);
            btnUpdate.setText("업데이트 중...");
            updateDrawDataAndRetry(missingRound, parseResult, btnUpdate);
        });

        llGamesContainer.addView(btnUpdate);

        // 재시도 버튼 추가
        Button btnRetry = new Button(this);
        btnRetry.setText("다시 확인하기");
        btnRetry.setTextSize(14f);
        btnRetry.setPadding(32, 12, 32, 12);
        btnRetry.setBackgroundColor(ContextCompat.getColor(this, R.color.gray_600));
        btnRetry.setTextColor(ContextCompat.getColor(this, R.color.white));

        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        retryParams.setMargins(16, 0, 16, 0);
        btnRetry.setLayoutParams(retryParams);

        btnRetry.setOnClickListener(v -> {
            // 데이터 업데이트 없이 바로 재시도
            if (!isFinishing() && !isDestroyed()) {
                executor.execute(() -> checkWinningNumbers(parseResult));
            }
        });

        llGamesContainer.addView(btnRetry);

        llGamesContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 당첨번호 데이터 업데이트 후 재시도
     */
    private void updateDrawDataAndRetry(int targetRound, QrLottoParser.Result parseResult, Button updateButton) {
        executor.execute(() -> {
            LottoDataLoader loader = null;
            try {
                // Activity 종료 체크
                if (isFinishing() || isDestroyed()) {
                    Log.w(TAG, "Activity 종료됨, 업데이트 중단");
                    return;
                }

                Log.d(TAG, "당첨번호 데이터 업데이트 시작");

                // CSV 데이터 로더를 사용해서 최신 데이터 동기화
                loader = new LottoDataLoader(getApplicationContext(), repository);
                boolean updateSuccess = loader.loadLottoDataSync();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    updateButton.setEnabled(true);

                    if (updateSuccess) {
                        Toast.makeText(this, "데이터 업데이트 완료! 다시 확인합니다.", Toast.LENGTH_SHORT).show();
                        updateButton.setText("업데이트 완료");

                        // 업데이트 후 재시도
                        executor.execute(() -> checkWinningNumbers(parseResult));

                    } else {
                        updateButton.setText("업데이트 실패");
                        Toast.makeText(this, String.format("%d회 당첨번호를 아직 가져올 수 없습니다.\n나중에 다시 시도해보세요.", targetRound),
                                Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "데이터 업데이트 중 오류", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        updateButton.setEnabled(true);
                        updateButton.setText("업데이트 실패");
                        Toast.makeText(this, "데이터 업데이트 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                // LottoDataLoader의 ExecutorService 정리
                if (loader != null) {
                    loader.shutdown();
                }
            }
        });
    }

    /**
     * 새로운 Material 3 스타일의 게임 뷰 생성 (당첨번호 색상 포함)
     */
    private View createMaterialGameView(char gameLabel, List<Integer> numbers, WinningResult winResult,
                                       List<Integer> winningNumbers, int bonusNumber) {
        return createMaterialGameView(gameLabel, numbers, true, winResult, winningNumbers, bonusNumber);
    }

    /**
     * 새로운 Material 3 스타일의 게임 뷰 생성 (공통 메서드)
     * @param gameLabel 게임 라벨 (A, B, C, D, E)
     * @param numbers 로또 번호들
     * @param enableWinningCheck 당첨 확인 기능 활성화 여부
     * @param winResult 당첨 결과 (enableWinningCheck가 true일 때만 사용)
     * @param winningNumbers 당첨 번호들 (enableWinningCheck가 true일 때만 사용)
     * @param bonusNumber 보너스 번호 (enableWinningCheck가 true일 때만 사용)
     */
    private View createMaterialGameView(char gameLabel, List<Integer> numbers, boolean enableWinningCheck,
                                       WinningResult winResult, List<Integer> winningNumbers, int bonusNumber) {
        View gameView = getLayoutInflater().inflate(R.layout.item_qr_game_result, null);

        // 게임 라벨 설정
        TextView tvGameLabel = gameView.findViewById(R.id.tvGameLabel);
        tvGameLabel.setText(String.valueOf(gameLabel));


        // 번호들 설정 및 색상 적용
        TextView[] numberViews = {
            gameView.findViewById(R.id.number1),
            gameView.findViewById(R.id.number2),
            gameView.findViewById(R.id.number3),
            gameView.findViewById(R.id.number4),
            gameView.findViewById(R.id.number5),
            gameView.findViewById(R.id.number6)
        };

        // 번호 표시 및 색상 적용
        for (int i = 0; i < 6 && i < numbers.size(); i++) {
            int number = numbers.get(i);
            numberViews[i].setText(String.valueOf(number));

            if (enableWinningCheck) {
                // 당첨 확인 모드: 당첨번호와 일치하는지 확인하여 색상 적용
                if (winningNumbers != null && winningNumbers.contains(number)) {
                    // 당첨번호와 일치 - 로또볼 색상 적용
                    applyLottoBallColor(numberViews[i], number);
                } else if (number == bonusNumber) {
                    // 보너스번호와 일치 - 보너스 색상 적용
                    numberViews[i].setBackgroundResource(R.drawable.bg_lotto_ball_bonus);
                } else {
                    // 일반 번호 - 배경 없이 숫자만 표시
                    numberViews[i].setBackground(null);
                }
            } else {
                // 단순 표시 모드: 모든 번호를 배경 없이 숫자만 표시
                numberViews[i].setBackground(null);
            }
        }

        // 당첨 정보 설정
        LinearLayout llWinInfo = gameView.findViewById(R.id.llWinInfo);
        TextView tvNoWin = gameView.findViewById(R.id.tvNoWin);

        if (enableWinningCheck) {
            // 당첨 확인 모드: 당첨 정보 표시
            if (winResult != null && winResult.rank > 0) {
                // 당첨된 경우
                llWinInfo.setVisibility(View.VISIBLE);
                tvNoWin.setVisibility(View.GONE);

                TextView tvWinRank = gameView.findViewById(R.id.tvWinRank);
                TextView tvMatchCount = gameView.findViewById(R.id.tvMatchCount);

                tvWinRank.setText(winResult.description + " 당첨!");
                String matchText = winResult.matchCount + "개 일치";
                if (winResult.hasBonus) {
                    matchText += " + 보너스";
                }
                tvMatchCount.setText(matchText);

            } else {
                // 미당첨인 경우
                llWinInfo.setVisibility(View.GONE);
                tvNoWin.setVisibility(View.VISIBLE);
            }
        } else {
            // 단순 표시 모드: 당첨 정보 숨김
            llWinInfo.setVisibility(View.GONE);
            tvNoWin.setVisibility(View.GONE);
        }

        return gameView;
    }

    /**
     * 로또볼에 번호별 색상 적용
     */
    private void applyLottoBallColor(TextView numberView, int number) {
        if (number >= 1 && number <= 10) {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball_yellow);
        } else if (number >= 11 && number <= 20) {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball_blue);
        } else if (number >= 21 && number <= 30) {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball_red);
        } else if (number >= 31 && number <= 40) {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball_gray);
        } else if (number >= 41 && number <= 45) {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball_green);
        } else {
            numberView.setBackgroundResource(R.drawable.bg_lotto_ball);
        }
    }

    /**
     * 에러 표시 및 종료
     */
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();  // 즉시 중단 (shutdown 대신 shutdownNow 사용)
            try {
                // 최대 2초 대기 후 강제 종료
                if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Executor termination interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}