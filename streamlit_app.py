from datetime import datetime

import streamlit as st

from news.briefing import KST, load
from news.sources import FEEDS

ALL = "전체"

st.set_page_config(page_title="뉴스 브리핑", page_icon="🗞", layout="wide")


def render_item(item: dict) -> None:
    with st.container(border=True):
        image_col, text_col = st.columns([1, 3])
        if item["image"]:
            image_col.image(item["image"], width="stretch")
        text_col.markdown(f"#### [{item['title']}]({item['link']})")
        published = datetime.fromisoformat(item["published"]).astimezone(KST)
        text_col.caption(f"{item['category']} · {item['source']} · {published:%m/%d %H:%M} · {item['outlets']}개 언론사 보도")
        if item["summary"]:
            text_col.write(item["summary"])
        if len(item["articles"]) > 1:
            with text_col.expander(f"관련 기사 {len(item['articles'])}건"):
                for a in item["articles"]:
                    st.markdown(f"- [{a['title']}]({a['link']}) — {a['source']}")


def render_snapshot(kind: str, empty_message: str) -> list[dict] | None:
    snapshot = load(kind)
    if not snapshot:
        st.info(empty_message)
        return None
    generated = datetime.fromisoformat(snapshot["generated_at"])
    st.caption(f"{generated:%Y-%m-%d %H:%M} 기준 · 최근 24시간 · 이슈(보도 언론사 수) 순")
    return snapshot["items"]


news_tab, politics_tab = st.tabs(["🗞 뉴스 브리핑", "🏛 정치 브리핑"])

with news_tab:
    items = render_snapshot("news", "아직 수집된 뉴스가 없습니다. 09시부터 3시간마다 갱신됩니다.")
    if items is not None:
        category = st.segmented_control("카테고리", [ALL, *FEEDS], default=ALL, required=True, label_visibility="collapsed")
        for item in items:
            if category in (ALL, item["category"]):
                render_item(item)

with politics_tab:
    items = render_snapshot("politics", "아직 정치 브리핑이 없습니다. 매일 08:30에 갱신됩니다.")
    for item in items or []:
        render_item(item)
