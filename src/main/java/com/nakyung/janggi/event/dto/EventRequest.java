package com.nakyung.janggi.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 한 건의 이벤트. 프런트의 {@code src/report/events.ts} 가 만드는 모양 그대로다.
 *
 * <p><b>길이 제한을 전부 걸어 둔 까닭</b> - 이 주소는 누구나 부를 수 있게 공개된다.
 * 제한이 없으면 한 칸에 10MB 짜리 글자를 넣어 보내는 것을 막을 수 없고, 그게
 * 그대로 로그에 쌓인다. 실제 값은 전부 짧다(브라우저 이름은 길어야 "사파리 17").
 *
 * <p>값이 없을 수 있는 칸은 일부러 막지 않았다. {@code os} 는 모르는 기기면 비고,
 * {@code inApp} 은 앱 안에서 열었을 때만 있고, {@code memory} 는 크로미움 계열에만
 * 있다. <b>없는 것과 잘못된 것은 다르다</b> - 없는 것은 그대로 받아 적어야 "이 기기는
 * 메모리를 알 수 없었다" 를 나중에 셀 수 있다.
 *
 * @param name     이벤트 이름. 목록 확인은 {@code EventService} 가 한다
 * @param sid      방문 번호. 쪽을 열 때마다 새로 만든 8글자다. 사람을 이어 붙이지 않는다
 * @param ver      앱 버전
 * @param browser  브라우저 이름
 * @param os       운영체제. 모르면 빈다
 * @param inApp    앱 안에서 열었으면 그 앱 이름(카카오톡 따위). 아니면 빈다
 * @param isolated 격리됐는지. 이것이 거짓이면 엔진이 구조적으로 못 뜬다
 * @param cores    코어 수
 * @param memory   기기 메모리(GB). 크로미움 계열에만 있다
 * @param screen   화면 크기. "390x844" 모양
 * @param at       보낸 쪽 시각. <b>참고용이다</b> - 사용자 시계는 틀릴 수 있어 기준은 받은 시각이다
 * @param env      "prod" 또는 "dev". 내 손에서 난 것을 가려내려고 받는다
 * @param data     이벤트마다 다른 칸. 모양이 자주 바뀔 자리라 한 칸에 몰아 둔다
 */
public record EventRequest(
        @NotBlank @Size(max = 32) String name,
        @NotBlank @Size(max = 16) String sid,
        @Size(max = 16) String ver,
        @Size(max = 64) String browser,
        @Size(max = 32) String os,
        @Size(max = 32) String inApp,
        Boolean isolated,
        @Min(0) @Max(1024) Integer cores,
        @Min(0) @Max(1024) Integer memory,
        @Size(max = 16) String screen,
        Long at,
        @Size(max = 8) String env,
        @Size(max = 20) Map<String, Object> data) {
}
