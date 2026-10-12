package com.nakyung.janggi.events.common.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 머리글에 맞는 토큰이 없으면 <b>404</b> 로 돌려보낸다.
 *
 * <p><b>403 이 아니라 404 인 까닭</b> - 403 은 "여기 뭔가 있는데 너는 못 본다" 는 말이다.
 * 그것만으로 주소가 실재한다는 것이 드러나고, 그다음은 토큰을 맞혀 보는 일만 남는다.
 * 404 는 아무것도 알려주지 않는다.
 *
 * <p><b>{@code sendError} 를 쓴다.</b> 같은 폴더의 {@code Problems} 는 일부러 쓰지
 * 않는다 - 그건 거절 모양을 <b>한결같게</b> 만들려는 것인데, 여기서 바라는 것은 정반대로
 * <b>없는 주소를 쳤을 때와 똑같아 보이는 것</b>이다. 우리 모양으로 404 를 쓰면 그 모양이
 * 곧 "이 주소는 특별하다" 는 표가 된다.
 *
 * <p><b>토큰이 안 걸려 있으면 언제나 404 다.</b> 설정을 깜빡했을 때 열리는 것이 아니라
 * 닫히는 쪽이어야 한다 - 공개 주소에서 반대로 하면 깜빡한 날 전부 새어 나간다.
 *
 * <p><b>비교는 상수 시간으로 한다.</b> 글자를 앞에서부터 하나씩 비교하면 맞은 글자 수만큼
 * 오래 걸려서, 응답 시간만 재도 토큰을 한 글자씩 알아낼 수 있다. 느린 길 위가 아니라
 * 해서 공짜로 지킬 수 있는 것을 버릴 까닭이 없다.
 *
 * <p><b>토큰에 한글을 쓸 수 없다.</b> HTTP 머리글 값은 ASCII 만 받는다 - 자바의
 * {@code HttpClient} 는 아예 보내지도 않는다({@code invalid header value}). 이 저장소는
 * 거의 모든 글이 한글이라 깜빡하기 쉬워서 적어 둔다.
 */
public class TokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Stats-Token";

    /** 비어 있으면 아무도 못 들어온다. */
    private final byte[] 참값;

    public TokenFilter(String token) {
        this.참값 =
                StringUtils.hasText(token) ? token.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!맞나(request.getHeader(HEADER))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean 맞나(String 보낸값) {
        if (참값.length == 0 || !StringUtils.hasText(보낸값)) {
            return false;
        }
        return MessageDigest.isEqual(참값, 보낸값.getBytes(StandardCharsets.UTF_8));
    }
}
