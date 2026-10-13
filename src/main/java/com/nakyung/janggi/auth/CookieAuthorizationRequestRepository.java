package com.nakyung.janggi.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.stereotype.Component;

/**
 * 로그인을 시작할 때 만든 {@code state} 를 <b>세션이 아니라 쿠키</b>에 둔다.
 *
 * <p><b>왜 세션을 못 쓰나</b> - Cloud Run 은 서버를 여러 대로 늘리고 요청을 아무 쪽으로나
 * 보낸다. 세션은 그 서버의 메모리에만 있어서, 구글에서 돌아온 콜백이 다른 서버에 닿으면
 * "로그인 요청을 못 찾겠다" 가 된다. 사람이 몰릴 때만 터지는 종류다.
 *
 * <p><b>스프링은 이것을 만들어 주지 않는다.</b> {@code AuthorizationRequestRepository} 의
 * 구현은 {@code HttpSessionOAuth2AuthorizationRequestRepository} 하나뿐이고, 쿠키 구현을
 * 넣어 달라는 요청(gh-6374)은 2021년에 <b>거절</b>됐다. 사유가 "쿠키 변조 취약점" 이라,
 * 직접 만들면 그 사유를 떠안는 셈이다. 그래서 {@link Signer} 로 서명한다.
 *
 * <p><b>담는 것을 손으로 고르지 않는다.</b> 처음에는 {@code state} 와 등록 이름만 담고
 * 나머지를 설정에서 다시 세우려 했는데, OIDC 는 {@code nonce} 를
 * {@code attributes} 에 넣어 두고 콜백에서 그것을 맞춰 본다. 하나라도 빠지면 <b>오류
 * 없이 로그인만 실패</b>하고, 스프링이 칸을 더 늘리면 그날 또 조용히 깨진다. 객체를
 * 통째로 담는다.
 *
 * <p>통째로 담으니 <b>돌아온 바이트를 그대로 되살리게 되는데</b>, 그것 자체가 위험한
 * 일이다. 그래서 둘을 겹쳐 둔다 - 서명이 맞지 않으면 되살리는 단계까지 가지 않고,
 * 되살릴 때도 {@link ObjectInputFilter} 로 <b>우리가 담은 종류만</b> 받는다.
 */
@Component
public class CookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    /** 짧게 둔다. 쿠키 이름은 머리글 길이에 그대로 더해진다. */
    static final String 이름 = "jg_state";

    /**
     * 로그인 한 번을 마치는 데 넉넉한 시간.
     *
     * <p>길게 둘 까닭이 없다 - 구글 화면에서 계정을 고르는 데 5분이면 충분하고, 남아
     * 있는 동안은 되쓸 수 있는 값이 된다.
     */
    private static final Duration 수명 = Duration.ofMinutes(5);

    /**
     * 되살릴 때 받아들이는 종류.
     *
     * <p>{@code !*} 가 맨 뒤에 있어 <b>적어 둔 것 말고는 전부 거절</b>한다. 서명이 이미
     * 막고 있지만, 열쇠가 새는 날 이 줄이 남는다.
     */
    private static final ObjectInputFilter 받을것 =
            ObjectInputFilter.Config.createFilter(
                    "maxbytes=8192;"
                            + "org.springframework.security.oauth2.core.**;"
                            + "java.util.**;java.lang.**;!*");

    private final Signer 서명자;

    CookieAuthorizationRequestRepository(Signer 서명자) {
        this.서명자 = 서명자;
    }

    /**
     * 콜백에서 돌아온 {@code state} 와 <b>쿠키에 든 것이 같은지</b> 본다.
     *
     * <p>세션 구현도 {@code state} 로 찾는다. 같은지 보지 않고 쿠키에 든 것을 그냥
     * 내주면, 남이 시작한 로그인의 답을 이 사람 것으로 처리하게 된다 - {@code state} 가
     * 막으려던 바로 그것이다.
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest 요청) {
        String 온state = 요청.getParameter(OAuth2ParameterNames.STATE);
        if (온state == null) {
            return null;
        }
        OAuth2AuthorizationRequest 담아둔것 = 읽기(요청);
        if (담아둔것 == null || !온state.equals(담아둔것.getState())) {
            return null;
        }
        return 담아둔것;
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest 담을것, HttpServletRequest 요청, HttpServletResponse 답) {
        // 스프링은 지우라는 뜻으로 null 을 넘긴다.
        if (담을것 == null) {
            지우기(답);
            return;
        }
        답.addHeader(HttpHeaders.SET_COOKIE, 쿠키(서명자.붙이기(싸기(담을것)), 수명).toString());
    }

    /**
     * 꺼내고 <b>바로 지운다.</b>
     *
     * <p>한 번 쓴 {@code state} 가 쿠키에 남아 있으면 같은 답을 두 번 처리할 수 있다.
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest 요청, HttpServletResponse 답) {
        OAuth2AuthorizationRequest 꺼낸것 = loadAuthorizationRequest(요청);
        지우기(답);
        return 꺼낸것;
    }

    private OAuth2AuthorizationRequest 읽기(HttpServletRequest 요청) {
        Cookie[] 쿠키들 = 요청.getCookies();
        if (쿠키들 == null) {
            return null;
        }
        for (Cookie c : 쿠키들) {
            if (이름.equals(c.getName())) {
                String 값 = 서명자.떼기(c.getValue());
                return 값 == null ? null : 풀기(값);
            }
        }
        return null;
    }

    private void 지우기(HttpServletResponse 답) {
        답.addHeader(HttpHeaders.SET_COOKIE, 쿠키("", Duration.ZERO).toString());
    }

    /**
     * 쿠키의 성질 넷. <b>하나씩 까닭이 있다.</b>
     *
     * <ul>
     *   <li><b>{@code SameSite=Lax}</b> - 콜백은 구글에서 오는 <b>최상위 이동</b>이다.
     *       {@code Strict} 면 그 이동에 쿠키가 실리지 않아 <b>로그인이 통째로
     *       실패한다.</b> 느슨하게 한 것이 아니라 이 흐름에 맞는 것이다.
     *   <li><b>{@code Path=/}</b> - 브라우저는 <b>자기가 보는 주소</b>로 판단한다.
     *       Vercel 이 {@code /api} 를 벗겨서 서버에 넘기므로, 서버가 보는 경로로 두면
     *       브라우저가 쿠키를 보내지 않는다.
     *   <li><b>{@code HttpOnly}</b> - 스크립트가 읽을 일이 없다.
     *   <li><b>{@code Secure}</b> - 평문으로 나가지 않는다. {@code localhost} 는
     *       브라우저가 안전한 곳으로 봐서 그대로 쓸 수 있다.
     * </ul>
     */
    private ResponseCookie 쿠키(String 값, Duration 수명) {
        return ResponseCookie.from(이름, 값)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(수명)
                .build();
    }

    private String 싸기(OAuth2AuthorizationRequest 담을것) {
        try (ByteArrayOutputStream 통 = new ByteArrayOutputStream();
                ObjectOutputStream 쓰기 = new ObjectOutputStream(통)) {
            쓰기.writeObject(담을것);
            쓰기.flush();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(통.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("로그인 요청을 쿠키에 담을 수 없습니다", e);
        }
    }

    /**
     * 되살리기. <b>실패하면 {@code null} 이다.</b>
     *
     * <p>서명을 이미 통과한 값이라 여기서 깨질 일은 거의 없지만, 열쇠를 그대로 두고
     * 라이브러리 판을 올리면 담아 둔 모양과 안 맞을 수 있다. 그때 예외를 던지면 그
     * 사람은 <b>쿠키를 손으로 지울 때까지</b> 로그인을 못 한다. 없는 것으로 보면 다시
     * 시작되어 저절로 풀린다.
     */
    private OAuth2AuthorizationRequest 풀기(String 값) {
        try (ObjectInputStream 읽기 =
                new ObjectInputStream(
                        new ByteArrayInputStream(Base64.getUrlDecoder().decode(값)))) {
            읽기.setObjectInputFilter(받을것);
            return (OAuth2AuthorizationRequest) 읽기.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
