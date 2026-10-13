package com.nakyung.janggi.common.guard;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * 문지기가 거절할 때 쓰는 응답.
 *
 * <p>문지기는 스프링 MVC 보다 앞에 서 있어서 {@code @RestControllerAdvice} 가 받아
 * 주지 못한다. 그래서 {@code ApiExceptionHandler} 와 <b>같은 모양(RFC 9457)</b>을
 * 여기서 손으로 만든다. 모양이 달라지면 보낸 쪽에서 "왜 거절됐나" 를 두 가지 방식으로
 * 읽어야 한다.
 *
 * <p>{@code response.sendError()} 를 쓰지 않는다. 그건 서버의 기본 오류 쪽으로 넘겨서
 * 우리가 정한 모양이 아닌 응답이 나간다.
 */
final class Problems {

    private Problems() {}

    static void write(HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // detail 은 이 저장소 안에 박힌 글자만 들어온다. 요청에서 온 값을 그대로 끼워
        // 넣으면 따옴표 하나로 JSON 이 깨지므로, 그런 값은 여기로 넘기지 않는다.
        response.getWriter()
                .write(
                        "{\"title\":\""
                                + status.getReasonPhrase()
                                + "\",\"status\":"
                                + status.value()
                                + ",\"detail\":\""
                                + detail
                                + "\"}");
    }
}
