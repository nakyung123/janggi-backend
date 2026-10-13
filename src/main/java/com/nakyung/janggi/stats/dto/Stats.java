package com.nakyung.janggi.stats.dto;

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
 * @param devices 기기별 방문·엔진·첫 수. <b>방문 단위로 접어서</b> 센다
 * @param referrers 들어온 곳
 * @param engine 엔진 뜨는 시간
 */
public record Stats(
        String env,
        Instant from,
        Instant to,
        long sessions,
        List<Step> funnel,
        List<Day> daily,
        List<Device> devices,
        List<Referrer> referrers,
        Engine engine) {

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

    /**
     * 기기 한 줄. {@code kind} 는 "브라우저" · "OS" · "인앱" 가운데 하나다.
     *
     * <p><b>{@code sessions} 가 분모다.</b> {@code engine}·{@code moved} 만 보면
     * 원래 많은 기기가 늘 1등이라 아무것도 알 수 없다.
     *
     * @param p50 그 기기의 엔진 뜨는 시간 중앙값(ms). 엔진이 안 뜬 기기면 {@code null}
     */
    public record Device(
            String kind, String label, long sessions, long engine, long moved, Integer p50) {}

    /**
     * 들어온 곳 한 줄.
     *
     * <p>{@code (직접)} 은 주소를 직접 치거나 책갈피로 온 것이고, {@code (안 보냄)} 은
     * {@code ref} 를 보내기 전에 쌓인 옛 기록이다. <b>둘은 다른 말이라 섞지 않는다.</b>
     */
    public record Referrer(String label, long sessions) {}

    /**
     * 엔진 뜨는 시간(ms).
     *
     * @param n 표본 수. <b>이것 없이 p90 을 읽으면 안 된다</b> - 서른 건에서 p90 은
     *     한두 점이 정하는 값이다
     * @param p50 절반이 이 안에 뜬다
     * @param p90 열 중 아홉이 이 안에 뜬다
     * @param worst 가장 오래 걸린 것. 꼬리가 어디까지 가는지 본다
     */
    public record Engine(long n, Integer p50, Integer p90, Integer worst) {}
}
