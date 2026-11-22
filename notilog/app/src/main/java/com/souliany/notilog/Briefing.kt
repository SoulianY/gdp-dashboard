package com.souliany.notilog

import org.json.JSONArray
import org.json.JSONObject

/**
 * 브리핑 한 건. 알림·마감·메일(push-inbox/briefings)과 뉴스·정치·커뮤니티(gdp-dashboard/data/briefings)가 같은 형식이다.
 * id는 "<날짜>/<시각>-<종류>".
 */
data class Briefing(
    val id: String,
    val kind: String,
    val title: String,
    val created: String,
    val summary: String,
    val sections: List<Section>,
    val merged: Int = 1,  // 합쳐진 회차 수 (앱에서 안 읽은 이전 회차를 합친 것)
) {
    /** app: 배지로 보여 줄 앱 이름, chat: 메시지(채팅방) 영역, fold: 접어서 보여 줌(제외 목록) */
    data class Section(val title: String, val app: String, val chat: Boolean, val fold: Boolean, val items: List<Item>)
    /** image: 사진 URL(뉴스·정치·커뮤니티), pkg: 알림을 보낸 앱(누르면 그 앱을 연다) */
    data class Item(val text: String, val sub: String, val link: String, val image: String, val pkg: String) {
        /** 같은 기사·글인지: 링크가 있으면 링크, 없으면 본문 */
        val key get() = link.ifEmpty { text }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind).put("title", title).put("created", created).put("summary", summary).put("merged", merged)
        .put("sections", JSONArray(sections.map { s ->
            JSONObject().put("title", s.title).put("app", s.app).put("group", if (s.chat) "chat" else "").put("fold", s.fold)
                .put("items", JSONArray(s.items.map {
                    JSONObject().put("text", it.text).put("sub", it.sub).put("link", it.link).put("image", it.image).put("package", it.pkg)
                }))
        }))

    companion object {
        /** 종류별 이름. 순서가 목록의 영역·필터 순서다. */
        val KINDS = linkedMapOf("noti" to "알림", "daily" to "마감", "news" to "뉴스", "politics" to "정치", "community" to "커뮤니티", "mail" to "메일")

        /**
         * 안 읽은 이전 회차들을 최신 회차 하나로 합친다(뉴스·정치·커뮤니티·마감·메일).
         * 같은 제목의 섹션끼리 모으고, 섹션 안은 최신 회차 것이 먼저, 같은 기사·글(링크)은 최신 것만 남긴다.
         * id·시각·요약은 최신 회차를 따른다. 알림 브리핑은 push-inbox가 원본에서 다시 집계하므로 여기서 합치지 않는다.
         */
        fun merge(list: List<Briefing>): Briefing {
            val newest = list.maxBy { java.time.OffsetDateTime.parse(it.created) }
            val sections = linkedMapOf<String, Section>()
            for (b in list.sortedByDescending { java.time.OffsetDateTime.parse(it.created) }) for (s in b.sections) {
                val cur = sections[s.title]
                sections[s.title] = cur?.copy(items = cur.items + s.items.filter { i -> cur.items.none { it.key == i.key } }) ?: s
            }
            val count = list.sumOf { it.merged }
            return newest.copy(title = "${newest.title} (못 본 ${count}회 합침)", sections = sections.values.toList(), merged = count)
        }

        fun parse(o: JSONObject) = Briefing(
            id = o.getString("id"),
            kind = o.getString("kind"),
            title = o.getString("title"),
            created = o.getString("created"),
            summary = o.optString("summary"),
            sections = o.optJSONArray("sections").objects().map { s ->
                Section(s.getString("title"), s.optString("app"), s.optString("group") == "chat", s.optBoolean("fold"),
                    s.optJSONArray("items").objects().map {
                    Item(it.getString("text"), it.optString("sub"), it.optString("link"), it.optString("image"), it.optString("package"))
                })
            },
            merged = o.optInt("merged", 1),
        )

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
