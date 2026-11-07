package com.souliany.notilog

import org.json.JSONArray
import org.json.JSONObject

/**
 * 브리핑 한 건. 알림·마감·메일(push-inbox/briefings)과 뉴스·정치(gdp-dashboard/data/briefings)가 같은 형식이다.
 * id는 "<날짜>/<시각>-<종류>".
 */
data class Briefing(
    val id: String,
    val kind: String,
    val title: String,
    val created: String,
    val summary: String,
    val url: String,
    val sections: List<Section>,
) {
    data class Section(val title: String, val items: List<Item>)
    data class Item(val text: String, val sub: String, val link: String)

    companion object {
        /** 종류별 이름. 순서가 목록의 영역·필터 순서다. */
        val KINDS = linkedMapOf("noti" to "알림", "daily" to "마감", "news" to "뉴스", "politics" to "정치", "mail" to "메일")

        fun parse(o: JSONObject) = Briefing(
            id = o.getString("id"),
            kind = o.getString("kind"),
            title = o.getString("title"),
            created = o.getString("created"),
            summary = o.optString("summary"),
            url = o.optString("url"),
            sections = o.optJSONArray("sections").objects().map { s ->
                Section(s.getString("title"), s.optJSONArray("items").objects().map {
                    Item(it.getString("text"), it.optString("sub"), it.optString("link"))
                })
            },
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
