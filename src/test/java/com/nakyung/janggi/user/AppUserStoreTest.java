package com.nakyung.janggi.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 로그인 두 문장이 <b>네 경우를 다 맞게 처리하나</b> 본다.
 *
 * <p><b>진짜 Postgres 가 필요하다.</b> {@code ON CONFLICT ... DO UPDATE ... RETURNING} 과
 * {@code make_interval} 은 H2 에 없고, 이 코드가 보려는 것이 바로 그 문장들이다.
 *
 * <p>{@code @EnabledIfDockerAvailable} 이 붙은 까닭은 {@code StatsCountTest} 와 같다 -
 * {@code Dockerfile} 안에서 빌드할 때는 도커 안에 도커가 없다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@EnabledIfDockerAvailable
class AppUserStoreTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired AppUserStore 저장소;
    @Autowired JdbcClient sql;

    /**
     * 줄을 비우고 시작한다.
     *
     * <p>이 테스트는 트랜잭션으로 되돌리지 않는다 - 되돌리면 {@code now()} 를 쓰는 두
     * 문장이 같은 트랜잭션 시각을 보게 되어 "30일 지났나" 를 검사할 수가 없다. 대신
     * 앞 테스트가 남긴 줄을 여기서 치운다. 안 치우면 <b>앞 테스트의 줄이 다음 테스트의
     * 숫자가 된다</b> - StatsCountTest 에서 실제로 겪었다.
     */
    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM app_user").update();
    }

    private void 탈퇴시키기(long id, int 며칠전) {
        sql.sql("UPDATE app_user SET deleted_at = now() - make_interval(days => :d) WHERE id = :id")
                .param("d", 며칠전)
                .param("id", id)
                .update();
    }

    @Test
    @DisplayName("처음 온 사람은 새 계정이고 epoch 이 1 이다")
    void 처음() {
        AppUserStore.로그인한사람 사람 = 저장소.로그인("구글-sub-1");

        assertThat(사람.id()).isPositive();
        assertThat(사람.epoch()).isEqualTo(1);
    }

    @Test
    @DisplayName("쓰고 있는 사람은 같은 계정이고 epoch 이 안 바뀐다 - 로그인할 때마다 다른 기기가 로그아웃되면 안 된다")
    void 다시_로그인() {
        AppUserStore.로그인한사람 처음 = 저장소.로그인("구글-sub-1");
        AppUserStore.로그인한사람 두번째 = 저장소.로그인("구글-sub-1");

        assertThat(두번째.id()).isEqualTo(처음.id());
        assertThat(두번째.epoch()).isEqualTo(처음.epoch());
    }

    @Test
    @DisplayName("30일 안에 탈퇴한 사람은 같은 계정으로 돌아오고 epoch 이 올라간다")
    void 되돌릴_틈_안() {
        AppUserStore.로그인한사람 처음 = 저장소.로그인("구글-sub-1");
        탈퇴시키기(처음.id(), 3);

        AppUserStore.로그인한사람 돌아온사람 = 저장소.로그인("구글-sub-1");

        // 같은 줄이라 기보가 그대로 있다.
        assertThat(돌아온사람.id()).isEqualTo(처음.id());
        // **올라가야 한다.** 탈퇴하면서 남은 쿠키를 그대로 받아 주면 탈퇴가 아무것도
        // 아닌 말이 된다.
        assertThat(돌아온사람.epoch()).isEqualTo(처음.epoch() + 1);
        assertThat(탈퇴중인가(처음.id())).isFalse();
    }

    @Test
    @DisplayName("30일이 지난 뒤 다시 오면 새 계정이다 - 옛 기보는 없다")
    void 되돌릴_틈_지남() {
        AppUserStore.로그인한사람 처음 = 저장소.로그인("구글-sub-1");
        탈퇴시키기(처음.id(), 31);

        AppUserStore.로그인한사람 새사람 = 저장소.로그인("구글-sub-1");

        assertThat(새사람.id()).isNotEqualTo(처음.id());
        assertThat(새사람.epoch()).isEqualTo(1);
        assertThat(줄수()).isEqualTo(1);
    }

    @Test
    @DisplayName("경계 - 딱 30일은 아직 되돌릴 수 있다")
    void 경계() {
        AppUserStore.로그인한사람 처음 = 저장소.로그인("구글-sub-1");
        탈퇴시키기(처음.id(), 29);

        assertThat(저장소.로그인("구글-sub-1").id()).isEqualTo(처음.id());
    }

    @Test
    @DisplayName("탈퇴한 줄이 이름을 계속 묶고 있다 - 그래서 문장이 둘이다")
    void 두_문장인_까닭() {
        // 이 검사는 코드가 아니라 **표의 성질**을 본다. 한 문장(INSERT)만으로 하려
        // 했다면 여기서 터졌을 것이고, 그 까닭을 기억하려고 남겨 둔다.
        AppUserStore.로그인한사람 처음 = 저장소.로그인("구글-sub-1");
        탈퇴시키기(처음.id(), 31);

        assertThatThrownBy(
                        () ->
                                sql.sql("INSERT INTO app_user (google_sub) VALUES ('구글-sub-1')")
                                        .update())
                .hasMessageContaining("app_user_google_sub_key");
    }

    @Test
    @DisplayName("다른 사람은 다른 계정이다")
    void 다른_사람() {
        assertThat(저장소.로그인("구글-sub-1").id()).isNotEqualTo(저장소.로그인("구글-sub-2").id());
        assertThat(줄수()).isEqualTo(2);
    }

    /** NULL 칸을 Object 로 읽으면 null 이 아닌 것이 와서 단정이 헛돈다. 불린으로 묻는다. */
    private boolean 탈퇴중인가(long id) {
        return sql.sql("SELECT deleted_at IS NOT NULL FROM app_user WHERE id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private long 줄수() {
        return sql.sql("SELECT count(*) FROM app_user").query(Long.class).single();
    }
}
