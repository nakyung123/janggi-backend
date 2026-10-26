# CLAUDE.md

장기 AI(janggi-ai.vercel.app)의 백엔드다. 측정 기록 받기, 구글 로그인, 기보 저장, 통계를 맡는다.
Spring Boot 4.1 · Java 21 · Neon Postgres(싱가포르, Flyway) · Cloud Run(asia-northeast1).
프론트(`janggi-frontend`)와는 코드로 안 붙고 `vercel.json` 의 되돌림(`/api/*`) 한 줄로만 붙는다.

측정은 브라우저가 `sendBeacon` 으로 던지고 잊으므로 콜드 스타트 몇 초를 감당한다. 기보 목록·올리기는
사람이 기다린다. 그 대응은 `GAMES.md` 맨 앞에 있다.

| 무엇 | 이름 |
| --- | --- |
| 깃허브 저장소 | `janggi-backend` |
| 내 컴퓨터 폴더 | `janggi-events` |
| Cloud Run 서비스 · GCP 프로젝트 | `janggi-events` (바꾸면 서비스 주소가 바뀐다) |

## 먼저 읽을 것

| 무엇을 할 때 | 어디를 본다 |
| --- | --- |
| 계획을 세울 때 | `c:\nakyung\work-base\projects\janggi\README.md` → 거기서 할 일에 맞는 파일 |
| 기보·사용자 | `GAMES.md` |
| 배포 명령, 리비전 되돌리기 | `deploy/DEPLOY.md` |
| 부하 시험 | `test/load/events.js` 머리 주석 |

`.claude/rules/` 의 파일(Boot 4, DB, 통계, 로그, 요청 제한, 도커, 환경 변수)은 맞는 파일을 열 때 자동으로 읽힌다.

## 구조

- 기능별 폴더: `event/` `game/` `user/` `auth/` `stats/` `purge/`. 모든 입구에 공통으로 걸리는 것(요청 제한, 400 변환, 로그 모양)은 `common/`.
- presentation / application / domain / infrastructure 네 겹으로 나누지 않는다.
- 스키마는 Flyway(`src/main/resources/db/migration/`)만 만든다. 하이버네이트는 `ddl-auto=validate` 로 맞춰 보기만 한다.

## 명령

```bash
./gradlew clean build                        # 커밋 전. 테스트가 몇 개 돌았는지 본다
java -jar build/libs/janggi-backend-*.jar    # 로그를 읽을 때. bootRun 은 한글 로그가 깨진다
```

- "BUILD SUCCESSFUL" 만 보지 않는다. 테스트가 `UP-TO-DATE` 로 건너뛰었을 수 있다.
- 띄워서 직접 불러 본다. DB 접속 정보가 없으면 4초 만에 죽는다. 이미지를 고쳤으면 컨테이너로도 띄운다(`.claude/rules/docker.md`).
- CI(`.github/workflows/ci.yml`)가 같은 확인을 하고, main 이면 그 이미지를 배포한다.

## 하지 말 것

- 적용된 마이그레이션 파일을 고치지 않는다. 주석 한 글자도. 새 마이그레이션을 만든다(`MigrationFilesTest` 가 지킨다).
- 새 입구(주소)를 만들고 `common/guard/GuardConfig` 에 등록하지 않으면 그 입구만 요청 제한 없이 열린다.
- 모르는 이벤트 이름을 받아들이지 않는다(400). 느슨하게 받으면 숫자를 못 믿는다.
- 로그 줄을 문자열을 이어 붙여 만들지 않는다. 보낸 값의 줄바꿈으로 기록을 위조할 수 있다(`EventLogLineTest`).
- 측정 기록에 IP 를 적지 않고, 사람을 이어 붙이지 않는다. 방문 번호(`sid`)는 쪽을 열 때마다 새로 만든다. "몇 명" 이 아니라 "몇 번" 만 센다.
- `X-Forwarded-For` 로 사람을 가르지 않는다. 직접 부르는 쪽이 마음대로 적을 수 있다.
- 설정을 빠뜨렸을 때 열리는 쪽으로 기본값을 두지 않는다. 닫히는 쪽으로 둔다.
- `--min-instances=1` 을 쓰지 않는다. 무료 한도 안에서만 돌린다.

## 되돌릴 수 없는 변경

계획을 파일로 남기고 승인받는다.

- **main 에 푸시하는 것.** CI 가 Cloud Run 에 배포한다.
- **마이그레이션.** 운영 DB 의 구조를 바꾼다. 올리기 전에 백업 작업(`.github/workflows/backup.yml`)을 손으로 돌린다.
- **운영 DB 의 데이터를 지우거나 고치는 것.** 부하 시험 기록 지우기도 포함한다.
- **이벤트 이름을 `EventName.java` 에서 빼는 것.** 프론트가 보내는 기록이 버려진다. 더할 때는 서버를 먼저 배포한다. 프론트가 아직 보내는 이름을 빼면 CI(`scripts/check-event-names.py`)가 막는다.
- **인증·권한.** 토큰 서명 열쇠, Secret Manager, IAM, Workload Identity 설정.

## 이 파일

항상 읽히는 파일이다. 특정 파일에만 해당하는 규칙은 `.claude/rules/` 에 `paths` 를 붙여 둔다. 일반 규칙은 `c:\nakyung\work-base` 에 있다. 여기에 되풀이하지 않는다.
