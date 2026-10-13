package com.nakyung.janggi.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GcpStructuredLogFormatterTest {

    private final GcpStructuredLogFormatter formatter = new GcpStructuredLogFormatter();
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private Map<String, Object> 찍어보기(Level level, String 메시지, Throwable 예외) {
        LoggerContext context = new LoggerContext();
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.nakyung.janggi.Test");
        event.setLevel(level);
        event.setMessage(메시지);
        event.setTimeStamp(1_760_000_000_000L);
        event.setThreadName("http-nio-1");
        if (예외 != null) {
            event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(예외));
        }
        event.setLoggerContext(context);

        String 줄 = formatter.format(event);

        // 한 줄이어야 한다. 줄이 나뉘면 Cloud Logging 이 따로따로 쌓는다.
        assertThat(줄).endsWith("\n");
        assertThat(줄.strip()).doesNotContain("\n");

        return mapper.readValue(줄, Map.class);
    }

    @Test
    @DisplayName("구글이 알아보는 칸으로 찍는다")
    void 기본_모양() {
        Map<String, Object> 결과 = 찍어보기(Level.INFO, "서버가 떴습니다", null);

        assertThat(결과.get("severity")).isEqualTo("INFO");
        assertThat(결과.get("message")).isEqualTo("서버가 떴습니다");
        assertThat(결과.get("time")).isEqualTo("2025-10-09T08:53:20Z");
        assertThat(결과.get("logger")).isEqualTo("com.nakyung.janggi.Test");
    }

    @Test
    @DisplayName("WARN 은 WARNING 으로 바꾼다")
    void 단계_이름() {
        // 구글은 'WARN' 을 모른다. 그대로 보내면 DEFAULT 로 들어가 걸러낼 수 없다.
        assertThat(찍어보기(Level.WARN, "조심", null).get("severity")).isEqualTo("WARNING");
        assertThat(찍어보기(Level.ERROR, "터짐", null).get("severity")).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("예외 스택이 한 건 안에 다 들어간다")
    void 예외_스택() {
        Map<String, Object> 결과 =
                찍어보기(Level.ERROR, "적지 못했습니다", new IllegalStateException("밑바닥"));

        String 메시지 = (String) 결과.get("message");
        assertThat(메시지).startsWith("적지 못했습니다");
        // 스택이 message 안에 있어야 Error Reporting 이 같은 오류끼리 묶는다.
        assertThat(메시지).contains("IllegalStateException", "밑바닥", "at ");
    }

    @Test
    @DisplayName("한글이 깨지지 않는다")
    void 한글() {
        Map<String, Object> 결과 = 찍어보기(Level.INFO, "사파리 17 · 아이폰", null);

        assertThat((String) 결과.get("message")).isEqualTo("사파리 17 · 아이폰");

        // 깨진 글자를 소스에 적지 않고 번호로 만든다. 자바의 유니코드 이스케이프로
        // 적으면, 그 글자를 거쳐 가는 도구가 역슬래시를 먹는 순간 **진짜 깨진 글자
        // 한 개**가 소스에 박힌다. 실제로 그렇게 박혔었고, 테스트는 의미가 같아서
        // 그대로 통과해서 아무도 몰랐다. 깨진 글자를 찾는 CI 가 생기고서야 드러났다.
        // 번호로 만들면 거쳐 가는 도구가 손댈 글자가 없다.
        assertThat((String) 결과.get("message")).doesNotContain(String.valueOf((char) 0xFFFD));
    }
}
