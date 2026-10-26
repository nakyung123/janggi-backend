---
paths:
  - "src/main/java/com/nakyung/janggi/common/guard/**"
  - "src/main/java/com/nakyung/janggi/common/security/**"
  - "src/main/java/com/nakyung/janggi/auth/**"
---

# 요청 제한 · 보안

- 공개 주소다. 누구나 부를 수 있다. 새 입구는 `GuardConfig` 에 주소를 같이 등록한다. 요청 제한은 주소를 하나하나 붙이게 되어 있다.
- 요청 제한은 본문이 있는 메서드만 검사한다. `OPTIONS` 를 411 로 막으면 CORS 사전 요청이 실패해 본 요청이 아예 오지 않는다.
- 상태 확인(`/actuator/health`)에는 요청 제한을 걸지 않는다. 몰아치는 동안 Cloud Run 이 죽었다고 보고 다시 띄우고, 그 콜드 스타트가 요금이다. 테스트가 한도에 막힌 뒤에도 health 가 200 인지 본다.
- `X-Forwarded-For` 로 사람을 가르지 않는다. 꾸밀 수 있는 값으로 세는 것은 제한이 아니다.
- 한도는 성능이 아니라 요금 때문이다. 로컬에서 초당 500건을 받는데 한도는 분당 120건이다.
- `RateLimitFilter` 는 미끄러지는 창이다. 고정 창은 경계에서 두 배까지 샜다. 둘을 구별하는 테스트는 "창은 한 칸씩 열린다" 하나뿐이다.
- 서버가 2대일 때 한도가 2배가 되는지는 확인하지 못했다. 확인하려면 느린 요청이 필요하다.
