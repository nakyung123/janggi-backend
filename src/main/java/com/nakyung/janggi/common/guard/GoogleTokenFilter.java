package com.nakyung.janggi.common.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 구글이 서명하고 <b>우리가 정한 계정이 보낸</b> 토큰만 들여보낸다. 아니면 <b>404</b>.
 *
 * <p>Cloud Scheduler 가 주소를 부를 때 구글이 만든 토큰을 머리글에 실어 준다. 그 토큰을
 * 확인해서, 시킨 적 없는 쪽이 부른 것을 막는다.
 *
 * <p><b>{@code /stats} 의 {@link TokenFilter} 처럼 글자 하나를 맞춰 보지 않는 까닭</b> -
 * 그 방식은 값이 한 번 새면 바꾸기 전까지 계속 쓸 수 있다. {@code /stats} 는 읽기라 그
 * 값을 치를 만했지만 <b>이 주소는 계정을 지운다.</b> 구글이 만드는 토큰은 짧게 만료되고
 * 서명을 흉내 낼 수 없다.
 *
 * <p><b>404 인 까닭</b>과 <b>{@code sendError} 를 쓰는 까닭</b>은 {@link TokenFilter} 에
 * 적힌 것과 같다 - 없는 주소를 쳤을 때와 똑같아 보여야 한다.
 *
 * <p><b>설정이 비어 있으면 아무도 못 들어온다.</b> 깜빡했을 때 열리는 것이 아니라 닫히는
 * 쪽이어야 한다. 이 주소는 특히 그렇다 - 열린 채로 나가면 아무나 남의 계정을 지운다.
 *
 * <p><b>네 가지를 다 본다.</b> 서명만 보면 구글 계정을 가진 아무나 통과한다. 받는 쪽
 * ({@code aud})을 안 보면 다른 서비스로 간 토큰을 가져다 쓸 수 있다. 보낸 쪽
 * ({@code email})을 안 보면 같은 주소를 아는 다른 사람이 부를 수 있다.
 */
public class GoogleTokenFilter extends OncePerRequestFilter {

    private static final String 머리글 = HttpHeaders.AUTHORIZATION;
    private static final String 앞말 = "Bearer ";

    /** {@code null} 이면 설정이 덜 됐다는 뜻이고, 그때는 아무도 못 들어온다. */
    private final JwtDecoder 검사기;

    private final String 받는쪽;
    private final String 보낸쪽;

    public GoogleTokenFilter(JwtDecoder 검사기, String 받는쪽, String 보낸쪽) {
        this.검사기 = 검사기;
        this.받는쪽 = 받는쪽;
        this.보낸쪽 = 보낸쪽;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!맞나(request.getHeader(머리글))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean 맞나(String 머리글값) {
        if (검사기 == null
                || !StringUtils.hasText(받는쪽)
                || !StringUtils.hasText(보낸쪽)
                || 머리글값 == null
                || !머리글값.startsWith(앞말)) {
            return false;
        }
        Jwt 토큰;
        try {
            // 서명 · 만료 · 발행자를 여기서 본다. 틀리면 던진다.
            토큰 = 검사기.decode(머리글값.substring(앞말.length()));
        } catch (JwtException e) {
            return false;
        }
        return 받는쪽인가(토큰) && 보낸쪽.equals(토큰.getClaimAsString("email"));
    }

    /**
     * {@code aud} 는 하나일 수도 여럿일 수도 있다. 구글이 Cloud Scheduler 토큰에 담는 것은
     * 하나지만, <b>모양이 목록이라</b> 하나만 있다고 읽으면 나중에 조용히 틀어진다.
     */
    private boolean 받는쪽인가(Jwt 토큰) {
        List<String> 받는쪽들 = 토큰.getAudience();
        return 받는쪽들 != null && 받는쪽들.contains(받는쪽);
    }
}
