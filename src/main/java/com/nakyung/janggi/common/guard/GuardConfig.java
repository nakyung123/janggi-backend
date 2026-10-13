package com.nakyung.janggi.common.guard;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 문지기를 어느 입구에 어떤 순서로 세울지 정한다.
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
}
