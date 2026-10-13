package com.nakyung.janggi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 로그인을 <b>처음부터 끝까지</b> 자동으로 돌려 본다.
 *
 * <p><b>왜 가짜 구글을 세우나</b> - 진짜 구글로는 자동으로 못 한다. 구글은 자동화된
 * 로그인을 막고(사람인지 묻는 화면이 뜬다), 계정 비밀을 검사 안에 둘 수도 없다. 그래서
 * 사람이 손으로 눌러 보는 것 말고는 길이 없다고 넘기기 쉬운데, <b>그러면 로그인은 영원히
 * 자동으로 확인되지 않는다.</b>
 *
 * <p>구글이 하는 일은 정해진 프로토콜(OIDC)이다. 그 자리에 <b>우리가 만든 서버</b>를
 * 세우면 스프링은 진짜로 주고받는다 - 인증 요청을 만들고, {@code state} 를 쿠키에 담고,
 * 돌아온 code 로 토큰을 받아 오고, {@code id_token} 의 서명·발급자·대상·<b>nonce</b> 를
 * 맞춰 본다. 우리 코드가 틀리면 여기서 걸린다.
 *
 * <p><b>이것으로 확인되지 않는 것</b> - 구글에 등록한 클라이언트와 리디렉션 주소가 맞나,
 * 그리고 Vercel 이 {@code /api} 를 벗기고 {@code Set-Cookie} 를 넘기나. 둘 다 바깥이
 * 하는 일이라 여기서 흉내 내면 "흉내가 틀렸을 위험" 이 새로 생긴다. 각각 다른 방법으로
 * 따로 본다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "janggi.guard.limit-per-minute=1000",
            "janggi.login.redirect=/me",
            "spring.security.oauth2.client.registration.google.client-id=우리-클라이언트",
            "spring.security.oauth2.client.registration.google.client-secret=우리-비밀",
            "spring.security.oauth2.client.registration.google.scope=openid",
            "spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/google"
        })
@Testcontainers
@EnabledIfDockerAvailable
class LoginFlowTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    /** 가짜 구글. 토큰을 내주고 공개 열쇠를 알려 준다. */
    static HttpServer 가짜구글;

    static RSAKey 열쇠;

    /** 다음에 내줄 {@code id_token}. 검사마다 바꿔 끼운다. */
    static final AtomicReference<String> 다음id토큰 = new AtomicReference<>();

    /** 토큰 주소가 몇 번 불렸나. code 를 두 번 쓰지 않는지 보려고 센다. */
    static final AtomicReference<Integer> 토큰요청수 = new AtomicReference<>(0);

    static {
        try {
            열쇠 = new RSAKeyGenerator(2048).keyID("가짜-열쇠-1").generate();
            가짜구글 = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

            가짜구글.createContext(
                    "/jwks",
                    교환 -> 답하기(교환, new JWKSet(열쇠.toPublicJWK()).toString()));

            가짜구글.createContext(
                    "/token",
                    교환 -> {
                        토큰요청수.updateAndGet(n -> n + 1);
                        답하기(
                                교환,
                                """
                                {"access_token":"가짜-접근토큰","token_type":"Bearer",\
                                "expires_in":3600,"id_token":"%s"}"""
                                        .formatted(다음id토큰.get()));
                    });

            // 쓰이지 않는다 - 브라우저를 보내는 주소라서 검사는 직접 콜백을 친다.
            // 발견 문서에 있어야 스프링이 등록을 세울 수 있어서 둔다.
            가짜구글.createContext("/authorize", 교환 -> 답하기(교환, "쓰이지 않는다"));

            // **발견 문서.** 이것을 안 내주면 등록 이름이 google 이라 스프링이 발급자를
            // https://accounts.google.com 으로 기대하고, id_token 의 iss 가 안 맞아
            // invalid_id_token 으로 떨어진다. 처음에 그렇게 두고 한참 봤다.
            가짜구글.createContext(
                    "/.well-known/openid-configuration",
                    교환 ->
                            답하기(
                                    교환,
                                    """
                                    {"issuer":"%1$s",                                    "authorization_endpoint":"%1$s/authorize",                                    "token_endpoint":"%1$s/token",                                    "jwks_uri":"%1$s/jwks",                                    "response_types_supported":["code"],                                    "subject_types_supported":["public"],                                    "grant_types_supported":["authorization_code"],                                    "id_token_signing_alg_values_supported":["RS256"]}"""
                                            .formatted(발급자())));

            가짜구글.start();
        } catch (Exception e) {
            throw new IllegalStateException("가짜 구글을 세울 수 없습니다", e);
        }
    }

    /** 가짜 구글의 주소. 발견 문서의 issuer 와 id_token 의 iss 가 이 값이어야 한다. */
    private static String 발급자() {
        return "http://localhost:" + 가짜구글.getAddress().getPort();
    }

    private static void 답하기(com.sun.net.httpserver.HttpExchange 교환, String 본문)
            throws java.io.IOException {
        byte[] 바이트 = 본문.getBytes(StandardCharsets.UTF_8);
        교환.getResponseHeaders().add("Content-Type", "application/json");
        교환.sendResponseHeaders(200, 바이트.length);
        try (OutputStream 통 = 교환.getResponseBody()) {
            통.write(바이트);
        }
    }

    @AfterAll
    static void 닫기() {
        가짜구글.stop(0);
    }

    /**
     * 발급자 하나만 알려 준다.
     *
     * <p>주소를 하나씩 적는 대신 이렇게 둔다 - 스프링이 발견 문서를 읽어 주소들을
     * 스스로 채우므로, <b>진짜 구글을 쓸 때와 같은 길</b>을 지난다.
     */
    @DynamicPropertySource
    static void 가짜주소(DynamicPropertyRegistry 설정) {
        설정.add("spring.security.oauth2.client.provider.google.issuer-uri", LoginFlowTest::발급자);
    }

    @LocalServerPort int port;

    @Autowired JdbcClient sql;

    /** 리다이렉트를 따라가지 않는다 - 단계마다 머리글을 봐야 한다. */
    private final HttpClient 손님 =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM app_user").update();
        토큰요청수.set(0);
    }

    private String 터(String 길) {
        return "http://localhost:" + port + 길;
    }

    /** 로그인 시작. 구글로 보내는 302 와 state 쿠키를 받는다. */
    private record 시작(String state, String nonce, String state쿠키) {}

    private 시작 로그인시작() throws Exception {
        HttpResponse<String> 답 =
                손님.send(
                        HttpRequest.newBuilder(URI.create(터("/oauth2/authorization/google")))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(답.statusCode()).isEqualTo(302);

        String 보낸곳 = 답.headers().firstValue("Location").orElseThrow();
        String 쿠키 =
                답.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("jg_state="))
                        .map(c -> c.substring(0, c.indexOf(';')))
                        .findFirst()
                        .orElseThrow();
        return new 시작(파라미터(보낸곳, "state"), 파라미터(보낸곳, "nonce"), 쿠키);
    }

    private static String 파라미터(String 주소, String 이름) {
        for (String 조각 : URI.create(주소).getQuery().split("&")) {
            int 금 = 조각.indexOf('=');
            if (조각.substring(0, 금).equals(이름)) {
                return URLDecoder.decode(조각.substring(금 + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** 구글이 내줄 {@code id_token} 을 만든다. */
    private String id토큰(String sub, String nonce, RSAKey 서명열쇠) throws Exception {
        JWTClaimsSet 내용 =
                new JWTClaimsSet.Builder()
                        .subject(sub)
                        .issuer(발급자())
                        .audience(List.of("우리-클라이언트"))
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                        .claim("nonce", nonce)
                        .build();
        SignedJWT 토큰 =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(서명열쇠.getKeyID()).build(),
                        내용);
        토큰.sign(new RSASSASigner(서명열쇠));
        return 토큰.serialize();
    }

    /** 구글이 브라우저를 우리 콜백으로 돌려보낸 것을 흉내 낸다. */
    private HttpResponse<String> 콜백(String state, String 쿠키) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(
                        URI.create(터("/login/oauth2/code/google?code=가짜-code&state=" + state)));
        if (쿠키 != null) {
            b.header("Cookie", 쿠키);
        }
        return 손님.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String 로그인쿠키(HttpResponse<String> 답) {
        return 답.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("jg_auth=") && !c.contains("Max-Age=0"))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    private HttpResponse<String> 나를_묻기(String 쿠키) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(터("/me")));
        if (쿠키 != null) {
            b.header("Cookie", 쿠키);
        }
        return 손님.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("로그인이 처음부터 끝까지 된다 - state·nonce·토큰교환·DB·쿠키·/me 가 한 줄로 이어진다")
    void 끝까지() throws Exception {
        시작 시작 = 로그인시작();
        다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 열쇠));

        HttpResponse<String> 콜백답 = 콜백(시작.state(), 시작.state쿠키());

        // 우리 성공 처리기가 보낸 곳.
        assertThat(콜백답.statusCode()).isEqualTo(302);
        // **절대 주소가 아니라 상대 주소여야 한다.** 절대로 나가면 프록시 뒤에서
        // Cloud Run 주소가 박혀 사용자가 앱을 떠난다.
        assertThat(콜백답.headers().firstValue("Location").orElse("")).isEqualTo("/me");

        // 로그인 쿠키가 심겼고, state 쿠키는 지워졌다.
        String 로그인쿠키 = 로그인쿠키(콜백답);
        assertThat(로그인쿠키).isNotNull();
        assertThat(콜백답.headers().allValues("Set-Cookie").toString())
                .contains("jg_state=;")
                .contains("SameSite=Strict");

        // 세션은 안 생겼다.
        assertThat(콜백답.headers().allValues("Set-Cookie").toString())
                .doesNotContain("JSESSIONID");

        // DB 에 줄이 생겼다. **sub 만 들어가고 이메일 칸은 없다.**
        assertThat(
                        sql.sql("SELECT count(*) FROM app_user WHERE google_sub = '구글-sub-1'")
                                .query(Long.class)
                                .single())
                .isEqualTo(1);

        // 그 쿠키로 /me 가 true 다.
        assertThat(나를_묻기(로그인쿠키).body()).contains("\"loggedIn\":true");

        assertThat(토큰요청수.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번 로그인해도 계정은 하나다 - 같은 sub 면 같은 줄이다")
    void 두_번_로그인() throws Exception {
        for (int i = 0; i < 2; i++) {
            시작 시작 = 로그인시작();
            다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 열쇠));
            assertThat(로그인쿠키(콜백(시작.state(), 시작.state쿠키()))).isNotNull();
        }

        assertThat(sql.sql("SELECT count(*) FROM app_user").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("state 쿠키가 없으면 로그인이 안 된다 - 세션에 안 두는 설계의 핵이다")
    void state쿠키가_없으면() throws Exception {
        시작 시작 = 로그인시작();
        다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 열쇠));

        HttpResponse<String> 답 = 콜백(시작.state(), null);

        assertThat(로그인쿠키(답)).isNull();
        assertThat(답.headers().firstValue("Location").orElse("")).contains("login=fail");
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("남이 보낸 state 로는 안 된다 - CSRF 를 막는 바로 그 값이다")
    void 남의_state() throws Exception {
        시작 시작 = 로그인시작();
        다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 열쇠));

        HttpResponse<String> 답 = 콜백("내가-아닌-state", 시작.state쿠키());

        assertThat(로그인쿠키(답)).isNull();
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("nonce 가 다른 id_token 은 거절한다 - 쿠키에 객체를 통째로 담은 까닭이 이것이다")
    void nonce가_다르면() throws Exception {
        시작 시작 = 로그인시작();
        // 다른 로그인 시도에서 온 nonce 를 넣는다.
        다음id토큰.set(id토큰("구글-sub-1", "남의-nonce", 열쇠));

        HttpResponse<String> 답 = 콜백(시작.state(), 시작.state쿠키());

        assertThat(로그인쿠키(답)).isNull();
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("다른 열쇠로 서명한 id_token 은 거절한다")
    void 서명이_다르면() throws Exception {
        RSAKey 남의열쇠 = new RSAKeyGenerator(2048).keyID("가짜-열쇠-1").generate();
        시작 시작 = 로그인시작();
        다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 남의열쇠));

        HttpResponse<String> 답 = 콜백(시작.state(), 시작.state쿠키());

        assertThat(로그인쿠키(답)).isNull();
        assertThat(줄수()).isZero();
    }

    @Test
    @DisplayName("로그아웃하면 쿠키가 지워지고 /me 가 false 가 된다")
    void 로그아웃() throws Exception {
        시작 시작 = 로그인시작();
        다음id토큰.set(id토큰("구글-sub-1", 시작.nonce(), 열쇠));
        String 로그인쿠키 = 로그인쿠키(콜백(시작.state(), 시작.state쿠키()));

        // CSRF 토큰을 받아 같이 보낸다 - 쿠키로 인증하는 주소라 그냥은 못 지나간다.
        HttpResponse<String> 받기 = 나를_묻기(로그인쿠키);
        String csrf = Csrf값(받기);
        HttpResponse<String> 나가기 =
                손님.send(
                        HttpRequest.newBuilder(URI.create(터("/logout")))
                                .header("Cookie", 로그인쿠키 + "; XSRF-TOKEN=" + csrf)
                                .header("X-XSRF-TOKEN", csrf)
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(나가기.statusCode()).isEqualTo(204);
        assertThat(나가기.headers().allValues("Set-Cookie").toString())
                .contains("jg_auth=")
                .contains("Max-Age=0");
    }

    /** CSRF 쿠키는 토큰이 처음 읽힐 때 심긴다. 없으면 null 이다. */
    private String Csrf값(HttpResponse<String> 답) {
        return 답.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    private long 줄수() {
        return sql.sql("SELECT count(*) FROM app_user").query(Long.class).single();
    }
}
