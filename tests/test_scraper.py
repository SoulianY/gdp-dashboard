from pathlib import Path

from dcinside_bot.scraper import parse_trending

HTML = (Path(__file__).parent / "fixtures" / "dcbest_list.html").read_text(encoding="utf-8")


def test_skips_notice_and_ad_rows():
    posts = parse_trending(HTML)
    assert [p.rank for p in posts] == [1, 2, 3]
    assert all("/board/view/" in p.url for p in posts)
    assert "실베 이용 안내" not in [p.title for p in posts]


def test_parses_post_fields():
    post = parse_trending(HTML)[0]
    assert post.title == "오늘 경기 <레전드> 장면"
    assert post.url == "https://gall.dcinside.com/board/view/?id=dcbest&no=9001&page=1"
    assert post.gallery == "국내야구"
    assert (post.comments, post.views, post.recommends) == (1234, 45678, 910)


def test_missing_columns_default_to_zero_and_none():
    post = parse_trending(HTML)[2]
    assert post.gallery is None
    assert post.comments == 0


def test_limit_caps_results():
    assert len(parse_trending(HTML, limit=2)) == 2
