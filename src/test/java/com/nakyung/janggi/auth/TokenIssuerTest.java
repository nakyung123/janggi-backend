package com.nakyung.janggi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 로그인 쿠키를 심고 읽는 것만 본다.
 *
 * <p>가장 중요한 검사는 <b>만료를 우리가 지키는지</b>다. {@code Max-Age} 는 브라우저가
 * 지키는 약속이라, 쿠키를 베껴 간 쪽은 안 지킨다 - 서명된 값 안에 넣은 시각으로 우리가
 * 거절해야 한다.
 */
class TokenIssuerTest {

    private static final Instant 지금 = Instant.parse("2026-10-13T00:00:00Z");

    private final Signer 서명자 = new Signer("열쇠-하나-1234567890");

    private TokenIssuer 발급자(Instant 때) {
        return new TokenIssuer(서명자, Clock.fixed(때, ZoneOffset.UTC));
    }

    /** 응답에 실린 쿠키를 다음 요청에 옮겨 붙인다. 브라우저가 하는 일이다. */
    private MockHttpServletRequest 돌아온요청(MockHttpServletResponse 답) {
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        Cookie 실린것 = 답.getCookie(TokenIssuer.이름);
        if (실린것 != null) {
            요청.setCookies(실린것);
        }
        return 요청;
    }

    @Test
    @DisplayName("심은 번호와 세대가 그대로 돌아온다")
    void 왕복() {
        TokenIssuer 발급자 = 발급자(지금);
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        발급자.심기(답, 7L, 3);

        TokenIssuer.들어온사람 사람 = 발급자.읽기(돌아온요청(답));

        assertThat(사람).isNotNull();
        assertThat(사람.id()).isEqualTo(7L);
        assertThat(사람.epoch()).isEqualTo(3);
    }

    @Test
    @DisplayName("30일이 지나면 안 받는다 - Max-Age 를 안 지키는 쪽도 막는다")
    void 만료() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        발급자(지금).심기(답, 7L, 1);

        // 브라우저가 Max-Age 를 무시하고 그대로 보냈다고 치자.
        MockHttpServletRequest 요청 = 돌아온요청(답);

        assertThat(발급자(지금.plus(Duration.ofDays(29))).읽기(요청)).isNotNull();
        assertThat(발급자(지금.plus(Duration.ofDays(31))).읽기(요청)).isNull();
    }

    @Test
    @DisplayName("값을 고친 쿠키는 없는 것으로 본다 - 세대를 올려 쓰지 못한다")
    void 고친_쿠키() {
        TokenIssuer 발급자 = 발급자(지금);
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        발급자.심기(답, 7L, 1);

        Cookie 원래 = 답.getCookie(TokenIssuer.이름);
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        요청.setCookies(new Cookie(원래.getName(), 원래.getValue().replace("7|1|", "8|9|")));

        assertThat(발급자.읽기(요청)).isNull();
    }

    @Test
    @DisplayName("쿠키 성질 - state 쿠키와 달리 SameSite=Strict 다")
    void 쿠키_성질() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        발급자(지금).심기(답, 7L, 1);

        assertThat(답.getHeader(HttpHeaders.SET_COOKIE))
                .contains("jg_auth=")
                // 이 쿠키는 우리 화면이 fetch 로 부를 때만 필요하다. 그건 같은 출처라
                // Strict 로도 실린다 - 더 조일 수 있는 자리를 느슨하게 둘 까닭이 없다.
                .contains("SameSite=Strict")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("Path=/")
                .contains("Max-Age=2592000");
    }

    @Test
    @DisplayName("지우면 Max-Age=0 으로 같은 쿠키를 덮는다")
    void 지우기() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        발급자(지금).지우기(답);

        assertThat(답.getHeader(HttpHeaders.SET_COOKIE)).contains("jg_auth=").contains("Max-Age=0");
    }

    @Test
    @DisplayName("쿠키가 없거나 모양이 아니면 null - 터지지 않는다")
    void 없거나_이상하면() {
        TokenIssuer 발급자 = 발급자(지금);

        assertThat(발급자.읽기(new MockHttpServletRequest())).isNull();

        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        요청.setCookies(new Cookie(TokenIssuer.이름, 서명자.붙이기("7|하나|999")));
        assertThat(발급자.읽기(요청)).isNull();

        MockHttpServletRequest 조각부족 = new MockHttpServletRequest();
        조각부족.setCookies(new Cookie(TokenIssuer.이름, 서명자.붙이기("7|1")));
        assertThat(발급자.읽기(조각부족)).isNull();
    }
}
