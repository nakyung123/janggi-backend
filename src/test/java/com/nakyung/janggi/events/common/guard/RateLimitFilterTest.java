package com.nakyung.janggi.events.common.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 건수 제한은 <b>서버가 살아 있는 동안 쌓이는 상태</b>다. 그래서 테스트를 나눠 쓰면 앞
 * 테스트가 쓴 건수가 뒤 테스트에 남는다. 한 테스트에서 끝까지 확인한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"janggi.guard.limit-per-minute=3", "janggi.guard.max-body-bytes=65536"})
class RateLimitFilterTest {

    private static final String ONE = "{\"events\":[{\"name\":\"app_open\",\"sid\":\"t1\"}]}";

    @LocalServerPort int port;

    @Test
    @DisplayName("한도까지는 받고, 넘으면 429 로 거절한다")
    void 한도를_넘으면_거절() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertThat(Http.post(port, ONE).statusCode()).as("%d번째", i).isEqualTo(204);
        }

        var res = Http.post(port, ONE);

        assertThat(res.statusCode()).isEqualTo(429);
        assertThat(res.body()).contains("조금 뒤에 다시 보내 주세요.");
        // 언제 다시 보내면 되는지 알려 준다. 없으면 보낸 쪽이 곧바로 또 보내서 더 몰린다.
        assertThat(res.headers().firstValue("Retry-After")).contains("60");

        // 한도에 걸린 뒤에도 상태 확인은 살아 있어야 한다. 여기까지 막히면 Cloud Run 이
        // 서버를 죽은 것으로 보고 다시 띄우고, 그 콜드 스타트가 또 요금이다.
        assertThat(Http.getHealth(port).statusCode()).isEqualTo(200);
    }
}
