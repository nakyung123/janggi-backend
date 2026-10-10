package com.nakyung.janggi.events.event;

import com.nakyung.janggi.events.event.dto.EventBatch;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저가 보내는 사용 기록을 받는 자리.
 *
 * <p>일부러 얇게 둔다. 여기서 하는 일은 <b>받아서 넘기는 것</b>뿐이고, 무엇을 받아
 * 들일지와 어떻게 적을지는 {@code EventService} 가 정한다. 컨트롤러가 두꺼워지면
 * 같은 규칙을 다른 입구에서 또 쓰고 싶을 때 복사하게 된다.
 */
@RestController
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    /**
     * 사용 기록을 받는다.
     *
     * <p><b>204(내용 없음)를 주는 까닭</b> - 돌려줄 것이 없다. 브라우저는
     * {@code navigator.sendBeacon} 으로 던지고 잊기 때문에 <b>답을 읽지도 않는다.</b>
     * 200 에 빈 몸통을 실으면 쓸데없는 바이트만 오간다.
     */
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void collect(@Valid @RequestBody EventBatch batch) {
        events.record(batch.events());
    }
}
