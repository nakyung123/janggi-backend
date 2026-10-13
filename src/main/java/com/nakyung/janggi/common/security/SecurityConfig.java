package com.nakyung.janggi.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
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
    SecurityFilterChain 울타리(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                         길 ->
                                길.requestMatchers(
                                                "/events",
                                                "/stats",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                // 스프링이 오류를 여기로 한 번 더 보낸다.
                                                // 빼 두면 400 이 403 으로 바뀌어 나간다.
                                                "/error")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .ignoringRequestMatchers("/events"))
                .sessionManagement(
                         세션 -> 세션.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
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
}
