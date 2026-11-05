"""RSS 수집 → 24시간 필터 → 같은 사건끼리 묶기 → 이슈 순 정렬."""

import calendar
import re
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from functools import lru_cache

import feedparser
import requests
from bs4 import BeautifulSoup

USER_AGENT = "Mozilla/5.0 (compatible; news-briefing/1.0)"
WINDOW = timedelta(hours=24)
SIMILARITY = 0.35  # 제목 바이그램 자카드 유사도 기준
SUMMARY_LEN = 200

_session = requests.Session()
_session.headers["User-Agent"] = USER_AGENT


def _get(url: str) -> requests.Response:
    resp = _session.get(url, timeout=15)
    resp.raise_for_status()
    return resp


def _clean_text(html: str) -> str:
    text = BeautifulSoup(html or "", "html.parser").get_text(" ", strip=True)
    text = re.sub(r"\s+", " ", text)
    return text[:SUMMARY_LEN] + ("…" if len(text) > SUMMARY_LEN else "")


def _summary(html: str) -> str:
    """피드 요약. 연합뉴스 속보처럼 '(' 한 글자만 오는 경우는 요약이 없는 것으로 본다."""
    text = _clean_text(html)
    return text if len(text) >= 10 else ""


def _entry_image(entry) -> str | None:
    """피드 항목의 사진 URL. 동영상 미디어(연합뉴스의 유튜브 임베드 등)와 깨진 src는 건너뛴다."""
    candidates = [m.get("url") for m in entry.get("media_content", []) + entry.get("media_thumbnail", [])
                  if m.get("medium", "image") == "image" and not m.get("type", "image").startswith("video")]
    candidates += [e.get("href") for e in entry.get("enclosures", []) if e.get("type", "").startswith("image")]
    for content in [entry.get("summary", "")] + [c.get("value", "") for c in entry.get("content", [])]:
        candidates += [img["src"] for img in BeautifulSoup(content, "html.parser").find_all("img", src=True)]
    return next((url for url in candidates if url and url.startswith("http")), None)


@lru_cache(maxsize=None)
def _page_meta(link: str) -> dict:
    """기사 페이지의 Open Graph·article 메타태그 (피드에 없는 날짜·사진·요약 보충용)."""
    try:
        soup = BeautifulSoup(_get(link).text, "html.parser")
    except requests.RequestException:
        return {}
    def meta(key: str) -> str | None:
        tag = soup.find("meta", property=key) or soup.find("meta", attrs={"name": key})
        return tag.get("content") if tag else None
    return {"published": meta("article:published_time"), "image": meta("og:image"),
            "description": meta("og:description")}


def _published(entry) -> datetime | None:
    parsed = entry.get("published_parsed") or entry.get("updated_parsed")
    if parsed:
        return datetime.fromtimestamp(calendar.timegm(parsed), timezone.utc)
    value = _page_meta(entry["link"]).get("published")  # 한겨레 RSS는 날짜가 없다
    return datetime.fromisoformat(value) if value else None


def fetch_feed(category: str, source: str, url: str, since: datetime) -> list[dict]:
    try:
        feed = feedparser.parse(_get(url).content)
    except requests.RequestException as exc:
        print(f"[skip] {source} {category}: {exc}", file=sys.stderr)
        return []
    articles = []
    for entry in feed.entries:
        if not entry.get("link"):
            continue
        published = _published(entry)
        if not published or published < since:
            continue
        articles.append({
            "title": _clean_text(entry.get("title", "")),
            "link": entry["link"],
            "source": source,
            "category": category,
            "published": published.isoformat(),
            "summary": _summary(entry.get("summary", "")),
            "image": _entry_image(entry),
        })
    return articles


def _bigrams(title: str) -> set[str]:
    title = re.sub(r"\[[^\]]*\]|\([^)]*\)|<[^>]*>", " ", title)  # [속보], (종합) 등 말머리 제거
    words = re.findall(r"\w+", title)
    return {w[i:i + 2] for w in words for i in range(max(len(w) - 1, 1))}


def cluster(articles: list[dict]) -> list[dict]:
    """제목이 비슷한 기사끼리 묶고, 보도한 언론사 수가 많은 순으로 정렬한다."""
    groups: list[tuple[set[str], list[dict]]] = []
    for article in sorted(articles, key=lambda a: a["published"]):
        grams = _bigrams(article["title"])
        for rep, members in groups:
            union = grams | rep
            if union and len(grams & rep) / len(union) >= SIMILARITY:
                members.append(article)
                break
        else:
            groups.append((grams, [article]))

    clusters = []
    for _, members in groups:
        lead = max(members, key=lambda a: (bool(a["image"]), len(a["summary"])))
        clusters.append({
            **lead,
            "category": Counter(a["category"] for a in members).most_common(1)[0][0],
            "outlets": len({a["source"] for a in members}),
            "latest": max(a["published"] for a in members),
            "articles": [{k: a[k] for k in ("title", "link", "source", "summary")} for a in members],
        })
    clusters.sort(key=lambda c: (c["outlets"], len(c["articles"]), c["latest"]), reverse=True)
    return clusters


def _fill_from_page(item: dict) -> None:
    meta = _page_meta(item["link"])
    item["image"] = item["image"] or meta.get("image")
    item["summary"] = item["summary"] or _clean_text(meta.get("description") or "")


def _fetch_all(pool: ThreadPoolExecutor, feeds: dict[str, list[tuple[str, str]]], since: datetime) -> list[dict]:
    """모든 피드를 받아 링크 기준으로 중복 제거 (연합뉴스는 한 기사를 여러 카테고리에 싣는다)."""
    jobs = [(cat, src, url) for cat, pairs in feeds.items() for src, url in pairs]
    unique: dict[str, dict] = {}
    for batch in pool.map(lambda j: fetch_feed(*j, since), jobs):
        for article in batch:
            unique.setdefault(article["link"], article)
    return list(unique.values())


def collect(feeds: dict[str, list[tuple[str, str]]], exclude: dict[str, list[tuple[str, str]]] | None = None) -> list[dict]:
    """exclude 피드에 실린 기사는 뺀다 (일반 브리핑에서 정치 기사 제외)."""
    since = datetime.now(timezone.utc) - WINDOW
    with ThreadPoolExecutor(max_workers=8) as pool:
        articles = _fetch_all(pool, feeds, since)
        if exclude:
            excluded = {a["link"] for a in _fetch_all(pool, exclude, since)}
            articles = [a for a in articles if a["link"] not in excluded]
        clusters = cluster(articles)
        list(pool.map(_fill_from_page, [c for c in clusters if not (c["image"] and c["summary"])]))
    return clusters
