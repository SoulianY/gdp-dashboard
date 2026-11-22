package com.souliany.notilog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 15분마다 두 저장소의 브리핑 파일 폴더(오늘·어제)를 보고 새 파일을 받아 온다.
 *  - 비공개 push-inbox/briefings: 알림·마감·메일 (설정의 저장소·토큰)
 *  - 공개 gdp-dashboard/data/briefings: 뉴스·정치·커뮤니티
 * 새 브리핑은 폰 알림으로 띄우고, 누르면 앱의 브리핑 화면이 열린다.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val store = BriefingStore(applicationContext)
        val first = !store.initialized
        val sources = buildList {
            if (settings.configured) add(settings.repo to "briefings")
            add(PUBLIC_REPO to "data/briefings")
        }
        val today = LocalDate.now(KST)
        val fresh = mutableListOf<Briefing>()
        try {
            if (settings.configured) uploadReadState(settings, store)
            for ((repo, root) in sources) {
                for (back in 0 until if (first) KEEP_DAYS else 2) {
                    val date = today.minusDays(back.toLong())
                    val names = list(repo, "$root/$date", settings.token)
                    for (name in names) {
                        if (!store.has("$date/${name.removeSuffix(".json")}")) {
                            fresh += store.save(fetch(repo, "$root/$date/$name", settings.token))
                        }
                    }
                    // 안 읽은 알림 브리핑은 다음 회차에 합쳐지고 push-inbox에서 지워진다. 여기서도 지운다.
                    if (repo == settings.repo) {
                        val kept = names.map { "$date/${it.removeSuffix(".json")}" }.toSet()
                        store.all().filter { it.kind == "noti" && it.id.startsWith("$date/") && it.id !in kept }.forEach { store.delete(it.id) }
                    }
                }
            }
        } catch (e: IOException) {
            return Result.retry()
        }
        if (first) {
            store.markRead(fresh.map { it.id })
            store.initialized = true
        } else {
            // 안 읽은 이전 회차는 새 회차에 합친다(알림 브리핑은 push-inbox가 이미 합쳐서 보낸다). 합쳐져 지워진 회차는 알리지 않는다.
            fresh.sortedBy { OffsetDateTime.parse(it.created) }
                .map { if (it.kind == "noti") it else store.mergeUnread(it) }
                .filter { store.get(it.id) != null }
                .forEach(::show)
        }
        store.prune(KEEP_DAYS)
        return Result.success()
    }

    /** 앱에서 어디까지 읽었는지 push-inbox의 state/read.json에 올린다(바뀌었을 때만). */
    private fun uploadReadState(settings: Settings, store: BriefingStore) {
        val through = store.notiReadThrough
        if (through.isEmpty() || through == store.notiReadUploaded) return
        val url = "https://api.github.com/repos/${settings.repo}/contents/$READ_PATH"
        val sha = try {
            JSONObject(get(url, settings.token, "application/vnd.github+json")).getString("sha")
        } catch (e: FileNotFoundException) {
            null
        }
        val body = JSONObject().put("noti", through).toString(1).toByteArray()
        val payload = JSONObject()
            .put("message", "read: noti $through")
            .put("content", Base64.encodeToString(body, Base64.NO_WRAP))
            .apply { sha?.let { put("sha", it) } }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) throw IOException("read.json HTTP ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
        store.notiReadUploaded = through
    }

    /** 폴더의 .json 파일 이름들. 폴더가 없으면(그날 브리핑 없음) 빈 목록. */
    private fun list(repo: String, path: String, token: String): List<String> = try {
        val arr = JSONArray(get("https://api.github.com/repos/$repo/contents/$path", token, "application/vnd.github+json"))
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("type") == "file" && it.getString("name").endsWith(".json") }
            .map { it.getString("name") }
    } catch (e: FileNotFoundException) {
        emptyList()
    }

    private fun fetch(repo: String, path: String, token: String) =
        get("https://api.github.com/repos/$repo/contents/$path", token, "application/vnd.github.raw+json")

    private fun get(url: String, token: String, accept: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("Accept", accept)
            if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun show(b: Briefing) {
        val ctx = applicationContext
        val open = PendingIntent.getActivity(
            ctx, notificationId(b.id),
            Intent(ctx, BriefingActivity::class.java).putExtra(BriefingActivity.EXTRA_ID, b.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(b.title)
            .setContentText(b.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(b.summary))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notificationId(b.id), n)
        } catch (e: SecurityException) {
            // 알림 권한이 꺼져 있으면 띄우지 않는다(목록에는 NEW로 남는다).
        }
    }

    companion object {
        private const val PUBLIC_REPO = "SoulianY/gdp-dashboard"
        private const val CHANNEL = "briefing"
        const val KEEP_DAYS = 7L
        private const val READ_PATH = "state/read.json"
        private val KST = ZoneId.of("Asia/Seoul")
        fun notificationId(briefingId: String) = briefingId.hashCode()

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "브리핑", NotificationManager.IMPORTANCE_DEFAULT),
            )
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "briefing",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BriefingWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        /** 앱의 새로고침. 끝나면 목록을 다시 그릴 수 있게 작업 ID를 돌려준다. */
        fun refresh(context: Context) = OneTimeWorkRequestBuilder<BriefingWorker>().setConstraints(online).build().also {
            WorkManager.getInstance(context).enqueueUniqueWork("briefing-now", ExistingWorkPolicy.REPLACE, it)
        }.id
    }
}
