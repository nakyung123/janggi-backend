package com.nakyung.janggi.events.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.events.event.EventService;
import com.nakyung.janggi.events.event.dto.EventRequest;
import java.util.List;
import java.util.Map;
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
}
