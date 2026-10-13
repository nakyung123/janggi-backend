package com.nakyung.janggi.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * 받아들이는 이벤트 이름.
 *
 * <p>여기가 <b>유일한 목록</b>이다. 새 이벤트를 더할 때 고칠 곳은 이 파일 하나다.
 *
 * <p>목록에 없는 이름은 받지 않는다(400). 느슨하게 받아 두면 오타나 장난이 섞여
 * 들어오고, 나중에 숫자를 셀 때 그게 몇 건인지 가려낼 방법이 없다. 대신 대가가
 * 하나 있다 - 프런트에 새 이벤트를 더할 때는 <b>서버를 먼저 배포</b>해야 한다.
 * 순서가 바뀌면 그 사이에 온 것이 버려진다.
 *
 * <p>자바 이름은 대문자(APP_OPEN)로 두고 실제로 오가는 글자는 따로 들고 있는다.
 * 자바 관례를 지키면서 밖과 주고받는 모양은 프런트가 보내는 그대로 맞춘다.
 */
public enum EventName {
    /** 쪽이 열렸다. 깔때기의 분모다. */
    APP_OPEN("app_open"),
    /** 엔진이 떠서 판을 둘 수 있게 됐다. */
    ENGINE_READY("engine_ready"),
    /** 엔진이 못 떴다. 지금 유일하게 알려진 장애다. */
    ENGINE_FAILED("engine_failed"),

    /*
     * 여기부터는 깔때기의 아래쪽이다.
     *
     * 2026-10-11 까지 모은 것으로 "엔진이 뜨나" 에는 답이 났다 - 아홉 명 중 아홉 명이
     * 떴다. 그런데 머문 시간이 6초인 사람과 743초인 사람이 있고 <b>그 사이에 무엇을
     * 했는지를 모른다.</b> 다음 질문은 "그럼 왜 떠나나" 이고, 그것을 재려고 넷을 더한다.
     */

    /** 판을 시작했다. 보기만 하고 간 사람과 가른다. */
    GAME_START("game_start"),
    /** 한 수라도 뒀다. <b>깔때기에서 제일 크게 깎일 자리로 본다.</b> */
    FIRST_MOVE("first_move"),
    /** 판이 끝났다. 이겼든 졌든 기권이든. */
    GAME_END("game_end"),
    /** 복기를 열었다. 쓰는 사람이 있는지 아직 모른다. */
    REVIEW_OPEN("review_open");

    private final String wire;

    EventName(String wire) {
        this.wire = wire;
    }

    /** 밖으로 오가는 글자. */
    public String wire() {
        return wire;
    }

    /** 모르는 이름이면 비어 있는 값을 준다. 던지지 않는 까닭은 부르는 쪽에서 400 을 만들기 때문이다. */
    public static Optional<EventName> of(String wire) {
        return Arrays.stream(values()).filter(e -> e.wire.equals(wire)).findFirst();
    }
}
