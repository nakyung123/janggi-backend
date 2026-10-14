package com.nakyung.janggi.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 잘못 온 요청을 400 으로 바꾸는 자리. 한 곳에 모아 둬서 컨트롤러가 깨끗해진다.
 *
 * <p>답은 {@link ProblemDetail}(RFC 9457) 모양으로 준다. 스프링이 기본으로 들고 있는
 * 표준 틀이라 따로 만들 필요가 없고, 받는 쪽이 무엇으로 짜였든 같은 모양으로 읽는다.
 *
 * <p><b>왜 답을 주는가</b> - 브라우저는 {@code sendBeacon} 으로 던지고 잊어서 이 답을
 * 읽지 않는다. 그래도 적는 까닭은 <b>사람이 curl 로 부를 때와 테스트가 읽기 때문</b>이다.
 * 무엇이 틀렸는지 안 알려주면 고칠 수가 없다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 우리가 정한 규칙을 어긴 것. 모르는 이벤트 이름, 기보 모양, 기보 판 수 한도. */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException e) {
        log.info("잘못된 요청: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 칸 검사에 걸린 것(빈 묶음, 50건 초과, 너무 긴 글자 따위).
     *
     * <p>어느 칸이 왜 틀렸는지를 붙여 준다. "요청이 잘못됐습니다" 만 돌려주면 보낸 쪽은
     * 어디를 고쳐야 할지 모른다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("요청 내용이 규칙에 맞지 않습니다.");
        log.info("검사에 걸린 요청: {}", detail);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * JSON 자체가 깨졌거나 몸통이 비어 있는 것.
     *
     * <p>원문을 그대로 돌려주지 않는다. 파서가 내는 글에는 우리 클래스 이름과 줄 번호가
     * 섞여 나오는데, 그건 공개된 주소에서 밖에 내보낼 것이 아니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        log.info("읽을 수 없는 몸통: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "JSON 을 읽을 수 없습니다.");
    }
}
