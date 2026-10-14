package com.nakyung.janggi.purge;

import com.nakyung.janggi.user.AppUserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 되돌릴 틈이 지난 계정을 치운다. <b>Cloud Scheduler 가 하루 한 번 부른다.</b>
 *
 * <p><b>이 주소는 사람이 부르는 자리가 아니다.</b> 그래서 {@code /internal} 로 시작하고,
 * 들어오는 길목에서 {@code GoogleTokenFilter} 가 구글이 만든 토큰을 확인한다. 토큰이
 * 없거나 틀리면 여기까지 오지도 않는다.
 *
 * <p><b>지운 줄 수를 기록에 남긴다.</b> 0이 아닌 날이 있었다는 것을 나중에 알 수 있어야
 * 한다. 이 일은 되돌릴 수 없어서, 몇 줄을 지웠는지가 남지 않으면 무슨 일이 있었는지
 * 영영 모른다.
 *
 * <p><b>콜드 스타트가 걸려도 괜찮다.</b> 사람이 화면을 보고 기다리는 길 위가 아니다.
 */
@RestController
public class PurgeController {

    private static final Logger log = LoggerFactory.getLogger(PurgeController.class);

    /** 돌려주는 모양. 칸을 더하기만 하고 빼지 않는다. */
    public record 치운결과(int deleted) {}

    private final AppUserStore 저장소;

    PurgeController(AppUserStore 저장소) {
        this.저장소 = 저장소;
    }

    @PostMapping("/internal/purge")
    치운결과 치우기() {
        int 지운줄 = 저장소.오래된것지우기();
        // 0 도 남긴다. "안 돌았다" 와 "돌았는데 지울 것이 없었다" 는 다른 말이다.
        log.info("되돌릴 틈이 지난 계정을 치웠습니다. 지운 줄 {}", 지운줄);
        return new 치운결과(지운줄);
    }
}
