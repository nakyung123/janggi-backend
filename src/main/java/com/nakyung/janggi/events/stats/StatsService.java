package com.nakyung.janggi.events.stats;

import com.nakyung.janggi.events.stats.dto.Stats;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 셀 범위를 정하고 모양을 만든다.
 *
 * <p><b>{@code prod} 만 센다.</b> 내가 확인하러 들어간 것(`?nostats`), CI 의 배포 확인,
 * 부하 시험이 전부 {@code dev} 로 들어와 있다. 섞어 세면 <b>내가 만든 숫자를 내가 보고
 * 늘었다고 좋아하게 된다.</b>
 */
@Service
public class StatsService {

    /** 기본으로 보는 기간. */
    static final int 기본_일수 = 7;

    /**
     * 한 번에 볼 수 있는 최대 기간.
     *
     * <p>표가 커지면 긴 기간은 느려지고, 느린 질의는 곧 Cloud Run 요금이다. 지금은
     * 백 줄짜리라 아무 값이나 괜찮지만, <b>제한이 없으면 늘었을 때 아무도 안 알려준다.</b>
     */
    static final int 최대_일수 = 90;

    private final StatsRepository 저장소;

    public StatsService(StatsRepository 저장소) {
        this.저장소 = 저장소;
    }

    /**
     * @param days 며칠치. 없거나 범위를 벗어나면 기본값으로 민다 - 400 으로 돌려보내지
     *     않는다. 이걸 보는 사람은 나 하나고, 숫자를 잘못 적었다고 아무것도 안 보여 주는
     *     것보다 쓸 만한 값을 보여 주는 쪽이 낫다.
     */
    @Transactional(readOnly = true)
    public Stats 보기(Integer days) {
        int 일수 = days == null || days < 1 ? 기본_일수 : Math.min(days, 최대_일수);
        Instant 지금 = Instant.now();
        Instant 부터 = 지금.minus(Duration.ofDays(일수));
        String env = "prod";

        List<Stats.Step> 깔때기 =
                저장소.깔때기(env, 부터).stream()
                        .map(r -> new Stats.Step(r.getName(), r.getEvents(), r.getSessions()))
                        .toList();

        List<Stats.Day> 날짜별 =
                저장소.날짜별(env, 부터).stream()
                        .map(r -> new Stats.Day(r.getDay(), r.getSessions(), r.getEvents()))
                        .toList();

        return new Stats(env, 부터, 지금, 저장소.방문수(env, 부터), 깔때기, 날짜별);
    }
}
