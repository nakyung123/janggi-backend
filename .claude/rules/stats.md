---
paths:
  - "src/main/java/com/nakyung/janggi/stats/**"
  - "src/test/java/com/nakyung/janggi/stats/**"
---

# 통계 (`GET /stats?days=7`)

머리글 `X-Stats-Token` 에 `JANGGI_STATS_TOKEN` 값을 넣어 부른다. 로컬은 `.env`, Cloud Run 은 Secret Manager 다.

## 막는 쪽

- 거절은 403 이 아니라 404 다. 없는 주소와 똑같이 보여야 해서 `Problems` 대신 `sendError` 로 넘긴다. 테스트가 둘을 견준다.
- 토큰이 안 걸려 있으면 아무도 못 들어온다. CI 가 토큰 없이 띄워 404 인지 본다.
- 토큰 비교는 상수 시간(`MessageDigest.isEqual`)이다. 토큰에 한글을 쓸 수 없다(머리글은 ASCII).
- `/events` 와 요청 제한 건수를 따로 센다. 같이 세면 통계를 여는 것이 기록 받을 몫을 깎는다.

## 세는 쪽

- `prod` 만 센다. `?nostats`·CI 배포 확인·부하 시험은 `dev` 로 들어온다.
- `events` 와 `sessions` 를 같이 준다. 한 사람이 세 판 두면 `first_move` 는 3이지만 사람은 하나다.
- 날짜는 한국 시각으로 자른다. 저장은 UTC, 볼 때만 옮긴다.
- 기기별은 방문 단위로 먼저 접는다(`WITH visit AS (... GROUP BY sid)`). 그냥 `GROUP BY browser` 하면 이벤트를 많이 보낸 사람이 자기 브라우저를 부풀린다.
- 같은 줄에 `sessions` 가 있어야 분모가 된다.
- 들어온 곳은 셋으로 가른다: 호스트(눌러서 옴) / `(직접)`(`ref` 가 빈 값) / `(안 보냄)`(`ref` 칸이 없는 옛 기록).
- `engine` 은 `n` 을 같이 준다. 서른 건의 p90 은 한두 점이 정한다.

## 테스트

- 세는 테스트는 돌리기 전에 표를 비운다. `@Transactional(NOT_SUPPORTED)` 라 되돌려지지 않아, 앞 테스트가 넣은 것이 뒤 테스트의 숫자가 된다.
