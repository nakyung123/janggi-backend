package com.nakyung.janggi.events.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>진짜 Postgres 로 도는 유일한 테스트다.</b>
 *
 * <p>H2 로는 여기 있는 것을 하나도 확인할 수 없다 - Flyway 의 Postgres 마이그레이션이
 * 아예 안 돌고, {@code ON CONFLICT} 도 {@code JSONB} 도 없다. 가짜로 통과시키면
 * <b>"테스트는 초록인데 실서버에서 깨지는"</b> 바로 그 종류가 된다. 이 저장소에서 이미
 * 당했다 - jar 로는 멀쩡한데 컨테이너가 시작조차 못 한 일이 있었다.
 *
 * <p><b>{@code @EnabledIfDockerAvailable} 이 붙은 까닭</b> - {@code Dockerfile} 안에서
 * {@code ./gradlew build} 를 돌릴 때는 <b>도커 안에 도커가 없다.</b> 그때 이 테스트는
 * 건너뛰고 나머지는 그대로 돈다. CI 러너에는 도커가 있어서 거기서는 실제로 돈다 -
 * 즉 <b>이미지가 만들어지기 전에 한 번은 반드시 돈다.</b>
 *
 * <p>컨텍스트가 뜨는 것 자체가 검사 하나다. {@code ddl-auto=validate} 가 켜져 있어서
 * {@code EventEntity} 와 {@code V1__event.sql} 이 어긋나면 <b>테스트가 시작도 못 한다.</b>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            // 테스트 기본값(H2 · Flyway 끔)을 여기서만 되돌린다.
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@Testcontainers
@EnabledIfDockerAvailable
@Transactional
class EventRepositoryTest {

    /**
     * 실서버와 같은 18 로 맞춘다. 메이저가 다르면 문법이 조용히 갈린다.
     *
     * <p>{@code PostgreSQLContainer<?>} 가 아니다 - Testcontainers 2.x 에서 <b>제네릭이
     * 사라졌다.</b> 1.x 예제를 그대로 베끼면 "does not take parameters" 로 막힌다.
     */
    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired EventRepository 저장소;

    private int 넣기(String sid, String 이름, Long 보낸시각, String data) {
        return 저장소.넣기(
                Instant.parse("2026-10-11T12:00:00Z"), 이름, sid, 보낸시각,
                "prod", "1.0.0", "사파리 17", "아이폰", null, true,
                (short) 8, (short) 4, "390x844", data);
    }

    @Test
    @DisplayName("넣으면 들어가고, 읽으면 넣은 그대로 나온다")
    void 넣고_읽기() {
        assertThat(넣기("aaaaaaaa", "app_open", 1L, "{\"ref\":\"첫배포\"}")).isEqualTo(1);

        var 줄 = 저장소.findAll().stream().filter(e -> "aaaaaaaa".equals(e.getSid())).findFirst();
        assertThat(줄).isPresent();
        assertThat(줄.get().getName()).isEqualTo("app_open");
        assertThat(줄.get().getBrowser()).isEqualTo("사파리 17");
        // 한글이 DB 를 한 바퀴 돌아도 그대로인지. 이 저장소는 주석도 값도 거의 한글이다.
        assertThat(줄.get().getData()).contains("첫배포");
        assertThat(줄.get().getReceivedAt()).isEqualTo(Instant.parse("2026-10-11T12:00:00Z"));
    }

    @Test
    @DisplayName("똑같은 것이 또 오면 두 번째는 버린다")
    void 중복은_버린다() {
        assertThat(넣기("bbbbbbbb", "app_open", 100L, null)).isEqualTo(1);
        // sendBeacon 은 at-least-once 다. 망이 끊기면 브라우저가 묶음을 통째로 다시 보낸다.
        assertThat(넣기("bbbbbbbb", "app_open", 100L, null)).isEqualTo(0);

        assertThat(저장소.findAll().stream().filter(e -> "bbbbbbbb".equals(e.getSid())))
                .hasSize(1);
    }

    @Test
    @DisplayName("같은 세션에서 같은 이름이라도 보낸 시각이 다르면 둘 다 받는다")
    void 한_판_더_둔_것은_중복이_아니다() {
        // 한 세션에서 세 판을 두면 first_move 가 세 번 찍힌다. 2026-10-11 에 실제로 있었다.
        assertThat(넣기("cccccccc", "first_move", 1919L, null)).isEqualTo(1);
        assertThat(넣기("cccccccc", "first_move", 17715L, null)).isEqualTo(1);
        assertThat(넣기("cccccccc", "first_move", 174589L, null)).isEqualTo(1);

        assertThat(저장소.findAll().stream().filter(e -> "cccccccc".equals(e.getSid())))
                .hasSize(3);
    }

    @Test
    @DisplayName("보낸 시각이 없으면 걸러지지 않는다 - 알고 그렇게 둔 것이다")
    void 보낸시각이_없으면_안_걸러진다() {
        // Postgres 는 NULL 끼리 같다고 보지 않아서 유일 인덱스에 안 걸린다. 비는 것은
        // 전부 내가 돌린 curl·CI 시험이고(브라우저는 항상 보낸다) 걸러질 까닭이 없다.
        // **문서에만 적어 두면 다음 사람이 버그로 보고 고친다.** 그래서 테스트로 박는다.
        assertThat(넣기("dddddddd", "app_open", null, null)).isEqualTo(1);
        assertThat(넣기("dddddddd", "app_open", null, null)).isEqualTo(1);

        assertThat(저장소.findAll().stream().filter(e -> "dddddddd".equals(e.getSid())))
                .hasSize(2);
    }
}
