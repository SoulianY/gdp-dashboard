# 커뮤니티 브리핑 절차 (예약 작업 세션용)

하루 두 번(06:48, 15:48 KST) 실행된다. 수집 기간은 지난 브리핑 시각부터 지금까지다(스크립트가 처리). 사이트당 최대 100건.
인기글과 별도로 디시 마라톤 갤러리의 기간 내 모든 글을 모아, 대회 접수 일정 글이 있으면 접수 정보만 페이지 맨 위에 따로 보여 준다.
결과는 비공개 브리핑 페이지(claude.ai 아티팩트)와 알림 로그 앱의 브리핑 파일(`data/briefings/`) 두 곳에 남긴다.
아래에서 `REPO`는 이 저장소 클론의 절대 경로, `OUT=/home/user/brief/out`(작업 폴더, 커밋하지 않음).

## 1. 수집

```
rm -rf $OUT && /usr/bin/python3 $REPO/community/brief.py collect $OUT
```

1~2분 걸린다. 필요한 패키지(requests, bs4, Pillow)는 `/usr/bin/python3`에 있으니 pip 설치는 하지 않는다.
사이트가 차단하거나 사진을 못 받아도 우회(헤드리스 브라우저, Referer 조작 등)하지 않는다. 스크립트가 실패 사실을 기록한다.

## 2. 이슈 묶기: `$OUT/briefing.json`

`$OUT/posts.json`(글 제목·반응 수 목록)을 읽고 쓴다. 형식:
`{"groups":[{"headline":"...","summary":"","posts":["dc-3","ruliweb-1"]}],"races":[{"post":"marathon-7"}]}`

- 같거나 비슷한 주제의 글은 한 그룹으로 묶는다. 여러 사이트에 걸친 주제를 우선한다.
- 그룹 12~16개, 반응(댓글·추천·조회)이 크거나 여러 사이트에 걸친 순서. 그룹당 posts는 반응 큰 순으로 최대 4개.
- headline: 짧은 기사 제목체. 이 단계에서 summary는 빈 문자열로 둔다.
- 광고·홍보·장터·포인트 받기 글, 화제성이 낮은 단독 잡담 글은 넣지 않는다.
- races: `posts.json`의 `marathon`(마라톤 갤러리 글, 제목 앞 `[...]`는 말머리) 제목에서 대회 접수 일정을 알리는 글(접수 시작·마감·추가 접수 안내, 월별 접수 일정 정리 등)만 고른다.
  `[🏆대회]` 말머리 글은 대부분 참가 후기이니 제목으로 가린다. 후기·질문·양도·잡담·개인 "신청완" 인증은 넣지 않는다. 없으면 빈 배열. 같은 대회를 다룬 글이 여럿이면 정보가 가장 많아 보이는 글 하나만.

## 3. 본문·사진

```
/usr/bin/python3 $REPO/community/brief.py enrich $OUT
```

묶인 글만 본문 요약문(desc)과 사진을, races로 고른 마라톤 글은 본문(body), 본문 속 링크(links), 본문 사진(posters: `$OUT/race/*.jpg` 경로)을 가져온다.

## 4. 요약 쓰기

`posts.json`의 desc를 참고해 `briefing.json` 각 그룹의 summary를 채운다. '~습니다'체 1~2문장.
제목과 desc에 있는 사실만 쓰고 추측하지 않는다. 필요하면 headline도 다듬는다.

races는 `marathon` 글의 title·body·links와 posters 이미지(대회 정보는 대개 포스터에 있으니 Read로 직접 본다)를 보고 대회마다 한 항목으로 채운다(한 글에 여러 대회가 있으면 같은 post로 여러 항목).
`{"post":"marathon-7","name":"대회명","signup":"접수 기간·시작 시각","date":"대회 날짜·장소","courses":"종목·참가비","link":"접수 페이지 URL"}`
글과 포스터에 없는 값은 빈 문자열로 두고 지어내지 않는다. link는 links 중 접수 페이지로 보이는 것만. 접수 기간·시작 시각(signup)이 글과 포스터 어디에도 없으면 그 항목을 지운다(대회 소개·후기는 접수 정보가 아니다).

## 5. 페이지와 앱 파일 만들기

```
/usr/bin/python3 $REPO/community/brief.py render $OUT
cd $REPO && /usr/bin/python3 community/brief.py app $OUT data/briefings
```

## 6. 페이지 게시

이 세션의 아티팩트 도구(Artifact 도구, 또는 `mcp__claude-artifacts__publish_files`가 있으면 그것)로
기존 아티팩트 slug `7fd8a618-4870-498b-a4b1-88099f7eb9c7` (https://claude.ai/artifact/GneUyJ3oWyFn1DNiyDYGQW)를 갱신한다.
새 아티팩트를 만들지 않는다. 올릴 파일: `$OUT/index.html` → `index.html`, `$OUT/img/` 안의 모든 jpg → `img/<파일명>`.
이번 실행에 없는 예전 `img/` 파일은 삭제한다.

## 7. 앱 파일 커밋

5단계가 만든 `data/briefings/<날짜>/<시각>-community.json` 한 파일만 커밋해 **main에 직접 푸시**한다.

```
cd $REPO && git add data/briefings/ && git commit -q -m "briefing file: community <YYYY-MM-DD HH:MM KST>"
git push -q origin main || (git pull -q --rebase origin main && git push -q origin main)
```

## 8. 마지막 답변

짧게: 페이지 링크 https://claude.ai/artifact/GneUyJ3oWyFn1DNiyDYGQW , 수집 기간, 주요 이슈 헤드라인 상위 5개,
마라톤 접수 일정 항목 수와 대회명,
앱 파일 커밋 여부, 수집에 실패했거나 0건인 사이트가 있으면 그 이름과 이유(MLB파크는 하루 늦게 집계되어 오후에는 보통 0건이다).
