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

    /** 알림을 기록만 하고 알림창에서 지울 앱(패키지 이름). 기본은 비어 있어 새 앱 알림은 그대로 뜬다. */
    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!
        set(v) = prefs.edit().putStringSet("hidden", v).apply()

    /** 제목·내용에 들어 있으면 알림을 숨길 단어(쉼표로 구분). 업무 프로필 앱에는 적용하지 않는다. */
    var blockWords: String
        get() = prefs.getString("blockWords", "(광고)").orEmpty()
        set(v) = prefs.edit().putString("blockWords", v).apply()

    fun blockWordList(): List<String> = blockWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** 카톡 오픈채팅에서 이 이름이 언급되면 1분 제한 없이 알림을 띄운다(쉼표로 구분). */
    var myNames: String
        get() = prefs.getString("myNames", "양원철, 원철, 양군").orEmpty()
        set(v) = prefs.edit().putString("myNames", v).apply()

    fun myNameList(): List<String> = myNames.split(',').map { it.trim() }.filter { it.isNotEmpty() }

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

    fun forget(pkg: String) = synchronized(this) {
        val o = JSONObject(prefs.getString("seen", "{}")!!)
        if (o.remove(pkg) != null) prefs.edit().putString("seen", o.toString()).apply()
    }

    fun setShown(pkg: String, shown: Boolean) {
        hidden = if (shown) hidden - pkg else hidden + pkg
    }

    /** 알림을 보낸 카톡 단톡방: 방 ID -> (이름 또는 최근 메시지, 마지막 알림 시각). */
    fun rooms(): List<SeenApp> {
        val o = JSONObject(prefs.getString("rooms", "{}")!!)
        return o.keys().asSequence()
            .map { o.getJSONObject(it).let { r -> SeenApp(it, r.getString("label"), r.getLong("last")) } }
            .sortedByDescending { it.last }
            .toList()
    }

    fun markRoom(id: String, label: String, time: Long) = synchronized(this) {
        val o = JSONObject(prefs.getString("rooms", "{}")!!)
        o.put(id, JSONObject().put("label", label).put("last", time))
        prefs.edit().putString("rooms", o.toString()).apply()
    }

    /** 1분에 한 번만 알림을 띄울 카톡 방 ID. */
    var throttledRooms: Set<String>
        get() = prefs.getStringSet("throttledRooms", emptySet())!!
        set(v) = prefs.edit().putStringSet("throttledRooms", v).apply()

    fun setThrottled(id: String, on: Boolean) {
        throttledRooms = if (on) throttledRooms + id else throttledRooms - id
    }

    val configured get() = repo.contains('/') && token.isNotEmpty()
}

data class SeenApp(val pkg: String, val label: String, val last: Long)
