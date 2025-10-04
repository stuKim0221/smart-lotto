package app.grapekim.smartlotto.data.local.room.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import app.grapekim.smartlotto.data.local.room.entity.GeneratedPickEntity;

import java.util.List;

@Dao
public interface GeneratedPickDao {

    @Insert
    long insert(GeneratedPickEntity e);

    @Update
    void update(GeneratedPickEntity e);

    @Query("UPDATE generated_picks SET favorite = :fav WHERE id = :id")
    void updateFavorite(long id, boolean fav);

    @Query("DELETE FROM generated_picks WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM generated_picks WHERE id = :id AND favorite = 0")
    void deleteByIdIfNotFavorite(long id);

    @Query("DELETE FROM generated_picks")
    void clearAll();

    @Query("DELETE FROM generated_picks WHERE favorite = 0")
    void clearAllExceptFavorites();

    // 정렬: 최신 우선/오래된 우선 (전체)
    @Query("SELECT * FROM generated_picks ORDER BY createdAt DESC")
    LiveData<List<GeneratedPickEntity>> observeAllDesc();

    @Query("SELECT * FROM generated_picks ORDER BY createdAt ASC")
    LiveData<List<GeneratedPickEntity>> observeAllAsc();

    // 즐겨찾기 전용
    @Query("SELECT * FROM generated_picks WHERE favorite = 1 ORDER BY createdAt DESC")
    LiveData<List<GeneratedPickEntity>> observeFavoritesDesc();

    @Query("SELECT * FROM generated_picks WHERE favorite = 1 ORDER BY createdAt ASC")
    LiveData<List<GeneratedPickEntity>> observeFavoritesAsc();

    // ==================== AI 데이터 정리를 위한 새로운 메서드들 ====================

    /**
     * 메서드 타입 일괄 변경 ("수정" → "AI")
     */
    @Query("UPDATE generated_picks SET method = :newMethod WHERE method = :oldMethod")
    void updateMethodFromTo(String oldMethod, String newMethod);

    /**
     * 특정 메서드로 저장된 항목들 조회
     */
    @Query("SELECT * FROM generated_picks WHERE method = :method ORDER BY createdAt DESC")
    List<GeneratedPickEntity> getByMethod(String method);

    /**
     * 제목 업데이트
     */
    @Query("UPDATE generated_picks SET title = :newTitle WHERE id = :id")
    void updateTitle(long id, String newTitle);

    // ==================== 결과 확인 기능 ====================

    /**
     * 로또 결과 업데이트
     * @param id 항목 ID
     * @param rank 등수 (1~5=등수, -1=낙첨, 0=미확인)
     * @param matchCount 맞춘 번호 개수
     * @param targetRound 해당 로또 회차 번호
     */
    @Query("UPDATE generated_picks SET " +
            "result_checked = 1, " +
            "result_rank = :rank, " +
            "result_match_count = :matchCount, " +
            "target_round = :targetRound " +
            "WHERE id = :id")
    void updateResult(long id, int rank, int matchCount, int targetRound);

    // ==================== QR 다중 게임 지원 (신규) ====================

    /**
     * QR 그룹에 속한 모든 게임 조회 (게임 라벨 순서로 정렬)
     */
    @Query("SELECT * FROM generated_picks WHERE qr_group_id = :qrGroupId ORDER BY game_label ASC")
    List<GeneratedPickEntity> getByQrGroupId(String qrGroupId);

    /**
     * QR 그룹 전체 삭제
     */
    @Query("DELETE FROM generated_picks WHERE qr_group_id = :qrGroupId")
    void deleteByQrGroupId(String qrGroupId);

    /**
     * QR 그룹 전체 삭제 (즐겨찾기가 아닌 것만)
     */
    @Query("DELETE FROM generated_picks WHERE qr_group_id = :qrGroupId AND favorite = 0")
    void deleteByQrGroupIdIfNotFavorite(String qrGroupId);

    /**
     * QR 그룹 전체 즐겨찾기 설정
     */
    @Query("UPDATE generated_picks SET favorite = :favorite WHERE qr_group_id = :qrGroupId")
    void updateQrGroupFavorite(String qrGroupId, boolean favorite);

    /**
     * 특정 항목의 QR 그룹 ID 조회
     */
    @Query("SELECT qr_group_id FROM generated_picks WHERE id = :id")
    String getQrGroupId(long id);

    /**
     * QR 그룹의 게임 수 조회
     */
    @Query("SELECT COUNT(*) FROM generated_picks WHERE qr_group_id = :qrGroupId")
    int getQrGroupSize(String qrGroupId);

    /**
     * 특정 항목의 생성 방식 조회
     */
    @Query("SELECT method FROM generated_picks WHERE id = :id")
    String getMethod(long id);

    /**
     * 모든 QR 그룹 ID 목록 조회 (중복 제거)
     */
    @Query("SELECT DISTINCT qr_group_id FROM generated_picks WHERE qr_group_id IS NOT NULL")
    List<String> getAllQrGroupIds();

    /**
     * 특정 항목의 소스 타입 조회
     */
    @Query("SELECT source_type FROM generated_picks WHERE id = :id")
    String getSourceType(long id);

    /**
     * QR 게임 여부 확인 (method 또는 source_type 기준)
     */
    @Query("SELECT COUNT(*) > 0 FROM generated_picks WHERE id = :id AND (method = 'QR' OR source_type LIKE 'QR_%')")
    boolean isQrGame(long id);

    /**
     * QR 그룹이 즐겨찾기인지 확인 (그룹 내 첫 번째 항목 기준)
     */
    @Query("SELECT favorite FROM generated_picks WHERE qr_group_id = :qrGroupId ORDER BY game_label ASC LIMIT 1")
    Boolean getQrGroupFavoriteStatus(String qrGroupId);

    /**
     * 특정 QR 그룹의 첫 번째 게임 조회 (대표 항목용)
     */
    @Query("SELECT * FROM generated_picks WHERE qr_group_id = :qrGroupId ORDER BY game_label ASC LIMIT 1")
    GeneratedPickEntity getQrGroupRepresentative(String qrGroupId);

    /**
     * QR 그룹별 통계 (관리용)
     */
    @Query("SELECT qr_group_id, COUNT(*) as game_count, " +
            "MIN(createdAt) as created_at, " +
            "MAX(favorite) as is_favorite " +
            "FROM generated_picks " +
            "WHERE qr_group_id IS NOT NULL " +
            "GROUP BY qr_group_id " +
            "ORDER BY created_at DESC")
    List<QrGroupSummary> getQrGroupSummaries();

    /**
     * QR 그룹 통계 정보 클래스
     */
    class QrGroupSummary {
        public String qr_group_id;
        public int game_count;
        public long created_at;
        public boolean is_favorite;
    }

    // ==================== 향상된 조회 메서드들 ====================

    /**
     * QR 게임만 조회 (최신순)
     */
    @Query("SELECT * FROM generated_picks WHERE method = 'QR' OR source_type LIKE 'QR_%' ORDER BY createdAt DESC")
    LiveData<List<GeneratedPickEntity>> observeQrGamesDesc();

    /**
     * 일반 게임만 조회 (QR 제외, 최신순)
     */
    @Query("SELECT * FROM generated_picks WHERE method != 'QR' AND (source_type IS NULL OR source_type NOT LIKE 'QR_%') ORDER BY createdAt DESC")
    LiveData<List<GeneratedPickEntity>> observeNonQrGamesDesc();

    /**
     * 전체 조회 (QR 그룹별로 묶어서 정렬)
     * QR 그룹은 대표 항목 하나만, 일반 게임은 개별적으로
     */
    @Query("SELECT * FROM generated_picks " +
            "WHERE qr_group_id IS NULL " +
            "   OR id IN (" +
            "       SELECT MIN(id) FROM generated_picks " +
            "       WHERE qr_group_id IS NOT NULL " +
            "       GROUP BY qr_group_id" +
            "   ) " +
            "ORDER BY createdAt DESC")
    LiveData<List<GeneratedPickEntity>> observeGroupedGamesDesc();
}