"""디시인사이드 실시간 베스트(인기급상승) 게시물 수집."""

from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

BASE_URL = "https://gall.dcinside.com"
# dcbest = 디시인사이드 실시간 베스트 갤러리(인기급상승 게시물 목록)
TRENDING_URL = f"{BASE_URL}/board/lists/?id=dcbest"

# 브라우저 UA가 아니면 dcinside가 403을 돌려준다.
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "ko-KR,ko;q=0.9",
    "Referer": BASE_URL,
}


@dataclass(frozen=True)
class Post:
    rank: int
    title: str
    url: str
    gallery: str | None
    comments: int
    views: int
    recommends: int


def fetch_trending(limit: int = 10) -> list[Post]:
    """실시간 베스트 상위 `limit`개를 가져온다."""
    with requests.Session() as session:
        session.headers.update(HEADERS)
        retry = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=("GET",),
        )
        session.mount("https://", HTTPAdapter(max_retries=retry))
        response = session.get(TRENDING_URL, timeout=10)
        response.raise_for_status()
        response.encoding = "utf-8"
        return parse_trending(response.text, limit)


def parse_trending(html: str, limit: int = 10) -> list[Post]:
    """갤러리 목록 HTML에서 게시물을 뽑는다. 공지·광고 행은 건너뛴다."""
    soup = BeautifulSoup(html, "html.parser")
    posts: list[Post] = []

    for row in soup.select("tr.ub-content"):
        link = row.select_one("td.gall_tit a[href]")
        if link is None or "/board/view/" not in link["href"]:
            continue
        if row.select_one("td.gall_num") and not _is_post_number(row):
            continue

        subject = row.select_one("td.gall_subject")
        posts.append(
            Post(
                rank=len(posts) + 1,
                title=link.get_text(strip=True),
                url=urljoin(BASE_URL, link["href"]),
                gallery=subject.get_text(strip=True) if subject else None,
                comments=_to_int(_text(row.select_one("a.reply_numbox .reply_num"))),
                views=_to_int(_text(row.select_one("td.gall_count"))),
                recommends=_to_int(_text(row.select_one("td.gall_recommend"))),
            )
        )
        if len(posts) == limit:
            break

    return posts


def _is_post_number(row) -> bool:
    """공지(`공지`)·설문 등 번호가 숫자가 아닌 행을 걸러낸다."""
    return _text(row.select_one("td.gall_num")).replace(",", "").isdigit()


def _text(node) -> str:
    return node.get_text(strip=True) if node is not None else ""


def _to_int(text: str) -> int:
    digits = "".join(ch for ch in text if ch.isdigit())
    return int(digits) if digits else 0
