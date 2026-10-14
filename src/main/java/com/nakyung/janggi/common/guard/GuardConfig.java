package com.nakyung.janggi.common.guard;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * 요청 제한을 어느 입구에 어떤 순서로 세울지 정한다.
 *
 * <p><b>{@code /events} 에만 세운다.</b> {@code @Component} 로 올리면 모든 주소에 걸려서
 * Cloud Run 의 상태 확인({@code /actuator/health})까지 한도에 걸린다. 그러면 몰아치는
 * 동안 서버가 "죽었다" 고 판정돼 재시작하고, 재시작은 또 콜드 스타트 요금이다.
 *
 * <p><b>건수 제한을 크기 제한보다 앞에 세운다.</b> 둘 다 머리글만 보지만, 몰아치는
 * 상황에서는 더 싼 쪽이 먼저 끊는 것이 맞다.
 *
 * <p>두 숫자는 환경 변수로 바꿀 수 있게 두었다. Cloud Run 에서 환경 변수는 이미지를 다시
 * 만들지 않고 바꿀 수 있어서, 사람이 몰렸을 때 배포 없이 올릴 수 있다.
 */
@Configuration
public class GuardConfig {

    private static final String PATH = "/events";
    private static final String STATS = "/stats";

    /**
     * 기보 주소. <b>{@code /*} 를 붙여야 {@code /games/7} 까지 걸린다</b> - 서블릿 패턴은
     * {@code /games} 만 적으면 그 주소 하나만 본다.
     */
    private static final String GAMES = "/games";

    private static final String GAMES_ALL = "/games/*";

    /** 되돌릴 틈이 지난 계정을 치우는 자리. Cloud Scheduler 만 부른다. */
    private static final String PURGE = "/internal/purge";

    /**
     * 계정을 치우는 주소를 막는다. <b>구글이 만든 토큰만 들여보낸다.</b>
     *
     * <p><b>설정이 하나라도 비면 아무도 못 들어온다.</b> {@link #statsToken} 과 같은
     * 규칙이고, 여기는 더 그렇다 - 열린 채로 나가면 아무나 남의 계정을 지운다.
     *
     * <p>{@code jwks-uri} 를 설정으로 둔 까닭은 <b>검사 때문</b>이다. 검사가 진짜 구글을
     * 부를 수는 없으니, 검사에서는 그 자리에 가짜 구글을 띄워 가리킨다.
     */
    @Bean
    FilterRegistrationBean<GoogleTokenFilter> purgeCaller(
            @Value("${janggi.purge.jwks-uri:}") String jwksUri,
            @Value("${janggi.purge.issuer:https://accounts.google.com}") String issuer,
            @Value("${janggi.purge.audience:}") String audience,
            @Value("${janggi.purge.caller:}") String caller) {
        FilterRegistrationBean<GoogleTokenFilter> bean =
                new FilterRegistrationBean<>(
                        new GoogleTokenFilter(검사기(jwksUri, issuer), audience, caller));
        bean.addUrlPatterns(PURGE);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    /**
     * 토큰을 확인할 도구. 주소가 비어 있으면 {@code null} 이고, 그러면 아무도 못 들어온다.
     *
     * <p><b>열쇠를 미리 받아 두지 않는다.</b> {@code NimbusJwtDecoder} 는 처음 쓸 때
     * 가져온다 - 뜰 때 구글을 부르면 구글이 느린 날 서버가 아예 안 뜬다.
     */
    private static JwtDecoder 검사기(String jwksUri, String issuer) {
        if (!StringUtils.hasText(jwksUri)) {
            return null;
        }
        NimbusJwtDecoder 것 = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        것.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return 것;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimit(GuardProperties props) {
        FilterRegistrationBean<RateLimitFilter> bean =
                new FilterRegistrationBean<>(
                        new RateLimitFilter(props.limitPerMinute(), Duration.ofMinutes(1)));
        bean.addUrlPatterns(PATH);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    FilterRegistrationBean<RequestSizeFilter> requestSize(GuardProperties props) {
        FilterRegistrationBean<RequestSizeFilter> bean =
                new FilterRegistrationBean<>(new RequestSizeFilter(props.maxBodyBytes()));
        bean.addUrlPatterns(PATH);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    /**
     * {@code /stats} 의 건수 제한. <b>위의 것과 다른 물건이다.</b>
     *
     * <p>같은 {@code RateLimitFilter} 를 두 주소에 걸면 <b>셈을 같이 쓴다.</b> 그러면
     * 내가 통계를 몇 번 열어 보는 것이 사용자 기록 받을 몫을 깎아먹는다 - 재려고 만든
     * 것이 재는 일을 방해하는 꼴이다.
     *
     * <p>한도를 따로 작게 잡는다. 이 주소는 사람이 손으로 여는 것이라 분당 서른 번이면
     * 넉넉하고, 토큰을 맞혀 보려는 쪽에는 그만큼이 벽이 된다.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> statsRateLimit() {
        FilterRegistrationBean<RateLimitFilter> bean =
                new FilterRegistrationBean<>(new RateLimitFilter(30, Duration.ofMinutes(1)));
        bean.addUrlPatterns(STATS);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /**
     * {@code /stats} 를 토큰으로 막는다.
     *
     * <p>건수 제한보다 <b>뒤에</b> 선다. 토큰 검사가 더 싸 보이지만, 앞에 두면 틀린
     * 토큰을 몰아치는 쪽이 건수 제한을 아예 안 거친다.
     *
     * <p>값은 {@code JANGGI_STATS_TOKEN} 환경 변수다. 안 걸려 있으면 아무도 못 들어온다
     * - 깜빡했을 때 <b>열리는 것이 아니라 닫히는 쪽</b>이어야 한다.
     */
    @Bean
    FilterRegistrationBean<TokenFilter> statsToken(
            @Value("${janggi.stats.token:}") String token) {
        FilterRegistrationBean<TokenFilter> bean =
                new FilterRegistrationBean<>(new TokenFilter(token));
        bean.addUrlPatterns(STATS);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    /**
     * 기보 주소의 건수 제한. <b>측정과 셈을 같이 쓰지 않는다.</b>
     *
     * <p>같은 {@code RateLimitFilter} 를 두 주소에 걸면 셈을 공유한다 - 기보를 올리는
     * 것이 사용자 기록 받을 몫을 깎아먹는다. {@code /stats} 를 따로 둔 까닭과 같다.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> gameRateLimit(GuardProperties props) {
        FilterRegistrationBean<RateLimitFilter> bean =
                new FilterRegistrationBean<>(
                        new RateLimitFilter(props.gameLimitPerMinute(), Duration.ofMinutes(1)));
        bean.addUrlPatterns(GAMES, GAMES_ALL);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /**
     * 기보 하나의 크기 한도. <b>측정보다 훨씬 크다</b> - 한 판이 4만 자쯤이다.
     *
     * <p>이 요청 제한이 없으면 올리는 주소에 <b>상한이 아예 없다.</b> 국면 개수는
     * {@code GameStore} 가 세지만, 그건 본문을 이미 다 읽어 들인 뒤의 일이다. 메모리로
     * 들어오기 전에 끊는 자리가 따로 있어야 한다.
     */
    @Bean
    FilterRegistrationBean<RequestSizeFilter> gameRequestSize(GuardProperties props) {
        FilterRegistrationBean<RequestSizeFilter> bean =
                new FilterRegistrationBean<>(new RequestSizeFilter(props.maxGameBytes()));
        bean.addUrlPatterns(GAMES, GAMES_ALL);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }
}
