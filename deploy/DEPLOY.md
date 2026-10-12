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
- **콜드 스타트 3.0 ~ 7.5초.** 로컬 컨테이너는 2.4초였다. 첫 배포 때 3.0초를 쟀는데
  2026-10-11 아침 첫 요청은 **7.5초**였다. 깨어난 뒤는 0.11초다. 한 번 재고 숫자를
  적어 두면 그 숫자를 믿게 되니 폭으로 적는다. `sendBeacon` 이 답을 안 기다려서
  사용자에게는 안 보인다.
- **Cloud Run 이 자기 요청 기록에 `remoteIp`(부른 사람의 IP)를 적는다.** 우리 앱은
  안 적지만 플랫폼은 적는다. 기본 보관 30일.

## 데이터베이스 (Neon Postgres, 싱가포르)

```
프로젝트    janggi-events          misty-meadow-28322077
리전        aws-ap-southeast-1     도쿄가 없어서 제일 가까운 데로 갔다
컴퓨트      0.25 CU 고정           기본은 0.25~2 였다. 2로 커지면 무료 시간이 8배로 닳는다
잠들기      5분 (끌 수 없다)        깨는 데 약 0.5초 - 직접 쟀다
```

접속 정보는 서비스에 이렇게 박혀 있다.

| 이름 | 어디서 오나 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | 평범한 환경 변수 (비밀 아님) |
| `SPRING_DATASOURCE_USERNAME` | 평범한 환경 변수 (비밀 아님) |
| `SPRING_DATASOURCE_PASSWORD` | **Secret Manager** 의 `janggi-db-password` 참조 |

**비밀번호만 Secret Manager 인 까닭** - 평범한 환경 변수로 두면
`gcloud run services describe` 와 콘솔 화면에 **값이 그대로 찍힌다.** 참조로 두면 이름만
보인다. 무료 한도가 활성 비밀 6개와 월 1만 회 조회라 우리는 공짜다(비밀 1개, 콜드
스타트마다 한 번씩 월 600회쯤).

한 번만 한 준비는 이렇다. **값은 파이프로만 흘린다 - 명령줄에 적지 않는다.**

```bash
P=janggi-events
RUNTIME=982664928901-compute@developer.gserviceaccount.com

gcloud services enable secretmanager.googleapis.com --project=$P

printf '%s' "$비밀번호" | gcloud secrets create janggi-db-password \
  --data-file=- --replication-policy=automatic --project=$P

gcloud secrets add-iam-policy-binding janggi-db-password \
  --member="serviceAccount:$RUNTIME" --role=roles/secretmanager.secretAccessor --project=$P

# 환경 변수는 파일로 준다. URL 에 & 와 = 가 있어서 명령줄로 주면 셸이 끊는다.
gcloud run services update janggi-events --region=asia-northeast1 --project=$P \
  --env-vars-file=env.yaml \
  --update-secrets="SPRING_DATASOURCE_PASSWORD=janggi-db-password:latest"
```

**워크플로의 `gcloud run deploy` 는 이 값들을 건드리지 않는다.** `--set-env-vars` 나
`--clear-env-vars` 를 안 주면 서비스에 있던 것이 그대로 남는다. 거꾸로 말하면 **누가
나중에 워크플로에 `--set-env-vars` 를 하나 더하는 순간 DB 설정이 통째로 날아간다** -
`--allow-unauthenticated` 와 같은 성질이다.

## 이미지 보관

무료 보관이 **0.5GB** 뿐이다. **1-5 에서 JPA·하이버네이트·Flyway·드라이버가 들어오면서
이미지가 331MB → 392MB 로 늘었다**(압축 전). 다음 판부터는 베이스가 공유돼 늘어나는 것은
앱 층뿐이다.

```bash
gcloud artifacts repositories set-cleanup-policies cloud-run-source-deploy \
  --location=asia-northeast1 --policy=deploy/cleanup-policy.json --dry-run
```

**2026-10-10 에 `--no-dry-run` 으로 바꿨다.** 하루 기다려 dry-run 결과를 보려 했는데
**기다려도 배울 것이 없었다** - 이미지가 1판뿐이라 지울 대상이 0개다. 자동 배포가 켜지면
푸시마다 이미지가 쌓이므로 **울타리를 먼저 세우고 수도를 틀었다.**

**좁은 길 하나.** `--min-instances=0` 이라 쉬다 깨어날 때마다 이미지를 새로 받는다. 쓰고
있는 이미지가 지워지면 **그때 서비스가 깨진다.** 최신 5판은 보호되고 배포는 항상 최신으로
트래픽을 옮기니, 이 일이 나려면 "트래픽이 옛 리비전에 머문 채 5번 더 푸시" 가 필요하다.
좁지만 되돌릴 수 없는 쪽이라 적어 둔다.

## 자동 배포 - 한 번만 하는 준비

`main` 에 올리면 `.github/workflows/ci.yml` 이 **시험한 그 이미지를** Cloud Run 에
올린다. 그러려면 GitHub 이 GCP 에 들어올 수 있어야 한다.

**열쇠 파일(JSON 키)을 쓰지 않는다.** 그 파일은 만료가 없어서, 한 번 새면 영구히 쓸 수
있다. **이 저장소는 공개라** 더더욱 그렇다. 대신 GitHub 이 서명한 증서를 GCP 가 확인하는
방식(Workload Identity Federation)을 쓴다 - 토큰 수명이 15분이고 저장할 비밀이 없다.

아래는 **한 번만** 돌린다.

```bash
P=janggi-events
NUM=982664928901
SA=github-deploy@$P.iam.gserviceaccount.com
RUNTIME=$NUM-compute@developer.gserviceaccount.com
GH=nakyung123/janggi-events

# 0. 토큰을 바꿔 주는 창구. 꺼져 있었다.
gcloud services enable sts.googleapis.com --project=$P

# 1. 배포만 하는 계정
gcloud iam service-accounts create github-deploy \
  --display-name="GitHub Actions 배포용" --project=$P

# 2. 권한 셋.
#    run.admin 이 아니라 run.developer 다 - setIamPolicy 가 빠져 있어서
#    "누가 부를 수 있나" 는 못 바꾼다. 그래서 워크플로에 --allow-unauthenticated 가 없다.
gcloud projects add-iam-policy-binding $P \
  --member="serviceAccount:$SA" --role=roles/run.developer
gcloud projects add-iam-policy-binding $P \
  --member="serviceAccount:$SA" --role=roles/artifactregistry.writer
gcloud iam service-accounts add-iam-policy-binding $RUNTIME \
  --member="serviceAccount:$SA" --role=roles/iam.serviceAccountUser --project=$P

# 3. 신원 풀과 제공자
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub" --project=$P

#    attribute-condition 이 이 설정 전체에서 가장 중요한 줄이다.
#    이것이 빠지면 아무 GitHub 저장소나 우리 계정으로 들어온다.
gcloud iam workload-identity-pools providers create-oidc janggi-events \
  --location=global --workload-identity-pool=github --project=$P \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '$GH'"

# 4. 그 저장소에서 온 것만 이 계정을 쓸 수 있게 묶는다
gcloud iam service-accounts add-iam-policy-binding $SA \
  --role=roles/iam.workloadIdentityUser --project=$P \
  --member="principalSet://iam.googleapis.com/projects/$NUM/locations/global/workloadIdentityPools/github/attribute.repository/$GH"
```

끌어온 PR(fork)에서는 배포가 돌지 않는다. 울타리가 둘이다 - 워크플로의 조건이
`push` + `main` 만 받고, 위의 `attribute-condition` 이 저장소를 묶는다.

## 되돌리기

자동 배포가 깨진 것을 올렸을 때.

```bash
gcloud run revisions list --service=janggi-events --region=asia-northeast1

gcloud run services update-traffic janggi-events \
  --region=asia-northeast1 --to-revisions=<옛-리비전-이름>=100
```

**2026-10-10 에 자동 배포가 처음 돌아 2판이 생겼다.** 그 전에는 리비전이 1개뿐이어서
되돌릴 자리가 없었다. 이제는 위 명령으로 바로 돌아간다.

트래픽을 되돌려도 **이미지는 지우지 않는다.** 되돌린 채로 5번 더 푸시하면 쓰고 있는 옛
이미지가 청소 정책에 지워지고, 그러면 쉬다 깨어날 때 받을 것이 없어진다. 되돌렸으면
**원인을 고쳐 다시 올리는 것까지** 해야 끝이다.

## 아직 안 한 것

- 내 권한을 배포용 계정 수준으로 좁히기 (자동 배포가 돈 다음)
- `EventService` 가 실제로 DB 에 적기 (표만 만들어 둔 상태다)
