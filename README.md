# 🗞 뉴스 브리핑

국내 주요 언론사 RSS에서 최근 24시간 뉴스를 모아 같은 사건끼리 묶은 뒤,
Claude가 여러 보도를 종합해 꼭지별 브리핑 원고를 쓰는 Streamlit 앱입니다.

- **뉴스 브리핑**: 경제·사회·국제·IT·과학·문화·스포츠 (정치 제외). KST 09·12·15·18·21시.
  직전 브리핑 이후 새 기사가 실린 이슈만 모아 브리핑하고, 새 기사가 없으면 브리핑·알림 모두 생략.
- **정치 브리핑**: 매일 KST 08:30, 최근 24시간 정치 이슈 전체로 브리핑.
- 카카오톡으로 핵심 요약과 '브리핑 보기' 버튼 전송.

## 구성

| 경로 | 역할 |
| --- | --- |
| `news/sources.py` | 카테고리별 RSS 피드 목록 |
| `news/collect.py` | 수집 · 24시간 필터 · 유사 제목 묶기 · 이슈 순 정렬 · 사진/요약 보강(og 태그) |
| `news/brief.py` | Claude로 브리핑 원고 작성 (꼭지 제목·본문·근거 이슈) |
| `news/briefing.py` | 실행 진입점: 새 기사 판별 → 브리핑 → 저장(`data/news/*.json`) → 알림 |
| `news/notify.py` | 카카오톡 '나에게 보내기' 알림 |
| `.github/workflows/news-briefing.yml` | 스케줄 실행 후 스냅샷 커밋 |
| `streamlit_app.py` | 화면 |

## 설정

### Claude API

[Claude Console](https://platform.claude.com)에서 API 키를 발급해 저장소 Secret `ANTHROPIC_API_KEY`로 등록합니다.
브리핑 1회에 약 1.5만 토큰 입력 · 수천 토큰 출력(Claude Opus 5.5 기준 회당 약 $0.1~0.2)이 듭니다.

### 대시보드 주소

Streamlit Community Cloud 등에 앱을 배포했다면 저장소 **Settings → Secrets and variables → Actions → Variables**에
`DASHBOARD_URL`(예: `https://내앱.streamlit.app`)을 등록하고, 같은 도메인을 아래 카카오 Web 사이트 도메인에도 추가합니다.
등록하지 않으면 카톡의 '브리핑 보기' 버튼이 첫 꼭지의 기사로 연결됩니다.

### 카카오톡 알림

카카오톡 **나에게 보내기** API를 씁니다 (개인용, 무료, 본인 카카오톡의 '나와의 채팅'으로 수신).

1. [Kakao Developers](https://developers.kakao.com) → 내 애플리케이션 → 애플리케이션 추가
2. **앱 키**에서 *REST API 키* 복사
3. **카카오 로그인** 활성화, Redirect URI에 `https://localhost` 등록
4. **동의항목**에서 *카카오톡 메시지 전송(talk_message)* 을 선택 동의로 설정
5. **플랫폼 → Web 사이트 도메인**에 기사 링크 도메인 등록 (등록 안 된 도메인 링크는 메시지에서 열리지 않음)
   `https://www.yna.co.kr`, `https://www.hani.co.kr`, `https://www.khan.co.kr`, `https://news.sbs.co.kr`
6. 브라우저로 아래 주소 접속 → 로그인·동의 → 이동된 `https://localhost/?code=…` 주소의 `code` 값 복사
   ```
   https://kauth.kakao.com/oauth/authorize?client_id=<REST_API_키>&redirect_uri=https://localhost&response_type=code&scope=talk_message
   ```
7. 리프레시 토큰 발급 (응답의 `refresh_token` 값)
   ```
   curl -X POST https://kauth.kakao.com/oauth/token \
     -d grant_type=authorization_code -d client_id=<REST_API_키> \
     -d redirect_uri=https://localhost -d code=<code>
   ```
   보안 → Client Secret을 *사용함*으로 둔 경우 `-d client_secret=<값>` 추가.

저장소 **Settings → Secrets and variables → Actions**에 추가:

| Secret | 값 |
| --- | --- |
| `KAKAO_REST_API_KEY` | 2번의 REST API 키 |
| `KAKAO_REFRESH_TOKEN` | 7번의 refresh_token |
| `KAKAO_CLIENT_SECRET` | Client Secret을 사용할 때만 |
| `GH_PAT` | 리프레시 토큰 자동 교체용 Fine-grained PAT (이 저장소, *Secrets: Read and write*) |

리프레시 토큰은 2개월마다 만료되며, 만료 1개월 전부터 매 실행 시 새 토큰을 받아 `GH_PAT`로 Secret을 자동 교체합니다.
`GH_PAT`가 없으면 2개월마다 6~7번을 다시 해서 직접 교체해야 합니다.

GitHub Actions 탭에서 `news-briefing`을 수동 실행(workflow_dispatch)하면 바로 첫 스냅샷이 생성됩니다.

## 로컬 실행

```
pip install -r requirements.txt
python -m news.briefing news       # 또는 politics
streamlit run streamlit_app.py
```
