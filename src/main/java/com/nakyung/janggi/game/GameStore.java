package com.nakyung.janggi.game;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nakyung.janggi.common.exception.BadRequestException;
import com.nakyung.janggi.game.dto.GameDetail;
import com.nakyung.janggi.game.dto.GameSummary;
import com.nakyung.janggi.game.dto.GameUpload;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기보를 넣고 꺼내고 지운다.
 *
 * <p><b>모든 메서드가 {@code userId} 와 {@code epoch} 를 같이 받는다.</b> 하나를
 * 빠뜨리면 컴파일이 안 된다 - 그게 이 서명들의 일이다. {@code epoch} 를 빠뜨리면 탈퇴한
 * 계정의 쿠키가 그대로 통하고, {@code userId} 를 빠뜨리면 남의 기보가 보인다.
 *
 * <p><b>계정이 살아 있는지 따로 묻지 않는다.</b> 어차피 하는 질의에 조건으로 얹는다 -
 * DB 왕복이 155ms 라(재 봤다) 한 번 더 묻는 값이 응답 시간의 절반이 된다.
 *
 * <p><b>없는 것과 남의 것을 가르지 않는다.</b> 둘 다 "없다" 로 답한다. 가르면 "거기 뭔가
 * 있다" 를 알려주는 셈이고, {@code TokenFilter} 가 404 를 고른 까닭과 같다.
 */
@Repository
public class GameStore {

    /**
     * 한 사람이 서버에 둘 수 있는 판 수.
     *
     * <p>브라우저는 100판만 든다({@code ARCHIVE_LIMIT}) - localStorage 한도 때문이다.
     * 서버는 그 제약이 없으니 넉넉히 두지만, <b>한도가 없으면 안 된다</b> - 한 판이 4만
     * 자쯤이라 무한히 받으면 무료 저장 1GB 가 한 사람으로 찬다.
     *
     * <p>500판이면 한 사람 20MB 쯤이다. 넘으면 올리기를 거절한다 - <b>오래된 것을 몰래
     * 버리지 않는다.</b> 기보는 지우라고 한 적 없는데 사라지면 안 되는 것이다.
     */
    static final int 한도 = 500;

    /** 국면 개수 상한. 장기 한 판이 이보다 길어지는 일은 없다. */
    static final int 국면_상한 = 600;

    private final JdbcClient db;
    private final ObjectMapper 젝슨;

    GameStore(JdbcClient db, ObjectMapper 젝슨) {
        this.db = db;
        this.젝슨 = 젝슨;
    }

    /**
     * 넣거나 덮어쓴다.
     *
     * <p><b>{@code DO NOTHING} 이 아니라 {@code DO UPDATE} 다.</b> 한 판이 여러 번
     * 올라온다 - 끝나는 순간, 복기를 돌린 뒤, 무르고 다시 둔 뒤. 두 번째를 무시하면
     * <b>복기 등급이 영원히 안 들어온다.</b>
     *
     * <p><b>덮어쓸 때 수가 섞이지 않는다.</b> 수를 따로 표에 두지 않고 한 칸에 담았으므로
     * 통째로 바뀐다 - "100수를 80수로 다시 올리면 81~100수가 남는" 일이 생길 구조가 아니다.
     *
     * @return 그 기보의 서버 번호
     */
    @Transactional
    public long 올리기(long userId, int epoch, GameUpload 올릴것) {
        확인(올릴것);

        // **한도를 먼저 센다.** 덮어쓰기는 줄이 늘지 않으므로 막지 않는다 - 500판을 가진
        // 사람이 복기를 돌려 다시 올리는 것을 거절하면 그게 더 이상한 일이다.
        Integer 지금 =
                db.sql(
                                """
                                SELECT count(*) FROM game
                                 WHERE user_id = :uid
                                   AND client_game_id <> :cid
                                   AND EXISTS (SELECT 1 FROM app_user
                                                WHERE id = :uid AND deleted_at IS NULL
                                                  AND token_epoch = :epoch)
                                """)
                        .param("uid", userId)
                        .param("cid", 올릴것.id())
                        .param("epoch", epoch)
                        .query(Integer.class)
                        .single();
        if (지금 >= 한도) {
            throw new BadRequestException(
                    "서버에 둘 수 있는 기보가 " + 한도 + "판까지입니다. 지운 뒤에 올려 주세요.");
        }

        try {
            return db.sql(
                            """
                            INSERT INTO game (user_id, client_game_id, ended_at, my_side,
                                              level_id, level_name, variant,
                                              result_kind, result_winner, moves_count,
                                              history, reviewed)
                            SELECT :uid, :cid, to_timestamp(:ended / 1000.0), :side,
                                   :levelId, :levelName, :variant,
                                   :kind, :winner, :moves,
                                   CAST(:history AS jsonb), CAST(:reviewed AS jsonb)
                             WHERE EXISTS (SELECT 1 FROM app_user
                                            WHERE id = :uid AND deleted_at IS NULL
                                              AND token_epoch = :epoch)
                            ON CONFLICT (user_id, client_game_id) DO UPDATE
                               SET ended_at      = EXCLUDED.ended_at,
                                   my_side       = EXCLUDED.my_side,
                                   level_id      = EXCLUDED.level_id,
                                   level_name    = EXCLUDED.level_name,
                                   variant       = EXCLUDED.variant,
                                   result_kind   = EXCLUDED.result_kind,
                                   result_winner = EXCLUDED.result_winner,
                                   moves_count   = EXCLUDED.moves_count,
                                   history       = EXCLUDED.history,
                                   reviewed      = EXCLUDED.reviewed,
                                   updated_at    = now()
                            RETURNING id
                            """)
                    .param("uid", userId)
                    .param("epoch", epoch)
                    .param("cid", 올릴것.id())
                    .param("ended", 올릴것.endedAt())
                    .param("side", 올릴것.mySide())
                    .param("levelId", 올릴것.levelId())
                    .param("levelName", 올릴것.levelName())
                    .param("variant", 올릴것.variant())
                    .param("kind", 올릴것.result().kind())
                    .param("winner", 올릴것.result().winner())
                    .param("moves", Math.max(0, 올릴것.history().size() - 1))
                    .param("history", 올릴것.history().toString())
                    .param("reviewed", 비었나(올릴것.reviewed()) ? null : 올릴것.reviewed().toString())
                    .query(Long.class)
                    .optional()
                    // **0줄이면 계정이 없거나 탈퇴했거나 세대가 다르다.** WHERE EXISTS 가
                    // 거짓이면 INSERT 가 한 줄도 안 만들고, 그러면 RETURNING 도 비어 있다.
                    .orElseThrow(() -> new BadRequestException("로그인이 만료되었습니다. 다시 로그인해 주세요."));
        } catch (DataIntegrityViolationException e) {
            // app_user 가 사라진 바로 그 순간 올라오면 외래 키에서 걸린다. 위의 EXISTS 와
            // 같은 뜻이라 같은 말을 한다.
            throw new BadRequestException("로그인이 만료되었습니다. 다시 로그인해 주세요.");
        }
    }

    /**
     * 내 기보 목록. <b>질의 한 번이고 블롭을 뽑지 않는다.</b>
     *
     * <p>커서 페이징을 아직 두지 않는다 - 한도가 500판이고 요약 한 줄이 100바이트쯤이라
     * 전부 줘도 50KB다. <b>필요를 재고 나서</b> 넣는다.
     */
    @Transactional(readOnly = true)
    public List<GameSummary> 목록(long userId, int epoch) {
        return db.sql(
                        """
                        SELECT g.id, g.client_game_id, g.ended_at, g.my_side,
                               g.level_id, g.level_name, g.variant,
                               g.result_kind, g.result_winner, g.moves_count,
                               g.reviewed IS NOT NULL AS has_review
                          FROM game g
                         WHERE g.user_id = :uid
                           AND EXISTS (SELECT 1 FROM app_user
                                        WHERE id = :uid AND deleted_at IS NULL
                                          AND token_epoch = :epoch)
                         ORDER BY g.ended_at DESC, g.id DESC
                         LIMIT :limit
                        """)
                .param("uid", userId)
                .param("epoch", epoch)
                .param("limit", 한도)
                .query(
                        (rs, 줄) ->
                                new GameSummary(
                                        rs.getLong("id"),
                                        rs.getString("client_game_id"),
                                        rs.getTimestamp("ended_at").getTime(),
                                        rs.getString("my_side"),
                                        rs.getString("level_id"),
                                        rs.getString("level_name"),
                                        rs.getString("variant"),
                                        rs.getString("result_kind"),
                                        rs.getString("result_winner"),
                                        rs.getInt("moves_count"),
                                        rs.getBoolean("has_review")))
                .list();
    }

    /** 기보 하나. 남의 것이면 비어 있다 - 없는 것과 똑같이 보인다. */
    @Transactional(readOnly = true)
    public Optional<GameDetail> 하나(long userId, int epoch, long gameId) {
        return db.sql(
                        """
                        SELECT g.id, g.client_game_id, g.ended_at, g.my_side,
                               g.level_id, g.level_name, g.variant,
                               g.result_kind, g.result_winner, g.moves_count,
                               g.history::text AS history, g.reviewed::text AS reviewed
                          FROM game g
                         WHERE g.id = :gid
                           AND g.user_id = :uid
                           AND EXISTS (SELECT 1 FROM app_user
                                        WHERE id = :uid AND deleted_at IS NULL
                                          AND token_epoch = :epoch)
                        """)
                .param("gid", gameId)
                .param("uid", userId)
                .param("epoch", epoch)
                .query(
                        (rs, 줄) ->
                                new GameDetail(
                                        rs.getLong("id"),
                                        rs.getString("client_game_id"),
                                        rs.getTimestamp("ended_at").getTime(),
                                        rs.getString("my_side"),
                                        rs.getString("level_id"),
                                        rs.getString("level_name"),
                                        rs.getString("variant"),
                                        rs.getString("result_kind"),
                                        rs.getString("result_winner"),
                                        rs.getInt("moves_count"),
                                        나무로(rs.getString("history")),
                                        나무로(rs.getString("reviewed"))))
                .optional();
    }

    /** 지운다. 없거나 남의 것이면 {@code false}. */
    @Transactional
    public boolean 지우기(long userId, int epoch, long gameId) {
        return db.sql(
                        """
                        DELETE FROM game
                         WHERE id = :gid
                           AND user_id = :uid
                           AND EXISTS (SELECT 1 FROM app_user
                                        WHERE id = :uid AND deleted_at IS NULL
                                          AND token_epoch = :epoch)
                        """)
                .param("gid", gameId)
                .param("uid", userId)
                .param("epoch", epoch)
                .update()
                > 0;
    }

    /**
     * 내용을 통째로 믿지 않는다.
     *
     * <p>모양을 못 박지는 않지만(프런트가 칸을 더할 수 있어야 한다) <b>우리가 꺼내 쓰는
     * 것</b>과 <b>개수</b>는 본다. 본문 전체 크기는 문지기가 따로 막는다.
     */
    private void 확인(GameUpload 올릴것) {
        JsonNode 국면들 = 올릴것.history();
        if (!국면들.isArray() || 국면들.isEmpty()) {
            throw new BadRequestException("기보에 국면이 없습니다.");
        }
        if (국면들.size() > 국면_상한) {
            throw new BadRequestException("국면이 너무 많습니다. (" + 국면들.size() + ")");
        }
        for (JsonNode 국면 : 국면들) {
            // fen 하나만 본다. 이것이 없으면 브라우저가 판을 그릴 수 없어서, 받아 두면
            // 올라가기는 했는데 열면 빈 판이 되는 모양이 된다.
            if (!국면.isObject() || !국면.path("fen").isString()) {
                throw new BadRequestException("국면에 판 정보(fen)가 없습니다.");
            }
        }
        JsonNode 복기 = 올릴것.reviewed();
        if (!비었나(복기)) {
            if (!복기.isArray()) {
                throw new BadRequestException("복기 모양이 아닙니다.");
            }
            if (복기.size() > 국면_상한) {
                throw new BadRequestException("복기가 너무 깁니다. (" + 복기.size() + ")");
            }
        }
    }

    /**
     * DB 에서 글로 꺼낸 것을 다시 나무로 만든다.
     *
     * <p><b>{@code jsonb} 를 글로 꺼내는 까닭</b> - JDBC 는 {@code jsonb} 를 그냥 글로
     * 준다. 여기서 한 번 읽어 나무로 만들면, 돌려줄 때 잭슨이 <b>그 안을 다시 짜지 않고
     * 그대로 흘린다.</b> 글로 들고 있다가 그냥 내보내면 따옴표가 한 겹 더 붙은 글이 된다.
     *
     * <p>우리가 넣은 것이라 깨질 일이 없다. 깨졌다면 DB 쪽이 상한 것이므로 삼키지 않는다.
     */
    private JsonNode 나무로(String 글) {
        if (글 == null) {
            return null;
        }
        try {
            return 젝슨.readTree(글);
        } catch (tools.jackson.core.JacksonException e) {
            // **Jackson 3 의 예외는 검사 예외가 아니다**(RuntimeException 을 상속한다).
            // 그래서 안 잡아도 컴파일은 되는데, 그러면 "저장된 것이 상했다" 가 잭슨의
            // 말로 그대로 올라간다. 어디서 난 것인지 적어 둔다.
            throw new IllegalStateException("저장된 기보를 읽을 수 없습니다", e);
        }
    }

    private static boolean 비었나(JsonNode 나무) {
        return 나무 == null || 나무.isNull();
    }
}
