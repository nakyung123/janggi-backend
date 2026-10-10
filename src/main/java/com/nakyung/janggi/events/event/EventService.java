package com.nakyung.janggi.events.event;

import com.nakyung.janggi.events.common.exception.BadRequestException;
import com.nakyung.janggi.events.event.dto.EventRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 받은 이벤트를 걸러서 적는다.
 *
 * <p><b>지금은 로그에만 적는다.</b> DB 는 1-5 에서 붙인다. 먼저 브라우저에서 서버까지
 * 관이 뚫리는지를 확인하는 것이 1단계의 목적이고, DB 를 같이 붙이면 안 될 때 범인이
 * 둘이 된다.
 *
 * <p><b>로그를 JSON 한 줄로 찍는 까닭</b> - Cloud Logging 이 JSON 을 알아서 뜯어
 * 칸으로 만들어 준다. 그래서 DB 가 없어도 "브라우저가 사파리인 것만" 같은 조건으로
 * 찾을 수 있다. 사람이 읽는 글로 찍으면 나중에 전부 손으로 세야 한다.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final ObjectMapper mapper;

    public EventService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 묶음을 받아 한 건씩 적는다.
     *
     * <p>이름이 하나라도 목록에 없으면 <b>묶음 전체를 받지 않는다.</b> 섞어서 일부만
     * 받으면 보낸 쪽은 성공한 줄 알고, 받은 쪽에는 구멍이 생긴다. 그런 구멍은 나중에
     * 숫자가 안 맞을 때 원인을 찾을 수 없게 만든다.
     */
    public void record(List<EventRequest> events) {
        // 먼저 전부 확인한다. 적기 시작한 뒤에 틀린 것을 발견하면 되돌릴 수 없다.
        for (EventRequest e : events) {
            EventName.of(e.name())
                    .orElseThrow(() -> new BadRequestException("모르는 이벤트 이름입니다: " + e.name()));
        }

        Instant receivedAt = Instant.now();
        for (EventRequest e : events) {
            write(e, receivedAt);
        }
    }

    private void write(EventRequest e, Instant receivedAt) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("evt", e.name());
        // 받은 시각이 기준이다. 보낸 쪽 시각(at)은 사용자 시계가 틀릴 수 있어 참고용이다.
        line.put("receivedAt", receivedAt.toString());
        line.put("sentAt", e.at());
        line.put("sid", e.sid());
        line.put("ver", e.ver());
        line.put("browser", e.browser());
        line.put("os", e.os());
        line.put("inApp", e.inApp());
        line.put("isolated", e.isolated());
        line.put("cores", e.cores());
        line.put("memory", e.memory());
        line.put("screen", e.screen());
        line.put("env", e.env());
        line.put("data", e.data());

        try {
            log.info("{}", mapper.writeValueAsString(line));
        } catch (JacksonException ex) {
            // 적는 데 실패했다고 요청을 깨뜨리지는 않는다. 다만 조용히 넘어가지도 않는다 -
            // 이 줄이 쌓이면 "왜 숫자가 비는지" 를 여기서 찾을 수 있다.
            log.warn("이벤트를 JSON 으로 만들지 못했습니다. evt={} sid={}", e.name(), e.sid(), ex);
        }
    }
}
