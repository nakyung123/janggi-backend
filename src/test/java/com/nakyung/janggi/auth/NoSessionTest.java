package com.nakyung.janggi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>세션이 한 번도 안 생기는지</b> 센다.
 *
 * <p>왜 이것을 세나 - Cloud Run 은 서버를 여러 대로 늘리고 요청을 아무 쪽으로나 보낸다.
 * 세션이 생기면 그 서버 메모리에만 있어서, 다음 요청이 다른 쪽에 닿으면 없는 것이 된다.
 * <b>사람이 몰릴 때만 터지는 종류</b>라 평소 확인으로는 안 걸린다.
 *
 * <p><b>{@code JSESSIONID} 가 응답에 없는 것은 증거가 아니다.</b> 쿠키를 안 보내도 세션
 * 객체는 만들어질 수 있다. 그래서 {@link HttpSessionListener} 를 걸어 횟수를 센다.
 *
 * <p><b>"0번" 만 보면 거짓으로 통과한다.</b> 리스너가 등록 자체에 실패해도 0번이기
 * 때문이다. 그래서 <b>일부러 세션을 만드는 주소</b>를 하나 두고, 거기서 1번 이상 세어지는
 * 것을 먼저 본다 - 그래야 0번이 말이 된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "janggi.guard.limit-per-minute=1000")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoSessionTest {

    /** 톰캣이 세션을 만들 때마다 올라간다. */
    static final AtomicInteger 생긴횟수 = new AtomicInteger();

    @TestConfiguration
    static class 세보기 {

        @Bean
        HttpSessionListener 세는사람() {
            return new HttpSessionListener() {
                @Override
                public void sessionCreated(HttpSessionEvent e) {
                    생긴횟수.incrementAndGet();
                }
            };
        }

        /** 일부러 세션을 만드는 주소. <b>세는 장치가 실제로 일하는지</b>를 보려고 둔다. */
        @Bean
        대조군 대조군() {
            return new 대조군();
        }

        /**
         * 대조군 주소만 여는 보안 설정.
         *
         * <p><b>진짜 보안 설정은 건드리지 않는다.</b> 거기는 적어 두지 않은 주소를 전부
         * 막으므로(처음에 이 대조군도 404 가 났다) 이 한 주소만 따로 열어 준다.
         * {@code @Order(1)} 이라 이 주소에는 이 사슬이 먼저 걸리고, 나머지는 전부 진짜
         * 보안 설정이 그대로 맡는다 - 아래 검사들이 보려는 것이 그 진짜 보안 설정다.
         */
        @Bean
        @org.springframework.core.annotation.Order(1)
        SecurityFilterChain 대조군_보안설정(HttpSecurity http) throws Exception {
            return http.securityMatcher("/테스트-세션-만들기")
                    .authorizeHttpRequests(길 -> 길.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }

    @RestController
    static class 대조군 {
        @GetMapping("/테스트-세션-만들기")
        String 만들기(HttpServletRequest 요청, HttpServletResponse 답) {
            요청.getSession(true);
            return "만들었다";
        }
    }

    @LocalServerPort int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void 세기전() {
        생긴횟수.set(0);
    }

    private HttpResponse<String> 부르기(String 길, String 쿠키) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + 길));
        if (쿠키 != null) {
            b.header("Cookie", 쿠키);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Order(1)
    @DisplayName("세는 장치가 일한다 - 일부러 만들면 1번 세어진다")
    void 대조군이_먼저() throws Exception {
        assertThat(부르기("/테스트-세션-만들기", null).statusCode()).isEqualTo(200);

        // 이 단정이 먼저 통과해야 아래의 "0번" 이 뜻을 갖는다. 여기가 0이면 리스너가
        // 안 걸린 것이고, 그러면 아래 검사는 아무것도 확인하지 않는다.
        assertThat(생긴횟수.get()).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("열린 주소들을 다 밟아도 세션이 0번 - 무상태 네 군데가 다 막혀 있다")
    void 열린_주소들() throws Exception {
        부르기("/actuator/health", null);
        부르기("/me", null);
        부르기("/stats", null);
        부르기("/없는주소", null);

        assertThat(생긴횟수.get()).isZero();
    }

    @Test
    @Order(3)
    @DisplayName("로그인을 시작해도 세션이 0번 - state 가 쿠키로 가기 때문이다")
    void 로그인_시작() throws Exception {
        // 구글로 보내는 302 가 나온다. 여기서 세션이 생기면 state 가 세션에 담긴 것이고,
        // 그러면 서버가 두 대일 때 콜백이 "요청을 못 찾겠다" 가 된다.
        HttpResponse<String> 답 = 부르기("/oauth2/authorization/google", null);

        assertThat(답.statusCode()).isEqualTo(302);
        assertThat(답.headers().firstValue("Location").orElse(""))
                .contains("accounts.google.com");
        assertThat(답.headers().allValues("Set-Cookie").toString()).contains("jg_state");
        assertThat(생긴횟수.get()).isZero();
    }

    @Test
    @Order(4)
    @DisplayName("로그인이 실패해도 세션이 0번 - 기본 실패 처리기는 예외를 세션에 넣는다")
    void 로그인_실패() throws Exception {
        // state 쿠키 없이 콜백만 치면 실패로 간다. 스프링 기본 failureUrl 을 썼다면
        // 여기서 세션이 생긴다.
        HttpResponse<String> 답 = 부르기("/login/oauth2/code/google?code=가짜&state=가짜", null);

        assertThat(답.statusCode()).isEqualTo(302);
        assertThat(답.headers().firstValue("Location").orElse("")).contains("login=fail");
        assertThat(생긴횟수.get()).isZero();
    }
}
