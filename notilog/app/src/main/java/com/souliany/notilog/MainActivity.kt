package com.souliany.notilog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.text.format.DateUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        val repo = findViewById<EditText>(R.id.repo).apply { setText(settings.repo) }
        val token = findViewById<EditText>(R.id.token).apply { setText(settings.token) }

        findViewById<Switch>(R.id.hide).apply {
            isChecked = settings.hideOthers
            setOnCheckedChangeListener { _, on -> settings.hideOthers = on }
        }
        findViewById<Button>(R.id.grant).setOnClickListener {
            startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            settings.repo = repo.text.toString()
            settings.token = token.text.toString()
            UploadWorker.schedule(this)
            UploadWorker.runNow(this)
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /** 알림을 보낸 앱마다 '알림 띄우기' 스위치. 켠 앱은 숨기지 않는다(예외). */
    private fun renderApps() {
        val list = findViewById<LinearLayout>(R.id.apps)
        list.removeAllViews()
        val apps = settings.seen()
        if (apps.isEmpty()) {
            list.addView(TextView(this).apply { text = "알림이 오면 여기에 앱이 나타납니다." })
            return
        }
        val exempt = settings.exempt
        for (app in apps) {
            list.addView(Switch(this).apply {
                text = "${app.label}  ·  ${DateUtils.getRelativeTimeSpanString(app.last)}"
                isChecked = app.pkg in exempt
                setPadding(0, 12, 0, 12)
                setOnCheckedChangeListener { _, on -> settings.setExempt(app.pkg, on) }
            })
        }
    }

    private fun render() {
        renderApps()
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        findViewById<TextView>(R.id.status).text = buildString {
            appendLine("알림 접근 권한: ${if (granted) "허용됨" else "필요함"}")
            appendLine("업로드 설정: ${if (settings.configured) settings.repo else "필요함"}")
            appendLine("올리기 대기: ${Outbox(this@MainActivity).count()}건")
            append("마지막 업로드: ${settings.lastResult.ifEmpty { "없음" }}")
        }
    }
}
