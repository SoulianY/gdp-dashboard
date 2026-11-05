"""카카오톡 '나에게 보내기'로 브리핑 알림을 보낸다.

리프레시 토큰은 유효기간 2개월이고, 만료 1개월 전부터는 갱신 요청 시 새 토큰이 함께 온다.
새 토큰은 KAKAO_REFRESH_TOKEN_FILE 경로에 저장하고, 워크플로가 이를 저장소 Secret에 다시 써 넣는다.
"""

import json
import os
import sys

import requests

TOKEN_URL = "https://kauth.kakao.com/oauth/token"
SEND_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send"
LIST_MAX = 3  # 리스트 템플릿이 담을 수 있는 최대 항목 수


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


def _link(url: str) -> dict:
    return {"web_url": url, "mobile_web_url": url}


def _description(item: dict) -> str:
    more = f" 외 {item['outlets'] - 1}곳" if item["outlets"] > 1 else ""
    return f"[{item['category']}] {item['source']}{more}"


def _template(heading: str, items: list[dict]) -> dict:
    if len(items) == 1:  # 리스트 템플릿은 2건 이상이어야 해서 1건이면 피드 템플릿
        item = items[0]
        return {
            "object_type": "feed",
            "content": {"title": f"{heading}\n{item['title']}", "description": _description(item),
                        "image_url": item["image"] or "", "link": _link(item["link"])},
        }
    return {
        "object_type": "list",
        "header_title": heading,
        "header_link": _link(items[0]["link"]),
        "contents": [
            {"title": item["title"], "description": _description(item),
             "image_url": item["image"] or "", "link": _link(item["link"])}
            for item in items[:LIST_MAX]
        ],
    }


def send(heading: str, items: list[dict]) -> None:
    if not (os.environ.get("KAKAO_REST_API_KEY") and os.environ.get("KAKAO_REFRESH_TOKEN")):
        print("[notify] KAKAO_REST_API_KEY / KAKAO_REFRESH_TOKEN 미설정 — 알림 생략", file=sys.stderr)
        return
    resp = requests.post(
        SEND_URL,
        headers={"Authorization": f"Bearer {_access_token()}"},
        data={"template_object": json.dumps(_template(heading, items), ensure_ascii=False)},
        timeout=15,
    )
    resp.raise_for_status()
