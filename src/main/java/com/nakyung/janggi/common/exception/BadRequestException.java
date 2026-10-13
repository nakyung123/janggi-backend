package com.nakyung.janggi.common.exception;

/**
 * 보낸 쪽이 잘못한 것. {@code ApiExceptionHandler} 가 400 으로 바꾼다.
 *
 * <p>자바가 기본으로 주는 {@code IllegalArgumentException} 을 쓰지 않는 까닭 -
 * 그걸 전부 400 으로 바꾸면 <b>우리 코드의 버그까지 "네가 잘못 보냈다" 로 둔갑한다.</b>
 * 서버가 틀린 것은 500 으로 나와야 우리가 알아챈다.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
