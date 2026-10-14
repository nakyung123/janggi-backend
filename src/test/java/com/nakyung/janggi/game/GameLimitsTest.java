package com.nakyung.janggi.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.auth.TokenIssuer;
import com.nakyung.janggi.user.AppUserStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
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
 * <b>한도가 실제로 막는지</b> 본다.
 *
 * <p>기보 주소는 이 서버에서 처음으로 <b>큰 본문을 받는 자리</b>다 - 한 판이 4만 자쯤이다.
 * 한도가 없으면 10MB 를 500번 올려 무료 저장 1GB 를 한 사람이 채울 수 있다. 막는 자리가
 * 셋이라 셋을 따로 본다.
 *
 * <ul>
 *   <li><b>본문 크기</b> - 문지기가 읽어 들이기 전에 끊는다
 *   <li><b>국면 개수</b> - 본문은 작아도 국면이 10만 개일 수 있다
 *   <li><b>판 수</b> - 한 사람이 서버에 둘 수 있는 판
 * </ul>
 *
 * <p>판 수 한도(500)는 설정이 아니라 자바 상수다. 그래서 낮춰서 보지 않고, <b>SQL 로
 * 판을 미리 채워 넣고</b> 경계에서 올려 본다 - HTTP 로 500판을 올리면 느리기만 하다.
 * 채우는 줄은 국면이 하나뿐인 작은 판이다. 세는 것은 줄 수라 내용은 상관없다.
 *
 * <p><b>동시에 올리는 경우를 같이 본다.</b> 세고 나서 넣는 사이가 비어 있으면 탭 두 개로
 * 한도를 넘길 수 있다. 그 검사는 스레드를 한꺼번에 푸는 것으로는 <b>안 된다</b> -
 * {@code 세는_사이에_끼어들지_못한다} 주석에 왜 안 되는지 적어 두었다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "janggi.guard.limit-per-minute=1000",
            "janggi.guard.game-limit-per-minute=1000",
            // 본문 한도를 작게 줄여 둔다. 진짜 한도(512KB)로 보려면 그만큼을 만들어
            // 보내야 하는데, 그건 느리기만 하고 더 알려 주는 것이 없다.
            "janggi.guard.max-game-bytes=16384"
        })
@Testcontainers
@EnabledIfDockerAvailable
class GameLimitsTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    @LocalServerPort int port;

    @Autowired TokenIssuer 토큰;
    @Autowired AppUserStore 사용자창고;
    @Autowired JdbcClient sql;
    @Autowired javax.sql.DataSource 연결통;

    private final HttpClient 손님 = HttpClient.newHttpClient();
    private String 내쿠키;
    private String 내csrf;
    private long 내id;

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM game").update();
        sql.sql("DELETE FROM app_user").update();
        AppUserStore.로그인한사람 나 = 사용자창고.로그인("구글-sub-나");
        내id = 나.id();
        MockHttpServletResponse 받아둘곳 = new MockHttpServletResponse();
        토큰.심기(받아둘곳, 나.id(), 나.epoch());
        내쿠키 = TokenIssuer.이름 + "=" + 받아둘곳.getCookie(TokenIssuer.이름).getValue();
        내csrf = csrf받기();
    }

    private String csrf받기() {
        try {
            HttpResponse<String> 답 =
                    손님.send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                                    .header("Cookie", 내쿠키)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            return 답.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN="))
                    .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                    .findFirst()
                    .orElseThrow();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String 올릴것(String id, int 국면수) {
        StringBuilder 국면 = new StringBuilder("[");
        for (int i = 0; i < 국면수; i++) {
            if (i > 0) {
                국면.append(',');
            }
            국면.append("{\"fen\":\"판\",\"move\":null,\"notation\":\"ㄱ\",\"mover\":null,\"score\":0}");
        }
        국면.append(']');
        return """
               {"id":"%s","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
               "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
               "history":%s,"reviewed":null}"""
                .formatted(id, 국면);
    }

    /**
     * 올린다.
     *
     * <p><b>시간 제한을 둔다.</b> 잠금이 기다리는 설계로 되돌아가면 답이 영원히 안 온다 -
     * 그때 검사가 매달려 있지 않고 터지게 하려는 것이다.
     */
    private HttpResponse<String> 올리기(String 본문) throws Exception {
        return 손님.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/games"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Cookie", 내쿠키 + "; XSRF-TOKEN=" + 내csrf)
                        .header("X-XSRF-TOKEN", 내csrf)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(본문))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("본문이 한도를 넘으면 413 - 메모리로 다 읽어 들이기 전에 끊는다")
    void 본문_크기() throws Exception {
        // 16384 바이트 한도를 넘기는 판. 국면 하나가 60바이트쯤이라 400개면 넉넉히 넘는다.
        HttpResponse<String> 답 = 올리기(올릴것("판-큰것", 400));

        assertThat(답.statusCode()).isEqualTo(413);
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("국면이 상한을 넘으면 400 - 본문이 작아도 개수로 막는다")
    void 국면_개수() throws Exception {
        // 본문 한도에 걸리지 않게 아주 짧은 국면을 많이 보낸다.
        StringBuilder 국면 = new StringBuilder("[");
        for (int i = 0; i < GameStore.국면_상한 + 1; i++) {
            if (i > 0) {
                국면.append(',');
            }
            국면.append("{\"fen\":\"판\"}");
        }
        국면.append(']');
        String 본문 =
                """
                {"id":"판-많은것","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
                "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
                "history":%s,"reviewed":null}"""
                        .formatted(국면);

        HttpResponse<String> 답 =
                손님.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/games"))
                                .header("Cookie", 내쿠키 + "; XSRF-TOKEN=" + 내csrf)
                                .header("X-XSRF-TOKEN", 내csrf)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(본문))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        // **크기가 아니라 개수에 걸려야 한다.** 짧은 국면만 보내 본문은 8KB 쯤이고
        // 한도는 16KB 다 - 413 이 나오면 개수 검사는 아직 아무것도 확인하지 못한 것이다.
        assertThat(답.statusCode()).isEqualTo(400);
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("국면이 하나도 없으면 400 - 올라가기는 했는데 열면 빈 판이 되는 것을 막는다")
    void 국면이_없으면() throws Exception {
        assertThat(올리기(올릴것("판-빈것", 0)).statusCode()).isEqualTo(400);
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("판 정보(fen)가 없는 국면은 400 - 받아 두면 열 때 빈 판이 된다")
    void fen이_없으면() throws Exception {
        String 본문 =
                """
                {"id":"판-fen없음","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
                "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
                "history":[{"notation":"ㄱ"}],"reviewed":null}""";

        assertThat(올리기(본문).statusCode()).isEqualTo(400);
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("history 가 배열이 아니면 400")
    void 모양이_아니면() throws Exception {
        String 본문 =
                """
                {"id":"판-모양","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
                "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
                "history":"배열이 아님","reviewed":null}""";

        assertThat(올리기(본문).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("빠진 칸이 있으면 400 - 어느 칸이 왜 틀렸는지 알려 준다")
    void 칸이_빠지면() throws Exception {
        // **하나만 뺀다.** 여러 칸을 빼면 어느 것이 메시지에 실릴지 정해져 있지 않아
        // 검사가 그날 운에 따라 빨간불이 된다.
        String 본문 =
                """
                {"id":"판-빠짐","mySide":"cho","levelId":"k12",                "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},                "history":[{"fen":"판"}],"reviewed":null}""";

        HttpResponse<String> 답 = 올리기(본문);

        assertThat(답.statusCode()).isEqualTo(400);
        assertThat(답.body()).contains("endedAt");
    }

    @Test
    @DisplayName("번호가 너무 길면 400 - DB 칸(24자)보다 긴 것이 오면 잘려 들어간다")
    void 번호가_길면() throws Exception {
        assertThat(올리기(올릴것("가".repeat(30), 2)).statusCode()).isEqualTo(400);
        assertThat(줄수()).isZero();
    }

    /**
     * 판을 미리 채운다. <b>한 문장이다</b> - 499번 왕복하면 검사가 느려진다.
     *
     * @param 몇판 넣을 줄 수
     */
    private void 채우기(int 몇판) {
        sql.sql(
                        """
                        INSERT INTO game (user_id, client_game_id, ended_at, my_side,
                                          level_id, level_name, variant,
                                          result_kind, moves_count, history)
                        SELECT :uid, 'fill-' || g, now(), 'cho', 'k12', '12급', 'janggi',
                               'resign', 1, '[{"fen":"판"}]'::jsonb
                          FROM generate_series(1, :n) g
                        """)
                .param("uid", 내id)
                .param("n", 몇판)
                .update();
    }

    @Test
    @DisplayName("499판에서 한 판 더는 되고, 500판에서는 400 - 경계를 양쪽으로 본다")
    void 경계_499에서_500() throws Exception {
        채우기(GameStore.한도 - 1);

        // 500번째는 들어가야 한다. 여기서 막히면 한도가 하나 모자라게 세는 것이다.
        assertThat(올리기(올릴것("판-500번째", 2)).statusCode()).isEqualTo(200);
        assertThat(줄수()).isEqualTo(GameStore.한도);

        HttpResponse<String> 답 = 올리기(올릴것("판-501번째", 2));

        assertThat(답.statusCode()).isEqualTo(400);
        // 사람이 무엇을 하면 되는지 적혀 있어야 한다.
        assertThat(답.body()).contains("500").contains("지운 뒤에");
        assertThat(줄수()).isEqualTo(GameStore.한도);
    }

    @Test
    @DisplayName("꽉 찬 사람도 이미 올린 판은 다시 올릴 수 있다 - 줄이 늘지 않으니까")
    void 한도에서_덮어쓰기() throws Exception {
        채우기(GameStore.한도);

        // 'fill-7' 은 채우기가 넣은 번호다. 덮어쓰기라 줄이 늘지 않는다.
        assertThat(올리기(올릴것("fill-7", 5)).statusCode()).isEqualTo(200);
        assertThat(줄수()).isEqualTo(GameStore.한도);
        // 덮어쓴 것이 맞는지 본다. 수가 바뀌어 있어야 한다.
        assertThat(
                        sql.sql("SELECT moves_count FROM game WHERE client_game_id = 'fill-7'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(4);
    }

    /**
     * <b>겹침을 운에 맡기지 않는다.</b>
     *
     * <p>스레드 여덟 개로 한꺼번에 올려 보는 검사를 먼저 만들었는데, <b>잠금을 빼고
     * 돌려도 통과했다.</b> 빈틈이 한 문장 사이라 ms 단위고, HTTP 와 톰캣을 지나오는
     * 동안 요청이 어긋나서 각자 앞 요청이 넣은 줄을 보게 된다. 경합은 있는데 검사가
     * 재현을 못 하는 것이다.
     *
     * <p>그래서 <b>검사 쪽에서 그 줄을 먼저 잠그고</b> 올려 본다. 세 가지가 한꺼번에
     * 걸린다.
     *
     * <ul>
     *   <li>잠금이 아예 없으면 - 그냥 들어가서 200 이 된다
     *   <li>기다리는 잠금이면 - 3초 안에 답이 없어 시간 초과로 터진다
     *   <li>{@code NOWAIT} 이면 - 바로 거절하고 다시 올리라고 한다
     * </ul>
     *
     * <p><b>기다리면 안 되는 까닭</b>은 연결 풀이 2개라서다. 기다리는 쪽이 연결을 쥐고
     * 있으면 {@code /events} 와 {@code /me} 가 연결을 못 받는다.
     */
    @Test
    @DisplayName("앞사람이 내 줄을 쥐고 있으면 기다리지 않고 거절한다 - 연결 둘을 지킨다")
    void 겹치면_기다리지_않고_거절한다() throws Exception {
        try (Connection 딴연결 = 연결통.getConnection()) {
            딴연결.setAutoCommit(false);
            try (PreparedStatement 잠그기 =
                    딴연결.prepareStatement("SELECT 1 FROM app_user WHERE id = ? FOR UPDATE")) {
                잠그기.setLong(1, 내id);
                잠그기.executeQuery().close();
            }

            HttpResponse<String> 답 = 올리기(올릴것("판-겹친것", 2));

            assertThat(답.statusCode()).isEqualTo(400);
            assertThat(답.body()).contains("잠시 뒤 다시");

            딴연결.rollback();
        }

        // 잠금이 풀리면 같은 요청이 들어가야 한다. 거절이 "영구히 막힌 것" 이 아님을 본다.
        assertThat(올리기(올릴것("판-겹친것", 2)).statusCode()).isEqualTo(200);
        assertThat(줄수()).isEqualTo(1);
    }

    private long 줄수() {
        return sql.sql("SELECT count(*) FROM game").query(Long.class).single();
    }
}
