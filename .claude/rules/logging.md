---
paths:
  - "src/main/resources/logback-spring.xml"
  - "src/main/java/com/nakyung/janggi/common/logging/**"
  - "src/main/java/com/nakyung/janggi/event/EventService.java"
---

# 로그 모양

Cloud Logging 은 stdout 의 한 줄이 **전체가** JSON 일 때만 칸으로 뜯는다. 앞에 시각이나 레벨이 붙으면 통째로 글자가 되어 조건 검색이 안 된다.

| 무엇 | 어떻게 |
| --- | --- |
| 이벤트 기록 | `janggi.events.record` 로거 → 메시지만 |
| 스프링 자신의 로그 | `GcpStructuredLogFormatter` (예외 스택이 한 건으로 묶인다) |

- 로거 이름 `janggi.events.record` 는 `EventService` 와 `logback-spring.xml` 의 `<logger name=...>` 가 짝이다. 한쪽만 바꾸면 오류 없이 검색만 안 된다.
- `additivity="false"` 를 지우면 같은 줄이 두 번 찍혀 건수가 두 배가 된다.
- `gcp` 라는 구조화 로그 형식은 없다. 그래서 클래스 이름을 직접 적는다.
- 로그 줄을 문자열을 이어 붙여 만들지 않는다. 줄바꿈으로 가짜 JSON 줄을 끼워 넣을 수 있다(`EventLogLineTest`).
