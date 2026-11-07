"""브리핑 알림: 카카오톡 '나에게 보내기'로 보내고, 폰 알림 로그 앱이 읽을 data/notify.json에도 남긴다.
python -m news.notify {news|politics|mail|noti}

mail·noti는 Gmail·폰 알림 브리핑 예약 작업이 workflow_dispatch로 부른다. 메일·알림 내용은 공개 저장소에
두지 않으므로 카톡에는 날짜·건수(noti는 NOTI_NOTE)와 비공개 브리핑 페이지(claude.ai 아티팩트) 링크만 담는다.

리프레시 토큰은 유효기간 2개월이고, 만료 1개월 전부터는 갱신 요청 시 새 토큰이 함께 온다.
새 토큰은 KAKAO_REFRESH_TOKEN_FILE 경로에 저장하고, 워크플로가 이를 저장소 Secret에 다시 써 넣는다.
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path

import requests

from news.briefing import KST, load

TOKEN_URL = "https://kauth.kakao.com/oauth/token"
SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send"
TEXT_MAX = 200  # 텍스트 템플릿 글자 수 제한
PAGE_URL = "https://souliany.github.io/gdp-dashboard/"  # GitHub Pages 브리핑 페이지
MAIL_URL = "https://claude.ai/artifact/Xurmt7kxsXpchkdZJN4SZj"  # Gmail 브리핑 페이지 (비공개)
NOTI_URL = "https://claude.ai/artifact/TaHcUefnnadtwpgARE4fqj"  # 폰 알림 브리핑 페이지 (비공개)
NOTIFY_FILE = Path("data/notify.json")  # 알림 로그 앱이 읽는 최근 알림 (공개: 제목·요약·링크만)


def _access_token() -> str:
    data = {
        "grant_type": "refresh_token",
        "client_id": os.environ["KAKAO_REST_API_KEY"],
        "refresh_token": os.environ["KAKAO_REFRESH_TOKEN"],
    }
    if os.environ.get("KAKAO_CLIENT_SECRET"):
        data["client_secret"] = os.environ["KAKAO_CLIENT_SECRET"]
    resp = requests.post(TOKEN_URL, data=data, timeout=15)
    resp.raise_for_status()
    token = resp.json()
    if "refresh_token" in token:
        out = os.environ.get("KAKAO_REFRESH_TOKEN_FILE")
        if out:
            with open(out, "w") as f:
                f.write(token["refresh_token"])
        else:
            print("[notify] 카카오 리프레시 토큰이 갱신됨 — KAKAO_REFRESH_TOKEN을 직접 교체하세요", file=sys.stderr)
    return token["access_token"]


def message(kind: str) -> dict:
    """브리핑 종류별 알림: 제목, 본문(한 줄 요약·건수), 열 페이지."""
    now = datetime.now(KST)
    if kind == "mail":
        return {"title": f"📬 {now:%m/%d} 메일 브리핑", "body": "오늘 메일 요약과 회신 초안을 확인하세요.", "url": MAIL_URL}
    if kind == "noti":
        return {"title": "🔔 알림 브리핑", "body": os.environ.get("NOTI_NOTE") or "새 브리핑이 있습니다.", "url": NOTI_URL}
    briefing = load(kind)
    at = datetime.fromisoformat(briefing["generated_at"])
    title = f"🏛 {at:%m/%d} 정치 브리핑" if kind == "politics" else f"🗞 {at:%m/%d %H:%M} 뉴스 브리핑"
    return {"title": title, "body": briefing["overview"], "url": f"{PAGE_URL}#{kind}"}


def kakao_template(m: dict) -> dict:
    text = f"{m['title']}\n\n{m['body']}"
    return {
        "object_type": "text",
        "text": text if len(text) <= TEXT_MAX else text[:TEXT_MAX - 1] + "…",
        "link": {"web_url": m["url"], "mobile_web_url": m["url"]},
        "button_title": "브리핑 보기",
    }


def record(kind: str, m: dict) -> None:
    """폰의 알림 로그 앱이 15분마다 읽어 알림으로 띄우는 최근 알림 목록(최신 20개)."""
    items = json.loads(NOTIFY_FILE.read_text(encoding="utf-8")) if NOTIFY_FILE.exists() else []
    items.append({"id": datetime.now(KST).isoformat(timespec="microseconds"), "kind": kind, **m})
    NOTIFY_FILE.write_text(json.dumps(items[-20:], ensure_ascii=False, indent=1), encoding="utf-8")


def send(template: dict) -> None:
    if not (os.environ.get("KAKAO_REST_API_KEY") and os.environ.get("KAKAO_REFRESH_TOKEN")):
        print("[notify] KAKAO_REST_API_KEY / KAKAO_REFRESH_TOKEN 미설정 — 알림 생략", file=sys.stderr)
        return
    resp = requests.post(
        SEND_URL,
        headers={"Authorization": f"Bearer {_access_token()}"},
        data={"template_object": json.dumps(template, ensure_ascii=False)},
        timeout=15,
    )
    resp.raise_for_status()


if __name__ == "__main__":
    kind = sys.argv[1]
    m = message(kind)
    record(kind, m)  # 카톡이 실패해도 앱 알림은 가게 먼저 남긴다
    send(kakao_template(m))
