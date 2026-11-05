from datetime import datetime

import streamlit as st

from news.briefing import load
from news.sources import FEEDS

ALL = "전체"

st.set_page_config(page_title="뉴스 브리핑", page_icon="🗞", layout="wide")


def render_story(story: dict) -> None:
    with st.container(border=True):
        image_col, text_col = st.columns([1, 3])
        if story["image"]:
            image_col.image(story["image"], width="stretch")
        text_col.markdown(f"#### {story['headline']}")
        text_col.caption(f"{story['category']} · {story['outlets']}개 언론사 보도")
        text_col.write(story["body"])
        with text_col.expander(f"출처 기사 {len(story['sources'])}건"):
            for source in story["sources"]:
                st.markdown(f"- [{source['title']}]({source['link']}) — {source['source']}")


def render_briefing(kind: str, empty_message: str) -> list[dict] | None:
    briefing = load(kind)
    if not briefing:
        st.info(empty_message)
        return None
    st.caption(f"{datetime.fromisoformat(briefing['generated_at']):%Y-%m-%d %H:%M} 브리핑")
    st.info(briefing["overview"])
    return briefing["stories"]


news_tab, politics_tab = st.tabs(["🗞 뉴스 브리핑", "🏛 정치 브리핑"])

with news_tab:
    stories = render_briefing("news", "아직 브리핑이 없습니다. 09시부터 3시간마다 갱신됩니다.")
    if stories is not None:
        category = st.segmented_control("카테고리", [ALL, *FEEDS], default=ALL, required=True, label_visibility="collapsed")
        for story in stories:
            if category in (ALL, story["category"]):
                render_story(story)

with politics_tab:
    for story in render_briefing("politics", "아직 정치 브리핑이 없습니다. 매일 08:30에 갱신됩니다.") or []:
        render_story(story)
