package com.nakyung.janggi.common.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 본문이 너무 크면 읽지 않고 거절한다.
 *
 * <p><b>스프링은 JSON 본문의 크기를 막아 주지 않는다.</b> 설정 목록을 직접 확인했다 -
 * {@code server.max-http-post-size} 와 {@code server.tomcat.max-http-post-size} 는
 * 둘 다 폐기됐고, 살아 있는 {@code server.tomcat.max-http-form-post-size} 는 이름대로
 * <b>폼 데이터에만</b> 걸린다. 그래서 지금 이 요청 제한이 없으면 누가 100MB JSON 을 보내도
 * 톰캣이 다 받아서 잭슨이 다 파싱한다. 공개 주소에서는 그게 곧 요금이다.
 *
 * <p><b>길이를 안 알려주면 받지 않는다.</b> 길이를 모르는 요청(chunked)은 다 읽어 봐야
 * 크기를 알 수 있어서 "읽지 않고 거절" 이 불가능하다. 브라우저의 {@code sendBeacon} 은
 * 항상 길이를 붙이므로, 길이가 없는 요청은 우리 쪽에서 올 일이 없다. 그래서 세는 장치를
 * 만드는 대신 <b>411 로 돌려보낸다</b> - 구멍이 남지 않고 코드도 짧다.
 */
public class RequestSizeFilter extends OncePerRequestFilter {

    /**
     * 본문을 가질 수 있는 메서드. 이것만 검사한다.
     *
     * <p><b>이 구분이 없으면 CORS 사전 요청이 막힌다.</b> 브라우저는 교차 출처로
     * 보내기 전에 본문 없는 {@code OPTIONS} 를 먼저 던지는데, 그걸 "길이를 안 알려줬다"
     * 며 411 로 돌려보내면 <b>본 요청은 아예 오지도 않는다.</b> 지금 계획은 Vercel
     * 되돌림으로 같은 출처가 되게 하는 것이라 사전 요청이 생기지 않지만, 그 전제가
     * 바뀌는 순간 원인 찾기 어려운 고장이 된다. {@code GET} · {@code HEAD} 도 본문이
     * 없어서 길이를 따질 일이 없다(둘 다 MVC 가 405 로 답하는 것이 맞다).
     */
    private static final Set<String> 본문을_가질_수_있는_메서드 = Set.of("POST", "PUT", "PATCH");

    private final long maxBytes;

    public RequestSizeFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!본문을_가질_수_있는_메서드.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        long length = request.getContentLengthLong();

        if (length < 0) {
            Problems.write(response, HttpStatus.LENGTH_REQUIRED, "본문 길이를 알려 주세요.");
            return;
        }
        if (length > maxBytes) {
            Problems.write(response, HttpStatus.CONTENT_TOO_LARGE, "본문이 너무 큽니다.");
            return;
        }

        chain.doFilter(request, response);
    }
}
