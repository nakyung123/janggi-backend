package com.nakyung.janggi.purge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nakyung.janggi.common.guard.GoogleTokenFilter;
import com.nakyung.janggi.user.AppUserStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 되돌릴 틈이 지난 계정을 치우는 자리를 <b>진짜 HTTP 와 진짜 Postgres</b> 로 본다.
 *
 * <p><b>여기서 보려는 것은 둘이다.</b> 아무나 못 부른다는 것과, 부를 수 있는 쪽이 불렀을
 * 때 <b>지울 것만</b> 지운다는 것. 뒤엣것이 틀리면 멀쩡한 사람이 사라지고, 그것은 이
 * 저장소에서 유일하게 되돌릴 수 없는 일이다.
 *
 * <p><b>구글은 부르지 않는다.</b> 열쇠를 알려 주는 자리만 흉내 내면 되므로, 작은 서버를
 * 하나 띄워 공개 열쇠를 내주고 토큰은 검사가 직접 만들어 서명한다. {@code LoginFlowTest}
 * 가 쓰는 방법과 같다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "janggi.guard.limit-per-minute=1000",
            "janggi.purge.jwks-uri=http://localhost:${가짜구글포트}/jwks",
            "janggi.purge.issuer=http://localhost:${가짜구글포트}",
            "janggi.purge.audience=https://우리-주소/internal/purge",
            "janggi.purge.caller=치우는이@우리.iam.gserviceaccount.com"
        })
@Testcontainers
@EnabledIfDockerAvailable
class PurgeTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    /** 겹침 검사에서 줄을 붙들고 있는 시간. 치우기가 이만큼 기다려야 맞다. */
    private static final Duration 붙든시간 = Duration.ofMillis(600);

    private static final String 받는쪽 = "https://우리-주소/internal/purge";
    private static final String 보낸쪽 = "치우는이@우리.iam.gserviceaccount.com";

    /** 공개 열쇠만 알려 주는 작은 서버. 구글의 그 자리 하나만 흉내 낸다. */
    static HttpServer 가짜구글;

    static RSAKey 열쇠;

    static {
        try {
            열쇠 = new RSAKeyGenerator(2048).keyID("가짜-열쇠-1").generate();
            가짜구글 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            가짜구글.setExecutor(Executors.newSingleThreadExecutor());
            가짜구글.createContext(
                    "/jwks", 교환 -> 답하기(교환, new JWKSet(열쇠.toPublicJWK()).toString()));
            가짜구글.start();
            // 포트를 미리 알 수 없어서 설정 자리에 끼워 넣는다. @SpringBootTest 의
            // properties 는 상수여야 해서, 플레이스홀더로 두고 여기서 채운다.
            System.setProperty("가짜구글포트", String.valueOf(가짜구글.getAddress().getPort()));
        } catch (Exception e) {
            throw new IllegalStateException("가짜 구글을 세울 수 없습니다", e);
        }
    }

    private static void 답하기(HttpExchange 교환, String 본문) throws IOException {
        byte[] 바이트 = 본문.getBytes(StandardCharsets.UTF_8);
        교환.getResponseHeaders().add("Content-Type", "application/json");
        교환.sendResponseHeaders(200, 바이트.length);
        교환.getResponseBody().write(바이트);
        교환.close();
    }

    @LocalServerPort int port;

    @Autowired AppUserStore 저장소;
    @Autowired JdbcClient sql;

    /** 겹침 검사에서 연결을 하나 더 잡으려고 쓴다. */
    @Autowired DataSource 연결샘;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM game").update();
        sql.sql("DELETE FROM app_user").update();
    }

    @Test
    @DisplayName("토큰이 없으면 404 - 주소가 있다는 것조차 알려주지 않는다")
    void 토큰_없이() throws Exception {
        assertThat(치우기(null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("남이 서명한 토큰은 404 - 구글이 서명한 것만 받는다")
    void 남의_서명() throws Exception {
        RSAKey 남의열쇠 = new RSAKeyGenerator(2048).keyID("가짜-열쇠-1").generate();
        assertThat(치우기(토큰(받는쪽, 보낸쪽, 남의열쇠, 600)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("다른 서비스로 간 토큰은 404 - 주워 와서 우리에게 쓸 수 없다")
    void 남의_받는쪽() throws Exception {
        assertThat(치우기(토큰("https://남의-주소", 보낸쪽, 열쇠, 600)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("다른 계정이 보낸 토큰은 404 - 구글 계정이 있다고 들어오지 못한다")
    void 남의_계정() throws Exception {
        assertThat(치우기(토큰(받는쪽, "남@gmail.com", 열쇠, 600)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("지난 토큰은 404 - 샌 토큰이 오래 쓰이지 않는다")
    void 지난_토큰() throws Exception {
        assertThat(치우기(토큰(받는쪽, 보낸쪽, 열쇠, -60)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("지울 것이 없으면 0 - 안 돌았다와 돌았는데 없었다는 다른 말이다")
    void 지울것이_없으면() throws Exception {
        저장소.로그인("구글-sub-쓰는사람");

        HttpResponse<String> 답 = 치우기(토큰(받는쪽, 보낸쪽, 열쇠, 600));

        assertThat(답.statusCode()).isEqualTo(200);
        assertThat(답.body()).contains("\"deleted\":0");
        assertThat(사람수()).isEqualTo(1);
    }

    @Test
    @DisplayName("30일 지난 줄만 지운다 - 29일과 30일을 양쪽으로 본다")
    void 경계() throws Exception {
        long 쓰는사람 = 저장소.로그인("구글-sub-쓰는사람").id();
        long 어제탈퇴 = 탈퇴시키기("구글-sub-어제", 1);
        long _29일 = 탈퇴시키기("구글-sub-29일", 29);
        long _31일 = 탈퇴시키기("구글-sub-31일", 31);

        HttpResponse<String> 답 = 치우기(토큰(받는쪽, 보낸쪽, 열쇠, 600));

        assertThat(답.body()).contains("\"deleted\":1");
        assertThat(있나(쓰는사람)).isTrue();
        assertThat(있나(어제탈퇴)).isTrue();
        assertThat(있나(_29일)).isTrue();
        assertThat(있나(_31일)).isFalse();
    }

    @Test
    @DisplayName("계정이 지워지면 그 사람의 기보도 같이 간다 - 남의 기보는 그대로다")
    void 기보도_같이() throws Exception {
        long 쓰는사람 = 저장소.로그인("구글-sub-쓰는사람").id();
        long _31일 = 탈퇴시키기("구글-sub-31일", 31);
        기보넣기(쓰는사람, "판-쓰는사람");
        기보넣기(_31일, "판-나간사람");

        치우기(토큰(받는쪽, 보낸쪽, 열쇠, 600));

        assertThat(기보수(쓰는사람)).isEqualTo(1);
        assertThat(기보수(_31일)).isZero();
    }

    /**
     * <b>지우는 찰나에 그 사람이 돌아오면.</b>
     *
     * <p>순서대로 부르면 이 상황이 안 만들어진다 - 로그인이 들어오면서 만료된 자기 줄을
     * 먼저 지우고 새로 만들기 때문이다. 그래서 <b>연결을 둘 잡고</b> 한쪽에서 줄을
     * 되살린 채 커밋하지 않고 붙들어, 치우기가 그 줄에서 기다리게 만든다.
     *
     * <p>바라는 끝 모양은 <b>안 지우는 것</b>이다. Postgres 는 잠긴 줄을 만나면 기다렸다가
     * 조건을 다시 보므로, 그때는 {@code deleted_at} 이 비어 있어 안 걸린다. 이것이
     * 사실인지 재 보는 것이 이 검사다 - 읽어서 아는 것으로 두지 않는다.
     */
    @Test
    @DisplayName("지우려는 찰나에 돌아오면 안 지운다 - 방금 로그인한 사람이 사라지면 안 된다")
    void 치우는_중에_돌아오면() throws Exception {
        long 번호 = 탈퇴시키기("구글-sub-31일", 31);

        try (Connection 다른연결 = 연결샘.getConnection()) {
            다른연결.setAutoCommit(false);
            try (PreparedStatement 되살리기 =
                    다른연결.prepareStatement("UPDATE app_user SET deleted_at = NULL WHERE id = ?")) {
                되살리기.setLong(1, 번호);
                되살리기.executeUpdate();
            }

            // 치우기는 잠긴 줄에서 기다린다. 기다리는 동안 우리가 커밋한다.
            var 돌린것 = Executors.newSingleThreadExecutor();
            long 시작 = System.nanoTime();
            Future<HttpResponse<String>> 치우는중 =
                    돌린것.submit(() -> 치우기(토큰(받는쪽, 보낸쪽, 열쇠, 600)));
            Thread.sleep(붙든시간.toMillis());
            다른연결.commit();

            HttpResponse<String> 답 = 치우는중.get(10, TimeUnit.SECONDS);
            Duration 걸린시간 = Duration.ofNanos(System.nanoTime() - 시작);
            돌린것.shutdown();

            assertThat(답.statusCode()).isEqualTo(200);
            assertThat(답.body()).contains("\"deleted\":0");
            // **기다린 것이 맞나.** 이것이 없으면 치우기가 커밋 뒤에 돌았어도 똑같이
            // 통과한다 - 그러면 겹침을 본 적이 없으면서 봤다고 말하는 검사가 된다.
            assertThat(걸린시간).isGreaterThan(붙든시간.minusMillis(100));
        }

        assertThat(있나(번호)).isTrue();
    }

    @Test
    @DisplayName("치운 뒤에 돌아오면 새로 만든다 - 같은 사람이지만 기보는 없다")
    void 치운_뒤에_돌아오면() throws Exception {
        long 옛번호 = 탈퇴시키기("구글-sub-31일", 31);
        기보넣기(옛번호, "판-옛것");

        치우기(토큰(받는쪽, 보낸쪽, 열쇠, 600));
        AppUserStore.로그인한사람 돌아온사람 = 저장소.로그인("구글-sub-31일");

        assertThat(있나(옛번호)).isFalse();
        assertThat(돌아온사람.id()).isNotEqualTo(옛번호);
        assertThat(기보수(돌아온사람.id())).isZero();
    }

    /**
     * <b>설정을 깜빡하면 닫힌다.</b>
     *
     * <p>스프링을 다시 띄우지 않고 필터만 직접 불러서 본다. 설정이 비어 있는 서버를 하나
     * 더 띄우려면 컨텍스트가 하나 더 생기는데, 보려는 것이 가지 하나뿐이라 값이 안 맞는다.
     *
     * <p><b>이 검사가 없으면 그 가지를 아무도 안 본다.</b> 일부러 빼 보니 139개가 그대로
     * 통과했다 - 다른 검사들은 설정이 채워진 채로 돌기 때문이다.
     */
    @Test
    @DisplayName("설정이 비어 있으면 아무도 못 들어온다 - 깜빡했을 때 열리면 안 된다")
    void 설정이_비면() throws Exception {
        String 멀쩡한토큰 = 토큰(받는쪽, 보낸쪽, 열쇠, 600);

        for (GoogleTokenFilter 덜된것 :
                List.of(
                        new GoogleTokenFilter(null, 받는쪽, 보낸쪽),
                        new GoogleTokenFilter(검사기(), "", 보낸쪽),
                        new GoogleTokenFilter(검사기(), 받는쪽, ""))) {
            MockHttpServletRequest 요청 = new MockHttpServletRequest("POST", "/internal/purge");
            요청.addHeader("Authorization", "Bearer " + 멀쩡한토큰);
            MockHttpServletResponse 답 = new MockHttpServletResponse();
            MockFilterChain 다음 = new MockFilterChain();

            덜된것.doFilter(요청, 답, 다음);

            assertThat(답.getStatus()).isEqualTo(404);
            assertThat(다음.getRequest()).as("막혔으면 뒤로 넘기지 않는다").isNull();
        }
    }

    /** 가짜 구글의 공개 열쇠를 보는 검사기. 위 검사에서 "설정 하나만 빈" 경우를 만든다. */
    private JwtDecoder 검사기() {
        String 터 = "http://localhost:" + 가짜구글.getAddress().getPort();
        NimbusJwtDecoder 것 = NimbusJwtDecoder.withJwkSetUri(터 + "/jwks").build();
        것.setJwtValidator(JwtValidators.createDefaultWithIssuer(터));
        return 것;
    }

    /** 로그인시키고 바로 탈퇴시킨 뒤, 탈퇴한 때를 며칠 전으로 민다. */
    private long 탈퇴시키기(String sub, int 며칠전) {
        long 번호 = 저장소.로그인(sub).id();
        sql.sql(
                        """
                        UPDATE app_user
                           SET deleted_at = now() - make_interval(days => :days)
                         WHERE id = :id
                        """)
                .param("days", 며칠전)
                .param("id", 번호)
                .update();
        return 번호;
    }

    private void 기보넣기(long 사람, String 판번호) {
        sql.sql(
                        """
                        INSERT INTO game (user_id, client_game_id, ended_at, my_side, level_id,
                                          level_name, variant, result_kind, moves_count, history)
                        VALUES (:uid, :cid, now(), 'cho', 'k12', '12급', 'janggi',
                                'resign', 1, CAST('[{"fen":"판모양0"}]' AS jsonb))
                        """)
                .param("uid", 사람)
                .param("cid", 판번호)
                .update();
    }

    private boolean 있나(long id) {
        return sql.sql("SELECT count(*) FROM app_user WHERE id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private int 사람수() {
        return sql.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
    }

    private int 기보수(long 사람) {
        return sql.sql("SELECT count(*) FROM game WHERE user_id = :uid")
                .param("uid", 사람)
                .query(Integer.class)
                .single();
    }

    private HttpResponse<String> 치우기(String 토큰) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/purge"));
        if (토큰 != null) {
            b.header("Authorization", "Bearer " + 토큰);
        }
        return http.send(
                b.POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** 구글이 Cloud Scheduler 에게 만들어 주는 토큰을 흉내 낸다. */
    private String 토큰(String 받는쪽, String 보낸쪽, RSAKey 서명열쇠, int 몇초뒤만료)
            throws Exception {
        JWTClaimsSet 내용 =
                new JWTClaimsSet.Builder()
                        .subject("가짜-서비스계정-번호")
                        .issuer("http://localhost:" + 가짜구글.getAddress().getPort())
                        .audience(List.of(받는쪽))
                        .issueTime(Date.from(Instant.now().minusSeconds(120)))
                        .expirationTime(Date.from(Instant.now().plusSeconds(몇초뒤만료)))
                        .claim("email", 보낸쪽)
                        .claim("email_verified", true)
                        .build();
        SignedJWT 토큰 =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(서명열쇠.getKeyID()).build(),
                        내용);
        토큰.sign(new RSASSASigner(서명열쇠));
        return 토큰.serialize();
    }
}
