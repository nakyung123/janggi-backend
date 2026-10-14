package com.nakyung.janggi.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.auth.TokenIssuer;
import com.nakyung.janggi.user.AppUserStore;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기보를 올리고 받고 지우는 것을 <b>진짜 HTTP 와 진짜 Postgres</b> 로 본다.
 *
 * <p>여기서 보려는 것 넷이다 - 덮어쓰기가 <b>복기 등급을 받아들이나</b>, 목록이
 * <b>블롭을 끌고 오지 않나</b>, 남의 기보가 <b>없는 것처럼 보이나</b>, 그리고
 * {@code jsonb} 가 <b>올린 모양 그대로 돌아오나.</b>
 *
 * <p>H2 로는 하나도 못 본다 - {@code jsonb} 도 {@code ON CONFLICT ... DO UPDATE} 도 없다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "janggi.guard.limit-per-minute=1000",
            "janggi.guard.game-limit-per-minute=1000"
        })
@Testcontainers
@EnabledIfDockerAvailable
class GameApiTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @LocalServerPort int port;

    @Autowired TokenIssuer 토큰;
    @Autowired AppUserStore 사용자창고;
    @Autowired JdbcClient sql;
    @Autowired ObjectMapper 젝슨;

    private final HttpClient 손님 = HttpClient.newHttpClient();

    private String 내쿠키;
    private long 내번호;
    private String 내csrf;

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM game").update();
        sql.sql("DELETE FROM app_user").update();
        AppUserStore.로그인한사람 나 = 사용자창고.로그인("구글-sub-나");
        내번호 = 나.id();
        내쿠키 = 쿠키(나);
        내csrf = csrf받기(내쿠키);
    }

    /**
     * CSRF 토큰을 받아 온다. <b>프런트가 할 일을 그대로 한다.</b>
     *
     * <p>쿠키로 인증하는 주소에 쓰기를 보내려면 토큰이 있어야 한다. 스프링은 그것을
     * 아무도 읽지 않으면 쿠키로 내보내지도 않아서, 먼저 아무 GET 을 한 번 해야 한다.
     */
    private String csrf받기(String 쿠키) {
        try {
            HttpResponse<String> 답 =
                    손님.send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                                    .header("Cookie", 쿠키)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            return 답.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN="))
                    .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF 토큰이 안 왔습니다"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String 쿠키(AppUserStore.로그인한사람 사람) {
        MockHttpServletResponse 받아둘곳 = new MockHttpServletResponse();
        토큰.심기(받아둘곳, 사람.id(), 사람.epoch());
        return TokenIssuer.이름 + "=" + 받아둘곳.getCookie(TokenIssuer.이름).getValue();
    }

    /** 복기까지 돌린 판 하나. 국면은 수 + 1 개다. */
    private String 올릴것(String id, int 수, boolean 복기포함) {
        StringBuilder 국면 = new StringBuilder("[");
        for (int i = 0; i <= 수; i++) {
            if (i > 0) {
                국면.append(',');
            }
            국면.append(
                    """
                    {"fen":"판모양%d","move":%s,"notation":"표기%d","mover":%s,"score":%d}"""
                            .formatted(
                                    i,
                                    i == 0 ? "null" : "\"a%db%d\"".formatted(i, i),
                                    i,
                                    i == 0 ? "null" : "\"cho\"",
                                    i * 10));
        }
        국면.append(']');

        String 복기 =
                복기포함
                        ? """
                          [{"index":0,"mover":"cho","played":"a1b1","playedNotation":"표기1",\
                          "playedPlain":"차를 앞으로 옮겼습니다.","best":"a1c1","bestNotation":"표기2",\
                          "scoreBefore":0,"scoreAfter":-30,"loss":30,"grade":"bad"}]"""
                        : "null";

        return """
               {"id":"%s","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
               "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
               "history":%s,"reviewed":%s}"""
                .formatted(id, 국면, 복기);
    }

    /** 쓰기에는 CSRF 토큰을 같이 싣는다. 읽기는 필요 없다. */
    private HttpResponse<String> 보내기(String 길, String 방법, String 본문, String 쿠키)
            throws Exception {
        String csrf = 쿠키 == null ? null : (쿠키.equals(내쿠키) ? 내csrf : csrf받기(쿠키));
        return 보내기(길, 방법, 본문, 쿠키, csrf);
    }

    private HttpResponse<String> 보내기(
            String 길, String 방법, String 본문, String 쿠키, String csrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + 길));
        if (쿠키 != null) {
            b.header("Cookie", csrf == null ? 쿠키 : 쿠키 + "; XSRF-TOKEN=" + csrf);
        }
        if (csrf != null) {
            b.header("X-XSRF-TOKEN", csrf);
        }
        if (본문 != null) {
            b.header("Content-Type", "application/json");
        }
        b.method(
                방법,
                본문 == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(본문));
        return 손님.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode 나무(String 글) {
        return 젝슨.readTree(글);
    }

    @Test
    @DisplayName("올리고 목록에서 보고 하나를 열어 본다 - 올린 모양 그대로 돌아온다")
    void 왕복() throws Exception {
        HttpResponse<String> 올린답 = 보내기("/games", "POST", 올릴것("판-1", 3, true), 내쿠키);
        assertThat(올린답.statusCode()).isEqualTo(200);
        long 서버번호 = 나무(올린답.body()).path("id").asLong();
        assertThat(서버번호).isPositive();

        JsonNode 목록 = 나무(보내기("/games", "GET", null, 내쿠키).body());
        assertThat(목록.size()).isEqualTo(1);
        assertThat(목록.get(0).path("clientId").asString()).isEqualTo("판-1");
        assertThat(목록.get(0).path("moves").asInt()).isEqualTo(3);
        assertThat(목록.get(0).path("levelName").asString()).isEqualTo("12급");
        assertThat(목록.get(0).path("reviewed").asBoolean()).isTrue();
        // **목록에 판의 내용이 실려 있지 않다.** 실리면 스무 판에 800KB가 오간다.
        assertThat(목록.get(0).has("history")).isFalse();

        JsonNode 하나 = 나무(보내기("/games/" + 서버번호, "GET", null, 내쿠키).body());
        assertThat(하나.path("history").size()).isEqualTo(4);
        assertThat(하나.path("history").get(0).path("fen").asString()).isEqualTo("판모양0");
        assertThat(하나.path("history").get(3).path("move").asString()).isEqualTo("a3b3");
        // 모양을 서버가 못 박지 않았으니, 프런트가 보낸 칸이 그대로 있어야 한다.
        assertThat(하나.path("reviewed").get(0).path("playedPlain").asString())
                .isEqualTo("차를 앞으로 옮겼습니다.");
        assertThat(하나.path("reviewed").get(0).path("grade").asString()).isEqualTo("bad");
    }

    @Test
    @DisplayName("같은 판을 다시 올리면 덮어쓴다 - DO NOTHING 이면 복기 등급이 영원히 안 들어온다")
    void 덮어쓰기() throws Exception {
        long 처음 = 나무(보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키).body()).path("id").asLong();
        long 두번째 =
                나무(보내기("/games", "POST", 올릴것("판-1", 3, true), 내쿠키).body()).path("id").asLong();

        // 같은 줄이다.
        assertThat(두번째).isEqualTo(처음);
        assertThat(줄수()).isEqualTo(1);

        // 복기가 들어왔다.
        JsonNode 하나 = 나무(보내기("/games/" + 처음, "GET", null, 내쿠키).body());
        assertThat(하나.path("reviewed").isArray()).isTrue();
        assertThat(하나.path("reviewed").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("100수를 80수로 다시 올리면 81~100수가 남지 않는다 - 통째로 바뀐다")
    void 짧아진_판() throws Exception {
        long 번호 = 나무(보내기("/games", "POST", 올릴것("판-1", 100, false), 내쿠키).body()).path("id").asLong();
        보내기("/games", "POST", 올릴것("판-1", 80, false), 내쿠키);

        JsonNode 하나 = 나무(보내기("/games/" + 번호, "GET", null, 내쿠키).body());
        assertThat(하나.path("history").size()).isEqualTo(81);
        assertThat(하나.path("moves").asInt()).isEqualTo(80);
    }

    @Test
    @DisplayName("남의 기보는 없는 것처럼 보인다 - 403 은 거기 뭔가 있다는 말이다")
    void 남의_기보() throws Exception {
        long 내것 = 나무(보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키).body()).path("id").asLong();

        AppUserStore.로그인한사람 남 = 사용자창고.로그인("구글-sub-남");
        String 남의쿠키 = 쿠키(남);

        assertThat(보내기("/games/" + 내것, "GET", null, 남의쿠키).statusCode()).isEqualTo(404);
        assertThat(보내기("/games/" + 내것, "DELETE", null, 남의쿠키).statusCode()).isEqualTo(404);
        // 남의 목록은 비어 있다.
        assertThat(나무(보내기("/games", "GET", null, 남의쿠키).body()).size()).isZero();
        // 지워지지 않았다.
        assertThat(줄수()).isEqualTo(1);
    }

    @Test
    @DisplayName("CSRF 토큰 없이 올리면 거절한다 - 남의 쪽에서 내 쿠키로 기보를 넣지 못한다")
    void csrf가_없으면() throws Exception {
        // 토큰만 빼고 나머지는 다 맞다. 403 이 아니라 404 인 까닭은 우리 거절 처리기가
        // 그렇게 바꾸기 때문이다 - 없는 주소와 똑같이 보이게 하려는 것이다.
        assertThat(보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키, null).statusCode())
                .isEqualTo(404);
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("쿠키가 없으면 404 - 주소가 있다는 것조차 알려주지 않는다")
    void 쿠키가_없으면() throws Exception {
        assertThat(보내기("/games", "GET", null, null).statusCode()).isEqualTo(404);
        assertThat(보내기("/games/1", "GET", null, null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("세대가 안 맞는 쿠키로는 아무것도 안 된다 - 질의에 조건을 접어 넣은 자리다")
    void 낡은_쿠키() throws Exception {
        long 내것 = 나무(보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키).body()).path("id").asLong();

        // 탈퇴하고 되살리면 세대가 올라간다.
        sql.sql("UPDATE app_user SET deleted_at = now() WHERE id = :id").param("id", 내번호).update();
        사용자창고.로그인("구글-sub-나");

        assertThat(보내기("/games", "GET", null, 내쿠키).body()).isEqualTo("[]");
        assertThat(보내기("/games/" + 내것, "GET", null, 내쿠키).statusCode()).isEqualTo(404);
        assertThat(보내기("/games", "POST", 올릴것("판-2", 3, false), 내쿠키).statusCode()).isEqualTo(400);
        assertThat(보내기("/games/" + 내것, "DELETE", null, 내쿠키).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("지우면 목록에서 사라진다")
    void 지우기() throws Exception {
        long 번호 = 나무(보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키).body()).path("id").asLong();

        assertThat(보내기("/games/" + 번호, "DELETE", null, 내쿠키).statusCode()).isEqualTo(204);
        assertThat(나무(보내기("/games", "GET", null, 내쿠키).body()).size()).isZero();
        // 두 번 지우면 없는 것이다.
        assertThat(보내기("/games/" + 번호, "DELETE", null, 내쿠키).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("목록은 끝난 순서로 선다 - 올린 순서가 아니다")
    void 목록_순서() throws Exception {
        올리기("판-가", 1_700_000_000_000L);
        올리기("판-나", 1_760_000_000_000L);
        올리기("판-다", 1_730_000_000_000L);

        JsonNode 목록 = 나무(보내기("/games", "GET", null, 내쿠키).body());
        assertThat(목록.get(0).path("clientId").asString()).isEqualTo("판-나");
        assertThat(목록.get(1).path("clientId").asString()).isEqualTo("판-다");
        assertThat(목록.get(2).path("clientId").asString()).isEqualTo("판-가");
    }

    private void 올리기(String id, long 끝난때) throws Exception {
        보내기(
                "/games",
                "POST",
                올릴것(id, 2, false).replace("\"endedAt\":1760000000000", "\"endedAt\":" + 끝난때),
                내쿠키);
    }

    @Test
    @DisplayName("목록 응답에 판의 내용이 섞여 들지 않는다 - SELECT * 한 번이면 수백 KB가 오간다")
    void 목록_크기() throws Exception {
        // 100수짜리 다섯 판. 서버에 쌓인 내용은 다 합쳐 수십 KB다.
        for (int i = 1; i <= 5; i++) {
            보내기("/games", "POST", 올릴것("판-" + i, 100, true), 내쿠키);
        }

        String 목록 = 보내기("/games", "GET", null, 내쿠키).body();

        // **이 검사가 잡는 것과 못 잡는 것을 가려 둔다.**
        //
        // 잡는 것 - 응답에 판의 내용이 실려 나가는 것. 사용자가 치르는 바이트다.
        //
        // **못 잡는 것** - 질의에서만 블롭을 끌어오고 응답에는 안 넣는 경우. 일부러 그렇게
        // 고쳐 보니 이 검사가 통과했다. 그건 DB 에서 앱까지만 오는 바이트라 응답에 안
        // 보이는데, 싱가포르 왕복에 얹히면 느려지기는 한다.
        //
        // 응답 쪽은 사실 **GameSummary 의 모양이 막고 있다** - 그 레코드에 내용 칸이
        // 없으니 SELECT * 로 고쳐도 실려 나갈 자리가 없다. 이 검사는 그 모양이 바뀌는
        // 날을 위한 울타리다.
        assertThat(목록).doesNotContain("fen").doesNotContain("playedPlain");
        assertThat(목록.length()).isLessThan(3000);
        assertThat(나무(목록).size()).isEqualTo(5);

        // 반대로 하나를 열면 내용이 들어 있어야 한다 - 위의 단정이 "아무것도 안 준다" 를
        // 통과시키지 않게 같이 본다.
        long 번호 = 나무(목록).get(0).path("id").asLong();
        assertThat(보내기("/games/" + 번호, "GET", null, 내쿠키).body()).contains("fen");
    }

    @Test
    @DisplayName("계정이 지워지면 기보도 같이 간다")
    void 계정과_함께() throws Exception {
        보내기("/games", "POST", 올릴것("판-1", 3, false), 내쿠키);
        assertThat(줄수()).isEqualTo(1);

        sql.sql("DELETE FROM app_user WHERE id = :id").param("id", 내번호).update();

        assertThat(줄수()).isZero();
    }

    private long 줄수() {
        return sql.sql("SELECT count(*) FROM game").query(Long.class).single();
    }
}
