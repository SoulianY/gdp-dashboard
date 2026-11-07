package com.souliany.notilog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 브리핑 알림: gdp-dashboard 발송 장치가 남기는 data/notify.json(최근 알림 20개)을 15분마다 읽어
 * 새 항목을 폰 알림으로 띄운다. 누르면 그 브리핑 페이지가 열린다.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("briefing", Context.MODE_PRIVATE)
        val items = try {
            JSONArray(URL(FEED).readText())
        } catch (e: FileNotFoundException) {
            // 아직 알림 파일이 없음: 이후 생기는 항목은 모두 새 알림이다.
            if (!prefs.contains("lastId")) prefs.edit().putString("lastId", "").apply()
            return Result.success()
        } catch (e: IOException) {
            return Result.retry()
        }
        val last = prefs.getString("lastId", null)
        val fresh = (0 until items.length()).map { items.getJSONObject(it) }.filter { last != null && it.getString("id") > last }
        // 처음 실행할 때는 지난 알림을 쏟아내지 않고 기준만 잡는다.
        fresh.forEach(::show)
        if (items.length() > 0) prefs.edit().putString("lastId", items.getJSONObject(items.length() - 1).getString("id")).apply()
        return Result.success()
    }

    private fun show(item: org.json.JSONObject) {
        val ctx = applicationContext
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, item.getString("id").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(item.getString("url"))),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(item.getString("title"))
            .setContentText(item.getString("body"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.getString("body")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(item.getString("id").hashCode(), n)
        } catch (e: SecurityException) {
            // 알림 권한이 꺼져 있으면 띄우지 않는다.
        }
    }

    companion object {
        private const val FEED = "https://raw.githubusercontent.com/SoulianY/gdp-dashboard/main/data/notify.json"
        private const val CHANNEL = "briefing"

        fun schedule(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "브리핑", NotificationManager.IMPORTANCE_DEFAULT),
            )
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "briefing",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BriefingWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
