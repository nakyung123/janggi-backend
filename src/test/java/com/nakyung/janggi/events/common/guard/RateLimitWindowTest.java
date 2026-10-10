package com.nakyung.janggi.events.common.guard;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 창이 지나면 다시 받는지 본다.
 *
 * <p><b>이걸 확인하지 않으면 제일 큰 구멍이 남는다.</b> 창이 초기화되지 않으면 서버는
 * 한도에 한 번 닿은 뒤 <b>영원히 429 만 돌려준다.</b> 사람이 몰린 날 한 번 걸리면 그
 * 인스턴스가 살아 있는 동안 측정이 통째로 멈추는데, 이 서버는 사람이 기다리는 길 위에
 * 없어서 <b>아무도 모른다.</b>
 *
 * <p>진짜 소켓이 아니라 가짜 요청으로 부른다. 여기서 보려는 것은 "어느 주소에 붙는가"
 * 가 아니라 세는 논리뿐이고, 창을 짧게 넣어야 1분을 기다리지 않는다.
 */
class RateLimitWindowTest {

    private static final Duration 짧은_창 = Duration.ofMillis(150);

    private int 불러보기(RateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }

    @Test
    @DisplayName("창이 지나면 다시 받는다")
    void 창이_초기화된다() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 짧은_창);

        assertThat(불러보기(filter)).isEqualTo(200);
        assertThat(불러보기(filter)).isEqualTo(200);
        assertThat(불러보기(filter)).as("한도를 넘은 세 번째").isEqualTo(429);

        Thread.sleep(짧은_창.toMillis() + 50);

        assertThat(불러보기(filter)).as("창이 지난 뒤").isEqualTo(200);
        assertThat(불러보기(filter)).isEqualTo(200);
        assertThat(불러보기(filter)).as("새 창에서도 한도는 그대로").isEqualTo(429);
    }

    @Test
    @DisplayName("Retry-After 는 창의 길이를 알려 준다")
    void 다시_보낼_시각() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, Duration.ofMinutes(1));

        assertThat(불러보기(filter)).isEqualTo(200);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }
}
