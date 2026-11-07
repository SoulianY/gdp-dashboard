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
import org.json.JSONArray
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 15분마다 두 저장소의 브리핑 파일 폴더(오늘·어제)를 보고 새 파일을 받아 온다.
 *  - 비공개 push-inbox/briefings: 알림·마감·메일 (설정의 저장소·토큰)
 *  - 공개 gdp-dashboard/data/briefings: 뉴스·정치
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
            for ((repo, root) in sources) {
                for (back in 0 until if (first) KEEP_DAYS else 2) {
                    val date = today.minusDays(back.toLong())
                    for (name in list(repo, "$root/$date", settings.token)) {
                        if (!store.has("$date/${name.removeSuffix(".json")}")) {
                            fresh += store.save(fetch(repo, "$root/$date/$name", settings.token))
                        }
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
            fresh.forEach(::show)
        }
        store.prune(KEEP_DAYS)
        return Result.success()
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
            ctx, b.id.hashCode(),
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
            NotificationManagerCompat.from(ctx).notify(b.id.hashCode(), n)
        } catch (e: SecurityException) {
            // 알림 권한이 꺼져 있으면 띄우지 않는다(목록에는 NEW로 남는다).
        }
    }

    companion object {
        private const val PUBLIC_REPO = "SoulianY/gdp-dashboard"
        private const val CHANNEL = "briefing"
        const val KEEP_DAYS = 30L
        private val KST = ZoneId.of("Asia/Seoul")
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
