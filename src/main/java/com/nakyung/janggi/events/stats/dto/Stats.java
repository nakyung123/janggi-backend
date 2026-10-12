package com.nakyung.janggi.events.stats.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /stats} 가 돌려주는 모양.
 *
 * @param env 센 대상. 늘 {@code prod} 다 - 내가 확인하러 들어간 것(dev)은 세지 않는다
 * @param from 이때부터 (받은 시각 기준)
 * @param to 잰 순간
 * @param sessions 이 기간에 쪽을 연 횟수. <b>사람 수가 아니다</b> - sid 는 쪽을 열 때마다
 *     새로 만들어서 같은 사람이 두 번 오면 둘로 센다
 * @param funnel 이벤트별 깔때기
 * @param daily 날짜별. 날은 <b>한국 시각</b> 기준이다
 */
public record Stats(
        String env,
        Instant from,
        Instant to,
        long sessions,
        List<Step> funnel,
        List<Day> daily) {

    /**
     * 깔때기 한 칸.
     *
     * <p><b>{@code events} 와 {@code sessions} 를 같이 준다.</b> 한 사람이 세 판을 두면
     * {@code first_move} 가 3이지만 사람은 하나다. 둘을 안 가르면 "몇 명이 수를 뒀나" 를
     * 묻고 "몇 수를 뒀나" 를 받게 된다.
     */
    public record Step(String name, long events, long sessions) {}

    /** 하루치. */
    public record Day(LocalDate day, long sessions, long events) {}
}
