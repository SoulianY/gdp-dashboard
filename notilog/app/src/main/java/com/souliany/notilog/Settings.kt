package com.souliany.notilog

import android.content.Context

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

    val configured get() = repo.contains('/') && token.isNotEmpty()
}
