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
├── Dockerfile  .dockerignore            이미지
├── src/main/resources/
│   ├── application.properties           어디서나
│   ├── application-cloud.properties     Cloud Run 에서만 (프로파일 cloud)
│   └── logback-spring.xml               로그를 어떤 모양으로 내보내나
└── src/main/java/com/nakyung/janggi/events/
    ├── JanggiEventsApplication.java
    ├── event/                    기능 하나
    │   ├── EventController.java  받는다 (얇게)
    │   ├── EventName.java        받아들이는 이름 목록. 여기가 유일한 기준
    │   ├── EventService.java     거르고 적는다
    │   └── dto/                  주고받는 모양
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

**모르는 클래스는 짐작하지 말고 jar 를 열어 본다.**

```bash
unzip -l <jar> | grep ClassName
```

Boot 3.5 는 2026-06-30 에, 4.0 은 2026-12-31 에 지원이 끝난다. 그래서 4.1 이다.

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
- **`grep -c $''` 로 CRLF 를 세면 안 된다.** 이 셸에서는 패턴이 빈 것으로 넘어가서
  **순수 LF 파일에도 줄 수만큼 세어 준다.** 그걸 믿고 "저장소가 CRLF 로 들어갔다" 고
  한참 봤는데 처음부터 LF 였다. 바이트로 센다.

      tr -cd '' < 파일 | wc -c      # 0 이면 LF
      git cat-file blob $(git rev-parse HEAD:파일) | tr -cd '' | wc -c

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
java -jar build/libs/janggi-events-0.0.1-SNAPSHOT.jar
curl -X POST localhost:8080/events -H "Content-Type: application/json" --data-binary @본문.json
```

`--no-daemon` 을 붙이지 않는다. 매번 JVM 을 새로 띄워서 빌드가 20초씩 걸린다(데몬을
쓰면 14초). 다 끝나면 `./gradlew --stop` 으로 끈다.

**이미지를 고쳤으면 컨테이너로도 띄워 본다.** jar 로 되는 것과 이미지로 되는 것은
다르다 - `gcp` 형식이 없다는 것도, jar 는 멀쩡한데 컨테이너가 시작조차 못 하면서 드러났다.

```bash
docker build -t janggi-events:dev .
docker run -d --name t -p 8080:9090 -e PORT=9090 janggi-events:dev
curl -s -o /dev/null -w '%{http_code}
' localhost:8080/actuator/health   # 200
docker logs t                      # 줄마다 전체가 JSON 인지
docker stop t && docker logs t | tail -2   # Graceful shutdown complete 가 있는지
docker rm -f t
```

로그는 **눈으로 보지 말고 줄마다 파싱해 본다.** 한 줄이라도 JSON 이 아니면 그 줄은
Cloud Logging 에서 검색되지 않는다.

끌 때 `pkill` 은 안 먹는다. 포트로 잡는다.

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 아직 없는 것

DB · `/stats` · 프런트 연결. 순서는 **배포로 관을 먼저 뚫고** 그다음 DB 다. 로그만으로도
Cloud Logging 이 30일 보관하므로 그 사이 데이터가 버려지지 않는다.

**굳이 안 한 것** - 콜드 스타트를 더 줄이는 길(CDS 미리 만들기, 층 나눠 담기)은 두었다.
지금 2.4초이고 한 달 콜드 스타트를 다 합쳐도 무료 한도의 1.3% 라서, 복잡해지는 값이
얻는 것보다 크다. **사람이 기다리는 길 위에 없다는 것이 이 판단의 근거다** - 그 전제가
바뀌면 다시 본다.

**DB 가 생기면 규칙 둘이 여기 들어온다** - 한 요청이 반쯤 적히지 않게(원자성), 같은
이벤트가 두 번 와도 꼬이지 않게(멱등성). `sendBeacon` 은 at-least-once 라 재전송이
실제로 일어난다.
