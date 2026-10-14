package com.nakyung.janggi.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.auth.TokenIssuer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 쿠키 → 필터 → DB → 답을 <b>한 줄로 꿰어</b> 본다.
 *
 * <p>여기까지 와야 로그인이 "된다" 고 말할 수 있다. 조각마다 통과한 것과 꿰어서 도는
 * 것은 다르다 - 필터가 쿠키를 읽어 {@code SecurityContext} 에 담고, 컨트롤러가 그것을
 * 받고, 세대가 안 맞으면 {@code false} 가 되는 것까지가 한 흐름이다.
 *
 * <p><b>구글은 부르지 않는다.</b> 구글이 "맞다" 고 해 준 다음부터가 우리 몫이고, 그
 * 다음부터를 쿠키를 손으로 만들어 넣는 것으로 흉내 낸다. 구글과 실제로 주고받는 것은
 * 배포한 뒤에 한 번 해 봐야 아는 것이다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "janggi.guard.limit-per-minute=1000"
        })
@Testcontainers
@EnabledIfDockerAvailable
class MeTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @LocalServerPort int port;

    @Autowired TokenIssuer 토큰;
    @Autowired AppUserStore 창고;
    @Autowired JdbcClient sql;

    private final HttpClient 손님 = HttpClient.newHttpClient();

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM app_user").update();
    }

    /** 발급자가 심는 쿠키를 그대로 꺼내 쓴다 - 손으로 만들면 모양이 틀어진다. */
    private String 쿠키(long id, int epoch) {
        MockHttpServletResponse 받아둘곳 = new MockHttpServletResponse();
        토큰.심기(받아둘곳, id, epoch);
        return TokenIssuer.이름 + "=" + 받아둘곳.getCookie(TokenIssuer.이름).getValue();
    }

    private HttpResponse<String> 나를_묻기(String 쿠키) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"));
        if (쿠키 != null) {
            b.header("Cookie", 쿠키);
        }
        return 손님.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("쿠키가 없으면 거절이 아니라 loggedIn=false - 묻는 자리를 막으면 답을 받을 수 없다")
    void 쿠키가_없으면() throws Exception {
        HttpResponse<String> 답 = 나를_묻기(null);

        assertThat(답.statusCode()).isEqualTo(200);
        assertThat(답.body()).contains("\"loggedIn\":false");
    }

    @Test
    @DisplayName("로그인한 사람의 쿠키면 loggedIn=true - 쿠키·필터·질의가 다 맞아야 여기 온다")
    void 로그인한_사람() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");

        HttpResponse<String> 답 = 나를_묻기(쿠키(사람.id(), 사람.epoch()));

        assertThat(답.statusCode()).isEqualTo(200);
        assertThat(답.body()).contains("\"loggedIn\":true");
    }

    @Test
    @DisplayName("세대가 안 맞으면 false - 탈퇴하고 되살린 뒤 옛 쿠키가 통하면 안 된다")
    void 세대가_다르면() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        String 옛쿠키 = 쿠키(사람.id(), 사람.epoch());

        // 탈퇴 → 되돌릴 틈 안에 다시 로그인하면 세대가 올라간다.
        sql.sql("UPDATE app_user SET deleted_at = now() WHERE id = :id")
                .param("id", 사람.id())
                .update();
        AppUserStore.로그인한사람 돌아온사람 = 창고.로그인("구글-sub-1");

        assertThat(돌아온사람.epoch()).isEqualTo(사람.epoch() + 1);
        assertThat(나를_묻기(옛쿠키).body()).contains("\"loggedIn\":false");
        assertThat(나를_묻기(쿠키(돌아온사람.id(), 돌아온사람.epoch())).body())
                .contains("\"loggedIn\":true");
    }

    @Test
    @DisplayName("탈퇴 중이면 false - 줄은 남아 있어도 로그인은 아니다")
    void 탈퇴_중이면() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        String 쿠키 = 쿠키(사람.id(), 사람.epoch());

        sql.sql("UPDATE app_user SET deleted_at = now() WHERE id = :id")
                .param("id", 사람.id())
                .update();

        assertThat(나를_묻기(쿠키).body()).contains("\"loggedIn\":false");
    }

    @Test
    @DisplayName("고친 쿠키는 없는 것과 같다 - 남의 번호로 바꿔 쓸 수 없다")
    void 고친_쿠키() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        AppUserStore.로그인한사람 남 = 창고.로그인("구글-sub-2");

        String 고친것 =
                쿠키(사람.id(), 사람.epoch())
                        .replace("=" + 사람.id() + "|", "=" + 남.id() + "|");

        assertThat(나를_묻기(고친것).body()).contains("\"loggedIn\":false");
    }

    @Test
    @DisplayName("없는 번호의 쿠키도 false - 30일 지나 지워진 계정이 이렇게 된다")
    void 없는_번호() throws Exception {
        assertThat(나를_묻기(쿠키(999999L, 1)).body()).contains("\"loggedIn\":false");
    }

    @Test
    @DisplayName("탈퇴하면 로그인이 풀리고 쿠키가 지워진다 - 줄은 남는다")
    void 탈퇴() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        String 쿠키 = 쿠키(사람.id(), 사람.epoch());

        HttpResponse<String> 답 = 탈퇴하기(쿠키);

        assertThat(답.statusCode()).isEqualTo(204);
        // **같은 성질로 빈 값을 심어야** 브라우저가 같은 쿠키로 알아보고 지운다.
        assertThat(답.headers().allValues("Set-Cookie"))
                .anyMatch(c -> c.startsWith(TokenIssuer.이름 + "=;"));
        assertThat(나를_묻기(쿠키).body()).contains("\"loggedIn\":false");
        // **줄은 남아 있어야 한다.** 바로 지우면 30일 안에 돌아와도 기보가 없다.
        assertThat(줄수(사람.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("30일 안에 다시 로그인하면 돌아온다 - 같은 번호에 세대만 오른다")
    void 탈퇴하고_돌아오기() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        탈퇴하기(쿠키(사람.id(), 사람.epoch()));

        AppUserStore.로그인한사람 돌아온사람 = 창고.로그인("구글-sub-1");

        assertThat(돌아온사람.id()).isEqualTo(사람.id());
        assertThat(돌아온사람.epoch()).isEqualTo(사람.epoch() + 1);
        assertThat(나를_묻기(쿠키(돌아온사람.id(), 돌아온사람.epoch())).body())
                .contains("\"loggedIn\":true");
    }

    @Test
    @DisplayName("두 번 눌러도 탈퇴한 때가 안 밀린다 - 밀리면 30일이 그때부터 다시 센다")
    void 두_번_탈퇴() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        String 쿠키 = 쿠키(사람.id(), 사람.epoch());

        assertThat(탈퇴하기(쿠키).statusCode()).isEqualTo(204);
        String 처음때 = 탈퇴한때(사람.id());

        // **404 다.** 보안 설정은 쿠키의 서명만 보므로 탈퇴한 사람의 쿠키도 여기까지 온다.
        assertThat(탈퇴하기(쿠키).statusCode()).isEqualTo(404);
        assertThat(탈퇴한때(사람.id())).isEqualTo(처음때);
    }

    @Test
    @DisplayName("쿠키 없이 탈퇴는 404 - 보안 설정이 메서드를 갈라 본다")
    void 쿠키_없이_탈퇴() throws Exception {
        // **CSRF 토큰은 싣는다.** 안 실으면 CSRF 필터가 먼저 거절해서, 이 404 가 보안
        // 설정이 낸 것인지 알 수 없다. 실제로 그랬다 - 보안 설정에서 줄을 빼 봤는데
        // 안 깨졌다. 토큰은 로그인 안 해도 받을 수 있으니, 받아서 보내면 보안 설정만
        // 남는다.
        assertThat(탈퇴하기(null).statusCode()).isEqualTo(404);
        // 묻는 쪽은 그대로 열려 있어야 한다. 둘이 같이 닫히면 로그인 화면이 멈춘다.
        assertThat(나를_묻기(null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("CSRF 토큰 없이 탈퇴하면 거절한다 - 남의 쪽에서 내 쿠키로 계정을 못 지운다")
    void 토큰_없이_탈퇴() throws Exception {
        AppUserStore.로그인한사람 사람 = 창고.로그인("구글-sub-1");
        String 쿠키 = 쿠키(사람.id(), 사람.epoch());

        HttpResponse<String> 답 =
                손님.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                                .header("Cookie", 쿠키)
                                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(답.statusCode()).isNotEqualTo(204);
        assertThat(나를_묻기(쿠키).body()).contains("\"loggedIn\":true");
    }

    /** 프런트가 할 일을 그대로 한다 - 토큰을 한 번 받아 머리글과 쿠키에 같이 싣는다. */
    private HttpResponse<String> 탈퇴하기(String 쿠키) throws Exception {
        String csrf = csrf받기(쿠키);
        return 손님.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                        .header("Cookie", (쿠키 == null ? "" : 쿠키 + "; ") + "XSRF-TOKEN=" + csrf)
                        .header("X-XSRF-TOKEN", csrf)
                        .method("DELETE", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** 쿠키가 {@code null} 이면 로그인 안 한 채로 받는다. 그래도 토큰은 나온다. */
    private String csrf받기(String 쿠키) throws Exception {
        return 나를_묻기(쿠키).headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("CSRF 토큰이 안 왔습니다"));
    }

    private int 줄수(long id) {
        return sql.sql("SELECT count(*) FROM app_user WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }

    private String 탈퇴한때(long id) {
        return sql.sql("SELECT deleted_at::text FROM app_user WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }
}
