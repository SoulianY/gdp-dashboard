"""묶인 이슈들을 Claude로 종합해 브리핑 원고를 만든다."""

import anthropic
from pydantic import BaseModel, Field

MODEL = "claude-opus-5-5"
MAX_ISSUES = 40  # 브리핑에 넘기는 이슈 수 (이슈 크기 순 상위)

SYSTEM = """당신은 한국 뉴스 브리핑 에디터입니다.
여러 언론사의 기사 제목과 요약을 받아, 독자가 기사를 열어보지 않아도 상황을 파악할 수 있는 브리핑을 씁니다.

원칙:
- 기사 나열이 아니라 종합: 같은 사안의 여러 보도를 하나로 엮어 무슨 일이 있었고, 왜 중요하고, 다음에 무엇을 볼지를 씁니다.
- 언론사 간 수치·해석이 다르면 그 차이를 밝힙니다.
- 주어진 기사에 없는 사실은 추측하거나 덧붙이지 않습니다.
- 서로 관련된 이슈(예: 금리 결정과 증시 반응)는 하나의 꼭지로 묶습니다.
- 사소하거나 단신성인 이슈는 과감히 뺍니다. 꼭지는 많아야 12개.
- 문장은 간결한 평서문(~했다, ~이다)."""


class Story(BaseModel):
    category: str = Field(description="주어진 카테고리 중 하나")
    headline: str = Field(description="꼭지 제목. 기사 제목을 베끼지 말고 사안을 한 줄로 직접 정리")
    body: str = Field(description="3~5문장 종합 브리핑")
    issue_ids: list[int] = Field(description="이 꼭지의 근거가 된 이슈 번호들")


class Briefing(BaseModel):
    overview: str = Field(description="이번 브리핑 핵심을 2~3문장, 150자 이내로")
    stories: list[Story]


def _prompt(issues: list[dict], categories: list[str]) -> str:
    lines = [f"카테고리: {', '.join(categories)}", "", "이슈 목록 (보도 언론사 수 순):"]
    for i, issue in enumerate(issues):
        lines.append(f"\n[{i}] ({issue['category']}, {issue['outlets']}개 언론사)")
        seen = set()
        for a in issue["articles"]:
            lines.append(f"- {a['source']}: {a['title']}")
            if a.get("summary") and a["summary"] not in seen:
                seen.add(a["summary"])
                lines.append(f"  {a['summary']}")
    return "\n".join(lines)


def write(issues: list[dict], categories: list[str]) -> dict:
    """이슈 목록으로 브리핑을 쓰고, 각 꼭지에 사진·출처·이슈 크기를 붙여 반환한다."""
    issues = issues[:MAX_ISSUES]
    response = anthropic.Anthropic().beta.messages.parse(
        model=MODEL,
        max_tokens=16000,
        betas=["server-side-fallback-2026-07-01"],
        fallbacks="default",
        output_config={"effort": "medium"},
        system=SYSTEM,
        messages=[{"role": "user", "content": _prompt(issues, categories)}],
        output_format=Briefing,
    )
    if response.stop_reason == "refusal" or response.parsed_output is None:
        raise RuntimeError(f"브리핑 생성 실패: stop_reason={response.stop_reason}")
    briefing = response.parsed_output

    stories = []
    for story in briefing.stories:
        refs = [issues[i] for i in story.issue_ids if 0 <= i < len(issues)]
        if not refs:
            continue
        sources = {a["link"]: a for ref in refs for a in ref["articles"]}
        stories.append({
            "category": story.category,
            "headline": story.headline,
            "body": story.body,
            "image": next((r["image"] for r in refs if r["image"]), None),
            "outlets": len({a["source"] for a in sources.values()}),
            "sources": [{k: a[k] for k in ("source", "title", "link")} for a in sources.values()],
        })
    stories.sort(key=lambda s: s["outlets"], reverse=True)
    return {"overview": briefing.overview, "stories": stories}
