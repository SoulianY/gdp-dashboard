package com.souliany.notilog

import android.app.Notification
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * 시스템이 모든 앱의 알림을 넘겨주는 서비스. 사람이 읽을 알림만 골라 Outbox에 기록하고,
 * 사용자가 끈 앱의 알림은 기록한 뒤 알림창에서 지운다.
 */
class NotiListener : NotificationListenerService() {
    private lateinit var outbox: Outbox
    private lateinit var settings: Settings

    // 같은 알림이 내용·발생 시각 그대로 다시 게시되는 경우(갱신·재알림)를 거른다.
    // 발생 시각(when)까지 보는 이유: 삼성 월렛처럼 같은 알림 자리에 같은 금액·가게의 새 결제를 덮어 띄우는 앱이 있다.
    private val lastSeen = HashMap<String, Int>()

    override fun onCreate() {
        super.onCreate()
        outbox = Outbox(this)
        settings = Settings(this)
    }

    override fun onListenerConnected() {
        UploadWorker.schedule(this)
        BriefingWorker.schedule(this)
        // 지금 알림창에 있는 앱도 앱별 스위치 목록에 바로 보이게 한다.
        activeNotifications.filter { it.packageName != packageName }.forEach(::markSeen)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        markSeen(sbn)
        record(sbn)
        if (shouldHide(sbn)) cancelNotification(sbn.key)
    }

    private fun record(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return  // 음악·영상 재생 컨트롤

        val x = n.extras
        val (title, text) = titleText(sbn)
        if (title.isBlank() && text.isBlank()) return

        val hash = (title + "\u0000" + text + "\u0000" + sbn.notification.`when`).hashCode()
        if (lastSeen.put(sbn.key, hash) == hash) return

        val record = JSONObject()
            .put("time", Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toOffsetDateTime().toString())
            .put("app", appLabel(sbn.packageName, sbn.user))
            .put("package", sbn.packageName)
            .put("title", title)
            .put("text", text)
        // 카톡은 방 이름을 숨김 대화 제목·요약 칸에 넣기도 한다.
        (x.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: x.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
            ?.let { record.put("sub", it.toString()) }
        (x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: x.getCharSequence(HIDDEN_CONVERSATION_TITLE))
            ?.let { record.put("conversation", it.toString()) }
        if (x.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) record.put("group", true)
        n.channelId?.let { record.put("channel", it) }
        n.category?.let { record.put("category", it) }
        // 카톡이 오픈채팅·방 이름을 어디에 넣는지 찾기 위한 진단 기록 (규칙을 정하면 지운다)
        if (sbn.packageName == KAKAO) record.put("debug", kakaoDebug(sbn))

        outbox.append(record.toString())
        UploadWorker.soon(this)
    }

    /**
     * 앱별 스위치 목록에 올린다. 상시 알림(걸음 수·실행 중 표시 등 포그라운드 서비스)은 안드로이드가
     * 다른 앱이 지우는 것을 막아 숨길 수 없으므로 목록에서 뺀다(끄기로 해 둔 앱은 다시 켤 수 있게 남김).
     */
    private fun markSeen(sbn: StatusBarNotification) {
        if (!sbn.isClearable) {
            if (sbn.packageName !in settings.hidden) settings.forget(sbn.packageName)
            return
        }
        val work = if (sbn.user != Process.myUserHandle()) " (업무)" else ""
        settings.markSeen(sbn.packageName, appLabel(sbn.packageName, sbn.user) + work, sbn.postTime)
    }

    private fun titleText(sbn: StatusBarNotification): Pair<String, String> {
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        return title to text
    }

    /**
     * 끈 앱이거나, 개인 프로필 알림에 숨길 단어가 있거나, 카톡 오픈채팅이 1분 안에 이미 떴으면 숨긴다.
     * 통화·알람·재생 등은 건드리지 않는다.
     */
    private fun shouldHide(sbn: StatusBarNotification): Boolean {
        if (!sbn.isClearable || sbn.notification.category in KEEP_CATEGORIES) return false
        if (sbn.packageName in settings.hidden) return true
        if (sbn.user != Process.myUserHandle()) return false  // 업무 프로필은 단어 필터 제외
        val (title, text) = titleText(sbn)
        if (settings.blockWordList().any { title.contains(it, ignoreCase = true) || text.contains(it, ignoreCase = true) }) return true
        return openChatThrottled(sbn, title + "\n" + text)
    }

    private fun kakaoDebug(sbn: StatusBarNotification): JSONObject {
        val n = sbn.notification
        fun str(v: Any?): String = when (v) {
            is Bundle -> v.keySet().joinToString(", ", "{", "}") { "$it=${str(v.get(it))}" }
            is Array<*> -> v.joinToString(" | ", "[", "]") { str(it) }
            else -> v.toString()
        }.take(300)
        val extras = JSONObject()
        n.extras.keySet().forEach { extras.put(it, str(n.extras.get(it))) }
        return JSONObject()
            .put("extras", extras)
            .put("key", sbn.key)
            .put("tag", sbn.tag ?: "")
            .put("id", sbn.id)
            .put("group", n.group ?: "")
            .put("sortKey", n.sortKey ?: "")
            .put("shortcutId", n.shortcutId ?: "")
            .put("locusId", n.locusId?.id ?: "")
    }

    // 오픈채팅 방(알림 key)별로 마지막으로 알림을 띄운 시각
    private val openChatShown = HashMap<String, Long>()

    /** 카톡 오픈채팅은 방마다 1분에 한 번만 띄운다. 내 이름이 언급되면 항상 띄운다. */
    private fun openChatThrottled(sbn: StatusBarNotification, content: String): Boolean {
        if (sbn.packageName != KAKAO || sbn.notification.channelId?.contains("open", ignoreCase = true) != true) return false
        if (settings.myNameList().any { content.contains(it, ignoreCase = true) }) {
            openChatShown[sbn.key] = sbn.postTime
            return false
        }
        val last = openChatShown[sbn.key]
        if (last != null && sbn.postTime - last < 60_000) return true
        openChatShown[sbn.key] = sbn.postTime
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // 내가 숨긴 알림은 기억해 둬야 같은 내용 재게시를 다시 기록하지 않는다.
        if (reason != REASON_LISTENER_CANCEL) lastSeen.remove(sbn.key)
    }

    /** 업무 프로필 앱(회사 Teams 등)도 이름을 읽도록 알림을 보낸 사용자 프로필 기준으로 찾는다. */
    private fun appLabel(pkg: String, user: UserHandle): String = try {
        val info = getSystemService(LauncherApps::class.java).getApplicationInfo(pkg, 0, user)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val KAKAO = "com.kakao.talk"
        const val HIDDEN_CONVERSATION_TITLE = "android.hiddenConversationTitle"
        val KEEP_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_NAVIGATION,
        )
    }
}
