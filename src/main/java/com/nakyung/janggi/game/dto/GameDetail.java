package com.nakyung.janggi.game.dto;

import tools.jackson.databind.JsonNode;

/**
 * 기보 하나. 판의 내용까지 들어 있다.
 *
 * <p>{@code history} 와 {@code reviewed} 는 <b>올라온 그대로</b> 돌려준다. 서버가 모양을
 * 못 박지 않았으므로 꺼낼 때도 손대지 않는다 - 손대면 그 순간 서버가 모양을 아는 셈이 되고,
 * 프런트가 칸을 더하는 날 깨진다.
 */
public record GameDetail(
        long id,
        String clientId,
        long endedAt,
        String mySide,
        String levelId,
        String levelName,
        String variant,
        String resultKind,
        String resultWinner,
        int moves,
        JsonNode history,
        JsonNode reviewed) {}
