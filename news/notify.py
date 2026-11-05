"""카카오톡 '나에게 보내기'로 브리핑 알림을 보낸다: python -m news.notify {news|politics}

리프레시 토큰은 유효기간 2개월이고, 만료 1개월 전부터는 갱신 요청 시 새 토큰이 함께 온다.
새 토큰은 KAKAO_REFRESH_TOKEN_FILE 경로에 저장하고, 워크플로가 이를 저장소 Secret에 다시 써 넣는다.
"""

import json
import os
import sys
from datetime import datetime

import requests

from news.briefing import load

TOKEN_URL = "https://kauth.kakao.com/oauth/token"
SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send"
TEXT_MAX = 200  # 텍스트 템플릿 글자 수 제한
PAGE_URL = "https://souliany.github.io/gdp-dashboard/"  # GitHub Pages 브리핑 페이지


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


def _template(kind: str, label: str, briefing: dict) -> dict:
    """핵심 요약 + '브리핑 보기' 버튼(GitHub Pages 브리핑 페이지)."""
    url = f"{PAGE_URL}#{kind}"
    text = f"{label}\n\n{briefing['overview']}"
    return {
        "object_type": "text",
        "text": text if len(text) <= TEXT_MAX else text[:TEXT_MAX - 1] + "…",
        "link": {"web_url": url, "mobile_web_url": url},
        "button_title": "브리핑 보기",
    }


def send(kind: str, label: str, briefing: dict) -> None:
    if not (os.environ.get("KAKAO_REST_API_KEY") and os.environ.get("KAKAO_REFRESH_TOKEN")):
        print("[notify] KAKAO_REST_API_KEY / KAKAO_REFRESH_TOKEN 미설정 — 알림 생략", file=sys.stderr)
        return
    resp = requests.post(
        SEND_URL,
        headers={"Authorization": f"Bearer {_access_token()}"},
        data={"template_object": json.dumps(_template(kind, label, briefing), ensure_ascii=False)},
        timeout=15,
    )
    resp.raise_for_status()


if __name__ == "__main__":
    kind = sys.argv[1]
    briefing = load(kind)
    at = datetime.fromisoformat(briefing["generated_at"])
    send(kind, f"🏛 {at:%m/%d} 정치 브리핑" if kind == "politics" else f"🗞 {at:%m/%d %H:%M} 뉴스 브리핑", briefing)
