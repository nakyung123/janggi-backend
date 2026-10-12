// janggi-events 부하 시험.
//
// **이 폴더는 그래들과 상관없다.** 자바 테스트는 `src/test/java` 에 있다. 여기는
// k6 로 바깥에서 때려 보는 것이라 빌드에 안 끼어든다.
//
// ── 돌리는 법 ────────────────────────────────────────────────────────────────
//
// k6 를 깔지 않는다. 도커로 돌린다.
//
//   docker run --rm -i -e URL=... -e MODE=... grafana/k6:latest run --quiet - < 이파일
//
// **`-v` 로 파일을 넣지 말고 표준 입력(`-`)으로 넘긴다.** 윈도우 Git Bash 가 `-w /w`
// 를 `W:/` 로 번역해서 도커가 거절한다. 표준 입력에는 그 문제가 없다.
//
// 로컬에 띄워 두고 때릴 때는 앱과 같은 도커 네트워크에 붙인다.
//
//   docker network create ld-net
//   docker run -d --name ld-db --network ld-net -e POSTGRES_PASSWORD=ld -e POSTGRES_DB=ld postgres:18-alpine
//   docker run -d --name ld --network ld-net -p 8081:9090 -e PORT=9090 \
//     -e SPRING_DATASOURCE_URL=jdbc:postgresql://ld-db:5432/ld \
//     -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=ld \
//     -e JANGGI_GUARD_LIMIT_PER_MINUTE=1000000 \
//     janggi-events:ci
//   docker run --rm -i --network ld-net -e URL=http://ld:9090 -e MODE=steady -e RATE=500 \
//     grafana/k6:latest run --quiet - < test/load/events.js
//
// **문지기를 풀어야 서버가 얼마나 버티는지 보인다.** 기본값(분당 120)으로 두면
// 거의 다 429 라 처리량이 아니라 문지기를 재는 셈이 된다. 반대로 문지기를 시험할
// 때는 풀지 않는다.
//
// ── 실서버에 쓸 때 ───────────────────────────────────────────────────────────
//
//   URL=https://janggi-events-982664928901.asia-northeast1.run.app
//
// 지켜야 할 것 셋.
//
//   1. 보내는 것이 **env=dev** 다(아래 본문에 박혀 있다). 빼면 실제 통계가 더러워진다.
//   2. browser 를 **"부하시험"** 으로 두어 나중에 지울 수 있게 한다.
//      delete from event where browser='부하시험';
//   3. **짧게.** 30초면 충분하다. 그동안 들어온 진짜 사용자는 429 를 받아
//      그 사람의 기록이 사라진다 - 하루 40명이면 30초에 걸릴 확률이 1~2% 다.
//
// ── 환경 변수 ────────────────────────────────────────────────────────────────
//
//   URL    때릴 주소           (기본 http://localhost:8080)
//   MODE   steady | same
//   RATE   초당 요청 수         (기본 20)
//   DUR    얼마나              (기본 30s)
//
//   steady  서로 다른 방문으로 보낸다. 처리량과 지연을 본다.
//   same    **완전히 같은** 것을 보낸다. 아무리 많이 보내도 표에 1줄이어야 한다.
import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

const URL = __ENV.URL || "http://localhost:8080";
const MODE = __ENV.MODE || "steady";
const RATE = Number(__ENV.RATE || 20);
const DUR = __ENV.DUR || "30s";

// 응답 코드별로 센다. 평균만 보면 **거절당한 것이 안 보인다** - 429 가 쏟아져도
// 지연은 오히려 좋아지기 때문에 성공한 줄 알기 쉽다.
// (지표 이름은 ASCII 만 된다. k6 가 한글 이름을 거절한다.)
const 받음204 = new Counter("code_204");
const 거절429 = new Counter("code_429");
const 잘못400 = new Counter("code_400");
const 기타 = new Counter("code_other");

export const options = {
  scenarios: {
    main: {
      // **도착률을 고정한다.** VU 수를 고정하면 서버가 느려질 때 보내는 양도 같이
      // 줄어서, 느려진 것이 숫자에 안 나타난다. 처리 못 한 만큼은
      // `dropped_iterations` 로 드러난다 - 그 값이 곧 "못 버틴 양" 이다.
      executor: "constant-arrival-rate",
      rate: RATE,
      timeUnit: "1s",
      duration: DUR,
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // **429 는 실패가 아니다.** 그러라고 만든 문지기다. 여기서 보는 것은 연결이
    // 끊기거나 5xx 가 나오는 것뿐이다.
    checks: ["rate>0.99"],
  },
};

function 본문(이름, sid, 보낸시각) {
  return JSON.stringify({
    events: [
      {
        name: 이름,
        sid: sid,
        ver: "0.1.0",
        browser: "부하시험",
        os: "리눅스",
        isolated: true,
        cores: 8,
        memory: 16,
        screen: "390x844",
        at: 보낸시각,
        env: "dev",
        data: { 왜: "부하시험" },
      },
    ],
  });
}

export default function () {
  let 몸통;
  if (MODE === "same") {
    // 모든 VU 가 완전히 같은 것을 보낸다. (sid, name, sent_at) 이 같으므로
    // `event_once_idx` 에 걸려 표에는 한 줄만 남아야 한다.
    몸통 = 본문("app_open", "loadsame", 1760000000000);
  } else {
    // 서로 다른 방문. sid 칸이 16글자라 잘라 쓴다.
    const sid = `ld${String(__VU).padStart(3, "0")}${String(__ITER).padStart(6, "0")}`;
    몸통 = 본문("app_open", sid.slice(0, 16), Date.now());
  }

  const res = http.post(`${URL}/events`, 몸통, {
    headers: { "Content-Type": "application/json" },
    // 서버의 요청 타임아웃이 30초다. 그보다 짧게 끊으면 서버가 버틴 것을
    // 우리가 실패로 적게 된다.
    timeout: "30s",
  });

  if (res.status === 204) 받음204.add(1);
  else if (res.status === 429) 거절429.add(1);
  else if (res.status === 400) 잘못400.add(1);
  else 기타.add(1);

  check(res, {
    "응답이 왔고 5xx 가 아니다": (r) => r.status !== 0 && r.status < 500,
  });
}
