package com.nakyung.janggi.game.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 브라우저가 올리는 판 하나.
 *
 * <p><b>칸을 두 가지로 나눠 받는다.</b> 서버가 꺼내 쓰는 것(목록에 보일 것들)은 타입과
 * 길이를 못 박고, 판의 내용({@code history} · {@code reviewed})은 <b>모양을 못 박지
 * 않는다.</b>
 *
 * <p><b>왜 내용을 못 박지 않나</b> - 잭슨은 모르는 칸을 조용히 버린다(부트 기본값).
 * 프런트가 칸을 하나 더하는 날 서버가 그것을 버리고, 그러면 <b>올렸는데 돌려받을 때
 * 없는</b> 상태가 된다. 오류도 안 난다. {@code ReviewedMove.playedPlain} 주석에 "예전
 * 복기에는 없어서 물음표를 붙인다" 고 적혀 있으니, 이미 한 번 는 자리다.
 *
 * <p><b>그래도 통째로 믿지는 않는다.</b> 열 개수와 꼭 있어야 하는 칸은 본다
 * ({@code GameStore} 가 확인한다). 그리고 본문 전체 크기는 문지기가 막는다 - 한도가
 * 없으면 10MB 를 500번 올려 1GB 짜리 무료 저장을 채울 수 있다.
 */
public record GameUpload(
        /** 브라우저가 만든 번호(`newGameId`). 36진수 시각 + '-' + 4자라 14자쯤이다. */
        @NotBlank @Size(max = 24) String id,

        /** 판이 끝난 때(ms). 브라우저 시각이다. */
        @NotNull @Positive Long endedAt,

        @NotBlank @Size(max = 4) String mySide,
        @NotBlank @Size(max = 16) String levelId,
        @NotBlank @Size(max = 32) String levelName,
        @NotBlank @Size(max = 32) String variant,
        @NotNull @Valid 결과 result,

        /**
         * 국면 목록. 첫 칸이 시작 국면이라 <b>수의 개수는 길이 - 1</b> 이다.
         *
         * <p>타입이 {@link JsonNode} 인 까닭은 위 클래스 주석에 있다.
         */
        @NotNull JsonNode history,

        /** 복기 결과. 안 돌렸으면 없거나 {@code null} 이다. */
        JsonNode reviewed) {

    /** 어떻게 끝났고 누가 이겼나. */
    public record 결과(
            @NotBlank @Size(max = 16) String kind,
            /** 비겼거나 안 끝났으면 {@code null}. */
            @Size(max = 4) String winner) {}
}
