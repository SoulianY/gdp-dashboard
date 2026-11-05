# 🗞 뉴스 브리핑

국내 주요 언론사 RSS에서 최근 24시간 뉴스를 모아, 같은 사건을 다룬 기사끼리 묶고
보도한 언론사 수(이슈 크기) 순으로 보여주는 Streamlit 앱입니다.

- **일반 브리핑**: 경제·사회·국제·IT·과학·문화·스포츠 (정치 제외). KST 09·12·15·18·21시 갱신.
  직전 브리핑 이후 새 기사가 있을 때만 텔레그램 알림.
- **정치 브리핑**: 매일 KST 08:30 별도 생성 및 알림.

## 구성

| 경로 | 역할 |
| --- | --- |
| `news/sources.py` | 카테고리별 RSS 피드 목록 |
| `news/collect.py` | 수집 · 24시간 필터 · 유사 제목 묶기 · 이슈 순 정렬 · 사진/요약 보강(og 태그) |
| `news/briefing.py` | 스냅샷(`data/news/*.json`) 저장 및 새 기사 판별 |
| `news/notify.py` | 텔레그램 알림 |
| `.github/workflows/news-briefing.yml` | 스케줄 실행 후 스냅샷 커밋 |
| `streamlit_app.py` | 화면 |

## 설정

저장소 **Settings → Secrets and variables → Actions**에 추가:

- `TELEGRAM_BOT_TOKEN` — @BotFather에서 발급한 봇 토큰
- `TELEGRAM_CHAT_ID` — 알림 받을 채팅 ID

GitHub Actions 탭에서 `news-briefing`을 수동 실행(workflow_dispatch)하면 바로 첫 스냅샷이 생성됩니다.

## 로컬 실행

```
pip install -r requirements.txt
python -m news.briefing news       # 또는 politics
streamlit run streamlit_app.py
```
