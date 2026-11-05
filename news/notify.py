"""텔레그램 봇으로 브리핑 알림을 보낸다."""

import os
import sys
from html import escape

import requests

MAX_ITEMS = 10


def send(heading: str, items: list[dict]) -> None:
    token, chat_id = os.environ.get("TELEGRAM_BOT_TOKEN"), os.environ.get("TELEGRAM_CHAT_ID")
    if not (token and chat_id):
        print("[notify] TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID 미설정 — 알림 생략", file=sys.stderr)
        return
    lines = [f"<b>{escape(heading)}</b>", ""]
    for item in items[:MAX_ITEMS]:
        more = f" 외 {item['outlets'] - 1}곳" if item["outlets"] > 1 else ""
        lines.append(
            f"• [{escape(item['category'])}] <a href=\"{escape(item['link'])}\">{escape(item['title'])}</a>"
            f" — {escape(item['source'])}{more}"
        )
    if len(items) > MAX_ITEMS:
        lines.append(f"…외 {len(items) - MAX_ITEMS}건")
    requests.post(
        f"https://api.telegram.org/bot{token}/sendMessage",
        json={"chat_id": chat_id, "text": "\n".join(lines), "parse_mode": "HTML",
              "disable_web_page_preview": True},
        timeout=15,
    ).raise_for_status()
