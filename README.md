# 🗞 뉴스 브리핑

국내 주요 언론사 RSS에서 최근 24시간 뉴스를 모아 같은 사건끼리 묶은 뒤,
Claude Code 예약 작업 세션이 여러 보도를 읽고 종합해 꼭지별 브리핑을 쓰고,
[브리핑 페이지](https://souliany.github.io/gdp-dashboard/)(GitHub Pages)와 카카오톡으로 전달합니다.
API 키 없이 Claude 구독으로 돌아갑니다.

- **뉴스 브리핑**: 경제·사회·국제·IT·과학·문화·스포츠 (정치 제외). 3시간마다(09~21시).
  직전 브리핑 이후 새 기사가 실린 이슈만 브리핑하고, 새 기사가 없으면 브리핑·알림 모두 생략.
- **정치 브리핑**: 매일 아침, 최근 24시간 정치 이슈 전체로 브리핑.
- **메일 브리핑**: Gmail 브리핑 예약 작업이 `workflow_dispatch`(kind=mail)로 카톡만 보냄. 메일 내용은 이 저장소에 올리지 않고, 알림은 비공개 브리핑 페이지(claude.ai) 링크만 담음.
- **알림 브리핑**: 폰 알림 브리핑 예약 작업(비공개 저장소 push-inbox)이 `workflow_dispatch`(kind=noti)로 카톡을 보냄. 확인 필요·중요 알림이 새로 생긴 회차와 밤 마감 브리핑에만, 건수와 비공개 페이지 링크만 담음.
- 모든 브리핑 알림은 `data/notify.json`(최근 20개, 제목·한 줄 요약·링크만)에도 남아, 폰의 [알림 로그 앱](notilog/README.md)이 15분마다 읽어 폰 알림으로 띄웁니다.
- 브리핑이 main에 푸시되면 GitHub Actions가 페이지를 갱신하고, 카카오톡으로 핵심 요약과 '브리핑 보기' 버튼을 보냄.

## 흐름

1. Claude Code 예약 작업이 정해진 시각에 새 세션을 띄움
2. 세션이 [`news/GUIDE.md`](news/GUIDE.md)를 따라 `python -m news.briefing prepare <kind>` → 원고 작성 → `publish`
3. 세션이 `data/news/<kind>.json`을 main에 푸시
4. `.github/workflows/news-briefing.yml`이 브리핑 페이지 배포 → 카톡 발송

## 구성

| 경로 | 역할 |
| --- | --- |
| `news/sources.py` | 카테고리별 RSS 피드 목록 |
| `news/collect.py` | 수집 · 24시간 필터 · 유사 제목 묶기 · 이슈 순 정렬 · 사진/요약 보강(og 태그) |
| `news/GUIDE.md` | 예약 작업 세션이 따르는 절차와 작성 원칙 |
| `news/briefing.py` | `prepare`: 수집·새 기사 판별 / `publish`: 원고 검증·사진·출처 붙여 저장 |
| `news/notify.py` | 카카오톡 '나에게 보내기' 알림 + `data/notify.json` 기록 (`python -m news.notify <kind>`) |
| `site/index.html` | 브리핑 페이지 (배포 시 `data/news/*.json`을 함께 올려 읽음) |
| `.github/workflows/news-briefing.yml` | 브리핑 푸시 시 페이지 배포 → 카톡 발송 |
| `notilog/` | 폰 알림을 비공개 저장소로 모으는 개인용 Android 앱 ([README](notilog/README.md)) |

## 설정

### 브리핑 페이지 (GitHub Pages)

저장소 **Settings → Pages → Build and deployment → Source**를 **GitHub Actions**로 설정합니다.
주소는 `https://souliany.github.io/gdp-dashboard/` (`#politics`로 정치 브리핑).

### 카카오톡 알림

카카오톡 **나에게 보내기** API를 씁니다 (개인용, 무료, 본인 카카오톡의 '나와의 채팅'으로 수신).

1. [Kakao Developers](https://developers.kakao.com) → 내 애플리케이션 → 애플리케이션 추가
2. **앱 키**에서 *REST API 키* 복사
3. **카카오 로그인** 활성화, Redirect URI에 `https://localhost` 등록
4. **동의항목**에서 *카카오톡 메시지 전송(talk_message)* 을 선택 동의로 설정
5. **플랫폼 → Web 사이트 도메인**에 기사 링크 도메인 등록 (등록 안 된 도메인 링크는 메시지에서 열리지 않음)
   `https://souliany.github.io`(브리핑 페이지), `https://claude.ai`(메일·알림 브리핑 페이지), `https://www.yna.co.kr`, `https://www.hani.co.kr`, `https://www.khan.co.kr`, `https://news.sbs.co.kr`
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

## 로컬 실행

```
pip install -r requirements.txt
python -m news.briefing prepare news     # data/news/news.issues.json 생성
# GUIDE.md 형식으로 data/news/news.draft.json 작성
python -m news.briefing publish news
cp data/news/*.json site/ && python -m http.server -d site   # http://localhost:8000
```
