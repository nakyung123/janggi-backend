---
paths:
  - "Dockerfile"
  - ".dockerignore"
  - ".github/workflows/**"
---

# 도커 · CI

이미지를 고쳤으면 컨테이너로도 띄워 본다. jar 로 되는 것과 이미지로 되는 것은 다르다. DB 접속 정보를 같이 줘야 뜬다(CI 의 "DB 를 띄운다" 단계가 본보기다).

```bash
docker build -t janggi-backend:dev .
docker run -d --name t -p 8080:9090 -e PORT=9090 janggi-backend:dev   # + DB 접속 정보
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health   # 200
docker logs t                      # 줄마다 전체가 JSON 인지 파싱해서 본다
docker stop t && docker logs t | grep -c 'Graceful shutdown complete'   # 1
docker rm -f t
```

- 종료 확인은 `tail` 이 아니라 글자로 찾는다. 맨 끝 줄은 JPA·Hikari 정리 줄이다.
- 종료 대기(`spring.lifecycle.timeout-per-shutdown-phase`)는 10초를 넘기지 않는다. Cloud Run 은 SIGTERM 뒤 10초에 SIGKILL 을 보낸다.
- `gradlew` 의 실행 권한(100755)이 윈도우 커밋에서 빠지면 리눅스에서 빌드가 안 된다. `git ls-files -s gradlew` 로 확인하고 `git update-index --chmod=+x` 로 박는다.
- CI 가 보는 것(테스트 수, 컨테이너 기동, 로그 JSON, 깨진 글자)은 `ci.yml` 주석에 까닭이 있다.
