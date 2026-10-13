package com.nakyung.janggi.event;

import com.nakyung.janggi.common.exception.BadRequestException;
import com.nakyung.janggi.event.dto.EventRequest;
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
 * <p><b>두 군데 적는다. 로그가 먼저다.</b>
 *
 * <ol>
 *   <li>로그 - 늘 된다. 돌아가는 부품이 없다.
 *   <li>DB - 여기가 실패해도 <b>요청은 204 로 끝낸다.</b>
 * </ol>
 *
 * <p>순서가 뒤집히면 안 된다. 로그가 먼저라야 DB 가 죽은 동안의 기록이 남고, 나중에
 * 그것을 보고 채워 넣을 수 있다. DB 를 먼저 쓰고 실패하면 <b>그 건은 어디에도 없다.</b>
 *
 * <p><b>DB 실패에 500 을 주지 않는 까닭</b> - 브라우저는 {@code sendBeacon} 으로 던지고
 * <b>답을 읽지 않는다.</b> 500 을 줘 봐야 다시 보내지도 않고, 사용자에게 보이지도 않는다.
 * 얻는 것 없이 "서비스가 고장났다" 는 신호만 남는다. 대신 경고 로그를 남긴다 -
 * 조용히 넘어가는 것과 요청을 깨뜨리는 것은 다르다.
 *
 * <p><b>로그를 JSON 한 줄로 찍는 까닭</b> - Cloud Logging 은 stdout 에 나온 줄이
 * <b>전체가 JSON 일 때만</b> 그것을 뜯어 칸으로 만들어 준다. 그래야 DB 가 없어도
 * "브라우저가 사파리인 것만" 같은 조건으로 찾을 수 있다.
 *
 * <p>그래서 보통 쓰는 클래스 로거를 쓰지 않는다. 클래스 로거는 앞에 시각·레벨·클래스명이
 * 붙어서 줄이 JSON 이 아니게 되고, Cloud Logging 이 통째로 글자 덩어리로 넣어 버린다.
 * 대신 {@code janggi.events.record} 라는 이름의 로거로 보내고, {@code logback-spring.xml}
 * 에서 그 이름만 <b>메시지만 찍도록</b> 따로 묶었다. 로거 이름을 바꾸면 그 연결이 끊긴다.
 */
@Service
public class EventService {

    /** 경고와 오류용. 사람이 읽는다. */
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    /**
     * 이벤트 기록용. <b>이 이름은 {@code logback-spring.xml} 과 짝이다.</b> 거기서 이
     * 이름만 장식 없이 찍도록 묶어 두었다.
     */
    private static final Logger eventLog = LoggerFactory.getLogger("janggi.events.record");

    private final ObjectMapper mapper;
    private final EventStore 창고;

    public EventService(ObjectMapper mapper, EventStore 창고) {
        this.mapper = mapper;
        this.창고 = 창고;
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

        넣기(events, receivedAt);
    }

    /**
     * 창고에 넘긴다. <b>실패를 삼키되 조용히는 아니다.</b>
     *
     * <p>기록은 바로 위에서 이미 로그에 적혔다. 쌓는 데 실패했다고 요청을 깨뜨리면
     * 얻는 것 없이 "서비스가 고장났다" 는 신호만 남는다 - 브라우저는 답을 읽지도 않는다.
     * 대신 경고를 남긴다. 이 줄이 쌓이면 <b>"DB 에만 구멍이 난 구간"</b> 을 집어내서
     * 로그에서 채워 넣을 수 있다.
     */
    private void 넣기(List<EventRequest> events, Instant receivedAt) {
        try {
            창고.넣기(events, receivedAt);
        } catch (RuntimeException ex) {
            log.warn(
                    "쌓지 못했습니다. 로그에는 남아 있습니다. {}건 sid={}",
                    events.size(),
                    events.get(0).sid(),
                    ex);
        }
    }

    private void write(EventRequest e, Instant receivedAt) {
        Map<String, Object> line = new LinkedHashMap<>();
        // severity 와 message 는 Cloud Logging 이 특별하게 보는 칸이다. severity 가 없으면
        // 전부 DEFAULT 로 들어가 심각도로 걸러낼 수 없고, message 가 없으면 로그 목록의
        // 한 줄 요약이 비어서 펼치지 않고는 무슨 이벤트인지 알 수 없다.
        line.put("severity", "INFO");
        line.put("message", e.name());
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
            eventLog.info(mapper.writeValueAsString(line));
        } catch (JacksonException ex) {
            // 적는 데 실패했다고 요청을 깨뜨리지는 않는다. 다만 조용히 넘어가지도 않는다 -
            // 이 줄이 쌓이면 "왜 숫자가 비는지" 를 여기서 찾을 수 있다.
            log.warn("이벤트를 JSON 으로 만들지 못했습니다. evt={} sid={}", e.name(), e.sid(), ex);
        }
    }
}
