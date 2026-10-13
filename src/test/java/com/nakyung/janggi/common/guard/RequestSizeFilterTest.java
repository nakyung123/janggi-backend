package com.nakyung.janggi.common.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "janggi.guard.max-body-bytes=200",
            // 건수 제한이 끼어들지 않게 넉넉히 둔다. 두 문지기를 한 테스트에서 같이
            // 건드리면 어느 쪽이 거절했는지 알 수 없다.
            "janggi.guard.limit-per-minute=1000"
        })
class RequestSizeFilterTest {

    private static final String ONE = "{\"events\":[{\"name\":\"app_open\",\"sid\":\"t1\"}]}";

    @LocalServerPort int port;

    @Test
    @DisplayName("크기 안에 들면 받는다")
    void 작은_본문() throws Exception {
        assertThat(Http.post(port, ONE).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("본문이 한도를 넘으면 413 으로 거절한다")
    void 큰_본문() throws Exception {
        String 긴_값 = "x".repeat(500);
        String body =
                "{\"events\":[{\"name\":\"app_open\",\"sid\":\"t1\",\"data\":{\"k\":\""
                        + 긴_값
                        + "\"}}]}";

        var res = Http.post(port, body);

        assertThat(res.statusCode()).isEqualTo(413);
        assertThat(res.body()).contains("본문이 너무 큽니다.");
    }

    @Test
    @DisplayName("길이를 알려주지 않으면 411 로 거절한다")
    void 길이_없는_본문() throws Exception {
        var res = Http.postWithoutLength(port, ONE);

        assertThat(res.statusCode()).isEqualTo(411);
        assertThat(res.body()).contains("본문 길이를 알려 주세요.");
    }

    @Test
    @DisplayName("본문 없는 메서드는 길이를 따지지 않는다")
    void 사전_요청() throws Exception {
        // 브라우저는 교차 출처로 보내기 전에 본문 없는 OPTIONS 를 먼저 던진다. 그걸
        // "길이를 안 알려줬다" 며 411 로 돌려보내면 **본 요청은 아예 오지 않는다.**
        // 지금은 같은 출처로 쓸 계획이라 사전 요청이 안 생기지만, 그 전제가 바뀌는
        // 순간 원인 찾기 어려운 고장이 된다.
        assertThat(Http.options(port).statusCode()).isNotEqualTo(411);

        // GET 도 본문이 없다. 길이 문제가 아니라 "그런 메서드는 없다"(405)로 답해야 한다.
        assertThat(Http.get(port).statusCode()).isEqualTo(405);
    }

    @Test
    @DisplayName("상태 확인은 크기 문지기를 거치지 않는다")
    void 상태_확인은_통과() throws Exception {
        // GET 에는 길이가 없다. 문지기가 모든 주소에 붙어 있었다면 여기서 411 이 난다.
        // 그러면 Cloud Run 이 서버가 죽은 줄 알고 계속 다시 띄우고, 그게 요금이 된다.
        assertThat(Http.getHealth(port).statusCode()).isEqualTo(200);
    }
}
