package com.souliany.notilog

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/** 받아 온 브리핑 파일(최근 30일, 지난 것은 지운다)과 읽음 표시. 앱의 브리핑 목록은 여기서 읽는다. */
class BriefingStore(private val context: Context) {
    private val dir = File(context.filesDir, "briefings").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("briefing", Context.MODE_PRIVATE)

    private fun file(id: String) = File(dir, id.replace('/', '_') + ".json")

    fun has(id: String) = file(id).exists()

    fun save(json: String): Briefing {
        val b = Briefing.parse(JSONObject(json))
        file(b.id).writeText(json)
        return b
    }

    fun get(id: String): Briefing? = file(id).takeIf { it.exists() }?.let { Briefing.parse(JSONObject(it.readText())) }

    /** 최신순 */
    fun all(): List<Briefing> = dir.listFiles().orEmpty()
        .mapNotNull { runCatching { Briefing.parse(JSONObject(it.readText())) }.getOrNull() }
        .sortedByDescending { OffsetDateTime.parse(it.created) }

    /** 처음 동기화 때는 지난 브리핑을 모두 읽은 것으로 두고 알림도 띄우지 않는다. */
    var initialized: Boolean
        get() = prefs.getBoolean("initialized", false)
        set(v) = prefs.edit().putBoolean("initialized", v).apply()

    private val read: Set<String> get() = prefs.getStringSet("read", emptySet())!!

    fun isNew(id: String) = id !in read

    /** 읽음 표시. 읽은 브리핑의 폰 알림도 지운다(다른 앱처럼 앱에서 읽으면 알림이 사라짐). */
    fun markRead(ids: Collection<String>) {
        prefs.edit().putStringSet("read", read + ids).apply()
        val nm = NotificationManagerCompat.from(context)
        ids.forEach { nm.cancel(BriefingWorker.notificationId(it)) }
    }

    fun prune(keepDays: Long) {
        val cutoff = LocalDate.now().minusDays(keepDays).toString()
        dir.listFiles().orEmpty().filter { it.name.take(10) < cutoff }.forEach { it.delete() }
        val kept = dir.listFiles().orEmpty().map { it.name.removeSuffix(".json").replaceFirst('_', '/') }.toSet()
        prefs.edit().putStringSet("read", read intersect kept).apply()
    }
}
