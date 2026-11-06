package com.souliany.notilog

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * 시스템이 모든 앱의 알림을 넘겨주는 서비스. 사람이 읽을 알림만 골라 Outbox에 기록하고,
 * '다른 앱 알림 숨기기'가 켜져 있으면 예외 앱이 아닌 알림을 알림창에서 지운다.
 */
class NotiListener : NotificationListenerService() {
    private lateinit var outbox: Outbox
    private lateinit var settings: Settings

    // 같은 알림이 내용 그대로 다시 게시되는 경우(갱신·재알림)를 거른다.
    private val lastSeen = HashMap<String, Int>()

    override fun onCreate() {
        super.onCreate()
        outbox = Outbox(this)
        settings = Settings(this)
    }

    override fun onListenerConnected() {
        UploadWorker.schedule(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        record(sbn)
        if (shouldHide(sbn)) cancelNotification(sbn.key)
    }

    private fun record(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return  // 음악·영상 재생 컨트롤

        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val hash = (title + "\u0000" + text).hashCode()
        if (lastSeen.put(sbn.key, hash) == hash) return

        val record = JSONObject()
            .put("time", Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
            .put("app", appLabel(sbn.packageName))
            .put("package", sbn.packageName)
            .put("title", title)
            .put("text", text)
        x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { record.put("sub", it.toString()) }
        x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.let { record.put("conversation", it.toString()) }
        n.category?.let { record.put("category", it) }

        outbox.append(record.toString())
        UploadWorker.soon(this)
    }

    private fun shouldHide(sbn: StatusBarNotification): Boolean =
        settings.hideOthers &&
            sbn.isClearable &&
            sbn.packageName !in settings.exempt &&
            sbn.packageName !in SYSTEM &&
            sbn.notification.category !in KEEP_CATEGORIES

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // 내가 숨긴 알림은 기억해 둬야 같은 내용 재게시를 다시 기록하지 않는다.
        if (reason != REASON_LISTENER_CANCEL) lastSeen.remove(sbn.key)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        val SYSTEM = setOf("android", "com.android.systemui")
        val KEEP_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_NAVIGATION,
        )
    }
}
