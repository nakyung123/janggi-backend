package com.nakyung.janggi.events.common.guard;

import java.time.Duration;
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
}
