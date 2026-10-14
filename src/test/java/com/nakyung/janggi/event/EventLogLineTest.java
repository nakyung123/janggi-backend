package com.nakyung.janggi.event;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nakyung.janggi.event.dto.EventRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 적히는 줄이 <b>언제나 한 줄</b>인지 본다.
 *
 * <p>이게 깨지면 <b>기록을 위조할 수 있다.</b> 보내는 값 안에 줄바꿈을 넣어 가짜 JSON
 * 줄을 만들면, Cloud Logging 은 그것을 별개의 이벤트로 받아들인다. 그러면 숫자를 믿을
 * 수 없고, 더 나쁘게는 "있지도 않은 사용" 이 통계에 들어간다.
 *
 * <p>지금은 잭슨이 줄바꿈을 글자로 바꿔 주기 때문에 막힌다. 다만 <b>그건 우리가 고른
 * 성질이 아니라 쓰고 있는 도구의 성질</b>이라, 도구를 바꾸거나 직접 문자열을 이어
 * 붙이는 순간 조용히 깨진다. 그래서 여기 못으로 박아 둔다.
 */
class EventLogLineTest {

    private static final ObjectMapper 읽기 = JsonMapper.builder().build();

    private Logger 로거;
    private ListAppender<ILoggingEvent> 담아두기;
    private EventService service;

    @BeforeEach
    void 준비() {
        // 여기서 보려는 것은 **적히는 줄의 모양**뿐이라 저장소는 아무것도 안 하는 것을 끼운다.
        // 진짜 저장소를 쓰면 이 테스트가 Postgres 를 띄워야 한다 - 보려는 것과 상관없는 값이다.
        service = new EventService(JsonMapper.builder().build(), (events, receivedAt) -> {});

        담아두기 = new ListAppender<>();
        담아두기.start();
        로거 = (Logger) LoggerFactory.getLogger("janggi.events.record");
        로거.addAppender(담아두기);
    }

    @AfterEach
    void 정리() {
        로거.detachAppender(담아두기);
    }

    private String 적힌_줄(Map<String, Object> data) {
        service.record(
                List.of(
                        new EventRequest(
                                "app_open", "s1", null, null, null, null, null, null, null, null,
                                null, null, data)));
        assertThat(담아두기.list).hasSize(1);
        return 담아두기.list.get(0).getFormattedMessage();
    }

    @Test
    @DisplayName("값에 줄바꿈이 들어와도 한 줄로 적힌다")
    void 줄바꿈_위조() {
        String 가짜 = "진짜값\n{\"evt\":\"app_open\",\"sid\":\"WIJO\"}";

        String 줄 = 적힌_줄(Map.of("ref", 가짜));

        // 줄이 쪼개지면 Cloud Logging 이 두 건으로 받는다.
        assertThat(줄).doesNotContain("\n");
        assertThat(줄).doesNotContain("\r");

        // 그리고 값은 잃지 않아야 한다 - 막는다고 지워 버리면 무슨 일이 있었는지 모른다.
        Map<?, ?> 읽은것 = 읽기.readValue(줄, Map.class);
        assertThat(((Map<?, ?>) 읽은것.get("data")).get("ref")).isEqualTo(가짜);
    }

    @Test
    @DisplayName("따옴표와 역슬래시도 줄을 깨지 않는다")
    void 따옴표_위조() {
        String 가짜 = "\"},{\"evt\":\"app_open\",\"sid\":\"WIJO2\",\"x\":\"";

        String 줄 = 적힌_줄(Map.of("ref", 가짜));

        Map<?, ?> 읽은것 = 읽기.readValue(줄, Map.class);
        assertThat(읽은것.get("evt")).isEqualTo("app_open");
        assertThat(읽은것.get("sid")).isEqualTo("s1");
        assertThat(((Map<?, ?>) 읽은것.get("data")).get("ref")).isEqualTo(가짜);
    }
}
