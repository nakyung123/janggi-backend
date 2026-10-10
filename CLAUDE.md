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
com.nakyung.janggi.events/
├── JanggiEventsApplication.java
├── event/                    기능 하나
│   ├── EventController.java  받는다 (얇게)
│   ├── EventName.java        받아들이는 이름 목록. 여기가 유일한 기준
│   ├── EventService.java     거르고 적는다
│   └── dto/                  주고받는 모양
└── common/exception/         400 으로 바꾸는 자리
```

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
- **한글이 든 요청을 셸에서 `curl -d` 로 보내면 깨진다.** 본문을 UTF-8 파일로 쓰고
  `--data-binary @파일` 로 보낸다.

## 깨뜨리면 안 되는 것

- **개인정보.** IP 를 적지 않는다. 쿠키를 쓰지 않는다. 방문 번호(`sid`)는 쪽을 열
  때마다 새로 만든 것이라 사람을 이어 붙이지 않는다. **"몇 명" 이 아니라 "몇 번"
  만 센다.**
- **받는 시각이 기준이다.** 보낸 쪽 시각(`at`)은 참고용이다 - 사용자 시계는 틀릴 수
  있다.
- **모르는 이벤트 이름은 거절한다.** 느슨하게 받으면 나중에 숫자를 못 믿는다. 대가로
  순서가 생긴다 - **프런트에 새 이벤트를 더할 때는 서버를 먼저 배포한다.**
- **공개 주소다.** 누구나 부를 수 있다. 새 입구를 만들 때마다 크기 제한과 요청 수
  제한을 같이 생각한다.

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

끌 때 `pkill` 은 안 먹는다. 포트로 잡는다.

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

## 아직 없는 것

DB · 도커 · 레이트 리밋 · 크기 제한 · `/stats`. 순서는 **배포로 관을 먼저 뚫고**
그다음 DB 다. 로그만으로도 Cloud Logging 이 30일 보관하므로 그 사이 데이터가
버려지지 않는다.

**DB 가 생기면 규칙 둘이 여기 들어온다** - 한 요청이 반쯤 적히지 않게(원자성), 같은
이벤트가 두 번 와도 꼬이지 않게(멱등성). `sendBeacon` 은 at-least-once 라 재전송이
실제로 일어난다.
