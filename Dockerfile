# 측정 수집 서버 이미지.
#
# 두 단계로 나눈다. 빌드에 쓰는 JDK·그래들·소스는 **마지막 이미지에 들어가지 않는다.**
# 한 단계로 만들면 이미지가 몇 배로 커지고, 소스까지 같이 배포된다.

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /src

# 설정만 먼저 넣고 의존성을 내려받는다. 소스만 바뀌었을 때 이 층을 다시 쓰기 때문에
# 다시 빌드할 때 몇 분을 아낀다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies

COPY src ./src

# build 는 테스트까지 돌린다. bootJar 만 돌리면 **테스트가 깨진 코드로도 이미지가
# 만들어진다.** 배포되는 이미지는 테스트를 지난 것이어야 한다.
RUN ./gradlew --no-daemon build

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre-alpine

# 뿌리 권한으로 돌리지 않는다. 공개 주소라서 혹시 안으로 들어오는 길이 생겨도 할 수
# 있는 일을 줄여 둔다.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

# --chown 을 COPY 에 붙인다. 복사한 뒤에 RUN chown 으로 주인을 바꾸면 도커가 **같은
# 파일을 새 층에 통째로 다시 복사해서** jar 가 이미지 안에 두 번 들어간다. 처음에 그렇게
# 썼다가 23.7MB 를 그냥 버리고 있었다. Artifact Registry 무료 보관이 0.5GB 뿐이라
# 버전당 그만큼이 보관할 수 있는 버전 수를 절반으로 줄인다.
COPY --from=build --chown=app:app /src/build/libs/*.jar app.jar
USER app

# 이미지로 돌리면 곧 Cloud Run 이다. 여기서 프로파일을 박아 두면 로그가 구조화 JSON 으로
# 나가고, 로컬에서 jar 를 직접 돌릴 때는 사람이 읽는 글로 남는다.
ENV SPRING_PROFILES_ACTIVE=cloud

# 어느 커밋으로 지은 이미지인지. CI 가 넣어 주고, 손으로 지으면 unknown 으로 남는다 -
# **모르는 것을 아는 척하지 않는다.** 뜰 때 이 값을 로그에 한 줄 찍는다.
#
# 맨 아래 가까이 둔 까닭 - ARG 가 바뀌면 그 아래 층이 다시 만들어진다. 위에 두면
# 커밋이 바뀔 때마다 jar 복사까지 다시 한다.
ARG GIT_SHA=unknown
ENV JANGGI_COMMIT=$GIT_SHA

# 실제 포트는 Cloud Run 이 PORT 로 알려 준다. 이 줄은 사람이 읽기 위한 표시다.
EXPOSE 8080

# 대괄호 형태로 쓴다(exec form). 이걸 문자열로 쓰면 sh 가 1번 프로세스가 되어
# **SIGTERM 을 자바에게 넘기지 않는다.** 그러면 server.shutdown=graceful 이 아무 일도
# 못 하고, Cloud Run 이 서버를 줄일 때 처리 중이던 기록이 사라진다.
#
# MaxRAMPercentage - 컨테이너에 준 메모리를 기준으로 힙을 잡는다. 없으면 호스트 전체를
#   보고 잡아서 한도를 넘어 죽는다.
# TieredStopAtLevel=1 - 기계어 최적화를 1단계에서 멈춘다. 오래 돌 때의 최고 속도를
#   버리고 **뜨는 시간**을 줄인다. 이 서버는 요청이 드물어 거의 매번 새로 뜨므로 이쪽이 맞다.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
