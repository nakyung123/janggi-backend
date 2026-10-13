package com.nakyung.janggi.event;

import com.nakyung.janggi.event.dto.EventRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link EventStore} 를 Postgres 로 구현한다.
 *
 * <p><b>{@code @Transactional} 이 여기서는 실제로 돈다.</b> 부르는 쪽이 다른 빈
 * ({@code EventService})이라 프록시를 거치기 때문이다. 같은 클래스 안에서 부르면
 * 프록시를 안 거쳐서 <b>오류 없이 트랜잭션만 조용히 없다</b> - 그러면 묶음이 반쯤
 * 들어가도 모른다. 이 클래스를 따로 둔 까닭 하나가 그것이다.
 *
 * <p><b>묶음 하나가 한 트랜잭션이다.</b> 반쯤 들어가면 나중에 숫자를 믿을 수 없다.
 * {@code EventService} 가 이름을 미리 전부 확인하는 것과 같은 생각이다.
 */
@Service
class JpaEventStore implements EventStore {

    private static final Logger log = LoggerFactory.getLogger(JpaEventStore.class);

    private final EventRepository 저장소;
    private final ObjectMapper mapper;

    JpaEventStore(EventRepository 저장소, ObjectMapper mapper) {
        this.저장소 = 저장소;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void 넣기(List<EventRequest> events, Instant receivedAt) {
        for (EventRequest e : events) {
            저장소.넣기(
                    receivedAt,
                    e.name(),
                    e.sid(),
                    e.at(),
                    e.env(),
                    e.ver(),
                    e.browser(),
                    e.os(),
                    e.inApp(),
                    e.isolated(),
                    짧게(e.cores()),
                    짧게(e.memory()),
                    e.screen(),
                    글자로(e.data()));
        }
    }

    /**
     * 칸이 {@code SMALLINT} 라 줄인다. 들어오는 값은 {@code EventRequest} 의 검증에서
     * 0~1024 로 막혀 있어 넘칠 일이 없다.
     */
    private static Short 짧게(Integer 값) {
        return 값 == null ? null : 값.shortValue();
    }

    /**
     * {@code data} 를 글자로 바꾼다. 넣을 때 {@code CAST(? AS jsonb)} 가 받는다.
     *
     * <p><b>이 칸이 실패해도 나머지는 넣는다.</b> 칸 하나 때문에 "그 사람이 왔다" 는
     * 사실까지 버리지 않는다. 로그 쪽에는 이미 온전하게 남아 있다.
     */
    private String 글자로(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(data);
        } catch (JacksonException ex) {
            log.warn("data 를 JSON 으로 만들지 못했습니다. 그 칸만 비웁니다.", ex);
            return null;
        }
    }
}
