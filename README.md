# 🗞 뉴스 브리핑

국내 주요 언론사 RSS에서 최근 24시간 뉴스를 모아, 같은 사건을 다룬 기사끼리 묶고
보도한 언론사 수(이슈 크기) 순으로 보여주는 Streamlit 앱입니다.

- **일반 브리핑**: 경제·사회·국제·IT·과학·문화·스포츠 (정치 제외). KST 09·12·15·18·21시 갱신.
  직전 브리핑 이후 새 기사가 있을 때만 카카오톡 알림.
- **정치 브리핑**: 매일 KST 08:30 별도 생성 및 알림.

## 구성

| 경로 | 역할 |
| --- | --- |
| `news/sources.py` | 카테고리별 RSS 피드 목록 |
| `news/collect.py` | 수집 · 24시간 필터 · 유사 제목 묶기 · 이슈 순 정렬 · 사진/요약 보강(og 태그) |
| `news/briefing.py` | 스냅샷(`data/news/*.json`) 저장 및 새 기사 판별 |
| `news/notify.py` | 카카오톡 '나에게 보내기' 알림 |
| `.github/workflows/news-briefing.yml` | 스케줄 실행 후 스냅샷 커밋 |
| `streamlit_app.py` | 화면 |

## 카카오톡 알림 설정

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
