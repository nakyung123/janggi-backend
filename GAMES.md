# GAMES.md

**기보와 사용자 설계.** 2026-10-13 에 정했고 **아직 안 만들었다.**

일하는 방식은 `CLAUDE.md`, 배포는 `deploy/DEPLOY.md` 에 있다. 여기서 되풀이하지 않는다.
**왜 이렇게 골랐는지의 긴 이야기는** `janggi/docs/DECISIONS.md` 에 있다(올라가지 않는다).

## 먼저 - 이 기능이 서버의 전제를 깨뜨린다

`CLAUDE.md` 머리에 이렇게 적혀 있다.

> 이 서버는 사람이 지나가는 길 위에 없다. (...) 그래서 콜드 스타트 몇 초를 감당할 수
> 있다. **이 전제가 깨지는 기능(사람이 답을 기다리는 것)을 여기 붙이려면 설계를 다시
> 봐야 한다.**

**기보가 그 기능이다.** 측정은 `sendBeacon` 으로 던지고 잊어서 아무도 안 기다리지만,
기보 목록은 **사람이 화면을 보고 기다린다.** 그러면 둘이 사람 눈에 보인다.

| 무엇 | 지금 | 사람이 기다리나 |
| --- | --- | --- |
| 콜드 스타트 | **2.4초** | 측정은 아니다 → **기보는 그렇다** |
| DB 왕복 | **~155ms** | 측정은 아니다 → **기보는 그렇다** |

**그래서 화면을 먼저 고친다. 서버를 기다리지 않는 모양으로 만든다.**

```
기보 탭을 누르면
   ① 이 기기 목록(localStorage)을 바로 그린다        <- 0ms. 서버와 무관
   ② 로그인되어 있으면 서버에 묻는다
   ③ 답이 오면 합쳐서 다시 그린다                    <- 늦어도 화면이 비지 않는다
```

**이게 "로그인은 강제가 아니다" 와 정확히 맞는다.** 로그인 안 한 사람은 ①에서 끝나고,
로그인한 사람은 ①을 보는 동안 ②가 돈다. **콜드 스타트가 빈 화면이 되지 않는다.**

**다만 올리기와 지우기는 사람이 결과를 기다린다.** 거기서는 2.4초가 보인다. 누른 뒤
한 번이라 "저장 중…" 으로 덮을 수 있지만, **그래도 느린 것은 느린 것이다.**

`CLAUDE.md` 의 `## 아직 없는 것` 에 **"콜드 스타트를 더 줄이는 길은 두었다 - 사람이
기다리는 길 위에 없다는 것이 이 판단의 근거다. 그 전제가 바뀌면 다시 본다"** 고 적어
두었다. **지금 바뀐다.**

**다만 `--min-instances=1` 은 쓰지 않는다.** 항상 켜 두는 것이라 요금이 붙는데, 이
프로젝트는 **무료 한도 안에서만 돌린다**(2026-10-13 에 정했다). 남은 길은 둘이다.

| | 어떻게 | 값 |
| --- | --- | --- |
| **공짜로 줄이기** | CDS 미리 만들기 · 층 나눠 담기 | 2.4초를 1초쯤으로. 이미지가 복잡해진다 |
| **가리기** | 누른 뒤 "저장 중…" 으로 덮는다 | 공짜. 느린 것은 그대로 느리다 |

**둘 다 지금 하지 않는다.** 기보가 돌아가 봐야 2.4초가 실제로 얼마나 보이는지 알 수
있고, 안 보이는 것을 고치면 복잡해지기만 한다. **순서표 8번(운영)에서 재고 정한다.**

측정 수집기에 도메인이 없어서 "표 하나짜리"였다. 기보를 서버에 두면 **관계 · 인증 ·
조회**가 한꺼번에 생긴다. 억지로 얹는 기능이 아니라, 지금 기보가 브라우저에만 있어서
**기기를 바꾸면 사라진다.**

### 표

```
app_user
  id            BIGINT PK
  google_sub    VARCHAR(255) NOT NULL UNIQUE   -- 구글 공식 상한이 255 ASCII
  token_epoch   INT NOT NULL DEFAULT 1         -- 탈퇴·복구 때 올린다
  created_at    TIMESTAMPTZ NOT NULL
  deleted_at    TIMESTAMPTZ                    -- 탈퇴. 30일 뒤 실제 삭제

game
  id              BIGINT PK
  user_id         BIGINT NOT NULL -> app_user ON DELETE CASCADE
  client_game_id  VARCHAR(24) NOT NULL   -- 브라우저가 만든 번호(newGameId)
  level_id · level_name · side · variant · result · moves_count
  ended_at        TIMESTAMPTZ NOT NULL   -- 끝난 때 (브라우저)
  created_at · updated_at TIMESTAMPTZ    -- 올린 때 / 덮어쓴 때 (서버)
  UNIQUE (user_id, client_game_id)

game_move
  game_id   BIGINT NOT NULL -> game ON DELETE CASCADE
  ply       SMALLINT NOT NULL
  move · grade
  PRIMARY KEY (game_id, ply)
```

**`UNIQUE (user_id, client_game_id)` 가 설계의 열쇠다.** `/events` 의
`(sid, name, sent_at)` 과 같은 방식이고, 6,001건을 퍼부어 1줄이 되는 것까지 확인한 수다.

**내용 해시를 쓰지 않는다.** 처음에는 그렇게 하려 했는데 **짧은 판에서 충돌한다** -
실서버에 `2수 · 초 기권패` 기보가 있고, 수순만으로 해시를 내면 다른 두 판이 한 줄로
합쳐진다. 브라우저에 이미 안정된 번호(`newGameId`)가 있어서 해시가 필요 없었다.

**`client_game_id` 는 전역 식별자가 아니다.** 다른 기기에서 같은 대국을 올리면 두 줄이
된다. 그게 맞는 동작이다 - 기기마다 번호를 따로 만든다.

### 올리기는 넣기가 아니라 덮어쓰기다

`archive.ts` 주석에 적혀 있다 - **한 판이 여러 번 들어온다.** 끝나는 순간 한 번, 복기를
돌리면 또 한 번, 무르고 다시 두면 또 한 번. 브라우저도 "같은 id 면 바꾼다" 로 되어 있다.
**서버가 같은 규칙을 쓴다.**

`ON CONFLICT DO NOTHING` 으로 하면 **복기 등급이 영원히 안 들어온다.** 두 번째를
무시하기 때문이다.

**한 트랜잭션에서 네 걸음이다.**

```
① app_user 행을 SELECT ... FOR UPDATE 로 잠근다
② (user_id, client_game_id) 로 game 을 넣거나 고친다
③ 그 game 의 game_move 를 전부 지운다
④ 이번에 온 수와 등급을 전부 넣는다
```

**③번을 빠뜨리면 조용히 깨진다.** 100수 기보를 80수로 다시 올리면 81~100수가 남는다.

**①번 잠금이 두 가지를 같이 막는다** - 500판 한도의 경합(499개에서 둘이 동시에 들어와
둘 다 "499개" 로 읽는 것)과, 같은 기보를 동시에 올려 수가 섞이는 것.

### 인증 - 상태를 안 둔다

| 정한 것 | 까닭 |
| --- | --- |
| **리다이렉트** (팝업 아님) | COOP `same-origin` 이 있어야 엔진이 뜨는데, 그러면 **팝업이 원래 창과 말을 못 한다.** `same-origin-allow-popups` 로 풀면 격리가 깨져 엔진이 안 뜬다 - 맞바꿀 수 없다 |
| **서명 쿠키** (세션 저장소 없음) | DB 가 싱가포르라 **요청마다 155ms**(재 봤다). 세션을 읽으러 한 번 더 가면 2.5배가 된다. 그리고 Spring Session JDBC 는 **세션 표에 이메일이 들어가** 비저장 설계가 무너진다 |
| `redirect-uri` **고정값** | 프록시 뒤라 Spring 이 Cloud Run 주소로 만들고 `/api` 도 빠진다 |
| 쿠키 둘 다 **`Path=/`** | `Path` 는 **브라우저가 보는 주소**로 판단한다. Vercel 이 `/api` 를 벗기니 백엔드 경로로 두면 안 간다 |
| `state` 쿠키 **`SameSite=Lax`** | 콜백은 구글에서 오는 **최상위 이동**이라 `Strict` 면 쿠키가 안 가고 **로그인이 통째로 실패한다** |

**무상태는 네 군데를 다 막아야 한다.** 하나만 바꾸면 로그인은 되는데 세션이 계속 생긴다.

```
① SessionCreationPolicy.STATELESS
② SecurityContextRepository      → 우리 토큰 필터가 대신한다
③ AuthorizationRequestRepository → 쿠키 (state)
④ RequestCache → NullRequestCache  ← 이것도 세션을 만든다. 가장 조용하다
```

**증거는 `JSESSIONID` 가 없는 것이 아니다.** 응답 쿠키가 없는 것과 세션 객체가 안 생긴
것은 다르다. `HttpSessionListener` 를 걸어 `sessionCreated` 가 **0번**인지 센다.

### 토큰 세대 - 조회를 늘리지 않고 막는다

토큰에 `(user_id, token_epoch)` 를 담는다. **활성 확인을 위한 별도 조회를 하지 않는다** -
어차피 하는 질의에 조건으로 붙인다. 155ms 를 아끼는 자리다.

```sql
WHERE user_id = :uid
  AND EXISTS (SELECT 1 FROM app_user
               WHERE id = :uid AND deleted_at IS NULL AND token_epoch = :epoch)
```

**빠뜨리는 것을 구조로 막는다.** SQL 마다 손으로 붙이면 반드시 한 군데를 잊는다.

```java
interface GameAccess {   // 인자에 둘이 없으면 컴파일이 안 된다
    List<GameSummary> 목록(long userId, int epoch, Cursor cursor);
    Optional<GameDetail> 하나(long userId, int epoch, long gameId);
    long 올리기(long userId, int epoch, GameUpload 올릴것);
    boolean 지우기(long userId, int epoch, long gameId);
}
```

### 로그인 - 두 문장으로 네 경우

**30일 지났다고 행이 저절로 사라지지 않는다.** 지우개가 아직 안 돌았으면 `google_sub` 가
묶여 있어서 `INSERT` 가 유일 제약에 걸린다. 그래서 **만료된 것을 먼저 치운다.**

```sql
-- ① 만료된 것을 치운다. 기보도 CASCADE 로 같이 간다
DELETE FROM app_user
 WHERE google_sub = :sub AND deleted_at IS NOT NULL
   AND deleted_at <= now() - interval '30 days';

-- ② 넣거나, 30일 안이면 되살린다
INSERT INTO app_user (google_sub, created_at) VALUES (:sub, now())
ON CONFLICT (google_sub) DO UPDATE
   SET deleted_at = NULL,
       token_epoch = CASE WHEN app_user.deleted_at IS NOT NULL
                          THEN app_user.token_epoch + 1 ELSE app_user.token_epoch END
RETURNING id, token_epoch;
```

| 들어온 사람 | ① | ② | 결과 |
| --- | --- | --- | --- |
| 처음 | 0줄 | `INSERT` | 새 계정 |
| 쓰고 있는 사람 | 0줄 | 충돌, 그대로 | 그 계정 |
| 30일 **안** 탈퇴 | 0줄 | `deleted_at` 비움 + `epoch+1` | **복구** |
| 30일 **초과** 탈퇴 | **삭제** | `INSERT` | **새 계정.** 기보는 사라진다 |

**잠금이 필요 없다.** 둘 다 조건부라 동시에 들어와도 먼저 확정된 쪽이 이긴다. 동시
로그인은 ②에서 하나가 충돌해 `DO UPDATE` 로 빠져 **같은 계정**을 받는다.

### 조회 - N+1 은 여기서 치명적이다

**쿼리 하나가 싱가포르 왕복 ~75ms 다.** 로컬 DB 면 N+1 이 "조금 느림" 인데 여기서는
기보 20개에 21번이면 **1.6초**다. 나중에 고칠 것이 아니라 처음부터 안 생기게 짠다.

| 어디 | 어떻게 | 쿼리 |
| --- | --- | --- |
| 목록 | **요약 칸만 뽑는 DTO 질의.** 컬렉션을 아예 매핑하지 않는다 | **1** |
| 상세 하나 | `fetch join` (페이징이 없어서 안전) | **1** |

**목록에 컬렉션 `fetch join` 을 쓰지 않는다.** 페이징과 같이 쓰면 Hibernate 가 DB 가
아니라 **메모리에서 자른다.** 그리고 목록 화면은 `vs 12급 · 어제 · 초 기권패 · 2수` 처럼
요약만 보여주니 수가 필요 없다 - **컬렉션을 안 매핑하면 N+1 이 생길 구조가 아니다.**

**쿼리 수를 말로 적지 않고 센다.** 테스트 프로필에
`hibernate.generate_statistics=true` 를 켜고 `getPrepareStatementCount()` 를 단정한다.

**커서는 `id DESC` 다.** `OFFSET` 은 읽는 중에 새 기보가 들어오면 같은 판이 두 번 보인다.
순회 중에 들어온 새 기보는 안 끼어든다 - 그게 맞는 동작이다.

### API

```
GET    /api/oauth2/authorization/google   로그인 시작  (Vercel 이 /api 를 벗겨서
GET    /api/login/oauth2/code/google      콜백          Spring 기본 경로와 맞는다)
POST   /api/logout
GET    /api/me                            로그인했나
DELETE /api/me                            탈퇴

POST   /api/games                         올리기 (넣거나 덮어쓴다)
GET    /api/games?cursor=&result=&level=  내 기보 목록
GET    /api/games/{id}                    기보 하나 (수 포함)
DELETE /api/games/{id}                    지우기

POST   /internal/purge                    30일 지난 계정 치우기 (Cloud Scheduler 가 OIDC 로)
```

**URL 에 버전을 안 붙인다.** 버전은 "내가 못 고치는 클라이언트가 있을 때" 값이 생기는데,
프런트와 서버를 같이 배포한다. 다만 **열린 탭에 옛 번들이 남는 것**은 실재하므로
**칸을 더하기만 하고 빼지 않는다.**

**소유권이 없으면 404 다.** 403 은 "거기 뭔가 있다" 를 알려주는 셈이다 - `/stats` 에서
쓴 판단 그대로다.

### 정한 숫자와 그 근거

| 무엇 | 값 | 어디서 온 숫자인가 |
| --- | --- | --- |
| 1인당 기보 | **500판** | 브라우저가 100판(`ARCHIVE_LIMIT`). 하루 10판 둬도 50일치 |
| 인증 쿠키 수명 | 14일 | |
| `state` 쿠키 수명 | 5분 | 콜백에서 검증 뒤 **같은 속성으로 삭제** |
| 탈퇴 유예 | 30일 | |
| DB 왕복 | **~75ms**, 트랜잭션 하나 ~155ms | 2026-10-13 실측(404 101ms vs 삽입 255ms) |

**"몇 명까지 담기나" 는 정하지 않는다.** 1GB ÷ 1.5MB = 680명은 기보만 센 이론값이고
행·인덱스 덧값과 WAL 이 빠졌다. `/stats` 에 `storage` 칸을 따로 두고 **재서** 본다.
`pg_total_relation_size` 합은 **하한**이라 Neon 쪽 숫자와 한 번 대 봐야 한다.

### 안 하기로 한 것

| 무엇 | 까닭 |
| --- | --- |
| 공유 링크 | 지금 필요 없다. 나중에 |
| **제목·메모** | 기보는 끝난 판이라 고칠 게 없다. 넣으면 **수정 API · XSS · 낙관적 락**이 따라온다 |
| **이메일 저장** | `sub` 만 둔다. **DB 가 새도 이메일이 안 샌다** |
| **이메일 화면 표시** | 둘 데가 없다. 토큰에 넣으면 서명은 숨기지 않아서 쿠키가 기록에 남을 때 같이 남는다. `로그인됨` 표시로 충분하다 |
| 자동 올리기 | 허락 없이 올라간다. 그리고 **지운 기보가 되살아난다** |
| Redis · 큐 · 서비스 쪼개기 | 하루 10명이다. 필요를 재고 나서 |

### 만들 순서

```
1  패키지 정리      메인 클래스도 com.nakyung.janggi 로 같이 옮긴다
2  무상태 로그인     2a state 쿠키 · 2b 토큰 발급 · 2c 검증 필터 · 2d 세션 0번
3  기보 CRUD + CSRF  목록 쿼리 1번 · CSRF 를 같이 올린다
4  정합성           FOR UPDATE · 수순 통째 교체 · 499→500 경계
5  권한             낡은 토큰 거부 · 남의 기보 404 · 매핑 자동 열거
6  검색·커서 페이징
7  탈퇴·자동 삭제     30일 경계 · 복구/삭제 경합 · Scheduler OIDC
8  운영             백업 복구 · 표 용량 vs Neon · 메트릭
```

**어려운 건 3번이 아니라 2번과 7번이다.** 2번은 네 조각으로 나눠 각각 테스트한다.

**CSRF 를 뒤로 미루지 않는다.** 쿠키로 인증하면서 기보를 만들고 지우는 주소를 열어 두면
그 사이가 구멍이다. `SameSite=Lax` 는 도움이지 방어가 아니다.

### 보호된 주소를 빠뜨리지 않는 법

**손으로 적은 목록은 틀어진다.** 이 저장소에서 `inAppLabel()` 을 만들고 한 군데 안 붙인
적이 있고, 타입 검사도 `git grep` 도 못 잡았다.

| | 하는 일 |
| --- | --- |
| **자동 열거** | Spring 매핑을 읽어 **표에 안 적힌 보호 주소가 있으면 테스트를 실패시킨다** |
| **표** | 주소마다 **유효한 경로 변수와 본문**을 두고, 낡은 토큰으로 불러 404 를 본다 |

**자동이 검증을 대신하지 않는다.** 본문이 틀리면 400 이 먼저 나서 토큰 검증을 입증하지
못한다. 자동 열거는 **표를 유지하도록 강제하는** 장치다.
