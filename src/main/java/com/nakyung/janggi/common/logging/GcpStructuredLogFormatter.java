package com.nakyung.janggi.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.ObjectMapper;

/**
 * 스프링의 로그를 Cloud Logging 이 알아보는 JSON 한 줄로 만든다.
 *
 * <p><b>왜 직접 만드나.</b> 부트가 기본으로 주는 형식은 {@code ecs}·{@code gelf}·
 * {@code logstash} 세 가지뿐이고 <b>구글 것은 없다.</b> {@code gcp} 라고 적었다가
 * "Unknown format 'gcp'" 로 컨테이너가 시작도 못 하고 죽었다. 대신
 * {@code logging.structured.format.console} 에 <b>클래스 이름을 적는 길</b>이 열려
 * 있어서 그쪽을 쓴다.
 *
 * <p><b>왜 필요한가.</b> 사람이 읽는 패턴으로 찍으면 예외 스택 한 덩어리가 Cloud
 * Logging 에서 <b>줄마다 따로 쌓인다.</b> 20줄 스택이 20건이 되어 이어 읽을 수 없고,
 * 심각도도 전부 DEFAULT 로 들어가 오류만 걸러낼 수 없다. 실제로 장애가 났을 때 제일
 * 필요한 두 가지가 그것이다.
 *
 * <p>구글이 특별하게 보는 칸은 {@code severity} · {@code message} · {@code time} 이다.
 * 스택은 {@code message} 뒤에 붙인다 - 그래야 Error Reporting 이 같은 오류끼리 묶는다.
 */
public class GcpStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String 비상용 =
            "{\"severity\":\"ERROR\",\"message\":\"로그를 JSON 으로 만들지 못했습니다.\"}\n";

    @Override
    public String format(ILoggingEvent event) {
        // 전체를 감싼다. 여기는 **로그를 찍는 길 위**라서, 터지면 그 사실을 적을 수단도
        // 없고(또 이 길로 들어온다) 로그가 통째로 멈춘다. 처음에는 JSON 으로 만드는
        // 부분만 감쌌는데, getMDCPropertyMap() 이 NullPointerException 을 던지는 것을
        // 테스트에서 보고 넓혔다.
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("severity", severity(event.getLevel()));
            line.put("time", Instant.ofEpochMilli(event.getTimeStamp()).toString());
            line.put("logger", event.getLoggerName());
            line.put("thread", event.getThreadName());
            line.put("message", message(event));

            Map<String, String> mdc = mdc(event);
            if (!mdc.isEmpty()) {
                line.put("mdc", mdc);
            }

            return MAPPER.writeValueAsString(line) + "\n";
        } catch (RuntimeException ex) {
            return 비상용;
        }
    }

    /** MDC 는 비어 있을 수도, 읽다가 터질 수도 있다. 없는 것으로 보고 넘어간다. */
    private Map<String, String> mdc(ILoggingEvent event) {
        try {
            Map<String, String> map = event.getMDCPropertyMap();
            return map == null ? Map.of() : map;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    /** 스택을 메시지 뒤에 붙인다. 없으면 메시지만. */
    private String message(ILoggingEvent event) {
        String text = event.getFormattedMessage();
        if (event.getThrowableProxy() == null) {
            return text;
        }
        return text + "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
    }

    /**
     * 로그백의 단계를 구글의 심각도 이름으로 바꾼다. 이름이 다른 것은 WARN 하나뿐이지만,
     * 구글은 모르는 이름을 받으면 DEFAULT 로 넣어 버려서 걸러낼 수 없게 된다.
     */
    private String severity(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "ERROR";
            case Level.WARN_INT -> "WARNING";
            case Level.INFO_INT -> "INFO";
            default -> "DEBUG";
        };
    }
}
