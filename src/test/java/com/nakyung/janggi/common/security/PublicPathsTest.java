package com.nakyung.janggi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>보안을 넣고도 열려 있어야 하는 것이 그대로 열려 있나.</b>
 *
 * <p>이 검사가 막으려는 사고는 이렇다 - 보안 의존성을 넣으면 기본값이 "모든 주소를
 * 막는다" 라서, {@code /events} 와 {@code /actuator/health} 가 같이 401 이 된다.
 * <b>배포는 성공하고 테스트도 다 통과하는데</b> 실서버에서 측정이 안 들어오고 Cloud Run
 * 이 서버를 죽은 것으로 보고 계속 다시 띄운다.
 *
 * <p>그래서 <b>닫혀야 하는 것도 같이 본다.</b> 넷 다 200 이면 "다 열려 있다" 와
 * 구별되지 않는다 - 모르는 주소가 거절되는 것을 함께 봐야 이 검사가 무언가를 말한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 건수 제한이 끼어들면 어느 쪽이 거절했는지 알 수 없다.
        properties = "janggi.guard.limit-per-minute=1000")
class PublicPathsTest {

    /** 울타리의 permitAll 목록에 없는 주소. 아래 검사용 컨트롤러가 이 자리를 맡는다. */
    private static final String 안적힌길 = "/test-unlisted";

    @LocalServerPort int port;

    private final HttpClient 손님 = HttpClient.newHttpClient();

    private HttpResponse<String> 가져오기(String 길) throws Exception {
        return 손님.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + 길)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("상태 확인은 아무나 볼 수 있다 - 막히면 Cloud Run 이 서버를 계속 다시 띄운다")
    void 상태확인() throws Exception {
        assertThat(가져오기("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("측정은 인증도 CSRF 토큰도 없이 들어온다 - sendBeacon 은 머리글을 못 붙인다")
    void 측정입구() throws Exception {
        String 본문 =
                """
                {"events":[{"name":"app_open","sid":"opentest","env":"test","at":1760000000000}]}
                """;
        HttpResponse<String> 답 =
                손님.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(본문))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(답.statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("통계는 여전히 토큰이 맡는다 - 보안이 가로채 403 으로 바꾸지 않는다")
    void 통계는_토큰이_맡는다() throws Exception {
        // 토큰을 안 걸어 두면 TokenFilter 가 404 를 낸다. 403 이 나오면 보안 울타리가
        // 먼저 거절한 것이고, 그러면 **없는 주소인 척하기**가 깨진다 - 토큰으로 막는
        // 주소는 있다는 사실조차 알려 주지 않는 것이 요점이었다.
        assertThat(가져오기("/stats").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("적어 두지 않은 주소는 404 로 닫힌다 - 없는 주소와 똑같이 보인다")
    void 모르는_주소() throws Exception {
        // **403 이 아니라 404 다.** 403 으로 두면 없는 주소는 403, /stats 는 404 가 되어
        // 통계 주소만 튄다. TokenFilter 가 404 를 고른 까닭과 같다.
        //
        // **매핑이 없는 주소로 부른다.** 전에는 /games 로 불렀는데, 기보 주소가 생기면서
        // 그것은 "적어 두지 않은 주소" 가 아니라 **로그인해야 열리는 주소**가 됐다. 그대로
        // 두면 이름과 다른 것을 보게 된다. 단수형을 고른 까닭은 **있을 법한 오타**라서다.
        // 한글 주소는 쓰지 않는다 - URI.create 가 ASCII 가 아닌 글자에서 터진다.
        //
        // **이 단정으로는 울타리가 일하는지 알 수 없다.** 다룰 자리가 없으면 막든 열든
        // 스프링이 404 를 낸다. 재 봤다 - denyAll 을 permitAll 로 바꾸고 전체를 돌렸는데
        // 하나도 안 깨졌다. 그래서 아래 검사를 따로 둔다.
        assertThat(가져오기("/game").statusCode()).isEqualTo(404);
    }

    /**
     * 울타리에 안 적힌 주소를 <b>검사에서만</b> 하나 만든다.
     *
     * <p>이것이 없으면 {@code anyRequest().denyAll()} 가지를 아무도 안 본다. 기본값이
     * "막는다" 에서 "연다" 로 바뀌면 <b>앞으로 더하는 주소가 전부 열린 채로 나간다</b> -
     * 적어 두는 것을 깜빡한 사람은 그 사실도 모른다.
     */
    @TestConfiguration
    @RestController
    static class 안적힌주소 {

        @GetMapping(안적힌길)
        String 답() {
            return "열렸다";
        }
    }

    @Test
    @DisplayName("울타리에 안 적힌 주소는 다룰 자리가 있어도 404 - 기본값이 '막는다' 여야 한다")
    void 안_적힌_주소() throws Exception {
        assertThat(가져오기(안적힌길).statusCode()).isEqualTo(404);
    }
}
