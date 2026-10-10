# 올리는 법

**여기 적힌 명령은 전부 실제로 돌려 본 것이다.** 2026-10-10 첫 배포 때 쓴 그대로다.

```
프로젝트   janggi-events          리전   asia-northeast1 (도쿄)
서비스     janggi-events          주소   https://janggi-events-982664928901.asia-northeast1.run.app
```

## 왜 도쿄인가

서울(`asia-northeast3`)은 **Tier 2** 라 단가가 비싸고 무료 한도도 적게 쳐진다. 도쿄는
**Tier 1** 이다. 서울이 30ms 쯤 빠르지만 **이 서버는 사람이 답을 기다리지 않아서**
그 차이에 더 비싼 등급을 살 이유가 없다.

## 올리기

```bash
# 윈도우에서는 gcloud 경로에 공백이 있어 따옴표가 부딪힌다. PATH 에 넣고 쓴다.
export PATH="/c/Users/$USER/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin:$PATH"

gcloud run deploy janggi-events \
  --source . \
  --region=asia-northeast1 \
  --allow-unauthenticated \
  --max-instances=2 \
  --min-instances=0 \
  --timeout=30s \
  --memory=512Mi \
  --cpu=1
```

`--source` 는 이 폴더의 `Dockerfile` 로 Cloud Build 가 짓는다. **그 안에서 테스트도
돌기 때문에** 테스트가 깨지면 이미지가 안 만들어진다. 집에서 330MB 이미지를 올리는
것보다 소스만 보내는 쪽이 훨씬 빠르다.

각 값의 까닭.

| 값 | 왜 |
| --- | --- |
| `--max-instances=2` | 요금이 새는 속도를 묶는다. 지출 한도는 **늦게 닫히므로** 이게 같이 있어야 한다 |
| `--min-instances=0` | 아무도 안 쓰면 0대. 1로 두면 **24시간 내내 돈이 나간다** |
| `--timeout=30s` | 기본이 300초다. 멈춘 요청 하나가 5분씩 CPU 를 붙잡는 걸 막는다 |
| `--memory=512Mi` | `MaxRAMPercentage=75` 가 이 값을 보고 힙을 잡는다 |
| `--allow-unauthenticated` | 브라우저가 토큰 없이 부른다 |

## 올린 뒤 확인

```bash
U=https://janggi-events-982664928901.asia-northeast1.run.app

curl -s -o /dev/null -w '%{http_code}\n' $U/actuator/health          # 200
curl -s -X POST $U/events -H 'Content-Type: application/json' \
     --data-binary @본문.json -o /dev/null -w '%{http_code}\n'        # 204

# 로그가 칸으로 뜯겼는지. 이게 핵심이다 - 뜯히지 않으면 조건 검색이 안 된다.
gcloud logging read 'resource.labels.service_name="janggi-events" AND jsonPayload.evt!=""' \
  --limit=3 --format=json --freshness=30m

# 글자로 남은 줄이 있는지. 우리 앱 로그는 0건이어야 한다.
gcloud logging read 'resource.labels.service_name="janggi-events" AND textPayload!=""' \
  --limit=5 --format='value(textPayload)' --freshness=30m
```

마지막 명령에 **Cloud Run 자신의 줄**(`Default STARTUP TCP probe succeeded` 등)은
나온다. 그건 플랫폼 것이라 어쩔 수 없다. **우리 앱 줄이 거기 섞여 있으면 문제다.**

## 실서버가 로컬과 다른 점

- **길이를 안 알려준 요청(chunked)이 Cloud Run 에서는 204 다.** 로컬에서는 411 이었다.
  프록시가 본문을 받아 **길이를 대신 채워 준다.** 즉 크기 검사를 길이 숨기기로 우회하는
  길이 실서버에는 없다. `RequestSizeFilter` 의 411 은 거기서는 안 쓰이는 셈이다.
- **콜드 스타트 3.0초.** 로컬 컨테이너는 2.4초였다.
- **Cloud Run 이 자기 요청 기록에 `remoteIp`(부른 사람의 IP)를 적는다.** 우리 앱은
  안 적지만 플랫폼은 적는다. 기본 보관 30일.

## 이미지 보관

무료 보관이 **0.5GB** 뿐이다. 이미지 한 판이 약 91MB 고, 다음 판부터는 베이스가
공유돼 20MB 안팎씩 는다.

```bash
gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy \
  --location=asia-northeast1 --policy=deploy/cleanup-policy.json --dry-run
```

**지금은 `--dry-run` 이다.** 진짜로 지우게 하려면 `--no-dry-run` 으로 다시 건다.
dry-run 결과는 반영에 하루쯤 걸리므로, 하루 뒤 감사 로그에서 무엇이 지워질지 보고
바꾼다. **지우는 것은 되돌릴 수 없다.**

## 아직 안 한 것

- 삭제 정책을 `--no-dry-run` 으로 전환 (하루 뒤)
- 지출 한도가 무료 사용량을 세는지 실제 값으로 확인 (하루 뒤)
- `vercel.json` 되돌림으로 같은 출처 만들기 (1-4)
