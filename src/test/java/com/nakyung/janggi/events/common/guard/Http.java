package com.nakyung.janggi.events.common.guard;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 문지기 테스트용 HTTP 호출.
 *
 * <p>MockMvc 가 아니라 <b>진짜 소켓으로</b> 부른다. 문지기는 스프링 MVC 보다 앞에 서
 * 있고, "어느 주소에 붙는가" 를 정하는 것이 {@code FilterRegistrationBean} 이라서 가짜
 * 요청으로는 그 등록이 맞는지 확인할 수 없다.
 *
 * <p>길이를 안 알려주는 요청(chunked)을 보내야 해서 JDK 의 {@code HttpClient} 를 쓴다.
 * 입력 스트림으로 본문을 주면 길이를 모르는 채로 보낸다.
 */
final class Http {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private Http() {}

    /** 길이를 붙여서 POST 한다. 브라우저의 sendBeacon 과 같은 모양이다. */
    static HttpResponse<String> post(int port, String body) throws Exception {
        return send(
                request(port)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    /** 길이를 붙이지 않고 POST 한다(chunked). */
    static HttpResponse<String> postWithoutLength(int port, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return send(
                request(port)
                        .POST(
                                HttpRequest.BodyPublishers.ofInputStream(
                                        () -> new ByteArrayInputStream(bytes))));
    }

    /** 사전 요청. 본문이 없다. */
    static HttpResponse<String> options(int port) throws Exception {
        return send(
                request(port)
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .header("Origin", "https://janggi-ai.vercel.app")
                        .header("Access-Control-Request-Method", "POST"));
    }

    static HttpResponse<String> get(int port) throws Exception {
        return send(request(port).GET());
    }

    static HttpResponse<String> getHealth(int port) throws Exception {
        return send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                        .GET());
    }

    private static HttpRequest.Builder request(int port) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events"))
                .header("Content-Type", "application/json");
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return CLIENT.send(
                builder.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
