"""RSS 수집 → 24시간 필터 → 같은 사건끼리 묶기 → 이슈 순 정렬."""

import calendar
import re
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

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


def _entry_image(entry) -> str | None:
    for key in ("media_content", "media_thumbnail"):
        for media in entry.get(key, []):
            if media.get("url"):
                return media["url"]
    for link in entry.get("links", []):
        if link.get("rel") == "enclosure" and link.get("type", "").startswith("image"):
            return link["href"]
    for content in [entry.get("summary", "")] + [c.get("value", "") for c in entry.get("content", [])]:
        img = BeautifulSoup(content, "html.parser").find("img", src=True)
        if img:
            return img["src"]
    return None


def fetch_feed(category: str, source: str, url: str, since: datetime) -> list[dict]:
    try:
        feed = feedparser.parse(_get(url).content)
    except requests.RequestException as exc:
        print(f"[skip] {source} {category}: {exc}", file=sys.stderr)
        return []
    articles = []
    for entry in feed.entries:
        parsed = entry.get("published_parsed") or entry.get("updated_parsed")
        if not parsed or not entry.get("link"):
            continue
        published = datetime.fromtimestamp(calendar.timegm(parsed), timezone.utc)
        if published < since:
            continue
        articles.append({
            "title": _clean_text(entry.get("title", "")),
            "link": entry["link"],
            "source": source,
            "category": category,
            "published": published.isoformat(),
            "summary": _clean_text(entry.get("summary", "")),
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
            "articles": [{k: a[k] for k in ("title", "link", "source")} for a in members],
        })
    clusters.sort(key=lambda c: (c["outlets"], len(c["articles"]), c["latest"]), reverse=True)
    return clusters


def _fill_from_page(item: dict) -> None:
    """피드에 사진·요약이 없으면 기사 페이지의 Open Graph 메타태그로 채운다."""
    try:
        soup = BeautifulSoup(_get(item["link"]).text, "html.parser")
    except requests.RequestException:
        return
    def og(prop: str) -> str | None:
        tag = soup.find("meta", property=f"og:{prop}")
        return tag.get("content") if tag else None
    item["image"] = item["image"] or og("image")
    item["summary"] = item["summary"] or _clean_text(og("description") or "")


def collect(feeds: dict[str, list[tuple[str, str]]]) -> list[dict]:
    since = datetime.now(timezone.utc) - WINDOW
    jobs = [(cat, src, url) for cat, pairs in feeds.items() for src, url in pairs]
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = pool.map(lambda j: fetch_feed(*j, since), jobs)
        clusters = cluster([a for batch in results for a in batch])
        list(pool.map(_fill_from_page, [c for c in clusters if not (c["image"] and c["summary"])]))
    return clusters
