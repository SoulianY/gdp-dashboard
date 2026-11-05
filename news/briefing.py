"""브리핑 두 단계 CLI. 원고는 예약 작업으로 실행되는 Claude Code 세션이 GUIDE.md를 따라 직접 쓴다.

  python -m news.briefing prepare {news|politics}
      기사를 수집·묶어 data/news/<kind>.issues.json 에 저장.
      news: 직전 브리핑 이후 새 기사가 실린 이슈만. 없으면 "NO_NEW_ISSUES" 출력 후 종료.
      politics: 최근 24시간 정치 이슈 전체.

  python -m news.briefing publish {news|politics}
      data/news/<kind>.draft.json (세션이 쓴 원고)을 검증하고 사진·출처를 붙여
      data/news/<kind>.json 으로 저장. 이 파일이 main에 푸시되면 카톡 알림 워크플로가 돈다.
"""

import json
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from news.collect import collect
from news.sources import FEEDS, POLITICS, POLITICS_FEEDS

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "news"
KST = ZoneInfo("Asia/Seoul")
MAX_ISSUES = 40  # 원고 작성에 넘기는 이슈 수 (이슈 크기 순 상위)


def _path(kind: str, suffix: str = "") -> Path:
    return DATA_DIR / f"{kind}{suffix}.json"


def _read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _write(path: Path, data: dict) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def load(kind: str) -> dict | None:
    path = _path(kind)
    return _read(path) if path.exists() else None


def categories(kind: str) -> list[str]:
    return [POLITICS] if kind == "politics" else list(FEEDS)


def prepare(kind: str) -> None:
    politics = {POLITICS: POLITICS_FEEDS}
    if kind == "politics":
        issues = collect(politics)
        fresh = issues
    else:
        issues = collect(FEEDS, exclude=politics)
        previous = load(kind)
        seen = set(previous["links"]) if previous else set()
        fresh = [i for i in issues if any(a["link"] not in seen for a in i["articles"])]

    print(f"{kind}: 이슈 {len(issues)}건, 브리핑 대상 {len(fresh)}건")
    if not fresh:
        print("NO_NEW_ISSUES")
        return
    _write(_path(kind, ".issues"), {
        "categories": categories(kind),
        "issues": [{"id": n, **issue} for n, issue in enumerate(fresh[:MAX_ISSUES])],
        "links": sorted({a["link"] for i in issues for a in i["articles"]}),
    })


def publish(kind: str) -> None:
    prepared = _read(_path(kind, ".issues"))
    draft = _read(_path(kind, ".draft"))
    issues = {i["id"]: i for i in prepared["issues"]}

    stories = []
    for story in draft["stories"]:
        if story["category"] not in prepared["categories"]:
            sys.exit(f"알 수 없는 카테고리: {story['category']}")
        refs = [issues[n] for n in story["issue_ids"] if n in issues]
        if not refs:
            sys.exit(f"근거 이슈가 없는 꼭지: {story['headline']}")
        sources = {a["link"]: a for ref in refs for a in ref["articles"]}
        stories.append({
            "category": story["category"],
            "headline": story["headline"],
            "body": story["body"],
            "image": next((r["image"] for r in refs if r["image"]), None),
            "outlets": len({a["source"] for a in sources.values()}),
            "sources": [{k: a[k] for k in ("source", "title", "link")} for a in sources.values()],
        })
    stories.sort(key=lambda s: s["outlets"], reverse=True)

    _write(_path(kind), {
        "generated_at": datetime.now(KST).isoformat(),
        "overview": draft["overview"],
        "stories": stories,
        "links": prepared["links"],
    })
    print(f"{kind}: 꼭지 {len(stories)}개 저장 → {_path(kind)}")


if __name__ == "__main__":
    commands = {"prepare": prepare, "publish": publish}
    if len(sys.argv) != 3 or sys.argv[1] not in commands or sys.argv[2] not in ("news", "politics"):
        sys.exit(__doc__)
    commands[sys.argv[1]](sys.argv[2])
