package com.nakyung.janggi.events.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.events.event.EventService;
import com.nakyung.janggi.events.event.dto.EventRequest;
import com.nakyung.janggi.events.stats.dto.Stats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 세는 질의를 <b>진짜 Postgres</b> 로 본다.
 *
 * <p>H2 로는 하나도 확인되지 않는다 - {@code AT TIME ZONE} 도 {@code ::date} 도 없어서
 * 500 이 난다. 막는 쪽은 {@link StatsTest} 가 H2 로 빠르게 본다.
 *
 * <p>{@code @EnabledIfDockerAvailable} 이 붙은 까닭은 {@code EventRepositoryTest} 와
 * 같다 - {@code Dockerfile} 안에서 빌드할 때는 도커 안에 도커가 없다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@EnabledIfDockerAvailable
class StatsCountTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired StatsService 통계;
    @Autowired EventService 받는곳;
    @Autowired StatsRepository 저장소;

    /**
     * <b>테스트마다 표를 비운다.</b> 이 테스트들은 {@code NOT_SUPPORTED} 라 트랜잭션이
     * 없고, 그래서 끝나도 되돌려지지 않는다 - 비우지 않으면 <b>앞 테스트가 넣은 것이
     * 뒤 테스트의 숫자가 된다.</b> 실제로 그래서 두 개가 깨졌다("방문 2를 기대했는데
     * 10"). 묶어 세는 표는 전부 이 함정을 밟는다.
     */
    @BeforeEach
    void 비우기() {
        저장소.deleteAllInBatch();
    }

    private static EventRequest 이벤트(String 이름, String sid, String env, long 보낸시각) {
        return new EventRequest(
                이름, sid, "0.1.0", "사파리 17", "아이폰", null, true, 6, 16, "390x844",
                보낸시각, env, Map.of("conn", "4g"));
    }

    /**
     * 테스트 트랜잭션 밖에서 돈다. 받는 쪽이 제 트랜잭션으로 넣고, 세는 쪽이 그것을
     * <b>커밋된 뒤에</b> 읽어야 실제와 같은 모양이 된다.
     */
    @Test
    @DisplayName("prod 만 세고, 한 사람이 여러 번 해도 방문은 하나로 센다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 세는_규칙() {
        받는곳.record(
                List.of(
                        이벤트("app_open", "stat0001", "prod", 1L),
                        이벤트("engine_ready", "stat0001", "prod", 2L),
                        // 같은 방문에서 세 판을 뒀다. 이벤트는 셋, 사람은 하나다.
                        이벤트("first_move", "stat0001", "prod", 3L),
                        이벤트("first_move", "stat0001", "prod", 4L),
                        이벤트("first_move", "stat0001", "prod", 5L),
                        이벤트("app_open", "stat0002", "prod", 6L),
                        // **내가 확인하러 들어간 것.** 세면 안 된다.
                        이벤트("app_open", "statdev1", "dev", 7L),
                        이벤트("first_move", "statdev1", "dev", 8L)));

        var 본것 = 통계.보기(7);

        assertThat(본것.env()).isEqualTo("prod");
        assertThat(본것.sessions()).as("dev 는 빼고 센다").isEqualTo(2);

        var 첫수 = 본것.funnel().stream().filter(s -> s.name().equals("first_move")).findFirst();
        assertThat(첫수).isPresent();
        // **이 둘을 가르는 것이 이 표의 핵심이다.** 안 가르면 "몇 명이 수를 뒀나" 를 묻고
        // "몇 수를 뒀나" 를 받게 된다.
        assertThat(첫수.get().events()).as("수를 둔 횟수").isEqualTo(3);
        assertThat(첫수.get().sessions()).as("수를 둔 방문 수").isEqualTo(1);

        assertThat(본것.funnel()).noneSatisfy(s -> assertThat(s.name()).isEqualTo("없는이름"));
        assertThat(본것.daily()).isNotEmpty();
        assertThat(본것.daily().getFirst().sessions()).isPositive();
    }

    @Test
    @DisplayName("기간을 터무니없이 주면 막지 않고 쓸 만한 값으로 민다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 기간은_밀어_준다() {
        // 400 으로 돌려보내면 보는 사람(나 하나)이 숫자 하나 잘못 적었다고 아무것도 못 본다.
        assertThat(통계.보기(null)).isNotNull();
        assertThat(통계.보기(0)).isNotNull();
        assertThat(통계.보기(-5)).isNotNull();
        assertThat(통계.보기(99999)).isNotNull();
    }

    /** 기기와 {@code data} 를 바꿔 넣을 수 있는 쪽. 위의 것은 이것을 짧게 부른 꼴이다. */
    private static EventRequest 이벤트(
            String 이름,
            String sid,
            long 보낸시각,
            String browser,
            String os,
            String inApp,
            Map<String, Object> data) {
        return new EventRequest(
                이름, sid, "0.1.0", browser, os, inApp, true, 6, 16, "390x844",
                보낸시각, "prod", data);
    }

    private static EventRequest 엔진이벤트(String sid, long 보낸시각, int ms) {
        return 이벤트("engine_ready", sid, 보낸시각, "크롬 154", "윈도우", null, Map.of("ms", ms));
    }

    private static Stats.Device 찾기(List<Stats.Device> 표, String kind, String label) {
        return 표.stream()
                .filter(d -> d.kind().equals(kind) && d.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError(kind + " / " + label + " 없음: " + 표));
    }

    private static long 찾기(List<Stats.Referrer> 표, String label) {
        return 표.stream()
                .filter(r -> r.label().equals(label))
                .mapToLong(Stats.Referrer::sessions)
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " 없음: " + 표));
    }

    /**
     * <b>이 테스트가 "부풀리기" 를 잡는다.</b> 이벤트를 그냥 {@code GROUP BY browser}
     * 하면 크롬이 4, 사파리가 1 로 나와 <b>크롬이 네 배 인기있는 것처럼 보인다.</b>
     * 실제로는 방문이 하나씩이고, 크롬 쪽 사람이 세 판을 더 둔 것뿐이다.
     *
     * <p>엔진 중앙값도 방문 단위다 - 한 방문에서 두 번 떴어도 한 표다.
     */
    @Test
    @DisplayName("기기는 방문 단위로 센다 - 이벤트 많이 보낸 사람이 부풀리지 않는다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 기기는_방문단위() {
        받는곳.record(
                List.of(
                        이벤트("app_open", "dev00001", 101L, "크롬 154", "안드로이드", null, Map.of()),
                        이벤트(
                                "engine_ready", "dev00001", 102L, "크롬 154", "안드로이드", null,
                                Map.of("ms", 800)),
                        이벤트("first_move", "dev00001", 103L, "크롬 154", "안드로이드", null, Map.of()),
                        이벤트("first_move", "dev00001", 104L, "크롬 154", "안드로이드", null, Map.of()),
                        이벤트("first_move", "dev00001", 105L, "크롬 154", "안드로이드", null, Map.of()),
                        // 사파리 쪽은 엔진까지만 가고 수를 안 뒀다.
                        이벤트("app_open", "dev00002", 106L, "사파리 17", "아이폰", "카카오톡", Map.of()),
                        이벤트(
                                "engine_ready", "dev00002", 107L, "사파리 17", "아이폰", "카카오톡",
                                Map.of("ms", 4000))));

        var 기기 = 통계.보기(7).devices();

        var 크롬 = 찾기(기기, "브라우저", "크롬 154");
        assertThat(크롬.sessions()).as("판을 세 번 뒀어도 방문은 하나다").isEqualTo(1);
        assertThat(크롬.engine()).isEqualTo(1);
        assertThat(크롬.moved()).isEqualTo(1);
        assertThat(크롬.p50()).as("엔진 중앙값").isEqualTo(800);

        var 사파리 = 찾기(기기, "브라우저", "사파리 17");
        assertThat(사파리.sessions()).isEqualTo(1);
        assertThat(사파리.engine()).isEqualTo(1);
        assertThat(사파리.moved()).as("엔진은 떴지만 수를 안 뒀다").isZero();
        assertThat(사파리.p50()).isEqualTo(4000);

        // OS 와 인앱도 같은 방식으로 묶인다. inApp 이 없으면 (앱 아님) 으로 모인다.
        assertThat(찾기(기기, "OS", "안드로이드").sessions()).isEqualTo(1);
        assertThat(찾기(기기, "인앱", "카카오톡").sessions()).isEqualTo(1);
        assertThat(찾기(기기, "인앱", "(앱 아님)").sessions()).isEqualTo(1);
    }

    /**
     * <b>빈 값과 칸이 없는 것은 다른 말이다.</b> 섞으면 "직접 들어온 둘" 이 되는데,
     * 하나는 정말 직접 온 것이고 하나는 {@code ref} 를 보내기 전에 쌓인 옛 기록이다.
     * 홍보가 됐는지 보려고 만든 표에서 이 둘을 섞으면 숫자가 아무 말도 안 한다.
     */
    @Test
    @DisplayName("들어온 곳 - 빈 값(직접)과 칸 없음(옛 기록)을 가른다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 들어온곳을_가른다() {
        받는곳.record(
                List.of(
                        이벤트(
                                "app_open", "ref00001", 201L, "크롬 154", "윈도우", null,
                                Map.of("ref", "m.dcinside.com")),
                        이벤트(
                                "app_open", "ref00002", 202L, "크롬 154", "윈도우", null,
                                Map.of("ref", "")),
                        // ref 칸이 아예 없다 - 보내기 전에 쌓인 것이다.
                        이벤트(
                                "app_open", "ref00003", 203L, "크롬 154", "윈도우", null,
                                Map.of("conn", "4g"))));

        var 들어온곳 = 통계.보기(7).referrers();

        assertThat(찾기(들어온곳, "m.dcinside.com")).isEqualTo(1);
        assertThat(찾기(들어온곳, "(직접)")).as("빈 값").isEqualTo(1);
        assertThat(찾기(들어온곳, "(안 보냄)")).as("칸이 없던 옛 기록").isEqualTo(1);
    }

    /**
     * <b>{@code n} 을 같이 줘야 한다.</b> 표본 수를 모르면 p90 을 실력으로 읽는다.
     * 다섯 건에서 p90 은 사실상 가장 느린 하나다.
     */
    @Test
    @DisplayName("엔진 뜨는 시간 - 표본 수와 함께 준다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 엔진_뜨는_시간() {
        받는곳.record(
                List.of(
                        엔진이벤트("eng00001", 301L, 800),
                        엔진이벤트("eng00002", 302L, 900),
                        엔진이벤트("eng00003", 303L, 1000),
                        엔진이벤트("eng00004", 304L, 1100),
                        엔진이벤트("eng00005", 305L, 7500)));

        var 엔진 = 통계.보기(7).engine();

        assertThat(엔진.n()).as("표본 수").isEqualTo(5);
        assertThat(엔진.p50()).as("가운데 값").isEqualTo(1000);
        assertThat(엔진.worst()).as("꼬리가 어디까지 가나").isEqualTo(7500);
        // p90 은 가장 느린 쪽에 붙는다. 다섯 건에서는 7500 하나가 거의 다 정한다.
        assertThat(엔진.p90()).isGreaterThan(엔진.p50());
    }

    /**
     * 기록이 하나도 없을 때. <b>비우는 단계가 있어서 이 테스트가 진짜 빈 표를 본다.</b>
     *
     * <p>{@code percentile_cont} 는 줄이 없으면 {@code null} 을 주고, 집계 함수가 없는
     * 질의는 <b>줄 자체를 안 줄 수도 있다.</b> 그때 서비스가 {@code null} 을 흘리면 보는
     * 쪽이 "칸이 아예 없는 것" 과 "0건" 을 가르게 된다 - 둘은 같은 말인데.
     */
    @Test
    @DisplayName("기록이 없으면 빈 표와 0건을 준다 - null 이 아니다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 없을_때도_모양은_같다() {
        var 본것 = 통계.보기(7);

        assertThat(본것.sessions()).isZero();
        assertThat(본것.devices()).isNotNull().isEmpty();
        assertThat(본것.referrers()).isNotNull().isEmpty();
        assertThat(본것.engine()).isNotNull();
        assertThat(본것.engine().n()).isZero();
        assertThat(본것.engine().p50()).as("표본이 없으면 값이 없다").isNull();
    }
}
