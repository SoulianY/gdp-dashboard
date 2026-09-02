"""디시인사이드 인기급상승 게시물을 텔레그램으로 보낸다."""

from __future__ import annotations

import argparse
import os
import sys

from . import message, telegram
from .scraper import fetch_trending


def main() -> int:
    parser = argparse.ArgumentParser(prog="dcinside_bot", description=__doc__)
    parser.add_argument("--limit", type=int, default=10, help="가져올 게시물 수 (기본 10)")
    parser.add_argument("--dry-run", action="store_true", help="전송하지 않고 본문만 출력")
    args = parser.parse_args()

    text = message.render(fetch_trending(args.limit))

    if args.dry_run:
        print(text)
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat_id:
        print("TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID 환경변수가 필요합니다.", file=sys.stderr)
        return 1

    telegram.send_message(token, chat_id, text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
