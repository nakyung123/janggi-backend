package com.nakyung.janggi.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 쿠키를 읽어 "이 요청은 누구" 를 세운다.
 *
 * <p><b>DB 를 보지 않는다.</b> 서명과 만료만 본다 - 계정이 살아 있는지는
 * {@link Caller} 주석에 적은 대로 <b>그 요청이 어차피 하는 질의</b>가 확인한다. 여기서
 * 보면 요청마다 155ms 가 붙는다.
 *
 * <p><b>쿠키가 없거나 틀려도 그냥 넘긴다.</b> 여기서 거절하지 않는다 - 어느 주소가
 * 로그인을 요구하는지는 {@code SecurityConfig} 의 규칙이 정할 일이고, 필터가 미리
 * 막으면 그 규칙이 두 군데로 갈라진다.
 *
 * <p><b>빈으로 두지 않는다.</b> 스프링 부트는 {@code Filter} 타입 빈을 보면 <b>서블릿
 * 컨테이너에도 따로 등록</b>한다 - 보안 사슬에 꽂은 것과 별개로 모든 주소에 한 번 더
 * 서게 된다. 게다가 빈이면 {@code @WebMvcTest} 조각 테스트가 이것을 만들려 하고, 그
 * 조각에는 {@link TokenIssuer} 가 없어서 <b>컨텍스트가 통째로 안 뜬다</b>(실제로 그렇게
 * 두고 테스트 12개를 빨간불로 만들었다). {@code SecurityConfig} 가 직접 만들어 꽂는다.
 *
 * <p><b>왜 {@code SecurityContext} 에 담나</b> - 그래야
 * {@code authorizeHttpRequests} 의 {@code authenticated()} 가 일하고, 컨트롤러가
 * {@code @AuthenticationPrincipal Caller} 로 바로 받을 수 있다. 쓰는 쪽이 쿠키를 다시
 * 읽지 않게 하는 것이 요점이다.
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    /**
     * 역할 하나만 준다.
     *
     * <p>지금 가름은 "로그인했나" 뿐이다. 관리자 같은 것이 생기면 그때 늘린다 - 쓸 데가
     * 없는 역할을 미리 만들면 어디서 쓰이는지 모르는 이름이 남는다.
     */
    private static final List<SimpleGrantedAuthority> 역할 =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final TokenIssuer 토큰;

    public AuthTokenFilter(TokenIssuer 토큰) {
        this.토큰 = 토큰;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest 요청, HttpServletResponse 답, FilterChain 다음)
            throws ServletException, IOException {
        TokenIssuer.들어온사람 사람 = 토큰.읽기(요청);
        if (사람 != null) {
            // **빈 컨텍스트를 새로 만들어 담는다.** 지금 것에 그냥 넣으면 톰캣이 되쓰는
            // 스레드에 남아 있던 것을 건드릴 수 있다. 치우는 일은 스프링의
            // SecurityContextHolderFilter 가 요청이 끝날 때 한다 - 이 필터는 그 뒤에
            // 서므로 여기서 또 치우지 않는다.
            SecurityContext 자리 = SecurityContextHolder.createEmptyContext();
            자리.setAuthentication(인증(사람));
            SecurityContextHolder.setContext(자리);
        }
        다음.doFilter(요청, 답);
    }

    /**
     * 비밀번호로 로그인한 것이 아닌데 {@code UsernamePasswordAuthenticationToken} 을
     * 쓰는 까닭 - <b>이미 확인이 끝난 것을 담는 그릇</b>으로 쓰는 것이고, 인자 셋을 받는
     * 생성자는 "확인됨" 으로 표시된다. 새 타입을 만들어도 하는 일이 똑같다.
     *
     * <p>두 번째 인자(자격 증명)는 {@code null} 이다. 담을 것이 없고, 담으면 그것이
     * 로그에 찍힐 자리만 늘어난다.
     */
    private Authentication 인증(TokenIssuer.들어온사람 사람) {
        return new UsernamePasswordAuthenticationToken(
                new Caller(사람.id(), 사람.epoch()), null, 역할);
    }
}
