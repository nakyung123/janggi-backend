package com.nakyung.janggi.events.event.dto;

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
 * 아니다. 바이트 크기 제한과 요청 수 제한은 공개 주소가 되는 1-3 에서 따로 넣는다.
 *
 * @param events 이벤트들. 비어 있으면 받지 않는다 - 보낼 게 없으면 아예 안 보내야 한다
 */
public record EventBatch(
        @NotEmpty @Size(max = 50) @Valid List<EventRequest> events) {
}
