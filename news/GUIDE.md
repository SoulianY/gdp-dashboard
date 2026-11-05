# 브리핑 작성 가이드 (예약 작업 세션용)

예약 작업으로 깨어난 Claude Code 세션이 이 문서를 따라 브리핑을 만든다.
`<kind>`는 `news`(3시간 브리핑) 또는 `politics`(08:30 정치 브리핑).

## 절차

1. `pip install -r requirements.txt`
2. `python -m news.briefing prepare <kind>`
   - `NO_NEW_ISSUES`가 출력되면 새 기사가 없는 것이다. 아무것도 커밋하지 말고 종료한다(알림 없음).
3. `data/news/<kind>.issues.json`을 읽고 아래 원칙대로 원고를 써서 `data/news/<kind>.draft.json`으로 저장한다.
4. `python -m news.briefing publish <kind>` — 오류가 나면 원고를 고쳐 다시 실행한다.
5. `data/news/<kind>.json`만 커밋해 **main 브랜치에 직접 푸시**한다. 푸시되면 GitHub Actions가 카톡 알림을 보낸다.
   - 커밋 메시지: `briefing: <kind> <YYYY-MM-DD HH:MM KST>`
   - 푸시가 거절되면 `git pull --rebase origin main` 후 다시 푸시한다.

## 원고 형식 (`<kind>.draft.json`)

```json
{
  "overview": "이번 브리핑의 핵심 2~3문장, 150자 이내 (카톡 알림 본문으로 쓰인다)",
  "stories": [
    {
      "category": "issues.json의 categories 중 하나",
      "headline": "꼭지 제목",
      "body": "3~5문장 종합 브리핑",
      "issue_ids": [0, 3]
    }
  ]
}
```

## 작성 원칙

- 기사 나열이 아니라 종합: 같은 사안의 여러 보도를 엮어 무슨 일이 있었고, 왜 중요하고, 다음에 무엇을 볼지를 쓴다.
- 꼭지 제목은 기사 제목을 베끼지 말고 사안을 한 줄로 직접 정리한다.
- 언론사 간 수치·해석이 다르면 그 차이를 밝힌다.
- 주어진 기사에 없는 사실은 추측하거나 덧붙이지 않는다. 기사 원문을 따로 찾아 읽지 않는다.
- 서로 관련된 이슈(예: 금리 결정과 증시 반응)는 한 꼭지로 묶고 `issue_ids`에 모두 넣는다.
- 사소하거나 단신성인 이슈는 뺀다. 꼭지는 많아야 12개. 언론사 수(`outlets`)가 많은 이슈를 우선한다.
- 문장은 간결한 평서문(~했다, ~이다).
