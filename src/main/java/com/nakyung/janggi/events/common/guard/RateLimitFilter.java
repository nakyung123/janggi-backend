package com.nakyung.janggi.events.common.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 지난 창 안에 정해진 건수보다 많이 오면 거절한다. <b>창은 고정이 아니라 미끄러진다.</b>
 *
 * <p><b>왜 IP 별이 아니라 전체인가.</b> 이 서버는 Vercel 의 되돌림을 거쳐서 불린다 -
 * 그러면 요청이 전부 Vercel 의 주소에서 오는 것으로 보여서 IP 별로 세는 것이 아무 의미가
 * 없다. 진짜 사용자 주소는 {@code X-Forwarded-For} 에 실려 오는데, <b>그 값은 Cloud Run
 * 주소를 직접 때리는 쪽에서 마음대로 쓸 수 있다.</b> 꾸밀 수 있는 값으로 세는 것은 제한이
 * 아니다. 전체 건수는 꾸밀 수 없고, 요금과 바로 이어지므로 그쪽을 센다.
 *
 * <p><b>대가</b> - 한 명이 몰아치면 그 사이 정상 사용자의 기록도 버려진다. 이 서버는
 * 사람이 기다리는 길 위에 없어서 잃는 것이 기록뿐이고, 요금이 새는 것보다 낫다고 봤다.
 *
 * <p><b>왜 고정 창에서 바꿨나.</b> 처음에는 "창 시작 시각 하나 + 셈 하나" 였다. 창이
 * 지나면 셈을 0 으로 되돌리는 방식인데, <b>창 경계 양쪽에 몰면 짧은 사이에 두 배가
 * 통과한다.</b> 분당 120 건으로 두고 재 보니 60 초에 230 건이 들어왔다. 요금을 묶는
 * 것이 목적이라 그 정도는 받아들였는데, 한도를 올릴수록 새는 양도 같이 커지는 모양이라
 * 고쳤다.
 *
 * <p><b>어떻게 세나.</b> <b>마지막 {@code limit} 건의 시각만</b> 고리 버퍼에 들고 있다.
 * {@code 시각[자리]} 가 그 가운데 가장 오래된 것이고, 그것이 아직 창 안에 있으면 지난
 * 창에 이미 {@code limit} 건이 있다는 뜻이라 거절한다. 어느 순간을 잡아도 지난 창의
 * 건수가 한도를 넘지 않는다.
 *
 * <p>가중 평균으로 어림하는 길(앞 창과 지금 창의 셈을 섞는)도 있고 메모리가 O(1) 이지만,
 * 여기서는 한도가 작아서 틀리지 않는 쪽을 골랐다. <b>거절할 때는 아무것도 넣지 않으므로
 * 몰아쳐도 자리가 늘지 않는다</b> - {@code long} 120 개, 1KB 가 천장이다. 들어온 시각을
 * 전부 쌓는 방식에 있는 위험이 여기서는 없다.
 *
 * <p><b>왜 {@code synchronized} 인가.</b> 고정 창일 때는 {@code AtomicLong} 둘이었고,
 * <b>두 배가 샌 까닭의 절반이 "셈과 창이 서로 맞물려 있지 않았다" 는 것이었다.</b> 읽고
 * 고치는 것이 한 덩어리여야 한다. 임계 구역이 몇 나노초이고 이 서버는 초당 수십 건이라
 * 경합이 문제 되지 않는다 - 초당 300 건을 걸어도 거절률이 한도 그대로 나온다.
 *
 * <p><b>인스턴스마다 따로 센다.</b> 메모리에만 있어서 서버가 2대로 늘면 실제 한도는 두
 * 배가 된다. 그래서 이것이 요금의 유일한 자물쇠가 아니다 - Cloud Run 의
 * {@code --max-instances} 와 구글의 지출 한도가 바깥에서 한 번 더 묶는다.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int limit;

    /**
     * 창의 길이. 돌 때는 1분이고, <b>테스트에서는 짧게 넣는다.</b> 상수로 박아 두면
     * "창이 지나면 다시 받는다" 를 확인하려고 테스트가 1분을 기다려야 해서, 결국 아무도
     * 확인하지 않는 부분이 된다. 창이 미끄러지지 않으면 서버는 한도에 한 번 닿은 뒤
     * <b>영원히 거절한다.</b>
     */
    private final long windowNanos;

    /** 마지막 {@code limit} 건이 들어온 시각. 오래된 것부터 차례로 덮어 쓴다. */
    private final long[] 시각;

    /** 다음에 덮어 쓸 자리. 곧 <b>지금 들고 있는 것 가운데 가장 오래된 자리</b>다. */
    private int 자리;

    /** 거절을 적은 마지막 시각. 창마다 한 번만 적기 위한 것이다. */
    private long 적은때;

    public RateLimitFilter(int limit, Duration window) {
        if (limit < 1) {
            throw new IllegalArgumentException("limitPerMinute 는 1 이상이어야 합니다: " + limit);
        }
        this.limit = limit;
        this.windowNanos = window.toNanos();

        // 창보다 더 오래 전으로 채워 둬야 처음 limit 건이 통과한다. Long.MIN_VALUE 를
        // 넣으면 뺄 때 넘쳐서 음수가 되므로 쓰지 않는다.
        long 아주_오래전 = System.nanoTime() - windowNanos;
        this.시각 = new long[limit];
        Arrays.fill(this.시각, 아주_오래전);
        this.적은때 = 아주_오래전;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long 기다릴_나노 = 통과시킬까();

        if (기다릴_나노 > 0) {
            response.setHeader("Retry-After", Long.toString(초로(기다릴_나노)));
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "조금 뒤에 다시 보내 주세요.");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 받을지 정하고, 거절할 때는 <b>얼마나 기다려야 하는지</b>를 나노초로 돌려준다.
     * 0 이면 받는다는 뜻이다.
     *
     * <p>받는 것과 세는 것이 한 덩어리여야 해서 자물쇠 안에서 같이 한다. 거절 로그도
     * 여기서 적는다 - 적은 시각 자체가 지켜야 하는 값이고, <b>창마다 한 번뿐</b>이라
     * 자물쇠를 오래 쥐지 않는다.
     */
    private synchronized long 통과시킬까() {
        long 지금 = System.nanoTime();
        long 가장오래된것 = 시각[자리];
        long 지난시간 = 지금 - 가장오래된것;

        if (지난시간 < windowNanos) {
            // 지난 창에 이미 limit 건이 있다. 덮어 쓰지 않으므로 자리가 늘지 않는다.
            if (지금 - 적은때 >= windowNanos) {
                적은때 = 지금;
                // 창마다 한 번만 적는다. 몰아치는 동안 거절마다 적으면 로그가 요금이 된다
                // (로그는 한 달 50GiB 까지 무료다).
                log.warn("요청이 한도({}건)를 넘어 거절하고 있습니다.", limit);
            }
            return windowNanos - 지난시간;
        }

        시각[자리] = 지금;
        자리 = (자리 + 1) % limit;
        return 0;
    }

    /** 올림한다. 내리면 그 시각에 보내도 또 거절당해서 보낸 쪽이 헛걸음한다. */
    private static long 초로(long 나노) {
        return Math.max(1, (나노 + 999_999_999L) / 1_000_000_000L);
    }
}
