package com.nakyung.janggi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nakyung.janggi.auth.TokenIssuer;
import com.nakyung.janggi.user.AppUserStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>로그인해야 열리는 주소를 빠뜨리지 않는다.</b>
 *
 * <p>막으려는 사고는 이렇다 - {@code GameController} 에 메서드를 하나 더 붙이면 그 주소도
 * 쿠키로 열린다. 그런데 <b>낡은 쿠키가 거기서도 막히는지는 아무도 안 본다.</b> 기존 검사는
 * 주소를 손으로 적어 두고 있어서 새 주소가 생겨도 조용히 통과한다. 이 저장소에서 이미 같은
 * 일이 있었다 - 화면 쪽에서 inAppLabel() 을 만들고 한 군데에 안 붙였는데 타입 검사도
 * 찾기도 아무 말을 안 했다.
 *
 * <p>그래서 <b>스프링이 들고 있는 매핑을 읽어 아래 표와 맞춰 본다.</b> 표에 없는 주소가
 * 생기면 빨간불이고, 표에 있는데 매핑이 없어져도 빨간불이다(죽은 줄이 쌓이지 않게).
 *
 * <p><b>열거가 검증을 대신하지 않는다.</b> 열거는 표를 유지하도록 강제할 뿐이고, 실제로
 * 막히는지는 주소마다 세 번 불러서 본다 - 쿠키 없이, 세대가 안 맞는 쿠키로, 제대로 된
 * 쿠키로. <b>세 번째가 있어야 앞의 둘에 이가 생긴다.</b> 표에 주소를 오타로 적으면 없는
 * 주소라서 404 가 나고, 그것만 보면 "막혔다" 와 구별되지 않는다.
 *
 * <p><b>답이 한 가지가 아니다.</b> 세대가 안 맞는 쿠키로 목록을 부르면 빈 배열이고, 올리기는
 * 400, 하나 열기와 지우기는 404 다. 사람을 찾는 질의에 세대 조건을 접어 넣은 구조에서
 * 나오는 차이다. 어느 쪽도 남의 것을 흘리지 않지만 <b>같지는 않아서</b> 표에 주소마다
 * 적는다. 뭉뚱그려 "다 404" 로 적으면 지금 계약이 숨는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            // 건수 제한이 끼어들면 어느 쪽이 거절했는지 알 수 없다.
            "janggi.guard.limit-per-minute=1000",
            "janggi.guard.game-limit-per-minute=1000"
        })
@Testcontainers
@EnabledIfDockerAvailable
class ProtectedPathsTest {

    @Container @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18-alpine");

    /**
     * 주소 한 줄.
     *
     * @param 방법 HTTP 메서드
     * @param 패턴 스프링이 아는 모양. 열거와 맞추는 열쇠다
     * @param 길 실제로 부를 주소. 판번호 자리는 올려 둔 기보 번호로 바뀐다
     * @param 본문 쓰기면 올릴 것, 읽기면 null
     * @param 보호됨 로그인해야 열리나
     * @param 쿠키없음 쿠키 없이 불렀을 때의 상태 코드
     * @param 낡은쿠키 세대가 안 맞는 쿠키로 불렀을 때의 상태 코드
     */
    private record 줄(
            String 방법,
            String 패턴,
            String 길,
            String 본문,
            boolean 보호됨,
            int 쿠키없음,
            int 낡은쿠키) {

        /** 열거와 맞추는 열쇠. */
        String 열쇠() {
            return 방법 + " " + 패턴;
        }
    }

    /** 길에서 이 자리가 올려 둔 기보 번호로 바뀐다. */
    private static final String 판자리 = "{판}";

    /**
     * <b>이 표가 전부여야 한다.</b> 스프링이 아는 주소와 한 줄도 어긋나면 안 된다.
     *
     * <p>열린 주소도 적는다. 적어 두지 않으면 "보호해야 할 것을 열어 둔 것" 과 "일부러 연
     * 것" 이 구별되지 않는다. 열린 주소가 실제로 어떻게 도는지는 {@link PublicPathsTest}
     * 가 본다 - 여기서는 <b>열어 둔 것이 뜻한 바인지</b>만 적는다.
     */
    private static final List<줄> 표 =
            List.of(
                    new 줄("POST", "/events", "/events", 측정본문(), false, 204, 204),
                    new 줄("GET", "/stats", "/stats", null, false, 404, 404),
                    new 줄("GET", "/me", "/me", null, false, 200, 200),
                    new 줄("POST", "/games", "/games", 기보본문("판-새것"), true, 404, 400),
                    new 줄("GET", "/games", "/games", null, true, 404, 200),
                    new 줄("GET", "/games/{id}", "/games/" + 판자리, null, true, 404, 404),
                    new 줄("DELETE", "/games/{id}", "/games/" + 판자리, null, true, 404, 404));

    @LocalServerPort int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping 매핑;

    @Autowired TokenIssuer 토큰;
    @Autowired AppUserStore 사용자창고;
    @Autowired JdbcClient sql;
    @Autowired ObjectMapper 젝슨;

    private final HttpClient 손님 = HttpClient.newHttpClient();

    private String 내쿠키;
    private String 낡은쿠키;

    @BeforeEach
    void 비우기() {
        sql.sql("DELETE FROM game").update();
        sql.sql("DELETE FROM app_user").update();
        AppUserStore.로그인한사람 나 = 사용자창고.로그인("구글-sub-울타리");
        내쿠키 = 쿠키(나.id(), 나.epoch());
        // **서명은 맞고 세대만 안 맞는 쿠키.** 탈퇴하고 되살리면 세대가 올라가서 전에 내준
        // 쿠키가 이 꼴이 된다. DB 를 건드려 흉내 내면 지금 쓰는 쿠키까지 같이 낡으므로
        // 발급할 때 세대만 하나 낮춰 만든다.
        낡은쿠키 = 쿠키(나.id(), 나.epoch() - 1);
    }

    @Test
    @DisplayName("스프링이 아는 주소와 표가 한 줄도 어긋나지 않는다 - 손으로 적은 목록은 틀어진다")
    void 열거() {
        assertThat(스프링이아는주소())
                .containsExactlyInAnyOrderElementsOf(표.stream().map(줄::열쇠).toList());
    }

    @Test
    @DisplayName("로그인해야 열리는 주소는 쿠키 없이도 낡은 쿠키로도 막힌다")
    void 보호된주소() throws Exception {
        for (줄 그줄 : 표.stream().filter(줄::보호됨).toList()) {
            long 판 = 판하나();
            String 길 = 그줄.길().replace(판자리, String.valueOf(판));

            assertThat(보내기(그줄.방법(), 길, 그줄.본문(), null).statusCode())
                    .as("%s 를 쿠키 없이", 그줄.열쇠())
                    .isEqualTo(그줄.쿠키없음());
            assertThat(보내기(그줄.방법(), 길, 그줄.본문(), 낡은쿠키).statusCode())
                    .as("%s 를 세대가 안 맞는 쿠키로", 그줄.열쇠())
                    .isEqualTo(그줄.낡은쿠키());
            // **이 줄이 앞의 둘에 이를 준다.** 주소를 오타로 적었으면 제대로 된 쿠키로도
            // 404 가 나고, 앞의 두 단정은 "없는 주소라서 404" 를 보고 통과한 것이 된다.
            assertThat(보내기(그줄.방법(), 길, 그줄.본문(), 내쿠키).statusCode())
                    .as("%s 를 제대로 된 쿠키로 - 여기서 404 면 주소가 틀린 것이다", 그줄.열쇠())
                    .isNotEqualTo(404);
        }
    }

    /** 스프링이 들고 있는 매핑 중 <b>우리가 적은 것</b>만. 액추에이터와 오류 주소는 뺀다. */
    private Set<String> 스프링이아는주소() {
        Set<String> 모음 = new TreeSet<>();
        매핑.getHandlerMethods()
                .forEach(
                        (정보, 다룸) -> {
                            if (!다룸.getBeanType()
                                    .getPackageName()
                                    .startsWith("com.nakyung.janggi")) {
                                return;
                            }
                            Set<RequestMethod> 방법들 = 정보.getMethodsCondition().getMethods();
                            // 메서드를 안 적은 매핑은 모든 메서드로 열린다. 조용히 빠지면
                            // 열거가 그 주소를 못 보므로 여기서 멈춘다.
                            if (방법들.isEmpty()) {
                                throw new IllegalStateException("메서드를 안 적은 매핑이 있습니다: " + 정보);
                            }
                            for (String 패턴 : 정보.getPathPatternsCondition().getPatternValues()) {
                                for (RequestMethod 방법 : 방법들) {
                                    모음.add(방법 + " " + 패턴);
                                }
                            }
                        });
        return 모음;
    }

    /** 지우기와 하나 열기에 쓸 기보를 하나 올려 둔다. 없으면 제대로 된 쿠키로도 404 다. */
    private long 판하나() throws Exception {
        HttpResponse<String> 답 = 보내기("POST", "/games", 기보본문("판-" + System.nanoTime()), 내쿠키);
        assertThat(답.statusCode()).as("검사용 기보 올리기").isEqualTo(200);
        return 젝슨.readTree(답.body()).path("id").asLong();
    }

    private String 쿠키(long 번호, int 세대) {
        MockHttpServletResponse 받아둘곳 = new MockHttpServletResponse();
        토큰.심기(받아둘곳, 번호, 세대);
        return TokenIssuer.이름 + "=" + 받아둘곳.getCookie(TokenIssuer.이름).getValue();
    }

    /** CSRF 토큰을 받아 온다. 쓰기에는 이것이 있어야 울타리를 지난다. */
    private String csrf받기(String 쿠키) throws Exception {
        HttpResponse<String> 답 =
                손님.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                                .header("Cookie", 쿠키)
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return 답.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("CSRF 토큰이 안 왔습니다"));
    }

    private HttpResponse<String> 보내기(String 방법, String 길, String 본문, String 쿠키)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + 길));
        if (쿠키 != null) {
            String csrf = csrf받기(쿠키);
            b.header("Cookie", 쿠키 + "; XSRF-TOKEN=" + csrf);
            b.header("X-XSRF-TOKEN", csrf);
        }
        if (본문 != null) {
            b.header("Content-Type", "application/json");
        }
        b.method(
                방법,
                본문 == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(본문));
        return 손님.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String 측정본문() {
        return """
               {"events":[{"name":"app_open","sid":"울타리","env":"test","at":1760000000000}]}""";
    }

    private static String 기보본문(String 번호) {
        return """
               {"id":"%s","endedAt":1760000000000,"mySide":"cho","levelId":"k12",\
               "levelName":"12급","variant":"janggi","result":{"kind":"resign","winner":"cho"},\
               "history":[{"fen":"판모양0","move":null,"notation":"표기0","mover":null,"score":0},\
               {"fen":"판모양1","move":"a1b1","notation":"표기1","mover":"cho","score":10}],\
               "reviewed":null}"""
                .formatted(번호);
    }
}
