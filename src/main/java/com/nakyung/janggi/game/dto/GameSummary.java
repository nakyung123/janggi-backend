package com.nakyung.janggi.game.dto;

/**
 * 목록 한 줄. <b>판의 내용이 들어 있지 않다.</b>
 *
 * <p>목록 화면은 {@code vs 12급 · 어제 · 초 기권패 · 2수} 처럼 요약만 보여 준다. 그래서
 * {@code history} 와 {@code reviewed} 를 <b>질의에서 아예 뽑지 않는다</b> - 한 판이 4만
 * 자쯤이라, 스무 판 목록에 그걸 끌고 오면 800KB가 오간다.
 *
 * <p>여기가 이 설계에서 N+1 대신 조심할 자리다. 조인이 없어서 N+1 은 생길 구조가 아니지만,
 * <b>{@code SELECT *} 한 번이면 같은 값을 치른다.</b>
 *
 * @param id 서버 번호. 기보 하나를 열거나 지울 때 쓴다
 * @param clientId 브라우저가 만든 번호. 이 기기 목록과 합칠 때 쓴다
 * @param moves 수의 개수
 */
public record GameSummary(
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
        boolean reviewed,

        /**
         * 끝났을 때의 판(FEN) 하나.
         *
         * <p><b>내용 전체를 주지 않으려고 이 한 칸만 뽑는다.</b> 목록 카드가 끝난 판을
         * 작은 그림으로 보여 주는데 거기 필요한 것이 이것뿐이다. 없으면 브라우저가 판
         * 하나하나를 또 불러야 한다 - 스무 판이면 스무 번이다.
         */
        String lastFen) {}
