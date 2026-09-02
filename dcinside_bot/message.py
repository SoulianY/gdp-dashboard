"""수집한 게시물을 텔레그램 메시지로 변환."""

from __future__ import annotations

from datetime import datetime
from html import escape
from zoneinfo import ZoneInfo

from .scraper import TRENDING_URL, Post

KST = ZoneInfo("Asia/Seoul")


def render(posts: list[Post], now: datetime | None = None) -> str:
    """텔레그램 parse_mode=HTML 로 보낼 본문을 만든다."""
    now = now or datetime.now(KST)
    lines = [f"<b>🔥 디시인사이드 인기급상승 {now:%Y-%m-%d} 아침</b>", ""]

    if not posts:
        lines.append("가져온 게시물이 없습니다. 목록 구조가 바뀌었는지 확인하세요.")
    for post in posts:
        title = escape(post.title)
        meta = [f"👁 {post.views:,}", f"👍 {post.recommends:,}", f"💬 {post.comments:,}"]
        if post.gallery:
            meta.insert(0, escape(post.gallery))
        lines.append(f'{post.rank}. <a href="{escape(post.url, quote=True)}">{title}</a>')
        lines.append(f"   {' · '.join(meta)}")

    lines += ["", f'<a href="{TRENDING_URL}">실시간 베스트 전체 보기</a>']
    return "\n".join(lines)
