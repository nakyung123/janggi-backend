---
paths:
  - "src/main/resources/application*.properties"
  - "deploy/**"
---

# 환경 변수

값은 코드에 넣지 않는다. 로컬은 `.env`, Cloud Run 은 서비스 환경 변수와 Secret Manager 다. 스프링이 `janggi.stats.token` 을 `JANGGI_STATS_TOKEN` 으로도 읽는다.

| 변수 | 언제 필요한가 | 무엇 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 늘 | DB 주소. 없으면 4초 만에 죽는다 |
| `SPRING_DATASOURCE_USERNAME` | 늘 | DB 계정 |
| `SPRING_DATASOURCE_PASSWORD` | 늘 | DB 비밀번호. 운영은 Secret Manager |
| `JANGGI_STATS_TOKEN` | `/stats` 를 열 때 | 통계 열쇠. 안 걸면 `/stats` 가 404 |
| `GOOGLE_CLIENT_ID` | 로그인 | 구글 OAuth 클라이언트 |
| `GOOGLE_CLIENT_SECRET` | 로그인 | 구글 OAuth 비밀 |
| `JANGGI_TOKEN_SECRET` | 로그인 | 로그인 쿠키에 서명하는 열쇠 |
| `JANGGI_LOGIN_REDIRECT` | 선택 | 로그인 뒤 돌아갈 곳. 기본 `/` |
| `JANGGI_GUARD_LIMIT_PER_MINUTE` | 선택 | 분당 받을 기록 수 |
| `JANGGI_GUARD_GAME_LIMIT_PER_MINUTE` | 선택 | 분당 받을 기보 수 |
| `JANGGI_GUARD_MAX_BODY_BYTES` | 선택 | 본문 크기 상한 |
| `JANGGI_GUARD_MAX_GAME_BYTES` | 선택 | 기보 본문 크기 상한 |
| `JANGGI_PURGE_CALLER` | 정리를 쓸 때 | `/internal/purge` 를 부를 수 있는 계정 |
| `JANGGI_PURGE_AUDIENCE` | 정리를 쓸 때 | 토큰의 대상 |
| `JANGGI_PURGE_ISSUER` | 선택 | 기본 `https://accounts.google.com` |
| `JANGGI_PURGE_JWKS_URI` | 선택 | 토큰 서명을 확인할 열쇠 주소 |
| `PORT` | 선택 | 기본 8080. Cloud Run 이 넣어 준다 |

- 빠뜨렸을 때 열리는 것이 아니라 닫히는 쪽으로 기본값을 둔다.
- `.env` 의 값에는 따옴표를 붙인다. URL 의 `&` 를 셸이 "뒤에서 실행" 으로 읽어 대입이 조용히 끊긴다. 그래서 Cloud Run 에 빈 주소를 한 번 올렸다.
