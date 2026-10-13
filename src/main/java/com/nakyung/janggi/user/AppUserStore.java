package com.nakyung.janggi.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 사람의 줄을 찾거나 만든다.
 *
 * <p><b>두 문장이다. 한 문장으로 안 된다.</b> 탈퇴해도 줄을 바로 지우지 않는데
 * ({@code deleted_at}), {@code google_sub} 에 UNIQUE 가 걸려 있어서 그 줄이 이름을 계속
 * 묶고 있다. 30일이 지났어도 <b>치우는 일이 돌기 전에</b> 다시 로그인하면
 * {@code INSERT} 가 유일 제약에 걸린다. 그래서 만료된 것을 먼저 치우고, 그다음에 넣거나
 * 되살린다.
 *
 * <p><b>잠그지 않는다.</b> 두 문장이 다 조건부라, 같은 사람이 두 창에서 동시에 로그인해도
 * 먼저 확정된 쪽이 이기고 나중 쪽은 ②에서 충돌해 {@code DO UPDATE} 로 빠진다 - <b>둘 다
 * 같은 계정을 받는다.</b> 잠금을 걸면 이 흐름이 느려지기만 한다(DB 왕복이 155ms 다).
 *
 * <p><b>시각은 DB 것을 쓴다</b>({@code now()}). 서버가 두 대일 때 시계가 조금씩 다를 수
 * 있고, "30일이 지났나" 를 두 시계로 판단하면 경계에서 답이 갈린다.
 */
@Repository
public class AppUserStore {

    /** 로그인한 사람. 쿠키에 담을 둘이다. */
    public record 로그인한사람(long id, int epoch) {}

    /**
     * 되돌릴 틈.
     *
     * <p>실수로 탈퇴를 눌렀을 때 이 안에 다시 로그인하면 기보가 그대로 돌아온다. 지나면
     * 같은 구글 계정이어도 <b>새 계정</b>이고 옛 기보는 없다.
     */
    private static final int 되돌릴_수_있는_날 = 30;

    private final JdbcClient db;

    AppUserStore(JdbcClient db) {
        this.db = db;
    }

    /**
     * 넷 중 하나로 끝난다.
     *
     * <table border="1">
     *   <caption>들어온 사람과 결과</caption>
     *   <tr><th>들어온 사람<th>①<th>②<th>결과
     *   <tr><td>처음<td>0줄<td>{@code INSERT}<td>새 계정
     *   <tr><td>쓰고 있는 사람<td>0줄<td>충돌, 그대로<td>그 계정
     *   <tr><td>30일 <b>안</b> 탈퇴<td>0줄<td>{@code deleted_at} 비움 + {@code epoch+1}
     *       <td><b>복구</b>
     *   <tr><td>30일 <b>초과</b> 탈퇴<td><b>삭제</b><td>{@code INSERT}
     *       <td><b>새 계정.</b> 기보는 사라진다
     * </table>
     *
     * <p><b>되살릴 때 {@code token_epoch} 을 올린다.</b> 탈퇴하면서 쿠키가 그대로 남아
     * 있을 수 있는데, 되살렸다고 그 쿠키를 다시 받아 주면 "탈퇴했다" 가 아무것도 아닌
     * 말이 된다. 쓰고 있는 사람은 올리지 않는다 - 그러면 로그인할 때마다 다른 기기에서
     * 로그아웃된다.
     */
    /**
     * 이 쿠키가 아직 쓸 수 있나.
     *
     * <p>탈퇴했거나 세대가 올라갔으면 {@code false} 다. <b>다른 주소에서는 이것을 따로
     * 묻지 않는다</b> - 어차피 하는 질의에 조건으로 얹어서 왕복을 아낀다. 여기만
     * 예외인데, 접어 넣을 질의가 없는 자리이기 때문이다({@code MeController}).
     */
    @Transactional(readOnly = true)
    public boolean 살아있나(long id, int epoch) {
        return db.sql(
                        """
                        SELECT EXISTS (SELECT 1 FROM app_user
                                        WHERE id = :id
                                          AND deleted_at IS NULL
                                          AND token_epoch = :epoch)
                        """)
                .param("id", id)
                .param("epoch", epoch)
                .query(Boolean.class)
                .single();
    }

    @Transactional
    public 로그인한사람 로그인(String googleSub) {
        // ① 되돌릴 틈이 지난 것을 치운다. 기보도 CASCADE 로 같이 간다.
        db.sql(
                        """
                        DELETE FROM app_user
                         WHERE google_sub = :sub
                           AND deleted_at IS NOT NULL
                           AND deleted_at <= now() - make_interval(days => :days)
                        """)
                .param("sub", googleSub)
                .param("days", 되돌릴_수_있는_날)
                .update();

        // ② 넣거나, 틈 안이면 되살린다.
        //
        // **DO NOTHING 이 아니다.** 그러면 쓰고 있는 사람이 로그인할 때 0줄이 돌아와서
        // id 를 모른다. DO UPDATE 로 두면 충돌한 줄이 그대로 RETURNING 된다.
        return db.sql(
                        """
                        INSERT INTO app_user (google_sub) VALUES (:sub)
                        ON CONFLICT (google_sub) DO UPDATE
                           SET deleted_at = NULL,
                               token_epoch = CASE WHEN app_user.deleted_at IS NOT NULL
                                                  THEN app_user.token_epoch + 1
                                                  ELSE app_user.token_epoch END
                        RETURNING id, token_epoch
                        """)
                .param("sub", googleSub)
                .query(
                        (rs, 줄) ->
                                new 로그인한사람(rs.getLong("id"), rs.getInt("token_epoch")))
                .single();
    }
}
