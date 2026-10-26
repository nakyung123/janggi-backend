"""프론트가 보내는 측정 이벤트 이름을 이 서버가 모두 받는지 본다.

EventName.java 는 모르는 이름을 400 으로 거절하고, 프론트는 sendBeacon 이라 답을 읽지
않는다. 그래서 프론트가 아직 보내는 이름을 여기서 빼면 오류 없이 기록만 버려진다. 두
목록이 다른 저장소에 있어 손으로 맞춰 왔다.

    python3 scripts/check-event-names.py           프론트 main 의 events.ts 를 GitHub 에서 받아 비교
    python3 scripts/check-event-names.py <경로>     내 컴퓨터의 events.ts 와 비교

서버에만 있는 이름은 실패로 보지 않는다(서버를 먼저 배포하는 순서가 그 모양이다).
"""

import io
import os
import re
import sys
import urllib.request

FRONT_URL = (
    "https://raw.githubusercontent.com/nakyung123/janggi-frontend/main/src/report/events.ts"
)
HERE = os.path.dirname(os.path.abspath(__file__))
SERVER_FILE = os.path.join(
    HERE, "..", "src", "main", "java", "com", "nakyung", "janggi", "event", "EventName.java"
)


def front_names(ts):
    m = re.search(r"type EventName =([^;]*);", ts)
    return re.findall(r'"([^"]+)"', m.group(1)) if m else []


def server_names(java):
    # APP_OPEN("app_open"), 처럼 생긴 줄에서 따옴표 안의 글자만 뽑는다.
    return re.findall(r'^\s*[A-Z][A-Z0-9_]*\("([^"]+)"\)', java, re.MULTILINE)


def front_source(path):
    if path:
        return io.open(path, encoding="utf-8").read()
    with urllib.request.urlopen(FRONT_URL, timeout=30) as res:
        return res.read().decode("utf-8")


def main():
    front = front_names(front_source(sys.argv[1] if len(sys.argv) > 1 else None))
    server = server_names(io.open(SERVER_FILE, encoding="utf-8").read())

    # 하나라도 0개면 읽는 부분이 깨진 것이다. 비교할 것이 없는데 통과시키지 않는다.
    if not front or not server:
        print(f"이름을 읽지 못했다: 프론트 {len(front)}개, 서버 {len(server)}개", file=sys.stderr)
        return 1

    missing = [n for n in front if n not in server]
    server_only = [n for n in server if n not in front]
    print(f"프론트 {len(front)}개, 서버 {len(server)}개")
    if server_only:
        print("서버에만 있는 이름(문제 아님): " + ", ".join(server_only))
    if missing:
        print("프론트가 보내는데 서버가 모르는 이름: " + ", ".join(missing), file=sys.stderr)
        print(
            "서버에서 뺐다면 되돌린다. 프론트가 아직 보내는 이름을 빼면 버려진다. "
            "프론트가 먼저 더했다면 EventName.java 에 더해 배포한다.",
            file=sys.stderr,
        )
        return 1
    print("프론트가 보내는 이름은 모두 서버 목록에 있다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
