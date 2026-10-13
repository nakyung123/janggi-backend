package com.nakyung.janggi.common.guard;

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

    /**
     * <b>고정 창이 깨지는 자리다.</b> 고정 창은 경계가 지나면 셈을 통째로 0 으로
     * 되돌리므로 여기서 <b>두 건</b>을 받는다. 미끄러지는 창은 오래된 것이 빠진 만큼만,
     * 곧 <b>한 건</b>만 받아야 한다.
     *
     * <p>이 테스트가 없으면 두 방식을 구별할 수 없다. 위의 "창이 지나면 다시 받는다" 는
     * 고정 창에서도 통과한다.
     */
    @Test
    @DisplayName("창은 한 칸씩 열린다 - 오래된 것이 빠진 만큼만 받는다")
    void 한_칸씩_열린다() throws Exception {
        Duration 창 = Duration.ofMillis(400);
        RateLimitFilter filter = new RateLimitFilter(2, 창);

        assertThat(불러보기(filter)).as("첫 건").isEqualTo(200);

        Thread.sleep(300);

        assertThat(불러보기(filter)).as("300ms 뒤 둘째 건").isEqualTo(200);
        assertThat(불러보기(filter)).as("창 안에 두 건이 찼다").isEqualTo(429);

        // 150ms 더 기다리면 첫 건(0ms)만 창에서 빠진다. 둘째 건(300ms)은 아직 안에 있다.
        Thread.sleep(150);

        assertThat(불러보기(filter)).as("첫 건이 빠져 한 자리 열렸다").isEqualTo(200);
        assertThat(불러보기(filter))
                .as("둘째 건이 아직 창 안이라 더는 못 받는다 - 고정 창이면 여기서 받는다")
                .isEqualTo(429);
    }

    /**
     * 거절할 때 <b>남은 시간</b>을 알려 주는지 본다. 고정 창일 때는 늘 창의 길이를
     * 그대로 돌려줬는데, 창이 거의 다 지났을 때도 처음부터 기다리라고 하는 셈이었다.
     */
    @Test
    @DisplayName("Retry-After 는 남은 시간이다 - 창의 길이가 아니다")
    void 남은_시간을_알려_준다() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, Duration.ofSeconds(5));

        assertThat(불러보기(filter)).isEqualTo(200);

        Thread.sleep(2_000);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        // 5초 창에서 2초가 지났으니 3초쯤 남았다. 고정 창이면 5 였다.
        assertThat(Integer.parseInt(response.getHeader("Retry-After")))
                .as("남은 시간")
                .isBetween(2, 4);
    }

    @Test
    @DisplayName("방금 꽉 찼으면 Retry-After 가 창의 길이와 같다")
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
