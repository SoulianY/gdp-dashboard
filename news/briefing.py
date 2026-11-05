"""브리핑 실행: python -m news.briefing {news|politics}

news     : 정치 제외 카테고리. 직전 브리핑에 없던 기사가 있을 때만 알림.
politics : 정치면. 하루 한 번 실행하며 매번 알림.
"""

import json
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from news import notify
from news.collect import collect
from news.sources import FEEDS, POLITICS, POLITICS_FEEDS

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "news"
KST = ZoneInfo("Asia/Seoul")


def snapshot_path(kind: str) -> Path:
    return DATA_DIR / f"{kind}.json"


def load(kind: str) -> dict | None:
    path = snapshot_path(kind)
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def _links(items: list[dict]) -> set[str]:
    return {a["link"] for item in items for a in item["articles"]}


def run(kind: str) -> None:
    feeds = {POLITICS: POLITICS_FEEDS} if kind == "politics" else FEEDS
    items = collect(feeds)
    now = datetime.now(KST)

    if kind == "politics":
        new = items
        heading = f"🏛 {now:%m/%d} 정치 브리핑 — {len(items)}건"
    else:
        previous = load(kind)
        seen = _links(previous["items"]) if previous else set()
        new = [item for item in items if not {a["link"] for a in item["articles"]} & seen]
        heading = f"🗞 {now:%m/%d %H:%M} 브리핑 — 새 소식 {len(new)}건"

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    snapshot_path(kind).write_text(
        json.dumps({"generated_at": now.isoformat(), "items": items}, ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    print(f"{kind}: {len(items)}건 저장, 새 소식 {len(new)}건")
    if new:
        notify.send(heading, new)


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("news", "politics"):
        sys.exit(__doc__)
    run(sys.argv[1])
