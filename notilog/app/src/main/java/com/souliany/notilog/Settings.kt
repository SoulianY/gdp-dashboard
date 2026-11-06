package com.souliany.notilog

import android.content.Context
import org.json.JSONObject

/** 업로드 대상: 비공개 GitHub 저장소(owner/name)와 그 저장소 전용 Fine-grained PAT. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var repo: String
        get() = prefs.getString("repo", "").orEmpty()
        set(v) = prefs.edit().putString("repo", v.trim()).apply()

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    var lastResult: String
        get() = prefs.getString("lastResult", "").orEmpty()
        set(v) = prefs.edit().putString("lastResult", v).apply()

    /** 예외 앱이 아닌 알림을 기록 후 알림창에서 지운다. */
    var hideOthers: Boolean
        get() = prefs.getBoolean("hideOthers", false)
        set(v) = prefs.edit().putBoolean("hideOthers", v).apply()

    /** 알림을 그대로 띄울 앱(패키지 이름). */
    var exempt: Set<String>
        get() = prefs.getStringSet("exempt", emptySet())!!
        set(v) = prefs.edit().putStringSet("exempt", v).apply()

    /** 알림을 보낸 적 있는 앱: 패키지 -> (이름, 마지막 알림 시각). 앱별 스위치 목록에 쓴다. */
    fun seen(): List<SeenApp> {
        val o = JSONObject(prefs.getString("seen", "{}")!!)
        return o.keys().asSequence()
            .map { o.getJSONObject(it).let { a -> SeenApp(it, a.getString("label"), a.getLong("last")) } }
            .sortedByDescending { it.last }
            .toList()
    }

    fun markSeen(pkg: String, label: String, time: Long) = synchronized(this) {
        val o = JSONObject(prefs.getString("seen", "{}")!!)
        o.put(pkg, JSONObject().put("label", label).put("last", maxOf(time, o.optJSONObject(pkg)?.optLong("last") ?: 0)))
        prefs.edit().putString("seen", o.toString()).apply()
    }

    fun setExempt(pkg: String, on: Boolean) {
        exempt = if (on) exempt + pkg else exempt - pkg
    }

    val configured get() = repo.contains('/') && token.isNotEmpty()
}

data class SeenApp(val pkg: String, val label: String, val last: Long)
