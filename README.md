<a id="readme-top"></a>

<!-- 배지 -->
[![CI][ci-shield]][ci-url]
[![License][license-shield]][license-url]

<br />
<div align="center">

<h1 align="center">janggi-events</h1>

  <p align="center">
    <a href="https://janggi-ai.vercel.app">장기 AI</a> 가 보내는 사용 기록을 받아 쌓는 서버
    <br />
    <br />
    <a href="#재-본-숫자">재 본 숫자</a>
    &middot;
    <a href="https://github.com/nakyung123/janggi-board">화면 쪽 저장소</a>
    &middot;
    <a href="https://github.com/nakyung123/janggi-events/issues/new">문제 알리기</a>
  </p>
</div>

<!-- 목차 -->
<details>
  <summary>목차</summary>
  <ol>
    <li>
      <a href="#프로젝트-소개">프로젝트 소개</a>
      <ul>
        <li><a href="#만든-도구">만든 도구</a></li>
        <li><a href="#어디에-서-있나">어디에 서 있나</a></li>
      </ul>
    </li>
    <li><a href="#주소">주소</a></li>
    <li>
      <a href="#고른-것들">고른 것들</a>
      <ul>
        <li><a href="#요금을-네-겹으로-막는다">요금을 네 겹으로 막는다</a></li>
        <li><a href="#같은-것이-두-번-와도-한-줄">같은 것이 두 번 와도 한 줄</a></li>
        <li><a href="#열쇠-파일-없이-배포한다">열쇠 파일 없이 배포한다</a></li>
      </ul>
    </li>
    <li><a href="#재-본-숫자">재 본 숫자</a></li>
    <li><a href="#시작하기">시작하기</a></li>
    <li><a href="#로드맵">로드맵</a></li>
    <li><a href="#라이선스">라이선스</a></li>
    <li><a href="#연락처">연락처</a></li>
  </ol>
</details>

<!-- 프로젝트 소개 -->
## 프로젝트 소개

쓰는 사람이 몇 명인지, 엔진이 안 뜨는 기기가 있는지, 들어와서 무엇을 하다 떠나는지를
알려고 만들었습니다. **실제로 돌고 있고** 실제 사용자의 기록을 받습니다.

브라우저가 `navigator.sendBeacon` 으로 던지고 잊기 때문에, 이 서버는 **길 위에 없습니다.**
여기가 터져 있어도 장기는 둬집니다. 아래 판단 거의 전부가 이 한 줄에서 나왔습니다.

### 만든 도구

* [![Java][Java-shield]][Java-url]
* [![Spring Boot][Spring-shield]][Spring-url]
* [![Postgres][Postgres-shield]][Postgres-url]
* [![Docker][Docker-shield]][Docker-url]
* [![Cloud Run][CloudRun-shield]][CloudRun-url]

### 어디에 서 있나

```
사람의 폰
   │
   ▼
Vercel (janggi-board)   화면을 준다      <- 없으면 사이트가 없다
   │  /api/ 로 오는 것만 넘긴다
   ▼
Cloud Run (여기)        기록을 받는다    <- 없어도 장기는 둬진다
   │
   ▼
Neon Postgres           쌓는다
```

두 저장소는 코드로 안 붙고 **주소 한 줄로만** 붙습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 주소 -->
## 주소

| 주소 | 하는 일 |
| --- | --- |
| `POST /events` | 사용 기록을 받습니다. 묶음으로 최대 50건 |
| `GET /stats` | 센 것을 보여 줍니다. **토큰이 없으면 404** |
| `GET /actuator/health` | 살아 있는지. Cloud Run 이 봅니다 |

```http
POST /events
Content-Type: application/json

{"events": [
  {"name": "app_open", "sid": "a1b2c3d4", "browser": "사파리 17", "os": "아이폰"}
]}

204 No Content
```

돌려줄 것이 없어서 **204** 입니다. 브라우저는 던지고 답을 읽지 않습니다.

모르는 이벤트 이름은 **묶음 전체를 거절합니다**(400). 일부만 받으면 보낸 쪽은 성공한
줄 알고 받은 쪽에 구멍이 생기는데, 나중에 숫자가 안 맞을 때 원인을 찾을 수 없습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 고른 것들 -->
## 고른 것들

무엇을 만들었나보다 **왜 골랐고 무엇을 포기했나**를 적습니다.

| 고른 것 | 왜 | 포기한 것 |
| --- | --- | --- |
| 표 하나 (세션 표 없음) | 읽고 쓰는 사이가 없어 경쟁이 안 생깁니다 | 기기 정보가 줄마다 되풀이됩니다 (하루 5KB) |
| 로그 먼저, DB 나중 | DB 가 죽어도 기록이 남습니다 | 두 군데 적습니다 |
| 넣기만 네이티브 SQL | JPA 에 `ON CONFLICT` 가 없습니다 | 도구를 섞어 씁니다 |
| 뜰 때 DB 에 연결 안 함 | DB 가 죽어도 서버는 뜹니다 | 첫 요청이 조금 느립니다 |
| 잠들게 둠 (`min-instances=0`) | 무료 한도가 24시간을 못 버팁니다 | 콜드 스타트 3~8초 |
| 싱가포르 (도쿄 없음) | Neon 에 도쿄가 없습니다 | 70ms 더 멉니다 |

### 요금을 네 겹으로 막는다

누구나 부를 수 있는 주소입니다. 겹마다 **못 막는 것이 다릅니다.**

| 겹 | 무엇을 막나 | 못 막는 것 |
| --- | --- | --- |
| 건수 제한 (1분 120건) | 몰아치는 것 | 서버가 2대면 한도도 2배 |
| 크기 제한 (64KiB) | 큰 본문 | 작은 요청을 많이 |
| `--max-instances=2` | 서버가 늘어나는 것 | 2대가 계속 도는 것 |
| 구글 지출 한도 | 결국 돈 | **늦게 닫힙니다** |

건수를 **IP 별이 아니라 전체로** 셉니다. 진짜 주소가 실리는 `X-Forwarded-For` 는
Cloud Run 을 직접 때리는 쪽에서 **마음대로 쓸 수 있습니다** - 꾸밀 수 있는 값으로 세는
것은 제한이 아닙니다.

### 같은 것이 두 번 와도 한 줄

`sendBeacon` 은 at-least-once 라 망이 끊기면 **묶음을 통째로 다시 보냅니다.**

```sql
CREATE UNIQUE INDEX event_once_idx ON event (sid, name, sent_at);
INSERT ... ON CONFLICT (sid, name, sent_at) DO NOTHING;
```

같은 이벤트 6,001건을 동시에 퍼부어 **표에 1줄**이 남는 것을 확인했습니다.

묶음 하나는 **전부 아니면 전무**입니다(한 트랜잭션). 반쯤 들어가면 나중에
숫자를 믿을 수 없습니다.

### 열쇠 파일 없이 배포한다

서비스 계정 JSON 키를 깃허브에 넣지 않습니다. 그 파일은 **만료가 없고** 이 저장소는
공개입니다. 대신 깃허브가 서명한 증서를 GCP 가 확인하는 방식(Workload Identity
Federation)을 씁니다 - 토큰 수명 15분, 저장할 비밀 없음.

설정 전체에서 가장 중요한 줄은 이것입니다. 빠지면 **아무 저장소나** 들어옵니다.

```
--attribute-condition="assertion.repository == '<내-저장소>'"
```

배포 계정은 `run.admin` 이 아니라 **`run.developer`** 입니다. "누가 부를 수 있나" 는
못 바꿉니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 재 본 숫자 -->
## 재 본 숫자

감으로 적지 않고 전부 재 보았습니다.

| 무엇 | 값 |
| --- | --- |
| 처리량 (로컬) | 초당 **500건**까지 p95 6.7ms |
| 밀어붙이면 | 초당 710건에서 느려집니다. **깨지지 않고 느려집니다** |
| 동시에 같은 것 | 6,001건 → 표에 **1줄** |
| 실서버 지연 | p50 **43.6ms** (한국 → 도쿄) |
| 콜드 스타트 | Cloud Run 3.0~7.5초 + Neon 0.5초 |
| 테스트 | **44개** (7개는 진짜 Postgres 로) |

건수 제한이 **성능 때문이 아니라는 것**이 여기서 보입니다 - 앱은 초당 500건을 받는데
한도는 초당 2건입니다. 250배 차이고, 그 한도는 **요금** 때문입니다.

부하 시험 스크립트는 [`test/load/events.js`](test/load/events.js) 에 있습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 시작하기 -->
## 시작하기

### 준비물

* JDK 21
* Docker

### 돌려보기

DB 를 띄우고, 접속 정보를 주어 서버를 띄웁니다. 없으면 **4초 만에 죽습니다.**

```bash
docker run -d --name db -p 5432:5432 \
  -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=dev postgres:18-alpine

./gradlew clean build

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/dev \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=dev \
  java -jar build/libs/janggi-events-*.jar
```

한글이 든 요청은 **파일로** 만들어 보냅니다. 셸을 거치면 글자가 변합니다.

```bash
printf '%s' '{"events":[{"name":"app_open","sid":"local001"}]}' > 본문.json
curl -X POST localhost:8080/events \
  -H 'Content-Type: application/json' --data-binary @본문.json   # 204
```

### 통계 보기

```bash
JANGGI_STATS_TOKEN=아무거나 java -jar build/libs/janggi-events-*.jar
curl -H 'X-Stats-Token: 아무거나' 'localhost:8080/stats?days=7'
```

토큰을 안 걸면 **아무도 못 들어옵니다**(404). 깜빡했을 때 열리는 것이 아니라 닫히는
쪽이어야 합니다.

저장소에서 일하는 방식과 밟았던 함정은 [`CLAUDE.md`](CLAUDE.md), 배포는
[`deploy/DEPLOY.md`](deploy/DEPLOY.md) 에 있습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 로드맵 -->
## 로드맵

- [x] 기록 받기 (`POST /events`)
- [x] Cloud Run 배포 · 열쇠 없는 자동 배포
- [x] Postgres 에 쌓기 · 중복 막기 · 원자성
- [x] 센 것 보여 주기 (`GET /stats`)
- [x] 부하 시험
- [x] `/stats` 에 기기 · 들어온 곳 · 엔진 뜨는 시간
- [x] 미끄러지는 창 (고정 창은 경계에서 2배까지 샜습니다 - 60초에 230건 → 120건)

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 라이선스 -->
## 라이선스

**MIT.** 전문은 [`LICENSE`](LICENSE) 에 있습니다. 가져다 쓰고 고쳐도 되고, 이름과
라이선스 글만 남기면 됩니다.

화면 쪽 저장소는 **GPL v3** 입니다. 거기는 장기 엔진이 GPL 이라 따라간 것이고, 이
서버는 엔진을 쓰지 않아 고를 수 있었습니다.

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 연락처 -->
## 연락처

nakyung123 - [github.com/nakyung123](https://github.com/nakyung123)

프로젝트: [janggi-events](https://github.com/nakyung123/janggi-events) ·
[janggi-board](https://github.com/nakyung123/janggi-board)

<p align="right">(<a href="#readme-top">맨 위로</a>)</p>

<!-- 배지와 주소 -->
[ci-shield]: https://github.com/nakyung123/janggi-events/actions/workflows/ci.yml/badge.svg
[ci-url]: https://github.com/nakyung123/janggi-events/actions/workflows/ci.yml
[license-shield]: https://img.shields.io/badge/license-MIT-blue?style=flat
[license-url]: https://github.com/nakyung123/janggi-events/blob/main/LICENSE
[Java-shield]: https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white
[Java-url]: https://openjdk.org/
[Spring-shield]: https://img.shields.io/badge/Spring%20Boot%204-6DB33F?style=for-the-badge&logo=springboot&logoColor=white
[Spring-url]: https://spring.io/projects/spring-boot
[Postgres-shield]: https://img.shields.io/badge/PostgreSQL%2018-4169E1?style=for-the-badge&logo=postgresql&logoColor=white
[Postgres-url]: https://www.postgresql.org/
[Docker-shield]: https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white
[Docker-url]: https://www.docker.com/
[CloudRun-shield]: https://img.shields.io/badge/Cloud%20Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white
[CloudRun-url]: https://cloud.google.com/run
