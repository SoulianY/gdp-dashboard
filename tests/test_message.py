from datetime import datetime

from dcinside_bot.message import render
from dcinside_bot.scraper import Post


def _post(**kw):
    base = dict(rank=1, title="제목", url="https://gall.dcinside.com/board/view/?id=dcbest&no=1",
                gallery="주식", comments=1, views=2, recommends=3)
    return Post(**{**base, **kw})


def test_renders_rank_link_and_meta():
    text = render([_post()], now=datetime(2026, 9, 2))
    assert "2026-09-02" in text
    assert '1. <a href="https://gall.dcinside.com/board/view/?id=dcbest&amp;no=1">제목</a>' in text
    assert "주식 · 👁 2 · 👍 3 · 💬 1" in text


def test_escapes_html_in_title():
    text = render([_post(title="<b>낚시</b>")], now=datetime(2026, 9, 2))
    assert "&lt;b&gt;낚시&lt;/b&gt;" in text


def test_empty_result_is_reported():
    assert "가져온 게시물이 없습니다" in render([], now=datetime(2026, 9, 2))
