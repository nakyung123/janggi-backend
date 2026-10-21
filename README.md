# janggi-backend

[![CI][ci-shield]][ci-url]
[![License][license-shield]][license-url]

[![Java][Java-shield]][Java-url]
[![Spring Boot][Spring-shield]][Spring-url]
[![Postgres][Postgres-shield]][Postgres-url]
[![Docker][Docker-shield]][Docker-url]
[![Cloud Run][CloudRun-shield]][CloudRun-url]

[장기 AI](https://janggi-ai.vercel.app) 의 백엔드입니다. 사용 기록 수집과 통계,
Google OAuth2 로그인, 기보 저장 API 를 제공합니다. 운영 중입니다.

서비스 [janggi-ai.vercel.app](https://janggi-ai.vercel.app) ·
화면 쪽 저장소 [janggi-frontend](https://github.com/nakyung123/janggi-frontend)

## 주요 기능

| 기능 | 하는 일 |
| --- | --- |
| 이벤트 수집과 통계 | 브라우저가 보낸 이벤트를 묶음으로 받아 저장하고, 기간별로 집계합니다 |
| 인증 | Google OAuth2 기반 로그인과 사용자 식별 |
| 기보 관리 | 사용자별 대국 기록을 올리고, 보고, 지웁니다. 탈퇴하면 함께 지웁니다 |

## 아키텍처

```
Vercel                     화면 · /api 요청 전달
   │
   ▼
Cloud Run (여기)           API · 인증 · 비즈니스 로직
   │
   ▼
Neon PostgreSQL            이벤트 · 계정 · 기보
```

Vercel 이 `/api/*` 요청을 Cloud Run 으로 전달하고, Spring Boot 서버가 인증과 비즈니스
로직을 처리해 Neon PostgreSQL 에 저장합니다. 엔진은 브라우저 안에서 돌기 때문에
**서버가 멈춰도 장기는 둬집니다.** 서버가 있어야 하는 것은 로그인입니다.

## 프로젝트 구조

```
janggi-backend/
├── src/
│   ├── main/
│   │   ├── java/com/nakyung/janggi/
│   │   │   ├── event/              이벤트 수집과 저장
│   │   │   ├── stats/              통계 조회
│   │   │   ├── auth/               구글 로그인 · 토큰
│   │   │   ├── user/               계정과 탈퇴
│   │   │   ├── game/               기보 저장과 조회
│   │   │   ├── purge/              지울 계정 정리
│   │   │   └── common/             예외 · 요청 제한 · 로그 · 보안 설정
│   │   └── resources/db/migration/ Flyway 마이그레이션
│   └── test/                       단위 · DB 통합 테스트
├── test/load/                      k6 부하 시험
├── deploy/                         배포 가이드
└── GAMES.md                        기보와 인증 설계
```

## 주요 기술적 성과

- **데이터 일관성** - DB 유일성 제약과 `ON CONFLICT` 로 중복 이벤트를 막고, 동시
  요청 6,001건으로 검증했습니다(표에 1줄).
- **스키마 검증** - Flyway 로 스키마 변경을 관리하고, 애플리케이션 기동 시
  `ddl-auto=validate` 로 코드와 DB 의 불일치를 검증합니다.
- **배포 검증** - 만든 이미지를 DB 와 함께 실제로 띄워 API 를 불러 보고, **그 이미지
  그대로** Cloud Run 에 올립니다.
- **요청 제한** - 고정 창의 경계에서 한도의 두 배까지 허용되던 문제를 미끄러지는 창으로
  고쳤습니다. 같은 부하에서 초과 수용이 **60초에 230건 → 120건**(설정 한도)으로
  줄었습니다.

## 테스트와 배포

**JUnit 5 · Testcontainers · k6 · GitHub Actions · GitHub OIDC**

테스트 142개를 실행하며, Testcontainers 로 실제 PostgreSQL 을 사용하는 통합 테스트를
포함합니다. 부하 시험은 k6 로 별도 수행합니다([`test/load/events.js`](test/load/events.js)).

`main` 에 푸시하면 GitHub Actions 가 테스트 · 이미지 빌드 · 컨테이너 검증 · Cloud Run
배포를 합니다. GCP 에는 열쇠 파일 없이 OIDC 로 들어갑니다. 데이터는 매일 Cloud Storage
로 백업합니다.

```bash
./gradlew test
```

## 관련 문서

| 문서 | 담은 것 |
| --- | --- |
| [`GAMES.md`](GAMES.md) | 기보와 인증의 설계 |
| [`deploy/DEPLOY.md`](deploy/DEPLOY.md) | 배포 절차와 되돌리기 |
| [`CLAUDE.md`](CLAUDE.md) | 개발 메모 - 환경 변수 · 실행과 테스트 |

## 라이선스

**MIT.** 전문은 [`LICENSE`](LICENSE) 에 있습니다.

<!-- 배지 주소 -->
[ci-shield]: https://github.com/nakyung123/janggi-backend/actions/workflows/ci.yml/badge.svg
[ci-url]: https://github.com/nakyung123/janggi-backend/actions/workflows/ci.yml
[license-shield]: https://img.shields.io/badge/license-MIT-blue?style=flat
[license-url]: https://github.com/nakyung123/janggi-backend/blob/main/LICENSE
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
