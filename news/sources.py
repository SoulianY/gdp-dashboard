"""카테고리별 언론사 RSS 피드 목록."""

POLITICS = "정치"

# 정치를 제외한 일반 브리핑 카테고리. 순서가 화면의 탭 순서가 된다.
FEEDS: dict[str, list[tuple[str, str]]] = {
    "경제": [
        ("연합뉴스", "https://www.yna.co.kr/rss/economy.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/economy/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/economy_news.xml"),
        ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=02&plink=RSSREADER"),
    ],
    "사회": [
        ("연합뉴스", "https://www.yna.co.kr/rss/society.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/society/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/society_news.xml"),
        ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=03&plink=RSSREADER"),
    ],
    "국제": [
        ("연합뉴스", "https://www.yna.co.kr/rss/international.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/international/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/kh_world.xml"),
        ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=08&plink=RSSREADER"),
    ],
    "IT·과학": [
        ("연합뉴스", "https://www.yna.co.kr/rss/industry.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/science/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/science_news.xml"),
    ],
    "문화": [
        ("연합뉴스", "https://www.yna.co.kr/rss/culture.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/culture/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/culture_news.xml"),
        ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=07&plink=RSSREADER"),
    ],
    "스포츠": [
        ("연합뉴스", "https://www.yna.co.kr/rss/sports.xml"),
        ("한겨레", "https://www.hani.co.kr/rss/sports/"),
        ("경향신문", "https://www.khan.co.kr/rss/rssdata/kh_sports.xml"),
        ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=09&plink=RSSREADER"),
    ],
}

POLITICS_FEEDS: list[tuple[str, str]] = [
    ("연합뉴스", "https://www.yna.co.kr/rss/politics.xml"),
    ("한겨레", "https://www.hani.co.kr/rss/politics/"),
    ("경향신문", "https://www.khan.co.kr/rss/rssdata/politic_news.xml"),
    ("SBS", "https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=01&plink=RSSREADER"),
]
