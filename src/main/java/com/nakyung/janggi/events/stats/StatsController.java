package com.nakyung.janggi.events.stats;

import com.nakyung.janggi.events.stats.dto.Stats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쌓인 것을 세어 보여 주는 자리.
 *
 * <p><b>여기에는 막는 코드가 없다.</b> 토큰 검사는 {@code common/guard} 의
 * {@code TokenFilter} 가 이 주소 앞에서 한다. 컨트롤러에 넣으면 입구가 하나 늘 때마다
 * 같은 검사를 복사하게 되고, <b>한 군데 깜빡하면 그 입구만 열린다.</b> 문지기는
 * {@code GuardConfig} 에 주소를 적어 세우므로 한 곳만 보면 된다.
 *
 * <p>{@code EventController} 와 같은 생각으로 얇게 둔다 - 받아서 넘기는 것만 한다.
 */
@RestController
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    /**
     * @param days 며칠치를 볼지. 안 주면 일주일.
     */
    @GetMapping("/stats")
    public Stats 보기(@RequestParam(required = false) Integer days) {
        return stats.보기(days);
    }
}
