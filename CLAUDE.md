# CLAUDE.md

**장기 AI(janggi-ai.vercel.app)가 보내는 사용 기록을 받아 쌓는 서버.**

모든 답변과 주석은 **한국어**로 쓴다.

## 이 서버의 자리

```
사람의 폰
   │
   ▼
Vercel (janggi-board)   화면을 준다      <- 없으면 사이트가 없다
   │  /api/ 로 오는 것만 넘긴다
   ▼
Cloud Run (여기)        기록을 받는다    <- 없어도 장기는 둬진다
```

**이 서버는 사람이 지나가는 길 위에 없다.** 브라우저가 `sendBeacon` 으로 던지고
잊기 때문에, 여기가 터져 있어도 쓰는 사람은 모른다. **그래서 콜드 스타트 몇 초를
감당할 수 있다.** 이 전제가 깨지는 기능(사람이 답을 기다리는 것)을 여기 붙이려면
설계를 다시 봐야 한다.

화면 쪽 저장소는 `janggi-board` 다. 두 저장소는 **코드로 안 붙고 주소 한 줄로만**
붙는다(`vercel.json` 의 되돌림).

## 어디에 무엇이 있나

```
janggi-events/
├── CLAUDE.md                            일하는 방식 (여기)
├── GAMES.md                             기보·사용자 설계 (아직 안 만들었다)
├── deploy/DEPLOY.md                     실제로 돌린 배포 명령
├── Dockerfile  .dockerignore            이미지
├── .env                                 접속 비밀. .gitignore 에 있다
├── src/main/resources/
│   ├── application.properties           어디서나
│   ├── application-cloud.properties     Cloud Run 에서만 (프로파일 cloud)
│   ├── logback-spring.xml               로그를 어떤 모양으로 내보내나
│   └── db/migration/                    Flyway. 스키마의 유일한 기준
├── src/test/resources/
│   └── application-test.properties      테스트에만 얹는 설정
├── test/load/events.js                  k6 부하 시험 (그래들과 무관)
└── src/main/java/com/nakyung/janggi/
    ├── JanggiApplication.java
    ├── event/                    기능 하나
    │   ├── EventController.java  받는다 (얇게)
    │   ├── EventName.java        받아들이는 이름 목록. 여기가 유일한 기준
    │   ├── EventService.java     거르고 · 로그에 적고 · 창고에 넘긴다
    │   ├── EventStore.java       쌓는 자리. 여기가 위아래를 가르는 금이다
    │   ├── JpaEventStore.java    그 구현. 트랜잭션이 여기 걸린다
    │   ├── EventEntity.java      표 한 줄. 넣기는 안 하고 맞춰 보기와 읽기를 한다
    │   ├── EventRepository.java  넣기는 네이티브 SQL (ON CONFLICT)
    │   └── dto/                  주고받는 모양
    ├── stats/                    쌓인 것을 세어 보여 준다 (토큰으로 막힌다)
    │   ├── StatsController.java  받는다 (얇게. 막는 코드가 없다)
    │   ├── StatsService.java     기간과 env 를 정한다
    │   ├── StatsRepository.java  세는 질의만
    │   └── dto/Stats.java        돌려줄 모양
    └── common/
        ├── exception/            400 으로 바꾸는 자리
        ├── guard/                입구에 선 문지기 (크기 · 건수)
        └── logging/              로그를 구글이 알아보는 모양으로
```

**`common/` 은 기능이 아니라 모든 입구에 공통으로 걸리는 것들이다.** 기보 저장 같은
기능이 붙어도 문지기와 로그 모양은 그대로 적용돼야 해서 `event/` 안에 두지 않았다.

**기능별로 묶는다.** 지금은 기능이 하나뿐이라 계층형과 똑같아 보이지만, 기보 저장
같은 것이 붙으면 **폴더 하나가 느는 것**으로 끝난다. 계층형이면 네 폴더를 돌아다녀야
한다.

**`presentation` / `application` / `domain` / `infrastructure` 네 겹으로 나누지
않는다.** 클래스가 다섯 개인데 네 겹을 만들면 찾기가 더 어려워진다. 그리고 Spring
Data 의 `JpaRepository` 자체가 이미 그 경계다 - 저장소를 갈아탈 계획이 생기면 그때
`domain` 에 인터페이스를 뺀다.

## Boot 4 는 자료와 다르다

**찾아보는 글은 거의 전부 Boot 3 기준이다. 그대로 복사하면 안 된다.**

| 자료에 나오는 것 | 여기서는 |
| --- | --- |
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-test` 하나 | 스타터마다 `-test` (`...-webmvc-test`) |
| `com.fasterxml.jackson.databind.ObjectMapper` | **`tools.jackson.databind.ObjectMapper`** (Jackson 3) |
| `JsonProcessingException` | **`tools.jackson.core.JacksonException`** |
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | **`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`** |
| `logging.structured.format.console=gcp` | **그런 값은 없다.** `ecs`·`gelf`·`logstash` 뿐 |
| `@Valid List<T>` | **`List<@Valid T>`** (바깥에 붙이면 HV000271 폐기 경고) |
| `HttpStatus.PAYLOAD_TOO_LARGE` | **`CONTENT_TOO_LARGE`** (RFC 9110 에서 이름이 바뀜) |
| `org.flywaydb:flyway-core` 하나 | **`spring-boot-starter-flyway`** (자동설정이 모듈로 떨어졌다) |
| `org.testcontainers:postgresql` | **`org.testcontainers:testcontainers-postgresql`** (2.x) |
| `org.testcontainers:junit-jupiter` | **`...:testcontainers-junit-jupiter`** (2.x) |
| `new PostgreSQLContainer<>(...)` | **`new PostgreSQLContainer(...)`** (2.x 에서 제네릭이 사라짐) |

**`flyway-core` 가 제일 고약하다.** 라이브러리는 클래스패스에 있는데 스프링이 안 불러서
**오류 없이 마이그레이션만 조용히 안 돈다.** 증상은 엉뚱하게 `missing table` 로 나온다.
의존성을 넣었는데 아무 일도 안 일어나면 **조건 보고서에 그 자동설정이 있는지** 본다 -
없으면 라이브러리가 아니라 **부트 모듈**이 빠진 것이다.

**모르는 클래스는 짐작하지 말고 jar 를 열어 본다.**

```bash
unzip -l <jar> | grep ClassName
```

Boot 3.5 는 2026-06-30 에, 4.0 은 2026-12-31 에 지원이 끝난다. 그래서 4.1 이다.

## 데이터베이스

**Neon Postgres(싱가포르).** 도쿄가 없어서 제일 가까운 데로 갔다. 접속 정보는 코드에
없고 `SPRING_DATASOURCE_URL` · `_USERNAME` · `_PASSWORD` 환경 변수로 들어온다 -
로컬은 `.env`, Cloud Run 은 서비스 환경 변수와 **Secret Manager** 다(비밀번호만).

- **두 군데 적고, 로그가 먼저다.** 로그는 늘 되고(돌아가는 부품이 없다) DB 는 안 될 수
  있다. 순서가 뒤집히면 DB 가 죽은 동안의 기록이 **어디에도 없다.** DB 가 실패해도
  요청은 204 로 끝낸다 - 브라우저는 `sendBeacon` 이라 답을 읽지 않아서 500 을 줘 봐야
  얻는 것이 없다. 대신 경고 로그를 남긴다.
- **묶음 하나가 한 트랜잭션이다.** 반쯤 들어가면 나중에 숫자를 못 믿는다. 트랜잭션은
  `JpaEventStore` 에 걸려 있고, **`EventService` 가 다른 빈이라서** 프록시를 거쳐
  실제로 돈다. 같은 클래스 안에서 부르면 프록시를 안 거쳐 **오류 없이 트랜잭션만
  조용히 없다** - `EventStore` 를 떼어낸 까닭 하나가 그것이다.
- **스키마를 만드는 것은 Flyway 뿐이다.** 하이버네이트는 `ddl-auto=validate` 로 맞춰
  보기만 한다. 엔티티와 표가 한 글자라도 어긋나면 **시작조차 못 한다** - 넣다가 터지면
  그 요청의 기록은 이미 버려진 뒤라서, 일찍 큰 소리로 터지는 쪽이 낫다.
- **넣기는 JPA 로 하지 않는다.** `ON CONFLICT DO NOTHING` 이 필요한데 JPA 에 없다.
  "찾아보고 없으면 넣기" 는 **찾은 뒤 넣기 전 사이에** 깨진다. 읽기는 JPA 를 쓴다.
- **중복의 기준은 `(sid, name, sent_at)`** 이고 `V1__event.sql` 의 유일 인덱스와 짝이다.
  한쪽만 바꾸면 `ON CONFLICT` 가 통째로 실패한다(Postgres 가 맞는 인덱스를 요구한다).
- **`.env` 의 값에는 따옴표를 붙인다.** URL 에 `&` 가 있어서 따옴표가 없으면 셸이 그
  자리를 "뒤에서 실행" 으로 읽고 **대입이 조용히 끊긴다.** 실제로 그래서 Cloud Run 에
  빈 주소를 한 번 올렸다. 오류는 안 났다.
- **쉴 때 연결을 놓는다**(`minimum-idle=0`). Neon 무료는 5분 쉬면 잠드는 것이 조건이라
  (한 달 컴퓨트 100시간), 안 쓰는 연결을 붙들고 있으면 쓰지도 않으면서 시간이 닳는다.
- **풀러(`-pooler` 호스트)를 쓰지 않는다.** PgBouncer 가 transaction mode 라 세션 잠금이
  안 걸리는데, **Flyway 가 마이그레이션에 쓰는 것이 그 잠금**이다. 연결이 네 개뿐이라
  (서버 2대 × 2) 풀러가 필요하지도 않다.

### 테스트는 둘로 갈린다

| | 무엇으로 | 무엇을 보나 |
| --- | --- | --- |
| 대부분 | 메모리 H2 (Flyway 끔) | 컨텍스트가 뜨는지. **DB 를 안 건드린다** |
| `EventRepositoryTest` | **진짜 Postgres 18** (Testcontainers) | 마이그레이션 · `ON CONFLICT` · `JSONB` |

**H2 로는 위 셋 중 하나도 확인되지 않는다.** 가짜로 통과시키면 "테스트는 초록인데
실서버에서 깨지는" 종류가 된다.

- **테스트 설정을 `src/test/resources/application.properties` 에 두지 않는다.** 이름이
  같아 본체 설정을 **통째로 가린다.** 그렇게 뒀다가 문지기 숫자까지 사라져
  `limitPerMinute 는 1 이상이어야 합니다: 0` 으로 터졌다. 프로파일
  (`application-test.properties`)은 가리지 않고 얹힌다. 켜는 것은 `build.gradle` 이 한다.
- **`@EnabledIfDockerAvailable` 을 지우지 않는다.** `Dockerfile` 안에서 빌드할 때는
  **도커 안에 도커가 없다.** 이게 없으면 이미지 빌드가 통째로 실패한다. CI 러너에는
  도커가 있어서, **이미지가 만들어지기 전에 한 번은 반드시 돈다.**

## 통계를 보는 자리

`GET /stats?days=7`. 머리글 `X-Stats-Token` 에 토큰을 넣는다. 값은
`JANGGI_STATS_TOKEN` 환경 변수다 - 로컬은 `.env`, Cloud Run 은 Secret Manager 다.

    curl -s -H "X-Stats-Token: $JANGGI_STATS_TOKEN" \
      https://janggi-events-982664928901.asia-northeast1.run.app/stats?days=7

- **403 이 아니라 404 를 준다.** 403 은 "여기 뭔가 있다" 를 알려주는 셈이다. 그래서
  거절할 때 `Problems` 를 안 쓰고 `sendError` 로 넘긴다 - **없는 주소를 쳤을 때와 똑같이
  보여야** 한다. 테스트가 둘을 견주고 있다.
- **토큰이 안 걸려 있으면 아무도 못 들어온다.** 깜빡했을 때 열리는 것이 아니라 닫히는
  쪽이어야 한다. CI 가 매번 확인한다(컨테이너를 토큰 없이 띄워 404 인지 본다).
- **토큰에 한글을 쓸 수 없다.** HTTP 머리글 값은 ASCII 만 받는다.
- **비교는 상수 시간**(`MessageDigest.isEqual`)이다. 앞에서부터 한 글자씩 비교하면
  응답 시간만 재도 토큰을 알아낼 수 있다.
- **`/events` 와 건수 제한을 따로 센다.** 같이 세면 내가 통계를 열어 보는 것이 사용자
  기록 받을 몫을 깎는다.
- **`prod` 만 센다.** `?nostats`·CI 배포 확인·부하 시험이 전부 `dev` 로 들어와 있다.
  섞어 세면 내가 만든 숫자를 내가 보고 늘었다고 좋아하게 된다.
- **`events` 와 `sessions` 를 같이 준다.** 한 사람이 세 판 두면 `first_move` 가 3이지만
  사람은 하나다. 안 가르면 "몇 명이 뒀나" 를 묻고 "몇 수를 뒀나" 를 받는다.
- **날짜는 한국 시각으로 자른다.** UTC 로 자르면 사람이 제일 많이 오는 저녁이 이틀로
  쪼개진다. 저장은 UTC 로 두고 **볼 때만** 옮긴다.

### 돌려주는 칸

`sessions` · `funnel` · `daily` 에 2026-10-12 에 셋을 더했다.

| 칸 | 무엇 | 어디서 나오나 |
| --- | --- | --- |
| `devices` | 기기별 방문 · 엔진 뜸 · 첫 수 둠 · 엔진 중앙값 | `browser` · `os` · `in_app` 열 |
| `referrers` | 들어온 곳 | `app_open` 의 `data->>'ref'` |
| `engine` | 엔진 뜨는 시간 `n` · `p50` · `p90` · `worst` | `engine_ready` 의 `data->>'ms'` |

**기기는 방문 단위로 먼저 접는다.** 이벤트를 그냥 `GROUP BY browser` 하면 **이벤트를
많이 보낸 사람이 자기 브라우저를 부풀린다** - 세 판 둔 사람의 크롬이 한 판 둔 사람의
사파리보다 다섯 배로 보이는데 그건 브라우저에 대한 말이 아니다. `WITH visit AS (... GROUP
BY sid)` 로 접어 **방문 하나를 한 표**로 만든다. 일부러 `GROUP BY sid, sent_at` 으로
되돌려 보니 테스트가 `1 을 기대했는데 5` 로 잡았다 - 그 5가 부풀려진 숫자다.

**같은 줄에 `sessions` 가 있어야 분모가 된다.** "실패한 사람 중에 사파리가 많다" 는
사파리가 원래 많으면 아무 말도 아니다. `events.ts` 주석에 적힌 것과 같은 까닭이다.

**들어온 곳은 셋으로 가른다.**

| 라벨 | 뜻 |
| --- | --- |
| `m.dcinside.com` 같은 호스트 | 거기서 눌러서 왔다 |
| `(직접)` | `ref` 가 빈 값. 주소를 직접 치거나 책갈피 |
| `(안 보냄)` | `ref` 칸 자체가 없다. **보내기 전에 쌓인 옛 기록** |

섞으면 "직접 들어온 서른두 명" 이라는 거짓이 된다 - 실제로는 스물다섯과 일곱이다.

**`engine` 은 `n` 을 같이 준다.** 서른 건에서 p90 은 한두 점이 정하는 값이라, 표본 수
없이 보면 운을 실력으로 읽는다.

### 밟은 것

- **`data ? 'ref'` 를 쓸 수 없다.** Hibernate 가 `?` 를 위치 파라미터로 읽어서 질의가
  아예 안 뜬다. `jsonb_exists(data, 'ref')` 가 같은 일을 한다.
- **SQL 식별자는 ASCII 로 둔다.** 프로젝션 getter 이름과 별칭이 짝이라 어차피 ASCII 여야
  하고, 한글은 주석과 라벨 문자열(데이터)에만 둔다.
- **세는 테스트는 돌리기 전에 표를 비운다.** `@Transactional(NOT_SUPPORTED)` 라 되돌려
  지지 않아서, 비우지 않으면 **앞 테스트가 넣은 것이 뒤 테스트의 숫자가 된다.** 실제로
  "방문 2를 기대했는데 10" 으로 둘이 깨졌다. 묶어 세는 표는 전부 이 함정을 밟는다.
- **H2 로는 하나도 확인되지 않는다.** `jsonb`·`percentile_cont`·`FILTER` 가 전부 없다.
  막는 쪽만 H2 로 빠르게 보고, 세는 쪽은 진짜 Postgres 로 본다.

## 기보와 사용자

**설계만 있고 아직 안 만들었다.** 표 · API · 인증 · 정한 숫자와 그 근거는
[`GAMES.md`](GAMES.md) **에 있다.** 그 영역을 건드릴 때는 **먼저 열어 본다.**

짚어둘 것 하나 - **이 기능은 위의 "사람이 지나가는 길 위에 없다" 는 전제를 깨뜨린다.**
기보 목록은 사람이 기다린다. 대응이 `GAMES.md` 맨 앞에 있다.

## 부하 - 재 본 숫자

`test/load/events.js` (k6). **돌리는 법과 주의할 것은 그 파일 머리에 있다.**

2026-10-12 측정. 로컬은 윈도우 도커라 Cloud Run 과 다르다.

| 무엇 | 결과 |
| --- | --- |
| 로컬 처리량 | 초당 **500건**까지 p95 6.7ms. 2000을 요구하면 710건 처리하며 p50 271ms |
| 망가지는 모양 | **느려질 뿐 안 깨진다.** 5xx 0건, 끊김 0건 |
| **동시에 같은 것** | 6,001건을 퍼부어 **표에 1줄.** `ON CONFLICT` 가 동시성에서 버틴다 |
| 실서버 지연 | p50 **43.6ms**(한국→도쿄). 꼬리 3~4초는 콜드 스타트 |

**세 가지를 알게 됐다.**

- **문지기 한도는 성능이 아니라 요금 때문이다.** 앱은 초당 500건을 받는데 한도는 초당
  2건이다 - **250배** 차이다. 느려서 막는 것이 아니다.
- **고정 창은 경계에서 2배까지 샜다. 미끄러지는 창으로 고쳤다.** 고치기 전에는 60초에
  230건이 통과했다(한도 120). 창이 분 단위로 끊기므로 걸쳐 보내면 최대 두 배가
  지나갔다. 고친 뒤 같은 부하에서 **정확히 120건**이다.
- **"2대가 되면 한도도 2배" 는 아직 못 봤다.** 거절이 너무 싸서 동시성이 안 쌓이고,
  그래서 Cloud Run 이 늘릴 까닭을 못 느낀다. 확인하려면 **느린 요청**이 필요한데 지금
  서버에는 느린 길이 없다. 추론은 그대로 두되 **확인되지 않았다는 것**을 적어 둔다.

### 미끄러지는 창 (2026-10-12)

`RateLimitFilter` 는 **마지막 `한도` 건의 시각만** 고리 버퍼에 들고, 그 가운데 가장
오래된 것이 아직 창 안이면 거절한다. 어느 순간을 잡아도 지난 1분의 건수가 한도를 넘지
않는다. **거절할 때는 아무것도 넣지 않으므로** 몰아쳐도 자리가 안 늘어난다 - `long`
120개(1KB)가 천장이다.

| 무엇 | 고정 창 | 미끄러지는 창 |
| --- | --- | --- |
| 60초에 통과한 건수 (한도 120) | **230** | **120** |
| 창을 채운 뒤 `Retry-After` | 늘 `60` | `21` → `8` → 받음 |
| 거절 23,808번 동안 로그 줄 수 | - | **1줄** (창마다 한 번) |

`AtomicLong` 둘을 `synchronized` 하나로 바꿨다. **2배가 샌 까닭의 절반이 "셈과 창이
맞물려 있지 않았다" 는 것**이었다. 초당 400건을 걸어도 p95 가 0.48ms 라 경합은 안 보인다.

**구별하는 테스트가 하나뿐이다.** `창은 한 칸씩 열린다` 가 그것이고, 나머지("창이 지나면
다시 받는다" 등)는 **고정 창에서도 통과한다.** 일부러 고정 창으로 되돌려 그 하나만
빨간불이 되는 것을 봤다.

5초 폭주에서 통과가 117건이었던 적이 있다 - 바로 앞에 찔러본 3건이 아직 창 안이었기
때문이고, **미끄러진다는 증거 그 자체다.**

**실서버에 돌리면 쌓인 것을 지운다.** 보내는 것은 `env=dev` 에 `browser='부하시험'` 이라
구분된다 - `delete from event where browser='부하시험';`

## 윈도우에서 밟는 것

- **`./gradlew bootRun` 으로 띄우면 로그의 한글이 깨진다.** 그래들이 forked JVM 의
  출력을 **자기 콘솔 인코딩(cp949)으로 다시 쓴다.** 앱은 멀쩡하다 - `java -jar` 로
  직접 띄우면 UTF-8 로 온전하다. **로그를 읽어야 할 때는 jar 를 직접 돌린다.**
  (`logging.charset.console` 로 고치려다 **빼고 돌려 보니 애초에 필요 없었다.**
  아무 일도 안 하는 설정을 남기지 않는다.)
- **그래들이 자바 21 을 자동으로 못 찾는다.** winget 으로 깐 Microsoft OpenJDK 는
  표준 탐지에 안 잡히고 `JAVA_HOME` 은 17 을 가리킨다. **저장소가 아니라**
  `~/.gradle/gradle.properties` 의 `org.gradle.java.installations.paths` 에
  적어 뒀다. 저장소에 내 컴퓨터 경로를 박으면 남이 받았을 때 깨진다.
- **`gradlew` 의 실행 권한이 커밋에서 빠진다.** 윈도우에 그 개념이 없어서다. 내
  컴퓨터와 도커(`chmod +x` 가 들어 있다)에서는 안 보이고, **리눅스에서 받으면 빌드
  자체가 안 된다.** `git update-index --chmod=+x <파일>` 로 저장소에 박는다.
  확인은 `git ls-files -s`(100755 여야 한다).
- **소스에 유니코드 이스케이프로 특수 글자를 적지 않는다.** 역슬래시 + u + 네 자리로
  적으면, 거쳐 가는 도구가 역슬래시를 먹는 순간 **진짜 그 글자**가 소스에 박힌다.
  테스트는 의미가 같아 통과해서 아무도 모른다. `(char) 0xFFFD` 처럼 **번호로** 만든다.
  이 줄을 쓰면서도 두 번 당했다 - 설명하려고 예를 적는 순간 또 박힌다.
- **한글이 든 요청을 셸에서 `curl -d` 로 보내면 깨진다.** 본문을 UTF-8 파일로 쓰고
  `--data-binary @파일` 로 보낸다.
- **기다리는 루프는 최대 대기 시간을 먼저 계산한다.** `curl --max-time 2` + `sleep 1`
  을 40번 돌려서 **죽은 컨테이너를 2분 기다렸다.** `docker ps` 를 먼저 본다.
- **`grep -c $'
'` 로 CRLF 를 세면 안 된다.** 이 셸에서는 패턴이 빈 것으로 넘어가서
  **순수 LF 파일에도 줄 수만큼 세어 준다.** 그걸 믿고 "저장소가 CRLF 로 들어갔다" 고
  한참 봤는데 처음부터 LF 였다. 바이트로 센다.

      tr -cd '
' < 파일 | wc -c      # 0 이면 LF
      git cat-file blob $(git rev-parse HEAD:파일) | tr -cd '
' | wc -c

- **셸을 거치면 역슬래시가 먹힌다.** 파이썬 heredoc 안에 자바의 `"
"` 을 적었는데
  진짜 줄바꿈이 되어 문자열이 끊겼다. **자바 파일은 `cat > 파일 <<'JAVA'` 로 통째로
  쓰고**, 파이썬이 필요하면 스크립트를 파일로 저장해 `python -I <파일>` 로 돌린다.

## 로그가 검색되는 모양

**Cloud Logging 은 stdout 의 한 줄이 _전체가_ JSON 일 때만** 그것을 뜯어 칸으로 만든다.
앞에 시각이나 레벨이 붙으면 줄을 통째로 글자로 넣어서 조건 검색이 안 된다. 그래서 두
갈래로 내보낸다.

| 무엇 | 어떻게 | 왜 |
| --- | --- | --- |
| 이벤트 기록 | `janggi.events.record` 로거 → 메시지만 | 칸으로 뜯혀야 세고 걸러낼 수 있다 |
| 스프링 자신의 로그 | `GcpStructuredLogFormatter` | 예외 스택이 **한 건**으로 묶인다 |

짚을 것 셋.

- **로거 이름이 `logback-spring.xml` 과 짝이다.** `EventService` 의
  `janggi.events.record` 와 XML 의 `<logger name=...>` 중 한쪽만 바꾸면 연결이 끊겨
  이벤트 줄 앞에 장식이 붙는다. 오류는 안 난다 - **검색만 조용히 안 된다.**
- **`additivity="false"` 를 지우면 같은 줄이 두 번 찍힌다.** 건수가 두 배로 들어와
  나중에 숫자를 못 믿는다.
- **`gcp` 라는 형식은 없다.** 부트가 주는 건 `ecs`·`gelf`·`logstash` 뿐이라 클래스
  이름을 직접 적는다. 까닭은 `GcpStructuredLogFormatter` 주석에 있다.

## 도커

- **`COPY` 뒤에 `RUN chown` 을 쓰지 않는다.** 도커가 같은 파일을 새 층에 통째로 다시
  복사해서 jar 가 이미지에 **두 번** 들어간다(23.7MB 를 그냥 버렸다).
  `COPY --chown=app:app` 으로 붙인다. Artifact Registry 무료 보관이 0.5GB 뿐이라
  버전당 그만큼이 보관 가능한 버전 수를 반으로 줄인다.
- **`ENTRYPOINT` 를 대괄호 형태로 쓴다.** 문자열로 쓰면 `sh` 가 1번 프로세스가 되어
  **SIGTERM 을 자바에게 넘기지 않는다.** 그러면 `server.shutdown=graceful` 이 아무
  일도 못 하고, Cloud Run 이 서버를 줄일 때 처리 중이던 기록이 사라진다.
  확인법 - `docker stop` 하고 **"Graceful shutdown complete" 가 찍히는지** 본다.
  넘어가지 않으면 10초를 기다렸다 강제로 죽는다.
- **그래들의 기본 `jar` 는 끄고 부트 jar 만 만든다.** 둘 다 있으면 `COPY *.jar` 가
  어느 쪽인지 몰라 빌드가 멈춘다.

## 깨뜨리면 안 되는 것

- **개인정보.** IP 를 적지 않는다. 쿠키를 쓰지 않는다. 방문 번호(`sid`)는 쪽을 열
  때마다 새로 만든 것이라 사람을 이어 붙이지 않는다. **"몇 명" 이 아니라 "몇 번"
  만 센다.**
- **받는 시각이 기준이다.** 보낸 쪽 시각(`at`)은 참고용이다 - 사용자 시계는 틀릴 수
  있다.
- **모르는 이벤트 이름은 거절한다.** 느슨하게 받으면 나중에 숫자를 못 믿는다. 대가로
  순서가 생긴다 - **프런트에 새 이벤트를 더할 때는 서버를 먼저 배포한다.**
- **적히는 줄은 언제나 한 줄이어야 한다.** 보낸 값 안의 줄바꿈이 줄을 쪼개면 **기록을
  위조할 수 있다** - 가짜 JSON 줄을 끼워 넣으면 Cloud Logging 이 별개 이벤트로 받는다.
  지금은 잭슨이 막아 주지만 **그건 도구의 성질이지 우리가 고른 것이 아니다.** 문자열을
  직접 이어 붙여 로그를 만들지 않는다. `EventLogLineTest` 가 지킨다.
- **문지기는 본문이 있는 메서드만 검사한다.** `OPTIONS` 를 411 로 막으면 브라우저의
  CORS 사전 요청이 실패해서 **본 요청이 아예 오지 않는다.** 같은 출처로 쓰는 동안에는
  드러나지 않다가, 전제가 바뀌는 순간 원인 찾기 어려운 고장이 된다.
- **종료 대기는 10초를 넘기지 않는다.** Cloud Run 은 SIGTERM 뒤 10초에 SIGKILL 을
  보내고 그건 못 막는다. 20초로 적어 두면 플랫폼이 지킬 수 없는 약속이 된다.
- **공개 주소다.** 누구나 부를 수 있다. 새 입구를 만들면 `common/guard` 의
  `GuardConfig` 에 **그 주소를 같이 등록한다.** 문지기는 주소를 하나하나 적어 붙이게
  돼 있어서, 잊으면 그 입구만 아무 제한 없이 열린다.
- **문지기를 모든 주소에 붙이지 않는다.** 상태 확인(`/actuator/health`)까지 걸리면
  몰아치는 동안 Cloud Run 이 "죽었다" 고 보고 다시 띄우는데, 그 콜드 스타트가 또
  요금이다. 테스트가 이것을 지키고 있다 - 한도에 막힌 뒤에도 상태 확인이 200 인지 본다.
- **`X-Forwarded-For` 로 사람을 가르지 않는다.** Cloud Run 주소를 직접 때리는 쪽에서
  마음대로 적을 수 있는 값이다. 꾸밀 수 있는 값으로 세는 것은 제한이 아니다.

## 기계가 막아 주는 것

**푸시하면 `.github/workflows/ci.yml` 이 깨끗한 리눅스에서 다시 확인한다.** 적어 둔
규칙은 안 지켜진다 - 아래 셋은 전부 여기서 적어 두고도 밟았던 것이고, 이제 기계가 막는다.

| CI 가 보는 것 | 안 보면 생기는 일 |
| --- | --- |
| 테스트가 **몇 개** 돌았는지 | `UP-TO-DATE` 로 건너뛰고도 초록불이 된다 |
| 컨테이너를 띄워 직접 불러 보기 | jar 는 되는데 이미지가 시작도 못 하는 일이 있었다 |
| 로그 줄이 **전부** JSON 인지 | 한 줄만 아니어도 그 줄은 영영 검색되지 않는다 |
| 깨진 글자 | 오류가 안 나서 아무도 모른다 |

**검사를 만들면 일부러 망가뜨려 본다.** 늘 통과하는 검사는 없느니만 못하다. 이 셋은
JSON 아닌 줄·깨진 글자·이벤트 0건을 각각 넣어서 빨간불이 뜨는 것을 확인하고 올렸다.

## 끝내기 전에 돌릴 것

```bash
./gradlew build        # 컴파일 + 테스트
```

**"BUILD SUCCESSFUL" 만 보고 넘어가지 않는다.** 테스트가 `UP-TO-DATE` 로 건너뛰었을
수 있다. 커밋 전에는 `./gradlew clean build` 로 실제로 돌리고 **몇 개가 돌았는지**
확인한다.

그리고 **띄워서 직접 불러 본다.** 테스트가 통과한 것과 진짜로 도는 것은 다르다.

```bash
java -jar build/libs/janggi-server-*.jar
curl -X POST localhost:8080/events -H "Content-Type: application/json" --data-binary @본문.json
```

`--no-daemon` 을 붙이지 않는다. 매번 JVM 을 새로 띄워서 빌드가 20초씩 걸린다(데몬을
쓰면 14초). 다 끝나면 `./gradlew --stop` 으로 끈다.

**이미지를 고쳤으면 컨테이너로도 띄워 본다.** jar 로 되는 것과 이미지로 되는 것은
다르다 - `gcp` 형식이 없다는 것도, jar 는 멀쩡한데 컨테이너가 시작조차 못 하면서 드러났다.

```bash
docker build -t janggi-server:dev .
docker run -d --name t -p 8080:9090 -e PORT=9090 janggi-server:dev
curl -s -o /dev/null -w '%{http_code}
' localhost:8080/actuator/health   # 200
docker logs t                      # 줄마다 전체가 JSON 인지
docker stop t && docker logs t | grep -c 'Graceful shutdown complete'   # 1 이어야 한다
docker rm -f t
```

**`tail -2` 로 보지 않는다.** DB 가 붙은 뒤로 종료 순서가 바뀌어서, 맨 끝 두 줄은
`Graceful shutdown complete` 가 아니라 JPA·Hikari 정리 줄이다. 끝에 있다고 믿고 보면
**멀쩡한데 실패로 읽는다.** 글자로 찾는다.

**DB 없이는 뜨지 않는다.** 위 `docker run` 에 접속 정보를 같이 줘야 한다 - 없으면
`Failed to determine a suitable driver class` 로 4초 만에 죽는다. 빈 Postgres 하나를
같이 띄우면 된다(CI 의 `DB 를 띄운다` 단계가 그대로 쓸 수 있는 본보기다).

로그는 **눈으로 보지 말고 줄마다 파싱해 본다.** 한 줄이라도 JSON 이 아니면 그 줄은
Cloud Logging 에서 검색되지 않는다.

끌 때 `pkill` 은 안 먹는다. 포트로 잡는다.

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 아직 없는 것

1-6 까지 다 섰다. 2026-10-11 까지의 기록도 넣었다(82줄 중 76줄 - 내가 만든 `prod` 오염
6줄은 뺐고, 원본은 `C:\nakyung\janggi-logs\events-2026-10-11.jsonl` 에 그대로 있다).

앞으로 할 만한 것 - 어느 것도 지금 급하지 않다.

- **서버 2대일 때 건수 제한이 2배가 되는지 확인.** 느린 요청을 만들어야 볼 수 있다.

**굳이 안 한 것이었는데 전제가 바뀐다** - 콜드 스타트를 더 줄이는 길(CDS 미리 만들기,
층 나눠 담기)은 두었다. 지금 2.4초이고 한 달 콜드 스타트를 다 합쳐도 무료 한도의 1.3%
라서, 복잡해지는 값이 얻는 것보다 컸다. **사람이 기다리는 길 위에 없다는 것이 그 판단의
근거였는데, 기보 기능이 그 전제를 깨뜨린다**(`GAMES.md` 맨 앞). 화면을 로컬 먼저 그리는
것으로 목록은 가리지만 **올리기·지우기는 사람이 기다린다.** 다시 봐야 한다.

**`--min-instances=1` 은 쓰지 않는다** - 이 프로젝트는 무료 한도 안에서만 돌린다
(2026-10-13). 공짜로 줄이는 길(CDS·층 나눠 담기)과 "저장 중…" 으로 가리는 길이
남았고, **둘 다 기보가 돌아간 뒤에 재고 정한다**(`GAMES.md` 맨 앞).

**멱등성은 표가 맡고 있다** - `(sid, name, sent_at)` 유일 인덱스와 `ON CONFLICT DO
NOTHING` 이다. `sendBeacon` 은 at-least-once 라 재전송이 실제로 일어난다. **원자성은
`JpaEventStore` 의 트랜잭션이 맡는다** - 묶음 하나가 전부 들어가거나 전부 안 들어간다.
둘 다 진짜 Postgres 로 테스트가 박혀 있고, **일부러 망가뜨려 잡히는 것까지 봤다.**
