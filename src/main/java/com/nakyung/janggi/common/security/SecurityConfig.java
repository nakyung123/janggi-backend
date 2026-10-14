package com.nakyung.janggi.common.security;

import com.nakyung.janggi.auth.AuthTokenFilter;
import com.nakyung.janggi.auth.CookieAuthorizationRequestRepository;
import com.nakyung.janggi.auth.LoginSuccessHandler;
import com.nakyung.janggi.auth.TokenIssuer;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 누가 어디에 들어올 수 있나.
 *
 * <p><b>이 파일이 없으면 보안을 넣는 순간 서버가 조용히 죽는다.</b> Spring Security 의
 * 기본값은 "모든 주소를 막는다" 라서, 넣기만 하면 측정이 들어오는 {@code /events} 와
 * Cloud Run 이 두드리는 {@code /actuator/health} 가 같이 401 이 된다. 배포는 성공하고
 * 그 뒤로 숫자만 0이 되는 모양이다 - 그래서 로그인을 만드는 것보다 <b>지금 열려 있는
 * 것을 계속 열어 두는 것</b>이 먼저다.
 *
 * <p><b>모르는 주소는 닫는다</b>({@code denyAll}). 나중에 기보 주소를 만들면서 여기
 * 적는 것을 깜빡하면 <b>열리는 것이 아니라 닫힌다.</b> 깜빡했을 때 어느 쪽으로
 * 기울어지는지가 이 한 줄에 달려 있다.
 *
 * <p><b>웹으로 뜰 때만 올린다.</b> DB 만 보는 테스트는 {@code webEnvironment=NONE} 으로
 * 도는데, 거기에는 {@code HttpSecurity} 빈이 없어서 이 설정이 무조건 올라가면 컨텍스트가
 * 아예 안 뜬다. 실제로 그렇게 두고 테스트 28개를 빨간불로 만들었다.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * 무상태로 둔다 - <b>네 군데를 다 막아야 한다.</b>
     *
     * <p>하나만 바꾸면 로그인은 되는데 세션이 계속 생긴다. 세션이 생기면 서버가 2대로
     * 늘어날 때 한쪽에만 있는 상태가 되고, Cloud Run 은 요청을 아무 쪽으로나 보낸다.
     *
     * <ol>
     *   <li>{@code SessionCreationPolicy.STATELESS}
     *   <li>{@code SecurityContextRepository} - 우리 토큰 필터가 대신한다
     *   <li>{@code RequestCache} - <b>이것도 세션을 만든다.</b> 가장 조용하다
     *   <li>CSRF 저장소 - 기본값이 세션에 둔다. 아래 참고
     * </ol>
     *
     * <p><b>CSRF 를 켠 채로 둔다.</b> 지금은 쿠키로 인증하는 주소가 없어서 할 일이
     * 없지만, 꺼 두면 기보 주소를 만들 때 켜는 것을 깜빡할 수 있다. 켜 두면 새 주소가
     * <b>처음부터</b> 보호된다.
     *
     * <p>다만 저장소를 쿠키로 바꾼다. 기본값은 세션에 두는데 그러면 위의 ③과 싸운다.
     *
     * <p><b>거절은 403 이 아니라 404 로 낸다.</b> {@code TokenFilter} 가 이미 그렇게 하고
     * 있고 까닭이 거기 적혀 있다 - 403 은 "여기 뭔가 있는데 너는 못 본다" 라서 그것만으로
     * 주소가 실재한다는 것이 드러난다. 403 으로 두면 <b>없는 주소는 403, {@code /stats} 는
     * 404</b> 가 되어 오히려 통계 주소만 튄다. 실제로 그렇게 두고
     * {@code StatsTest} 를 빨간불로 만들었다 - 그 검사가 "없는 주소와 똑같이 보인다" 를
     * 지키고 있었다.
     *
     * <p><b>{@code /events} 만 CSRF 에서 뺀다.</b> 브라우저가 그 주소를
     * {@code navigator.sendBeacon} 으로 부르는데, <b>sendBeacon 은 머리글을 붙일 수가
     * 없다.</b> 토큰을 실을 자리가 없어서 끄는 것이고, 그래도 괜찮은 까닭은 그 주소가
     * 쿠키를 보지 않기 때문이다 - 남의 브라우저를 빌려서 보내도 얻는 것이 없다.
     */
    @Bean
    SecurityFilterChain 울타리(
            HttpSecurity http,
            CookieAuthorizationRequestRepository state저장소,
            LoginSuccessHandler 로그인성공,
            TokenIssuer 토큰) throws Exception {
        return http.authorizeHttpRequests(
                         길 ->
                                길
                                        // **탈퇴는 로그인해야 열린다.** 아래 목록에
                                        // /me 가 열려 있는데 그것은 "로그인했나" 를
                                        // 묻는 자리라서다. 메서드를 안 가르면 지우는
                                        // 쪽까지 같이 열린다. **이 줄이 먼저 와야
                                        // 한다** - 뒤에 두면 permitAll 이 먼저 맞아
                                        // 아무 일도 안 한다.
                                        .requestMatchers(HttpMethod.DELETE, "/me")
                                        .authenticated()
                                        .requestMatchers(
                                                "/events",
                                                "/stats",
                                                // 로그인 시작과 콜백. 스프링 필터가
                                                // 먼저 집어가지만, 적어 두지 않으면
                                                // 아래 denyAll 과 읽는 사람이 부딪힌다.
                                                "/oauth2/authorization/**",
                                                "/login/oauth2/code/**",
                                                // "로그인했나" 를 묻는 자리를 로그인해야
                                                // 열리게 두면 답을 받을 수가 없다.
                                                // 쿠키가 없으면 거절이 아니라 false 다.
                                                "/me",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                // 스프링이 오류를 여기로 한 번 더 보낸다.
                                                // 빼 두면 400 이 403 으로 바뀌어 나간다.
                                                "/error")
                                        .permitAll()
                                        // **로그인해야 열린다.** 쿠키가 없으면 우리
                                        // 거절 처리기가 404 로 돌려보낸다 - 403 은
                                        // "거기 뭔가 있다" 를 알려주는 셈이다.
                                        .requestMatchers("/games", "/games/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        // **기본 처리기(XOR)를 쓰지 않는다.** 스프링 6
                                        // 부터 토큰을 XOR 로 가려 기대하는데, 쿠키에는
                                        // 가리지 않은 값이 들어간다. 그래서 프런트가
                                        // 쿠키를 읽어 머리글로 보내는 길이 **늘 403**
                                        // 이 되고, 우리 거절 처리기가 그걸 404 로
                                        // 바꿔서 "주소가 없나" 로 보이기까지 한다.
                                        //
                                        // XOR 은 응답 본문에 찍힌 토큰을 압축 공격으로
                                        // 캐내는 것을 막으려는 것인데, 우리는 토큰을
                                        // 본문에 찍지 않는다 - 쿠키로만 간다. 막을 것이
                                        // 없는 자리에서 치르는 값이었다.
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers("/events"))
                .sessionManagement(
                         세션 -> 세션.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                // **쿠키를 읽는 필터를 인가 검사 바로 앞에 세운다.** 뒤에 세우면
                // authenticated() 가 아직 아무도 없는 상태에서 판단한다.
                .addFilterBefore(new AuthTokenFilter(토큰), AuthorizationFilter.class)
                // **토큰을 한 번 읽어 쿠키가 실제로 나가게 한다.**
                //
                // 스프링은 CSRF 토큰을 미루어 둔다 - 누가 읽을 때까지 만들지도, 쿠키로
                // 내보내지도 않는다. 서버가 화면을 그리던 시절에는 폼이 읽어 줬지만
                // 우리는 그럴 자리가 없어서 **아무도 읽지 않고, 그러면 프런트가 토큰을
                // 받을 길이 없다.** 그 상태로 두면 쿠키로 인증하는 모든 POST 가 403 인데
                // 서버 쪽에는 아무 오류도 안 남는다. 가짜 구글로 로그인을 끝까지 돌려
                // 보는 검사가 로그아웃에서 이것을 잡았다.
                .addFilterAfter(new CsrfCookie(), CsrfFilter.class)
                .logout(
                        나가기 ->
                                나가기.logoutUrl("/logout")
                                        // **기본 동작을 쓰지 않는다.** 기본은 /login?logout
                                        // 으로 보내는데 그런 쪽이 없고, 우리 쿠키는
                                        // 스프링이 모르므로 지워 주지도 않는다.
                                        // deleteCookies() 도 못 쓴다 - 성질(SameSite 등)이
                                        // 같지 않으면 브라우저가 다른 쿠키로 본다.
                                        .logoutSuccessHandler(
                                                (요청, 답, 인증) -> {
                                                    토큰.지우기(답);
                                                    답.setStatus(204);
                                                }))
                .oauth2Login(
                        로그인 ->
                                로그인.authorizationEndpoint(
                                                시작 ->
                                                        시작.authorizationRequestRepository(
                                                                state저장소))
                                        .successHandler(로그인성공)
                                        // **failureUrl() 을 쓰지 않는다.** 기본 실패
                                        // 처리기는 예외를 세션에 넣어 두는데, 그 한
                                        // 줄이 세션을 만든다 - 무상태로 맞춰 둔 네
                                        // 군데가 이 하나로 무너진다.
                                        .failureHandler(
                                                (요청, 답, 왜) -> {
                                                    log.warn("로그인 실패: {}", 왜.getMessage());
                                                    답.sendRedirect("/?login=fail");
                                                }))
                .exceptionHandling(
                        거절 ->
                                거절.authenticationEntryPoint(
                                                (요청, 답, 왜) ->
                                                        답.sendError(
                                                                HttpServletResponse.SC_NOT_FOUND))
                                        .accessDeniedHandler(
                                                (요청, 답, 왜) ->
                                                        답.sendError(
                                                                HttpServletResponse.SC_NOT_FOUND)))
                .build();
    }

    /**
     * CSRF 토큰을 한 번 읽어 {@code XSRF-TOKEN} 쿠키가 응답에 실리게 한다.
     *
     * <p><b>{@code /events} 에서는 건너뛴다.</b> 그 주소는 CSRF 를 안 쓰는데
     * ({@code sendBeacon} 은 머리글을 못 붙인다) 응답마다 쓸 일 없는 쿠키를 하나씩
     * 붙여 보낼 까닭이 없다. 하루에 가장 많이 불리는 주소다.
     */
    private static final class CsrfCookie extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest 요청) {
            return "/events".equals(요청.getServletPath());
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest 요청, HttpServletResponse 답, FilterChain 다음)
                throws ServletException, IOException {
            CsrfToken 토큰 = (CsrfToken) 요청.getAttribute(CsrfToken.class.getName());
            if (토큰 != null) {
                토큰.getToken();
            }
            다음.doFilter(요청, 답);
        }
    }
}
