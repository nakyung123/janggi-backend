package com.nakyung.janggi.events.stats;

import com.nakyung.janggi.events.event.EventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 세는 질의만 모아 둔다.
 *
 * <p><b>{@code event/EventRepository} 와 따로 두는 까닭</b> - 거기는 받아 적는 쪽이고
 * 여기는 읽는 쪽이다. 같은 표를 보지만 바뀌는 까닭이 다르다 - 넣는 모양은 프런트가
 * 보내는 것이 바뀔 때, 세는 모양은 <b>내가 궁금한 것이 바뀔 때</b> 바뀐다.
 *
 * <p>세는 것은 전부 네이티브 SQL 이다. 묶어 세는 일은 SQL 이 제일 잘하고, JPQL 로
 * 옮기면 {@code count(distinct ...)} 와 날짜 자르기에서 금방 막힌다.
 */
public interface StatsRepository extends JpaRepository<EventEntity, Long> {

    /** 깔때기 한 칸씩. 네이티브 질의라 칸 이름(별칭)이 getter 이름과 짝이다. */
    interface StepRow {
        String getName();

        long getEvents();

        long getSessions();
    }

    /** 하루치. */
    interface DayRow {
        LocalDate getDay();

        long getSessions();

        long getEvents();
    }

    @Query(
            value =
                    """
                    SELECT name            AS name,
                           count(*)        AS events,
                           count(DISTINCT sid) AS sessions
                      FROM event
                     WHERE env = :env AND received_at >= :from
                     GROUP BY name
                     ORDER BY count(DISTINCT sid) DESC, name
                    """,
            nativeQuery = true)
    List<StepRow> 깔때기(@Param("env") String env, @Param("from") Instant from);

    /**
     * 날짜별.
     *
     * <p><b>한국 시각으로 자른다.</b> UTC 로 자르면 한국의 저녁 9시 이후가 다음 날로
     * 넘어가서, <b>사람이 제일 많이 들어오는 시간대가 둘로 쪼개진다.</b> 저장은 UTC
     * (timestamptz)로 해 두고 볼 때만 옮긴다 - 반대로 하면 서머타임 쓰는 나라에서 깨진다.
     */
    @Query(
            value =
                    """
                    SELECT (received_at AT TIME ZONE 'Asia/Seoul')::date AS day,
                           count(DISTINCT sid) AS sessions,
                           count(*)            AS events
                      FROM event
                     WHERE env = :env AND received_at >= :from
                     GROUP BY 1
                     ORDER BY 1
                    """,
            nativeQuery = true)
    List<DayRow> 날짜별(@Param("env") String env, @Param("from") Instant from);

    /**
     * 기간 전체의 방문 수.
     *
     * <p>깔때기의 {@code app_open} 과 다를 수 있다. 이벤트를 보내다 만 방문이 있으면
     * 여기만 올라간다 - <b>그 차이가 곧 "오다 만 사람" 이다.</b>
     */
    @Query(
            value = "SELECT count(DISTINCT sid) FROM event WHERE env = :env AND received_at >= :from",
            nativeQuery = true)
    long 방문수(@Param("env") String env, @Param("from") Instant from);
}
