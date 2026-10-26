---
paths:
  - "build.gradle"
  - "src/**/*.java"
---

# Boot 4 는 자료와 다르다

찾아보는 글은 거의 전부 Boot 3 기준이다. 그대로 복사하지 않는다.

| 자료에 나오는 것 | 여기서는 |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` 하나 | 스타터마다 `-test` (`...-webmvc-test`) |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` (Jackson 3) |
| `JsonProcessingException` | `tools.jackson.core.JacksonException` |
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `logging.structured.format.console=gcp` | 그런 값은 없다. `ecs`·`gelf`·`logstash` 뿐 |
| `@Valid List<T>` | `List<@Valid T>` (바깥에 붙이면 HV000271 폐기 경고) |
| `HttpStatus.PAYLOAD_TOO_LARGE` | `CONTENT_TOO_LARGE` (RFC 9110 에서 이름이 바뀜) |
| `org.flywaydb:flyway-core` 하나 | `spring-boot-starter-flyway` (자동설정이 모듈로 떨어졌다) |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` (2.x) |
| `org.testcontainers:junit-jupiter` | `...:testcontainers-junit-jupiter` (2.x) |
| `new PostgreSQLContainer<>(...)` | `new PostgreSQLContainer(...)` (2.x 에서 제네릭이 사라짐) |

- `flyway-core` 만 넣으면 오류 없이 마이그레이션만 조용히 안 돈다. 증상은 `missing table` 이다. 의존성을 넣었는데 아무 일도 안 일어나면 조건 보고서에 그 자동설정이 있는지 본다. 없으면 부트 모듈이 빠진 것이다.
- 모르는 클래스는 짐작하지 말고 jar 를 열어 본다: `unzip -l <jar> | grep ClassName`
- Boot 3.5 는 2026-06-30 에, 4.0 은 2026-12-31 에 지원이 끝난다. 그래서 4.1 이다.
