package com.nakyung.janggi.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 한 번에 보내는 묶음. {@code {"events": [...]}} 모양이다.
 *
 * <p><b>왜 배열을 그냥 받지 않고 한 겹 감쌌나</b> - 나중에 묶음 전체에 붙는 값(보낸
 * 시각, 형식 버전 같은 것)을 더할 수 있다. 맨 바깥이 배열이면 그런 것을 더하는 순간
 * 모양이 통째로 바뀌어서, 옛날 앱을 쓰는 사람이 보내는 것을 못 받게 된다. 칸을 하나
 * 더 넣는 것은 깨지 않는 변경이다.
 *
 * <p><b>50건 제한</b> - 한 번 열면 보통 두세 건이다. 50을 넘겼다면 사람이 쓴 것이
 * 아니다. 바이트 크기와 요청 수는 {@code common.guard} 의 문지기가 따로 막는다.
 *
 * <p>{@code @Valid} 가 {@code List} 바깥이 아니라 <b>안쪽(타입 인자)</b>에 붙어 있다.
 * 바깥에 붙이면 하이버네이트 밸리데이터가 폐기 경고(HV000271)를 내는데, 그게 시작할
 * 때마다 세 줄씩 쌓인다. 이 서버는 요청이 드물어 거의 매번 새로 뜨므로 그 세 줄이
 * 계속 불어난다.
 *
 * @param events 이벤트들. 비어 있으면 받지 않는다 - 보낼 게 없으면 아예 안 보내야 한다
 */
public record EventBatch(
        @NotEmpty @Size(max = 50) List<@Valid EventRequest> events) {
}
