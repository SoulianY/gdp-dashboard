package com.souliany.notilog

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 묶음 파일을 GitHub Contents API로 비공개 저장소의 inbox/<날짜>/<시각>.jsonl에 올린다.
 * 파일마다 새 경로라 기존 파일을 고치지 않으므로 충돌·sha 관리가 없다.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    // 주기 작업과 '지금 올리기'가 겹쳐 같은 묶음을 두 번 올리지 않게 한다.
    override suspend fun doWork(): Result = running.withLock { upload() }

    private fun upload(): Result {
        val settings = Settings(applicationContext)
        if (!settings.configured) return Result.success()

        // 올리는 동안 새로 온 알림까지 비울 때까지 반복한다.
        val outbox = Outbox(applicationContext)
        while (true) {
            val batches = outbox.seal().ifEmpty { break }
            for (batch in batches) {
                val code = put(settings, batch.name.removeSuffix(".jsonl").toLong(), batch.readBytes())
                // 422: 같은 경로가 이미 있음 = 앞선 시도가 올라갔지만 응답만 못 받은 경우
                if (code !in 200..299 && code != 422) {
                    settings.lastResult = "${LocalDateTime.now().withNano(0)} 실패 (HTTP $code)"
                    return Result.retry()
                }
                batch.delete()
            }
        }
        settings.lastResult = "${LocalDateTime.now().withNano(0)} 성공"
        return Result.success()
    }

    private fun put(settings: Settings, sealedAt: Long, body: ByteArray): Int {
        val path = Instant.ofEpochMilli(sealedAt).atZone(ZoneId.systemDefault()).format(PATH)
        val conn = URL("https://api.github.com/repos/${settings.repo}/contents/$path").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val payload = JSONObject()
                .put("message", "inbox: $path")
                .put("content", Base64.encodeToString(body, Base64.NO_WRAP))
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            conn.responseCode
        } catch (e: IOException) {
            -1
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val running = Mutex()
        private val PATH = DateTimeFormatter.ofPattern("'inbox/'yyyy-MM-dd'/'HHmmss-SSS'.jsonl'")
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** 알림이 오면 3분 뒤 올린다. 그 사이 온 알림은 같은 묶음으로 간다. */
        fun soon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload-soon",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInitialDelay(3, TimeUnit.MINUTES)
                    .setConstraints(online)
                    .build(),
            )
        }

        /** 실패한 묶음 재시도와 빠진 알림을 챙기는 안전망. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "upload",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload-now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(online).build(),
            )
        }
    }
}
