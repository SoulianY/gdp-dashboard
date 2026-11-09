"""국내 커뮤니티 브리핑(아침·오후). 절차는 GUIDE.md.

python3 community/brief.py collect OUT        # 지난 브리핑 이후 인기글 목록 -> OUT/posts.json
python3 community/brief.py enrich OUT         # briefing.json에 묶인 글만 요약문·사진 -> OUT/posts.json, OUT/img/*.jpg
python3 community/brief.py render OUT         # OUT/posts.json + OUT/briefing.json -> OUT/index.html (브리핑 페이지)
python3 community/brief.py app OUT BRIEFINGS  # 같은 내용 -> BRIEFINGS/<날짜>/<시각>-community.json (알림 로그 앱)
"""
import html
import io
import json
import re
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import urljoin
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup
from PIL import Image

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/130 Safari/537.36")
MAX_PER_SOURCE = 100
BOARD_TOP = 10
KST = ZoneInfo("Asia/Seoul")
SLOTS = ((6, 48), (15, 48))  # 예약 실행 시각(KST). 바꾸면 예약 작업의 일정도 같이 바꾼다.
NSFW = re.compile(r"ㅇㅎ|ㅎㅂ|후방|19금|ㅗㅜㅑ|야스")
BAD_IMG = re.compile(r"logo|og-talk|nstatic|default|noimage|thumbnail_default", re.I)

session = requests.Session()
session.headers.update({"User-Agent": UA, "Accept-Language": "ko-KR,ko;q=0.9"})


def num(text):
    m = re.search(r"\d[\d,]*", text or "")
    return int(m.group().replace(",", "")) if m else 0


def soup(url):
    r = session.get(url, timeout=20)
    r.raise_for_status()
    if r.encoding and r.encoding.lower() == "iso-8859-1":
        r.encoding = r.apparent_encoding
    return BeautifulSoup(r.text, "html.parser")


# --- 목록 파서: 수집 기간(since ~ now) 안의 글만 (title, url, comments, score) ---

def by_reaction(items):
    return sorted(items, key=lambda p: -(p[2] + p[3]))


def list_dcbest(since):
    # 실베는 등재 순으로 쌓이므로 기간을 벗어날 때까지 페이지를 넘긴다
    out = []
    for page in range(1, 13):
        base = f"https://gall.dcinside.com/board/lists/?id=dcbest&page={page}"
        oldest = None
        for tr in soup(base).select("tr.ub-content"):
            if not tr.select_one("td.gall_num").get_text(strip=True).isdigit():
                continue
            posted = datetime.strptime(tr.select_one("td.gall_date")["title"], "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST)
            oldest = posted
            if posted < since:
                continue
            a = tr.select_one("td.gall_tit a")
            title = " ".join(a.get_text(" ", strip=True).split())
            out.append((title, urljoin(base, a["href"]).replace(f"&page={page}", ""),
                        num(tr.select_one(".reply_num").get_text() if tr.select_one(".reply_num") else ""),
                        num(tr.select_one("td.gall_recommend").get_text())))
        if oldest is None or oldest < since:
            break
        time.sleep(0.4)
    return by_reaction(out)


def list_time(text, now):
    # 목록의 작성 시각: 오늘 글은 "HH:MM", 이전 글은 날짜만(-> 그날 23:59로 본다)
    text = text.strip()
    if re.fullmatch(r"\d{1,2}:\d{2}", text):
        h, m = map(int, text.split(":"))
        return now.replace(hour=h, minute=m, second=0, microsecond=0)
    for fmt in ("%Y-%m-%d", "%Y.%m.%d", "%y.%m.%d", "%m.%d", "%m-%d", "%m/%d"):
        dated = fmt if "%y" in fmt.lower() else "%Y " + fmt
        try:
            d = datetime.strptime(text if dated == fmt else f"{now.year} {text}", dated)
        except ValueError:
            continue
        d = d.replace(hour=23, minute=59, tzinfo=KST)
        return d.replace(year=d.year - 1) if d > now + timedelta(days=1) else d  # 1월에 본 12월 글
    raise ValueError(f"알 수 없는 시각 형식: {text}")


def list_ruliweb(since):
    now = datetime.now(KST)
    out, seen = [], set()
    for page in range(1, 6):
        base = f"https://bbs.ruliweb.com/best/all/now?page={page}"
        for tr in soup(base).select("tr.table_body"):
            a = tr.select_one("td.subject a.deco") or tr.select_one("td.subject a")
            when = tr.select_one("td.time")
            if not a or not when or "/best/board/" not in a["href"]:
                continue
            url = urljoin(base, a["href"]).split("?")[0]
            if url in seen or list_time(when.get_text(), now) < since:
                continue
            seen.add(url)
            reply = tr.select_one(".num_reply")
            title = a.get_text(" ", strip=True)
            if reply:
                title = title.replace(reply.get_text(strip=True), "").strip()
            title = re.sub(r"^\d+\s+", "", title)
            out.append((title, url, num(reply.get_text() if reply else ""),
                        num(tr.select_one("td.recomd").get_text() if tr.select_one("td.recomd") else "")))
        time.sleep(0.4)
    return by_reaction(out)


def list_clien(since):
    # 모두의공원 공감순(시간순이 아니라 페이지마다 기간을 거른다)
    out = []
    for po in range(4):
        base = f"https://www.clien.net/service/board/park?od=T33&po={po}"
        for row in soup(base).select("div.list_item.symph_row"):
            stamp = row.select_one(".timestamp")
            if stamp and datetime.strptime(stamp.get_text(strip=True), "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST) < since:
                continue
            a = row.select_one("a.list_subject")
            t = row.select_one("span.subject_fixed") or a
            url = urljoin(base, a["href"]).split("?")[0]
            out.append((t.get_text(" ", strip=True), url, num(row.get("data-comment-count")),
                        num(row.select_one(".list_symph").get_text() if row.select_one(".list_symph") else "")))
        time.sleep(0.4)
    return by_reaction(out)


def list_pann(since):
    # 어제까지는 날짜별 일간 랭킹, 오늘은 실시간 랭킹. 목록에 작성 시각이 없어 글마다 확인한다.
    today = datetime.now(KST).date()
    days = (today - since.date()).days
    pages = [f"https://pann.nate.com/talk/ranking/d?rankingType=total&stndDt={since.date() + timedelta(days=i):%Y%m%d}"
             for i in range(days)] + ["https://pann.nate.com/talk/ranking"]
    out, seen = [], set()
    for base in pages:
        for li in soup(base).select("ul.post_wrap > li"):
            a = li.select_one("h2 a")
            url = urljoin(base, a["href"]) if a else None
            if not url or url in seen:
                continue
            seen.add(url)
            when = soup(url).select_one(".post-tit-info .date")
            time.sleep(0.2)
            if not when or datetime.strptime(when.get_text(strip=True), "%Y.%m.%d %H:%M").replace(tzinfo=KST) < since:
                continue
            reply, count = li.select_one(".reple-num"), li.select_one(".count")
            out.append((a.get_text(" ", strip=True), url, num(reply.get_text() if reply else ""),
                        num(count.get_text() if count else "")))
    return by_reaction(out)


def list_bobae(since):
    now = datetime.now(KST)
    out = []
    for page in range(1, 13):
        base = f"https://www.bobaedream.co.kr/list?code=best&page={page}"
        oldest = None
        for tr in soup(base).select("tr"):
            a, when = tr.select_one("a.bsubject"), tr.select_one("td.date")
            if not a or not when:
                continue
            oldest = list_time(when.get_text(), now)
            if oldest < since:
                continue
            reply = tr.select_one(".totreply")
            out.append((a.get("title") or a.get_text(strip=True), urljoin(base, a["href"]),
                        num(reply.get_text() if reply else ""), num(tr.select_one("td.recomm").get_text())))
        if oldest is None or oldest < since:
            break
        time.sleep(0.4)
    return by_reaction(out)


def list_mlbpark(since):
    # 불펜 TODAY BEST(추천순): 반응 수가 없어 사이트 순위를 그대로 쓴다.
    # 하루 늦게 집계되는 랭킹이라 보통 아침 브리핑에만 새 글이 잡힌다.
    base = "https://mlbpark.donga.com/mp/best.php?b=bullpen&m=like"
    now = datetime.now(KST)
    out = []
    for tr in soup(base).select("table.tbl_type01 tr"):
        tds = tr.find_all("td")
        a = tr.select_one("td.t_left a[href]")
        if len(tds) < 4 or not a or list_time(tds[-1].get_text(), now) < since:
            continue
        out.append((a.get_text(" ", strip=True), a["href"], 0, 0))
    return out


def list_inven(since):
    # 오픈이슈갤러리 추천글
    now = datetime.now(KST)
    out = []
    for page in range(1, 6):
        base = f"https://www.inven.co.kr/board/webzine/2097?my=chu&p={page}"
        oldest = None
        for tr in soup(base).select("tr"):
            a, when = tr.select_one("a.subject-link"), tr.select_one("td.date")
            if not a or not when:
                continue
            oldest = list_time(when.get_text(), now)
            if oldest < since:
                continue
            reply = tr.select_one(".con-comment")
            out.append((a.get_text(" ", strip=True), a["href"].split("?")[0],
                        num(reply.get_text() if reply else ""), num(tr.select_one("td.reco").get_text())))
        if oldest is None or oldest < since:
            break
        time.sleep(0.4)
    return by_reaction(out)


SOURCES = [
    ("dc", "디시 실시간 베스트", "https://gall.dcinside.com/board/lists/?id=dcbest", list_dcbest),
    ("ruliweb", "루리웹 베스트", "https://bbs.ruliweb.com/best/all/now", list_ruliweb),
    ("clien", "클리앙 모두의공원", "https://www.clien.net/service/board/park?od=T33", list_clien),
    ("pann", "네이트판 랭킹", "https://pann.nate.com/talk/ranking", list_pann),
    ("bobae", "보배드림 베스트", "https://www.bobaedream.co.kr/list?code=best", list_bobae),
    ("mlbpark", "MLB파크 불펜 베스트", "https://mlbpark.donga.com/mp/best.php?b=bullpen&m=like", list_mlbpark),
    ("inven", "인벤 오픈이슈갤러리", "https://www.inven.co.kr/board/webzine/2097?my=chu", list_inven),
]


# --- 게시글: 요약문과 대표 사진 ---

def post_detail(url):
    s = soup(url)

    def og(prop):
        m = s.find("meta", property=prop)
        return (m.get("content") or "").strip() if m else ""

    desc = " ".join(og("og:description").split())
    img = og("og:image")
    if not img or BAD_IMG.search(img):
        body = s.select_one("#contentArea") or s.select_one(".write_div") or s.select_one(".view_content")
        tag = body.find("img", src=True) if body else None
        img = tag["src"] if tag else ""
    return desc[:220], urljoin(url, img) if img else ""  # "//host/..." 같은 주소도 절대 주소로


def save_image(img_url, path):
    r = session.get(img_url, timeout=20)
    r.raise_for_status()
    im = Image.open(io.BytesIO(r.content))
    im.seek(0)
    if im.width < 200 or im.height < 120:  # 아이콘·이모티콘
        return False
    im = im.convert("RGB")
    im.thumbnail((640, 640))
    im.save(path, "JPEG", quality=74, optimize=True)
    return True


def previous_slot(now):
    # 지난 브리핑 시각: 지금보다 1시간 이상 앞선 가장 최근 예약 시각
    slots = [(now - timedelta(days=d)).replace(hour=h, minute=m, second=0, microsecond=0)
             for d in (0, 1) for h, m in SLOTS]
    return max(t for t in slots if t <= now - timedelta(hours=1))


def collect(out):
    out.mkdir(parents=True, exist_ok=True)
    now = datetime.now(KST)
    since = previous_slot(now)
    posts, status = [], []
    for key, name, url, lister in SOURCES:
        try:
            items = lister(since)[:MAX_PER_SOURCE]
        except Exception as e:
            status.append({"source": key, "name": name, "url": url, "ok": False, "note": str(e)[:120]})
            continue
        status.append({"source": key, "name": name, "url": url, "ok": True, "note": f"{len(items)}건"})
        posts += [{"id": f"{key}-{i}", "source": key, "source_name": name, "title": title, "url": purl,
                   "comments": comments, "score": score, "desc": "", "image": "", "image_src": ""}
                  for i, (title, purl, comments, score) in enumerate(items, 1)]
        print(f"{name}: {len(items)}건", file=sys.stderr)
    data = {"since": since.isoformat(timespec="minutes"), "collected_at": now.isoformat(timespec="minutes"),
            "sources": status, "posts": posts}
    (out / "posts.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


def enrich(out):
    # 이슈로 묶인 글만 본문 요약문과 대표 사진을 가져온다.
    # image: 페이지에 올리는 사본, image_src: 원본 주소(앱이 직접 불러옴. 사이트가 막으면 앱에서 자리를 비운다)
    data = json.loads((out / "posts.json").read_text(encoding="utf-8"))
    brief = json.loads((out / "briefing.json").read_text(encoding="utf-8"))
    wanted = {i for g in brief["groups"] for i in g["posts"]}
    (out / "img").mkdir(exist_ok=True)
    for p in data["posts"]:
        if p["id"] not in wanted:
            continue
        try:
            p["desc"], img_url = post_detail(p["url"])
            if img_url and not NSFW.search(p["title"]) and save_image(img_url, out / "img" / f"{p['id']}.jpg"):
                p["image"] = f"img/{p['id']}.jpg"
                p["image_src"] = img_url
        except Exception as e:
            print(f"  {p['id']} 상세/사진 실패: {e}", file=sys.stderr)
        time.sleep(0.4)
    (out / "posts.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


# --- 렌더링 ---

CSS = """
:root{
  /* 아침 신문 한 장: 왼쪽 정렬 단일 칼럼, 이슈 카드 -> 커뮤니티별 목록 */
  --paper:#f6f7f9; --card:#ffffff; --ink:#15191f; --muted:#5d6673; --rule:#dde1e7; --accent:#1f4fd1;
  --display:"Nanum Myeongjo",Georgia,serif; --body:"IBM Plex Sans KR","Apple SD Gothic Neo","Malgun Gothic",sans-serif;
  --dc:#3b4890; --ruliweb:#d7461a; --clien:#37648c; --pann:#e0245e;
  --bobae:#127a5a; --mlbpark:#6a40a8; --inven:#9a5b00;
}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){
  --paper:#111419; --card:#1a1e25; --ink:#e8ebf0; --muted:#9aa3b1; --rule:#2b313b; --accent:#7ea2ff;
  --dc:#8d9cf0; --ruliweb:#ff8a5c; --clien:#7fb1dc; --pann:#ff7aa2;
  --bobae:#3fae86; --mlbpark:#9d7ae0; --inven:#d08a2a; color-scheme:dark}}
:root[data-theme="dark"]{
  --paper:#111419; --card:#1a1e25; --ink:#e8ebf0; --muted:#9aa3b1; --rule:#2b313b; --accent:#7ea2ff;
  --dc:#8d9cf0; --ruliweb:#ff8a5c; --clien:#7fb1dc; --pann:#ff7aa2;
  --bobae:#3fae86; --mlbpark:#9d7ae0; --inven:#d08a2a; color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:var(--paper);color:var(--ink);font:18px/1.65 var(--body);word-break:keep-all;overflow-wrap:anywhere}
main{max-width:860px;margin:0 auto;padding:32px 20px 64px;display:grid;gap:40px}
header{display:grid;gap:6px;border-bottom:2px solid var(--ink);padding-bottom:16px}
header h1{font:800 36px/1.25 var(--display);margin:0;text-wrap:balance}
.meta{color:var(--muted);font-size:16px}
h2{font:800 24px/1.3 var(--display);margin:0 0 14px}
.issues{display:grid;gap:14px}
.issue{background:var(--card);border:1px solid var(--rule);border-radius:10px;overflow:hidden;
  display:grid;grid-template-columns:220px 1fr}
.issue.noimg{grid-template-columns:1fr}
.issue img{width:100%;height:100%;max-height:220px;object-fit:cover;display:block;background:var(--rule)}
.issue .txt{padding:16px 18px;display:grid;gap:8px;align-content:start}
.issue h3{font:700 21px/1.4 var(--body);margin:0;text-wrap:balance}
.issue p{margin:0;color:var(--muted);font-size:17px;max-width:62ch}
.links{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:16px}
a{color:var(--accent)} a:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.links a{text-decoration:none;font-weight:600}
.links a:hover{text-decoration:underline}
.chip{display:inline-block;font-size:14px;font-weight:700;color:#fff;border-radius:4px;padding:1px 6px;margin-right:4px;vertical-align:1px}
.chip.dc{background:var(--dc)} .chip.ruliweb{background:var(--ruliweb)} .chip.clien{background:var(--clien)} .chip.pann{background:var(--pann)}
.chip.bobae{background:var(--bobae)} .chip.mlbpark{background:var(--mlbpark)} .chip.inven{background:var(--inven)}
.n{color:var(--muted);font-weight:400;font-variant-numeric:tabular-nums}
.boards{display:grid;grid-template-columns:repeat(auto-fit,minmax(400px,1fr));gap:14px}
.board{background:var(--card);border:1px solid var(--rule);border-radius:10px;padding:14px 16px}
.board h3{font-size:17px;margin:0 0 8px}
.board ol{margin:0;padding-left:22px;display:grid;gap:6px;font-size:16px}
.board a{color:var(--ink);text-decoration:none} .board a:hover{color:var(--accent);text-decoration:underline}
footer{font-size:15px;color:var(--muted);border-top:1px solid var(--rule);padding-top:14px}
footer ul{margin:6px 0 0;padding-left:18px}
.fail{color:var(--muted)}
@media (max-width:560px){
  .issue{grid-template-columns:1fr} .issue img{max-height:200px}
  .boards{grid-template-columns:1fr} header h1{font-size:30px}
}
"""

WEEKDAY = "월화수목금토일"


def e(s):
    return html.escape(str(s), quote=True)


def stat(p):
    parts = []
    if p["comments"]:
        parts.append(f"댓글 {p['comments']:,}")
    if p["score"]:
        parts.append(("조회 " if p["source"] == "pann" else "추천 ") + f"{p['score']:,}")
    return " · ".join(parts)


def load(out):
    """posts.json, briefing.json -> (전체 글, 이슈 [(그룹, 그 그룹의 글들)])"""
    data = json.loads((out / "posts.json").read_text(encoding="utf-8"))
    brief = json.loads((out / "briefing.json").read_text(encoding="utf-8"))
    by_id = {p["id"]: p for p in data["posts"]}
    issues = [(g, ps) for g in brief["groups"] if (ps := [by_id[i] for i in g["posts"] if i in by_id])]
    return data, issues


def render(out):
    data, groups = load(out)
    t = datetime.fromisoformat(data["collected_at"])
    f = datetime.fromisoformat(data["since"])
    date = f"{t.year}년 {t.month}월 {t.day}일 ({WEEKDAY[t.weekday()]}) {'아침' if t.hour < 12 else '오후'}"

    issues = []
    for g, ps in groups:
        img = next((p["image"] for p in ps if p["image"]), "")
        links = "".join(
            f'<a href="{e(p["url"])}" target="_blank" rel="noopener">'
            f'<span class="chip {p["source"]}">{e(p["source_name"].split()[0])}</span>원문보기'
            f'<span class="n"> {e(stat(p))}</span></a>' for p in ps)
        issues.append(
            f'<article class="issue{"" if img else " noimg"}">'
            + (f'<img src="{e(img)}" alt="" loading="lazy">' if img else "")
            + f'<div class="txt"><h3>{e(g["headline"])}</h3><p>{e(g["summary"])}</p>'
            f'<div class="links">{links}</div></div></article>')

    boards = []
    for s in data["sources"]:
        ps = [p for p in data["posts"] if p["source"] == s["source"]]
        if not ps:
            continue
        rows = "".join(f'<li><a href="{e(p["url"])}" target="_blank" rel="noopener">{e(p["title"])}</a>'
                       f'<span class="n"> {e(stat(p))}</span></li>' for p in ps[:BOARD_TOP])
        boards.append(f'<section class="board"><h3><span class="chip {s["source"]}">'
                      f'{e(s["name"].split()[0])}</span>{e(s["name"])}'
                      f'<span class="n"> · {len(ps)}건 중 상위 {min(len(ps), BOARD_TOP)}</span></h3><ol>{rows}</ol></section>')

    status = "".join(
        f'<li class="{"" if s["ok"] else "fail"}"><a href="{e(s["url"])}" target="_blank" rel="noopener">'
        f'{e(s["name"])}</a> · {"수집 " + e(s["note"]) if s["ok"] else "수집 실패 (" + e(s["note"]) + ")"}</li>'
        for s in data["sources"])

    page = f"""<!DOCTYPE html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>커뮤니티 브리핑</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;600;700&family=Nanum+Myeongjo:wght@800&display=swap" rel="stylesheet">
<style>{CSS}</style></head><body><main>
<header><h1>{date} 커뮤니티 브리핑</h1>
<div class="meta">지난 브리핑 이후({f.month}/{f.day} {f:%H:%M} ~ {t.month}/{t.day} {t:%H:%M}) 인기글 {len(data["posts"])}건에서 이슈 {len(issues)}개를 추렸습니다</div></header>
<section><h2>주요 이슈</h2><div class="issues">{"".join(issues)}</div></section>
<section><h2>커뮤니티별 인기글</h2><div class="boards">{"".join(boards)}</div></section>
<footer>수집 현황<ul>{status}</ul></footer>
</main></body></html>"""
    (out / "index.html").write_text(page, encoding="utf-8")


def app(out, briefings):
    """알림 로그 앱의 브리핑 파일. 형식은 news/notify.py의 뉴스 브리핑 파일과 같다.
    이슈마다 한 줄(제목, 요약, 반응 큰 글 원문, 사진 원본 주소), 커뮤니티별 인기글은 접어 둔다."""
    data, groups = load(out)
    t = datetime.fromisoformat(data["collected_at"])
    issues = []
    for g, ps in groups:
        src = next((p["image_src"] for p in ps if p.get("image_src")), "")
        sources = ", ".join(f"{p['source_name'].split()[0]} {stat(p)}".strip() for p in ps)
        issues.append({"text": g["headline"], "sub": f"{g['summary']}\n{sources}".strip(), "link": ps[0]["url"],
                       **({"image": src} if src else {})})
    boards = []
    for s in data["sources"]:
        ps = [p for p in data["posts"] if p["source"] == s["source"]][:BOARD_TOP]
        if ps:
            boards.append({"title": s["name"], "fold": True,
                           "items": [{"text": p["title"], "sub": stat(p), "link": p["url"]} for p in ps]})
    path = briefings / f"{t:%Y-%m-%d}" / f"{t:%H%M%S}-community.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({
        "id": f"{t:%Y-%m-%d}/{t:%H%M%S}-community", "kind": "community",
        "title": f"💬 {t:%m/%d} {'아침' if t.hour < 12 else '오후'} 커뮤니티 브리핑", "created": t.isoformat(),
        "summary": ", ".join(g["headline"] for g, _ in groups[:3]),
        "sections": [{"title": "주요 이슈", "items": issues}, *boards],
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print(path)


if __name__ == "__main__":
    cmd, out = sys.argv[1], Path(sys.argv[2])
    if cmd == "app":
        app(out, Path(sys.argv[3]))
    else:
        {"collect": collect, "enrich": enrich, "render": render}[cmd](out)
