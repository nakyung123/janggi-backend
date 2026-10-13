package com.nakyung.janggi.event;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code event} 표에 넣고 읽는다.
 *
 * <p><b>넣는 것만 네이티브 SQL 이다.</b> 중복이 와도 조용히 넘기려면
 * {@code ON CONFLICT DO NOTHING} 이 필요한데 JPA 에 그에 해당하는 것이 없다. 비슷한 흉내
 * (먼저 찾아보고 없으면 넣기)는 <b>찾은 뒤 넣기 전 사이에 남이 넣으면 깨진다.</b> 그
 * 틈이 없는 것이 이 구문의 값어치고, 그것 때문에 여기서는 도구를 내려놓는다.
 *
 * <p>읽는 쪽은 JPA 를 그대로 쓴다. 도구가 맞는 데서만 쓴다.
 */
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    /**
     * 한 건 넣는다. <b>이미 있으면 아무 일도 안 하고 0을 돌려준다.</b>
     *
     * <p>겹침의 기준은 {@code (sid, name, sent_at)} 이다 - {@code V1__event.sql} 의
     * {@code event_once_idx} 와 짝이고, 한쪽만 바꾸면 <b>이 구문이 통째로 실패한다</b>
     * (Postgres 는 맞는 유일 인덱스가 없으면 거절한다). 조용히 안 걸러지는 것이 아니라
     * 큰 소리로 터지는 쪽이라 다행이다.
     *
     * <p><b>{@code ON CONFLICT} 에 칸을 적은 까닭</b> - 적지 않으면 <b>아무 유일 제약
     * 위반이나</b> 다 삼킨다. 지금은 유일 인덱스가 하나뿐이라 결과가 같지만, 나중에 하나
     * 더 생기면 그때부터 엉뚱한 것을 조용히 버린다. 삼킬 것을 콕 집는다.
     *
     * <p>{@code data} 는 글자로 받아 {@code CAST} 로 바꾼다. 드라이버가 글자를 jsonb 칸에
     * 바로 못 넣는다.
     *
     * @return 넣었으면 1, 이미 있어서 건너뛰었으면 0
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO event (received_at, name, sid, sent_at, env, ver, browser, os,
                                       in_app, isolated, cores, memory, screen, data)
                    VALUES (:receivedAt, :name, :sid, :sentAt, :env, :ver, :browser, :os,
                            :inApp, :isolated, :cores, :memory, :screen, CAST(:data AS jsonb))
                    ON CONFLICT (sid, name, sent_at) DO NOTHING
                    """,
            nativeQuery = true)
    int 넣기(
            @Param("receivedAt") Instant receivedAt,
            @Param("name") String name,
            @Param("sid") String sid,
            @Param("sentAt") Long sentAt,
            @Param("env") String env,
            @Param("ver") String ver,
            @Param("browser") String browser,
            @Param("os") String os,
            @Param("inApp") String inApp,
            @Param("isolated") Boolean isolated,
            @Param("cores") Short cores,
            @Param("memory") Short memory,
            @Param("screen") String screen,
            @Param("data") String data);
}
