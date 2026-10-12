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

    /**
     * 기기 한 줄.
     *
     * <p>{@code kind} 가 브라우저·OS·인앱 가운데 무엇인지를 말한다. 세 질의로 나누지
     * 않고 한 줄에 종류를 붙인 까닭은, <b>묶는 방식이 똑같고 묶는 칸만 다르기</b>
     * 때문이다. 네 번째 칸(화면 크기 같은 것)을 더할 때 SQL 한 덩이만 늘어난다.
     */
    interface DeviceRow {
        String getKind();

        String getLabel();

        long getSessions();

        /** 그 기기에서 엔진이 뜬 방문 수. */
        long getEngine();

        /** 그 기기에서 첫 수까지 간 방문 수. */
        long getMoved();

        /** 그 기기의 엔진 뜨는 시간 중앙값(ms). 엔진이 안 뜬 기기면 {@code null} 이다. */
        Integer getP50();
    }

    /** 들어온 곳 한 줄. */
    interface ReferrerRow {
        String getLabel();

        long getSessions();
    }

    /**
     * 엔진 뜨는 시간.
     *
     * <p><b>{@code n} 을 같이 돌려주는 것이 핵심이다.</b> 서른 건에서 p90 은 한두 점이
     * 정하는 값이라, 표본 수 없이 보면 운을 실력으로 읽는다. 값이 없으면 전부
     * {@code null} 이라 박스 타입이다.
     */
    interface EngineRow {
        long getN();

        Integer getP50();

        Integer getP90();

        Integer getWorst();
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

    /**
     * 기기별로 방문·엔진·첫 수를 센다.
     *
     * <p><b>왜 방문 단위로 먼저 묶나.</b> 이벤트를 그냥 {@code GROUP BY browser} 하면
     * <b>이벤트를 많이 보낸 사람이 자기 브라우저를 부풀린다.</b> 세 판 둔 사람의 크롬이
     * 한 판 둔 사람의 사파리보다 다섯 배로 보이는데, 그건 브라우저에 대한 말이 아니다.
     * 먼저 {@code sid} 로 접어서 <b>방문 하나를 한 표</b>로 만든다.
     *
     * <p>{@code max(browser)} 로 접는 까닭 - 한 방문의 모든 이벤트에 같은 값이 실려
     * 오므로 어느 하나를 집어도 같다. {@code max} 는 그 "아무 하나" 를 고르는 가장 싼
     * 방법이다.
     *
     * <p><b>{@code engine}·{@code moved} 가 분모를 만든다.</b> "실패한 사람 중에
     * 사파리가 많다" 는 사파리가 원래 많으면 아무 말도 아니다. 같은 줄에 방문 수가
     * 있어야 비율이 된다.
     */
    @Query(
            value =
                    """
                    WITH visit AS (
                        SELECT sid,
                               max(browser)                       AS browser,
                               max(COALESCE(os, '(모름)'))        AS os,
                               max(COALESCE(in_app, '(앱 아님)')) AS in_app,
                               max(CASE WHEN name = 'engine_ready' THEN 1 ELSE 0 END) AS engine,
                               max(CASE WHEN name = 'first_move'   THEN 1 ELSE 0 END) AS moved,
                               max((data->>'ms')::numeric)
                                   FILTER (WHERE name = 'engine_ready')              AS ms
                          FROM event
                         WHERE env = :env AND received_at >= :from
                         GROUP BY sid
                    )
                    SELECT kind, label, sessions, engine, moved, p50
                      FROM (
                        SELECT 1 AS ord, '브라우저' AS kind, browser AS label,
                               count(*) AS sessions, sum(engine) AS engine, sum(moved) AS moved,
                               round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ms))::int AS p50
                          FROM visit GROUP BY browser
                        UNION ALL
                        SELECT 2, 'OS', os,
                               count(*), sum(engine), sum(moved),
                               round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ms))::int
                          FROM visit GROUP BY os
                        UNION ALL
                        SELECT 3, '인앱', in_app,
                               count(*), sum(engine), sum(moved),
                               round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ms))::int
                          FROM visit GROUP BY in_app
                      ) t
                     ORDER BY ord, sessions DESC, label
                    """,
            nativeQuery = true)
    List<DeviceRow> 기기별(@Param("env") String env, @Param("from") Instant from);

    /**
     * 들어온 곳. {@code app_open} 의 {@code data.ref} 다.
     *
     * <p><b>셋을 가른다.</b> 빈 값은 주소를 직접 치거나 책갈피로 온 것이고, 칸 자체가
     * 없는 것은 {@code ref} 를 보내기 전에 쌓인 옛 기록이다. 섞으면 <b>"직접 들어온
     * 서른두 명" 이라는 거짓</b>이 된다 - 실제로는 스물다섯과 일곱이다.
     *
     * <p><b>{@code data ? 'ref'} 를 쓰지 않는다.</b> Hibernate 가 {@code ?} 를 위치
     * 파라미터로 읽어서 질의가 아예 안 뜬다. {@code jsonb_exists} 가 같은 일을 한다.
     */
    @Query(
            value =
                    """
                    SELECT CASE
                             WHEN data IS NULL OR NOT jsonb_exists(data, 'ref') THEN '(안 보냄)'
                             WHEN data->>'ref' = ''                             THEN '(직접)'
                             ELSE data->>'ref'
                           END                 AS label,
                           count(DISTINCT sid) AS sessions
                      FROM event
                     WHERE env = :env AND received_at >= :from AND name = 'app_open'
                     GROUP BY 1
                     ORDER BY 2 DESC, 1
                    """,
            nativeQuery = true)
    List<ReferrerRow> 들어온곳(@Param("env") String env, @Param("from") Instant from);

    /**
     * 엔진이 뜨기까지 걸린 시간.
     *
     * <p>이벤트 단위로 센다 - 기기별 표와 달리 여기서는 <b>"뜨는 데 얼마 걸리나" 자체</b>가
     * 궁금한 것이라, 한 방문에서 두 번 떴으면 두 번 다 값이다.
     *
     * <p>{@code percentile_cont} 는 값이 없으면 {@code null} 을 준다. {@code n} 이 0 인
     * 것과 "0ms 에 떴다" 를 구별해야 해서 박스 타입으로 받는다.
     */
    @Query(
            value =
                    """
                    SELECT count(*)                                                   AS n,
                           round(percentile_cont(0.5) WITHIN GROUP (ORDER BY ms))::int AS p50,
                           round(percentile_cont(0.9) WITHIN GROUP (ORDER BY ms))::int AS p90,
                           max(ms)::int                                               AS worst
                      FROM (
                        SELECT (data->>'ms')::numeric AS ms
                          FROM event
                         WHERE env = :env AND received_at >= :from
                           AND name = 'engine_ready'
                           AND data IS NOT NULL AND jsonb_exists(data, 'ms')
                      ) t
                    """,
            nativeQuery = true)
    EngineRow 엔진시간(@Param("env") String env, @Param("from") Instant from);
}
