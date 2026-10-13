package com.nakyung.janggi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * {@code state} 를 쿠키에 담고 꺼내는 것만 본다.
 *
 * <p>여기서 보려는 것 셋이다 - <b>칸이 하나도 빠지지 않고 돌아오나</b>(OIDC 의
 * {@code nonce} 가 빠지면 오류 없이 로그인만 실패한다), <b>고친 쿠키를 거절하나</b>,
 * <b>쿠키 길이 한도를 넘지 않나.</b>
 */
class CookieAuthorizationRequestRepositoryTest {

    private final Signer 서명자 = new Signer("열쇠-하나-1234567890");
    private final CookieAuthorizationRequestRepository 저장소 =
            new CookieAuthorizationRequestRepository(서명자);

    /** 구글 로그인에서 실제로 만들어지는 모양. nonce 가 attributes 와 양쪽에 들어간다. */
    private OAuth2AuthorizationRequest 로그인요청(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("보기용-클라이언트-아이디.apps.googleusercontent.com")
                .redirectUri("https://janggi-ai.vercel.app/api/login/oauth2/code/google")
                .scopes(java.util.Set.of("openid"))
                .state(state)
                .additionalParameters(Map.of("nonce", "해시된-nonce"))
                .attributes(Map.of("registration_id", "google", "nonce", "날-nonce"))
                .build();
    }

    /** 담은 뒤 응답에 실린 쿠키를 다음 요청에 옮겨 붙인다. 브라우저가 하는 일이다. */
    private MockHttpServletRequest 돌아온요청(MockHttpServletResponse 답, String state) {
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        요청.setParameter(OAuth2ParameterNames.STATE, state);
        Cookie 실린것 = 답.getCookie(CookieAuthorizationRequestRepository.이름);
        if (실린것 != null) {
            요청.setCookies(실린것);
        }
        return 요청;
    }

    @Test
    @DisplayName("담은 것이 칸 하나 빠지지 않고 돌아온다 - nonce 가 빠지면 로그인만 조용히 실패한다")
    void 왕복() {
        OAuth2AuthorizationRequest 담을것 = 로그인요청("state-1234");
        MockHttpServletResponse 답 = new MockHttpServletResponse();

        저장소.saveAuthorizationRequest(담을것, new MockHttpServletRequest(), 답);
        OAuth2AuthorizationRequest 꺼낸것 = 저장소.loadAuthorizationRequest(돌아온요청(답, "state-1234"));

        assertThat(꺼낸것).isNotNull();
        assertThat(꺼낸것.getState()).isEqualTo("state-1234");
        assertThat(꺼낸것.getRedirectUri()).isEqualTo(담을것.getRedirectUri());
        assertThat(꺼낸것.getClientId()).isEqualTo(담을것.getClientId());
        assertThat(꺼낸것.getScopes()).isEqualTo(담을것.getScopes());
        assertThat(꺼낸것.getAuthorizationUri()).isEqualTo(담을것.getAuthorizationUri());
        // 이 둘이 이 검사의 요점이다.
        assertThat(꺼낸것.getAttributes()).isEqualTo(담을것.getAttributes());
        assertThat(꺼낸것.getAdditionalParameters()).isEqualTo(담을것.getAdditionalParameters());
    }

    @Test
    @DisplayName("쿠키 성질 넷을 지킨다 - SameSite=Lax 가 아니면 콜백에 쿠키가 실리지 않는다")
    void 쿠키_성질() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("state-1234"), new MockHttpServletRequest(), 답);

        String 머리글 = 답.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(머리글)
                .contains("jg_state=")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("Max-Age=300");
    }

    @Test
    @DisplayName("쿠키 한 개가 4KB 를 넘지 않는다 - 넘으면 브라우저가 조용히 버린다")
    void 길이() {
        // **재 보니 2435자다**(2026-10-13). 한도 4096의 60%이고, 자바 직렬화가
        // 클래스 이름까지 같이 담아서 이만큼 나온다. 한도를 3500으로 둔 것은 스프링이
        // 칸을 늘렸을 때 **브라우저가 조용히 버리기 전에** 여기서 알려 주기 위한 것이다.
        // 로그인 중 5분만 있는 쿠키라 평소에는 머리글에 실리지 않는다.
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("state-1234"), new MockHttpServletRequest(), 답);

        String 값 = 답.getCookie(CookieAuthorizationRequestRepository.이름).getValue();
        assertThat(값.length()).isLessThan(3500);
    }

    @Test
    @DisplayName("값을 고친 쿠키는 없는 것으로 본다")
    void 고친_쿠키() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("state-1234"), new MockHttpServletRequest(), 답);

        MockHttpServletRequest 요청 = 돌아온요청(답, "state-1234");
        Cookie 원래 = 요청.getCookies()[0];
        요청.setCookies(
                new Cookie(원래.getName(), 원래.getValue().substring(0, 원래.getValue().length() - 3) + "XYZ"));

        assertThat(저장소.loadAuthorizationRequest(요청)).isNull();
    }

    @Test
    @DisplayName("콜백의 state 가 쿠키와 다르면 안 내준다 - 남이 시작한 로그인을 이 사람 것으로 처리하지 않는다")
    void state가_다르면() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("내가-만든-state"), new MockHttpServletRequest(), 답);

        assertThat(저장소.loadAuthorizationRequest(돌아온요청(답, "남이-보낸-state"))).isNull();
    }

    @Test
    @DisplayName("state 파라미터가 없으면 안 내준다")
    void state가_없으면() {
        MockHttpServletResponse 답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("state-1234"), new MockHttpServletRequest(), 답);

        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        요청.setCookies(답.getCookie(CookieAuthorizationRequestRepository.이름));

        assertThat(저장소.loadAuthorizationRequest(요청)).isNull();
    }

    @Test
    @DisplayName("꺼내면 바로 지운다 - 같은 답을 두 번 처리하지 못하게")
    void 꺼내면_지운다() {
        MockHttpServletResponse 담은답 = new MockHttpServletResponse();
        저장소.saveAuthorizationRequest(로그인요청("state-1234"), new MockHttpServletRequest(), 담은답);

        MockHttpServletResponse 꺼낸답 = new MockHttpServletResponse();
        OAuth2AuthorizationRequest 꺼낸것 =
                저장소.removeAuthorizationRequest(돌아온요청(담은답, "state-1234"), 꺼낸답);

        assertThat(꺼낸것).isNotNull();
        assertThat(꺼낸답.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("쿠키가 아예 없으면 null - 예외로 터지지 않는다")
    void 쿠키가_없으면() {
        MockHttpServletRequest 요청 = new MockHttpServletRequest();
        요청.setParameter(OAuth2ParameterNames.STATE, "state-1234");

        assertThat(저장소.loadAuthorizationRequest(요청)).isNull();
    }
}
