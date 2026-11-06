# 📥 알림 로그 (Android)

폰에 오는 모든 앱 알림을 모아 **비공개 GitHub 저장소**에 올리는 개인용 앱.
Claude Code 세션(예약 작업 포함)이 그 저장소를 읽어 알림을 분석한다.

```
알림 → NotiListener → Outbox(pending.jsonl) → 3분 뒤 UploadWorker → <비공개 저장소>/inbox/<날짜>/<시각>.jsonl
```

알림 내용(메시지, 인증번호, 금융 알림 등)이 그대로 담기므로 **이 공개 저장소가 아닌 별도 비공개 저장소**에 올린다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `NotiListener.kt` | 시스템 알림 리스너. 진행 중 알림·미디어 재생 컨트롤·그룹 요약·내용 없는 알림·같은 내용 재게시를 거르고 기록 |
| `Outbox.kt` | 업로드 전 로컬 보관. 업로드 성공 전까지 지우지 않음 |
| `UploadWorker.kt` | 알림이 오면 3분 뒤(그 사이 알림은 한 묶음으로) GitHub Contents API 업로드. 실패분은 15분 주기 작업이 다시 올림 |
| `Settings.kt` | 업로드 대상 저장소·토큰, 알림 보낸 앱 목록, 숨길 앱, 숨길 단어 |
| `MainActivity.kt` | 권한 상태·대기 건수·마지막 업로드 표시, 앱별 알림 띄우기 스위치, 설정 입력 |

## 알림 숨기기

첫 화면의 **앱별 알림 띄우기** 목록에 알림을 보낸 앱이 최근 순으로 쌓인다(업무 프로필 앱은 "(업무)").
새 앱은 기본으로 켜져 있어 알림이 그대로 뜬다. 스위치를 끈 앱의 알림은 기록한 뒤 바로 알림창에서 지운다
(통화·알람·재생·내비게이션 알림은 건드리지 않음).
하단의 **숨길 단어**(쉼표로 구분, 기본 `(광고)`)가 제목·내용에 들어 있는 알림도, 켜 둔 앱이라도 숨긴다.
업무 프로필 앱 알림에는 단어 필터를 적용하지 않는다(업무상 광고 관련 알림이 있을 수 있어서).
리스너는 알림이 이미 게시된 뒤에 받으므로 소리·진동·팝업은 막지 못한다.
소리까지 막으려면 폰의 **방해 금지**를 켜 두고, 알림을 띄우는 앱을 방해 금지 예외로 등록한다.
방해 금지 중에도 알림은 게시되므로 기록은 그대로 된다.

## 기록 형식 (JSONL, 한 줄에 알림 하나)

```json
{"time":"2026-11-06T15:30:12.345+09:00","app":"카카오톡","package":"com.kakao.talk","title":"홍길동","text":"내일 몇 시?","sub":"가족방","category":"msg"}
```

`sub`·`conversation`·`category`는 앱이 넣어 준 경우에만 있다.

## 설치

1. GitHub에서 **비공개** 저장소를 만든다 (예: `SoulianY/push-inbox`, README로 초기화).
2. [Fine-grained PAT](https://github.com/settings/personal-access-tokens/new) 발급
   - Repository access: *Only select repositories* → 1번 저장소만
   - Permissions: *Contents: Read and write*
3. `app-debug.apk`를 폰에 설치 (출처를 알 수 없는 앱 설치 허용).
4. 앱에서 **알림 접근 권한 설정 열기** → *알림 로그* 허용.
   Android 13+에서 허용이 막혀 있으면: 설정 → 애플리케이션 → 알림 로그 → ⋮ → *제한된 설정 허용* 후 다시.
5. 저장소(`owner/name`)와 토큰 입력 → **저장하고 지금 올리기**.
6. 삼성 등에서 앱이 잠들지 않게: 설정 → 애플리케이션 → 알림 로그 → 배터리 → *제한 없음*.

## 빌드

JDK 17+와 Android SDK(platform 37)가 필요하다.

```
cd notilog
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

디버그 키로 서명되므로 다른 PC에서 빌드한 APK로는 덮어 설치가 안 된다 (지우고 다시 설치).
