# :earth_americas: GDP dashboard template

A simple Streamlit app showing the GDP of different countries in the world.

[![Open in Streamlit](https://static.streamlit.io/badges/streamlit_badge_black_white.svg)](https://gdp-dashboard-template.streamlit.app/)

### How to run it on your own machine

1. Install the requirements

   ```
   $ pip install -r requirements.txt
   ```

2. Run the app

   ```
   $ streamlit run streamlit_app.py
   ```

## 🔥 디시인사이드 인기급상승 봇

매일 아침 07:00 (KST) 디시인사이드 실시간 베스트(`dcbest` 갤러리)의 인기급상승
게시물 10개를 텔레그램으로 보내줍니다. GitHub Actions 스케줄로 돌기 때문에
따로 서버를 띄울 필요가 없습니다.

### 설정

1. 텔레그램에서 [@BotFather](https://t.me/BotFather)에게 `/newbot` 으로 봇을 만들고
   토큰을 받습니다.
2. 만든 봇과 대화를 시작(`/start`)한 뒤,
   `https://api.telegram.org/bot<토큰>/getUpdates` 를 열어 `chat.id` 를 확인합니다.
   (채널로 받으려면 봇을 채널 관리자로 추가하고 채널 chat id를 씁니다.)
3. 저장소 **Settings → Secrets and variables → Actions** 에 등록합니다.
   - `TELEGRAM_BOT_TOKEN`
   - `TELEGRAM_CHAT_ID`
4. **Actions** 탭에서 `dcinside-trending` 워크플로를 `Run workflow` 로 한 번 실행해
   메시지가 오는지 확인합니다.

보내는 시각은 `.github/workflows/dcinside-trending.yml` 의 cron(UTC 기준)에서 바꿉니다.
`0 22 * * *` = 07:00 KST.

### 로컬 실행

```
$ pip install -r dcinside_bot/requirements.txt
$ python -m dcinside_bot --dry-run          # 전송 없이 본문만 출력
$ TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... python -m dcinside_bot
```

### 테스트

파싱·메시지 렌더링은 저장된 HTML 픽스처로 검증합니다(네트워크 불필요).

```
$ pip install -r requirements-dev.txt
$ pytest
```
