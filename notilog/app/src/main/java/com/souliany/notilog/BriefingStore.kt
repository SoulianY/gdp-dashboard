package com.souliany.notilog

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/** 받아 온 브리핑 파일(최근 7일, 지난 것은 지운다)과 읽음 표시. 앱의 브리핑 목록은 여기서 읽는다. */
class BriefingStore(private val context: Context) {
    private val dir = File(context.filesDir, "briefings").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("briefing", Context.MODE_PRIVATE)

    private fun file(id: String) = File(dir, id.replace('/', '_') + ".json")

    /** 받아 온 적 있는 브리핑인지(다른 회차에 합쳐져 지운 것 포함). 동기화가 다시 받지 않게 한다. */
    fun has(id: String) = file(id).exists() || id in mergedAway

    private val mergedAway: Set<String> get() = prefs.getStringSet("mergedAway", emptySet())!!

    /** b보다 먼저 온 같은 종류의 안 읽은 브리핑이 있으면 b에 합치고 그 회차들은 지운다. 결과(b 또는 합친 것)를 돌려준다. */
    fun mergeUnread(b: Briefing): Briefing {
        val older = all().filter { it.kind == b.kind && it.id != b.id && isNew(it.id) && OffsetDateTime.parse(it.created) < OffsetDateTime.parse(b.created) }
        if (older.isEmpty()) return b
        val merged = Briefing.merge(older + b)
        file(b.id).writeText(merged.toJson().toString())
        older.forEach { delete(it.id) }
        prefs.edit().putStringSet("mergedAway", mergedAway + older.map { it.id }).apply()
        return merged
    }

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

    /** 읽음 표시. 읽은 브리핑의 폰 알림도 지운다(다른 앱처럼 앱에서 읽으면 알림이 사라짐).
     *  알림 브리핑이면 어디까지 읽었는지(notiReadThrough)도 남긴다. BriefingWorker가 push-inbox에 올리면,
     *  다음 회차가 그 뒤 안 읽은 회차를 합쳐 다시 집계한다. */
    fun markRead(ids: Collection<String>) {
        prefs.edit().putStringSet("read", read + ids).apply()
        val nm = NotificationManagerCompat.from(context)
        ids.forEach { nm.cancel(BriefingWorker.notificationId(it)) }
        ids.mapNotNull(::get).filter { it.kind == "noti" }.maxByOrNull { OffsetDateTime.parse(it.created) }?.let {
            if (notiReadThrough.isEmpty() || OffsetDateTime.parse(it.created) > OffsetDateTime.parse(notiReadThrough)) {
                notiReadThrough = it.created
            }
        }
    }

    /** 읽은 알림 브리핑 중 가장 최근 created. 올린 값은 notiReadUploaded. */
    var notiReadThrough: String
        get() = prefs.getString("notiReadThrough", "").orEmpty()
        set(v) = prefs.edit().putString("notiReadThrough", v).apply()

    var notiReadUploaded: String
        get() = prefs.getString("notiReadUploaded", "").orEmpty()
        set(v) = prefs.edit().putString("notiReadUploaded", v).apply()

    fun delete(id: String) {
        file(id).delete()
        NotificationManagerCompat.from(context).cancel(BriefingWorker.notificationId(id))
    }

    fun prune(keepDays: Long) {
        val cutoff = LocalDate.now().minusDays(keepDays).toString()
        dir.listFiles().orEmpty().filter { it.name.take(10) < cutoff }.forEach { it.delete() }
        val kept = dir.listFiles().orEmpty().map { it.name.removeSuffix(".json").replaceFirst('_', '/') }.toSet()
        prefs.edit().putStringSet("read", read intersect kept)
            .putStringSet("mergedAway", mergedAway.filter { it.take(10) >= cutoff }.toSet()).apply()
    }
}
