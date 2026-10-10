package com.nakyung.janggi.events.common.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 1분에 정해진 건수보다 많이 오면 거절한다.
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
 * <p><b>인스턴스마다 따로 센다.</b> 메모리에만 있어서 서버가 2대로 늘면 실제 한도는 두
 * 배가 된다. 그래서 이것이 요금의 유일한 자물쇠가 아니다 - Cloud Run 의
 * {@code --max-instances} 와 구글의 지출 한도가 바깥에서 한 번 더 묶는다.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_NANOS = 60_000_000_000L;

    private final int limitPerMinute;

    private final AtomicLong windowStart = new AtomicLong(System.nanoTime());
    private final AtomicLong count = new AtomicLong();

    /** 한 창에서 거절을 한 번만 적기 위한 표시. */
    private final AtomicBoolean reported = new AtomicBoolean();

    public RateLimitFilter(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!allow()) {
            if (reported.compareAndSet(false, true)) {
                // 창마다 한 번만 적는다. 몰아치는 동안 거절마다 적으면 로그가 요금이 된다
                // (로그는 한 달 50GiB 까지 무료다).
                log.warn("요청이 1분 한도({}건)를 넘어 거절하고 있습니다.", limitPerMinute);
            }
            response.setHeader("Retry-After", "60");
            Problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "조금 뒤에 다시 보내 주세요.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean allow() {
        long now = System.nanoTime();
        long start = windowStart.get();

        if (now - start >= WINDOW_NANOS) {
            // 창을 넘긴 첫 요청만 초기화한다. 동시에 여럿이 들어와도 한 번만 성공한다.
            if (windowStart.compareAndSet(start, now)) {
                count.set(0);
                reported.set(false);
            }
        }

        // 세는 것과 창을 바꾸는 것이 완전히 맞물려 있지는 않아서 창이 바뀌는 순간 몇 건이
        // 더 통과할 수 있다. 요금을 묶는 것이 목적이라 그 정도 오차는 받아들인다.
        return count.incrementAndGet() <= limitPerMinute;
    }
}
