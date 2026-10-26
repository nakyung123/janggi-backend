---
paths:
  - "src/main/resources/db/**"
  - "src/main/java/**/*Store*.java"
  - "src/main/java/**/*Repository*.java"
  - "src/main/resources/application*.properties"
  - "src/test/**"
---

# DB

- **로그가 먼저, DB 가 다음이다.** 로그는 늘 되고 DB 는 안 될 수 있다. DB 가 실패해도 `/events` 는 204 로 끝내고 경고 로그를 남긴다. 브라우저는 `sendBeacon` 이라 답을 읽지 않는다.
- **묶음 하나가 한 트랜잭션이다.** 트랜잭션은 `JpaEventStore` 에 걸려 있고, `EventService` 가 다른 빈이라 프록시를 거쳐 실제로 돈다. 같은 클래스 안에서 부르면 오류 없이 트랜잭션만 없다.
- **적용된 마이그레이션 파일은 한 글자도 고치지 않는다.** Flyway 는 파일의 검사합을 DB 에 적어 두고, 다르면 서버가 아예 안 뜬다. 2026-10-14 에 주석 낱말 하나로 배포가 깨졌다. 테스트는 빈 DB 에 처음부터 적용하므로 이걸 못 잡아서 `MigrationFilesTest` 가 파일 지문을 본다.
- **넣기는 네이티브 SQL(`ON CONFLICT DO NOTHING`)로 한다.** "찾아보고 없으면 넣기" 는 찾은 뒤와 넣기 전 사이에 깨진다. 읽기는 JPA 를 쓴다.
- **중복 기준 `(sid, name, sent_at)` 은 `V1__event.sql` 의 유일 인덱스와 짝이다.** 한쪽만 바꾸면 `ON CONFLICT` 가 통째로 실패한다. 이것이 멱등성을 맡는다(`sendBeacon` 은 재전송이 실제로 일어난다).
- **쉴 때 연결을 놓는다(`minimum-idle=0`).** Neon 무료는 5분 쉬면 잠들고 한 달 컴퓨트 100시간이다. 붙들고 있으면 시간이 닳는다.
- **풀러(`-pooler` 호스트)를 쓰지 않는다.** transaction mode 라 세션 잠금이 안 걸리는데, Flyway 가 그 잠금을 쓴다.
- **Hibernate 질의에 `?` 연산자를 쓰지 않는다.** 위치 파라미터로 읽힌다. `data ? 'ref'` 대신 `jsonb_exists(data, 'ref')`.
- SQL 식별자는 ASCII 로 둔다. 한글은 주석과 라벨 문자열에만.

## 테스트

- DB 를 건드리는 테스트는 Testcontainers 의 진짜 Postgres 18 로 돈다. H2 로는 `jsonb`·`percentile_cont`·`FILTER`·`ON CONFLICT` 가 확인되지 않는다.
- 테스트 설정을 `src/test/resources/application.properties` 에 두지 않는다. 이름이 같아 본체 설정을 통째로 가린다. `application-test.properties` 에 둔다.
- `@EnabledIfDockerAvailable` 을 지우지 않는다. `Dockerfile` 안에서 빌드할 때는 도커가 없어 이미지 빌드가 통째로 실패한다. CI 러너에는 도커가 있어 이미지 전에 한 번은 돈다.
