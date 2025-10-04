package app.grapekim.smartlotto.data.local.room;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import app.grapekim.smartlotto.data.local.room.dao.AiGenerationLogDao;
import app.grapekim.smartlotto.data.local.room.dao.GeneratedPickDao;
import app.grapekim.smartlotto.data.local.room.dao.LottoDrawHistoryDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberPairsDao;
import app.grapekim.smartlotto.data.local.room.dao.NumberStatisticsDao;
import app.grapekim.smartlotto.data.local.room.entity.AiGenerationLogEntity;
import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;
import app.grapekim.smartlotto.data.local.room.entity.LottoDrawHistoryEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberPairsEntity;
import app.grapekim.smartlotto.data.local.room.entity.NumberStatisticsEntity;

@Database(
        entities = {
                GeneratedPickEntity.class,           // 기존 Entity
                LottoDrawHistoryEntity.class,        // AI 기능: 과거 당첨번호
                NumberStatisticsEntity.class,        // AI 기능: 번호별 통계
                NumberPairsEntity.class,             // AI 기능: 번호 쌍 분석
                AiGenerationLogEntity.class          // AI 기능: 생성 기록
        },
        version = 5,                             // 버전 4 → 5로 증가
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    // ==================== 기존 DAO ====================
    public abstract GeneratedPickDao generatedPickDao();

    // ==================== AI 기능 DAO들 ====================
    public abstract LottoDrawHistoryDao lottoDrawHistoryDao();
    public abstract NumberStatisticsDao numberStatisticsDao();
    public abstract NumberPairsDao numberPairsDao();
    public abstract AiGenerationLogDao aiGenerationLogDao();

    private static volatile AppDatabase INSTANCE;

    /** 버전 1 → 2: 결과 확인 기능 필드 추가 */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 새로운 컬럼들 추가
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN result_checked INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN result_rank INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN result_match_count INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN target_round INTEGER");
        }
    };

    /** 버전 2 → 3: QR 다중 게임 지원 필드 추가 */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // QR 그룹 ID (같은 QR에서 나온 게임들을 묶음)
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN qr_group_id TEXT");

            // 게임 라벨 (A, B, C, D, E)
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN game_label TEXT");

            // QR 원본 데이터 (디버깅용)
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN qr_raw_data TEXT");

            // QR에서 파싱된 구매시간
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN purchase_time INTEGER");

            // QR에서 파싱된 회차 정보
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN parsed_round INTEGER");

            // 데이터 소스 타입 (기본값: "GENERATED")
            database.execSQL("ALTER TABLE generated_picks ADD COLUMN source_type TEXT NOT NULL DEFAULT 'GENERATED'");
        }
    };

    /** 버전 3 → 4: AI 번호 생성 기능 테이블들 추가 (Entity 구조에 맞게 수정) */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

            // 1. 로또 당첨번호 이력 테이블 생성 (LottoDrawHistoryEntity와 매칭)
            database.execSQL("CREATE TABLE IF NOT EXISTS `lotto_draw_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`draw_number` INTEGER NOT NULL, " +
                    "`draw_date` TEXT NOT NULL, " +
                    "`number1` INTEGER NOT NULL, " +
                    "`number2` INTEGER NOT NULL, " +
                    "`number3` INTEGER NOT NULL, " +
                    "`number4` INTEGER NOT NULL, " +
                    "`number5` INTEGER NOT NULL, " +
                    "`number6` INTEGER NOT NULL, " +
                    "`bonus_number` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)" +
                    ")");

            // 로또 당첨번호 이력 인덱스 생성
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lotto_draw_history_draw_number` ON `lotto_draw_history` (`draw_number`)");

            // 2. 번호별 통계 테이블 생성 (NumberStatisticsEntity와 매칭)
            database.execSQL("CREATE TABLE IF NOT EXISTS `number_statistics` (" +
                    "`number` INTEGER PRIMARY KEY NOT NULL, " +
                    "`appearance_count` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_draw_number` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_appearance_gap` INTEGER NOT NULL DEFAULT 0, " +
                    "`popularity_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`neglect_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`trend_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`is_odd` INTEGER NOT NULL, " +
                    "`last_digit` INTEGER NOT NULL, " +
                    "`avoidance_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`updated_at` INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)" +
                    ")");

            // 3. 번호 쌍 분석 테이블 생성 (NumberPairsEntity와 매칭)
            database.execSQL("CREATE TABLE IF NOT EXISTS `number_pairs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`number1` INTEGER NOT NULL, " +
                    "`number2` INTEGER NOT NULL, " +
                    "`pair_count` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_draw_together` INTEGER NOT NULL DEFAULT 0, " +
                    "`pair_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`updated_at` INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)" +
                    ")");

            // 번호 쌍 인덱스 생성
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_number_pairs_number1_number2` ON `number_pairs` (`number1`, `number2`)");

            // 4. AI 생성 기록 테이블 생성 (AiGenerationLogEntity와 매칭)
            database.execSQL("CREATE TABLE IF NOT EXISTS `ai_generation_log` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`generated_numbers` TEXT NOT NULL, " +
                    "`strategies_used` TEXT NOT NULL, " +
                    "`quality_score` REAL NOT NULL DEFAULT 0.0, " +
                    "`generation_method` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000), " +
                    "`is_saved` INTEGER NOT NULL DEFAULT 0, " +
                    "`notes` TEXT" +
                    ")");

            // AI 생성 기록 인덱스 생성
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_generation_log_created_at` ON `ai_generation_log` (`created_at`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_generation_log_is_saved` ON `ai_generation_log` (`is_saved`)");

            // 5. 번호별 통계 기본 데이터 삽입 (1-45번 초기화)
            for (int i = 1; i <= 45; i++) {
                boolean isOdd = (i % 2 == 1);
                int lastDigit = i % 10;
                double avoidanceScore = calculateDefaultAvoidanceScore(i);

                database.execSQL("INSERT OR IGNORE INTO number_statistics " +
                                "(number, is_odd, last_digit, avoidance_score, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?)",
                        new Object[]{i, isOdd ? 1 : 0, lastDigit, avoidanceScore, System.currentTimeMillis()});
            }
        }

        // 기본 대중 기피도 점수 계산 헬퍼 메서드
        private double calculateDefaultAvoidanceScore(int number) {
            // 기피되는 번호들 (높은 점수)
            if (number == 4 || number == 13 || number == 14 || number == 24 || number == 34 || number == 44) {
                return 80.0;
            }

            // 중간 정도 기피
            if (number % 10 == 4 || number % 10 == 0) {
                return 60.0;
            }

            // 선호되는 번호들 (낮은 점수)
            if (number == 7 || number == 3 || number == 8 || number == 1 || number == 9) {
                return 20.0;
            }

            // 라운드 넘버 (10, 20, 30, 40) - 많이 선택됨
            if (number % 10 == 0) {
                return 30.0;
            }

            return 50.0; // 기본값
        }
    };

    /** 버전 4 → 5: CSV 데이터 업데이트 강제 (신규 추가) */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 기존 당첨번호 데이터와 통계 데이터 삭제하여 새로운 CSV 데이터 로드 강제
            database.execSQL("DELETE FROM lotto_draw_history");
            database.execSQL("DELETE FROM number_statistics");
            database.execSQL("DELETE FROM number_pairs");

            // 번호별 통계 기본 데이터 다시 삽입
            for (int i = 1; i <= 45; i++) {
                boolean isOdd = (i % 2 == 1);
                int lastDigit = i % 10;
                double avoidanceScore = calculateDefaultAvoidanceScore(i);

                database.execSQL("INSERT INTO number_statistics " +
                                "(number, is_odd, last_digit, avoidance_score, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?)",
                        new Object[]{i, isOdd ? 1 : 0, lastDigit, avoidanceScore, System.currentTimeMillis()});
            }
        }

        private double calculateDefaultAvoidanceScore(int number) {
            if (number == 4 || number == 13 || number == 14 || number == 24 || number == 34 || number == 44) {
                return 80.0;
            }
            if (number % 10 == 4 || number % 10 == 0) {
                return 60.0;
            }
            if (number == 7 || number == 3 || number == 8 || number == 1 || number == 9) {
                return 20.0;
            }
            if (number % 10 == 0) {
                return 30.0;
            }
            return 50.0;
        }
    };

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "lotto.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)  // 새로운 Migration 추가
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}