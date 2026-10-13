package com.nakyung.janggi.auth;

import com.nakyung.janggi.user.AppUserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 구글이 "이 사람 맞다" 고 해 준 뒤에 하는 일.
 *
 * <p>셋이다 - 우리 쪽 줄을 찾거나 만들고, 쿠키를 심고, 앱으로 돌려보낸다.
 *
 * <p><b>기본 동작을 쓰지 않는 까닭</b> - 스프링의 기본 성공 처리기는 들어오려던 주소를
 * {@code RequestCache} 에서 꺼내 거기로 보낸다. 그런데 우리는 그 캐시를
 * {@code NullRequestCache} 로 막아 뒀다(세션을 만들기 때문이다). 그래서 어디로 보낼지를
 * 여기서 정한다.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final AppUserStore 창고;
    private final TokenIssuer 토큰;
    private final String 돌아갈곳;

    LoginSuccessHandler(
            AppUserStore 창고,
            TokenIssuer 토큰,
            @Value("${janggi.login.redirect:/}") String 돌아갈곳) {
        this.창고 = 창고;
        this.토큰 = 토큰;
        this.돌아갈곳 = 돌아갈곳;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest 요청, HttpServletResponse 답, Authentication 인증)
            throws IOException {
        String sub = 구글번호(인증);
        AppUserStore.로그인한사람 사람 = 창고.로그인(sub);
        토큰.심기(답, 사람.id(), 사람.epoch());

        // **구글 번호도 이메일도 적지 않는다.** 로그에 남으면 DB 에 안 둔 뜻이 없어진다.
        // 우리 쪽 번호만 적어 두면 "누가 로그인했나" 를 쫓을 수 있다.
        log.info("로그인: 사람 {} · 세대 {}", 사람.id(), 사람.epoch());

        답.sendRedirect(돌아갈곳);
    }

    /**
     * 구글이 이 사람을 가리키는 번호({@code sub}).
     *
     * <p><b>이메일이 아니라 {@code sub} 다.</b> 이메일은 바뀔 수 있고, 바뀌면 같은 사람이
     * 남의 계정이 되거나 자기 기보를 잃는다. {@code sub} 는 그 구글 계정에 영구히
     * 붙어 있다.
     *
     * <p>{@code getName()} 으로도 보통 같은 값이 나오지만, 그것은 설정
     * ({@code user-name-attribute}) 에 따라 달라지는 값이다. <b>설정을 고치면 조용히
     * 다른 사람이 되는</b> 자리라서 {@code getSubject()} 를 직접 쓴다.
     */
    private String 구글번호(Authentication 인증) {
        if (인증.getPrincipal() instanceof OidcUser 구글사람) {
            return 구글사람.getSubject();
        }
        // openid 범위로만 받고 있어서 여기 올 일이 없다. 와 버렸다면 우리가 모르는
        // 흐름이 생긴 것이므로 조용히 넘기지 않는다.
        throw new IllegalStateException(
                "OIDC 사용자가 아닙니다: " + 인증.getPrincipal().getClass().getName());
    }
}
