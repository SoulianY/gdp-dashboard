"""브리핑 실행: python -m news.briefing {news|politics}

news     : 정치 제외 카테고리. 직전 브리핑 이후 새 기사가 실린 이슈만 모아 브리핑하고,
           새 기사가 없으면 브리핑·알림 모두 생략.
politics : 정치면. 하루 한 번, 최근 24시간 이슈 전체로 브리핑.
"""

import json
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from news import brief, notify
from news.collect import collect
from news.sources import FEEDS, POLITICS, POLITICS_FEEDS

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "news"
KST = ZoneInfo("Asia/Seoul")


def snapshot_path(kind: str) -> Path:
    return DATA_DIR / f"{kind}.json"


def load(kind: str) -> dict | None:
    path = snapshot_path(kind)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def run(kind: str) -> None:
    politics = {POLITICS: POLITICS_FEEDS}
    if kind == "politics":
        issues = collect(politics)
        fresh, categories = issues, [POLITICS]
    else:
        issues = collect(FEEDS, exclude=politics)
        previous = load(kind)
        seen = set(previous["links"]) if previous else set()
        fresh = [i for i in issues if any(a["link"] not in seen for a in i["articles"])]
        categories = list(FEEDS)

    links = sorted({a["link"] for i in issues for a in i["articles"]})
    print(f"{kind}: 이슈 {len(issues)}건, 새 기사가 있는 이슈 {len(fresh)}건")
    if not fresh:
        return

    now = datetime.now(KST)
    snapshot = {"generated_at": now.isoformat(), **brief.write(fresh, categories), "links": links}
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    snapshot_path(kind).write_text(json.dumps(snapshot, ensure_ascii=False, indent=1), encoding="utf-8")

    label = f"🏛 {now:%m/%d} 정치 브리핑" if kind == "politics" else f"🗞 {now:%m/%d %H:%M} 뉴스 브리핑"
    notify.send(label, snapshot)


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("news", "politics"):
        sys.exit(__doc__)
    run(sys.argv[1])
