package com.nakyung.janggi.events.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.events.common.guard.TokenFilter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * {@code /stats} 를 <b>막는 부분만</b> 본다.
 *
 * <p>여기서는 H2 로 뜬다. 토큰이 틀리면 질의까지 가지 않으므로 DB 가 무엇이든 상관없고,
 * 그래서 도커 없이 빠르게 돈다.
 *
 * <p><b>통과한 뒤를 여기서 못 본다.</b> 세는 질의가 Postgres 문법
 * ({@code AT TIME ZONE}, {@code ::date})이라 H2 에서는 500 이 난다. 그쪽은
 * {@link StatsCountTest} 가 진짜 Postgres 로 본다 - <b>가짜 DB 로 통과시키면 실서버에서
 * 깨지는 종류가 된다.</b>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // **ASCII 여야 한다.** HTTP 머리글 값에 한글을 못 넣는다 - 자바의
            // HttpClient 가 보내기 전에 거절한다(invalid header value).
            "janggi.stats.token=open-sesame-1234",
            // 건수 제한이 끼어들면 어느 쪽이 거절했는지 알 수 없다.
            "janggi.guard.limit-per-minute=1000"
        })
class StatsTest {

    @LocalServerPort int port;

    private HttpResponse<String> 부르기(String 토큰) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/stats"));
        if (토큰 != null) {
            b.header(TokenFilter.HEADER, 토큰);
        }
        return HttpClient.newHttpClient().send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("토큰이 없으면 404 다 - 403 이 아니다")
    void 토큰이_없으면_없는_주소처럼() throws Exception {
        var res = 부르기(null);
        // 403 을 주면 "여기 뭔가 있다" 를 알려주는 셈이다. 404 는 아무것도 안 알려준다.
        assertThat(res.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("토큰이 틀려도 404 다")
    void 토큰이_틀려도() throws Exception {
        assertThat(부르기("wrong-token").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("한 글자만 달라도, 대소문자만 달라도 404 다")
    void 한_글자만_달라도() throws Exception {
        assertThat(부르기("open-sesame-123").statusCode()).isEqualTo(404);
        assertThat(부르기("Open-sesame-1234").statusCode()).isEqualTo(404);
        // 뒤에 공백을 붙인 것은 여기서 확인할 수 없다 - **HTTP 가 머리글 값의 앞뒤 공백을
        // 떼고 보낸다.** 우리 코드에 닿기 전에 같은 값이 된다. 모르고 "공백도 막힌다" 고
        // 적어 두면 거짓이 된다.
    }

    @Test
    @DisplayName("없는 주소와 토큰 없는 /stats 가 똑같아 보인다")
    void 없는_주소와_구별되지_않는다() throws Exception {
        // 모양이 다르면 그 차이가 곧 "이 주소는 특별하다" 는 표가 된다. 그래서 거절할 때
        // Problems 를 쓰지 않고 sendError 로 넘긴다.
        var 없는곳 =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create("http://localhost:" + port + "/없는주소"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        var 토큰없이 = 부르기(null);

        assertThat(토큰없이.statusCode()).isEqualTo(없는곳.statusCode());
        assertThat(토큰없이.headers().firstValue("content-type"))
                .isEqualTo(없는곳.headers().firstValue("content-type"));

        // 몸통에서 **그때그때 달라지는 두 칸**(시각, 주소)을 빼고 견준다. 진짜로 없는
        // 주소를 쳐도 path 에는 그 주소가 적히므로, 거기까지 같기를 바라는 것은 틀린
        // 기대다. 보려는 것은 **모양이 같은가**다.
        assertThat(지운다(토큰없이.body())).isEqualTo(지운다(없는곳.body()));
        // 우리 거절 모양(Problems 의 title 칸)이 새어 나오면 그것이 곧 표가 된다.
        assertThat(토큰없이.body()).doesNotContain("\"title\"");
    }

    private static String 지운다(String 몸통) {
        return 몸통.replaceAll("\"timestamp\":\"[^\"]*\"", "")
                .replaceAll("\"path\":\"[^\"]*\"", "");
    }

    /**
     * <b>설정을 깜빡했을 때 어떻게 되는지</b>를 본다.
     *
     * <p>이것이 이 울타리에서 제일 중요한 성질이다. 위의 테스트들은 전부 토큰이 걸린
     * 상태라, <b>안 걸렸을 때 열리는 버그를 하나도 못 잡는다.</b> 공개 주소에서 그 버그는
     * "깜빡한 날 전부 새어 나감" 이다.
     */
    @org.junit.jupiter.api.Nested
    @org.springframework.test.context.TestPropertySource(properties = "janggi.stats.token=")
    class 토큰을_안_걸었을_때 {

        @Test
        @DisplayName("아무도 못 들어온다 - 맞는 토큰이라는 것이 없다")
        void 닫혀_있다() throws Exception {
            assertThat(부르기(null).statusCode()).isEqualTo(404);
            assertThat(부르기("").statusCode()).isEqualTo(404);
            // 다른 데서 쓰던 토큰을 그대로 들고 와도 안 열린다.
            assertThat(부르기("open-sesame-1234").statusCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("이벤트 받는 자리는 그대로 열려 있다")
    void 이벤트는_막히지_않는다() throws Exception {
        // **문지기를 새로 세우다 엉뚱한 주소를 같이 막는 일이 흔하다.** /stats 에 토큰을
        // 걸었는데 /events 까지 404 가 되면, 사용자는 아무 말 없이 기록만 사라진다
        // (sendBeacon 은 답을 안 읽는다).
        var res =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create("http://localhost:" + port + "/events"))
                                        .header("Content-Type", "application/json")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        "{\"events\":[{\"name\":\"app_open\","
                                                                + "\"sid\":\"s1\"}]}"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(204);
    }
}
